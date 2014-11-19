package com.limelight.nvstream.av.video;

import java.util.HashSet;
import java.util.LinkedList;

import com.limelight.LimeLog;
import com.limelight.nvstream.av.ByteBufferDescriptor;
import com.limelight.nvstream.av.DecodeUnit;
import com.limelight.nvstream.av.ConnectionStatusListener;
import com.limelight.nvstream.av.PopulatedBufferList;
import com.limelight.nvstream.av.SequenceHelper;

public class VideoDepacketizer {
	
	// Current frame state
	private LinkedList<ByteBufferDescriptor> avcFrameDataChain = null;
	private int avcFrameDataLength = 0;
	private HashSet<VideoPacket> packetSet = null;
	
	// Sequencing state
	private int lastPacketInStream = 0;
	private int nextFrameNumber = 1;
	private int startFrameNumber = 1;
	private boolean waitingForNextSuccessfulFrame;
	private boolean waitingForIdrFrame = true;
	private long frameStartTime;
	private boolean decodingFrame;
	
	// Cached objects
	private ByteBufferDescriptor cachedReassemblyDesc = new ByteBufferDescriptor(null, 0, 0);
	private ByteBufferDescriptor cachedSpecialDesc = new ByteBufferDescriptor(null, 0, 0);
	
	private ConnectionStatusListener controlListener;
	private final int nominalPacketDataLength;
	
	private static final int CONSECUTIVE_DROP_LIMIT = 120;
	private int consecutiveFrameDrops = 0;
	
	private static final int DU_LIMIT = 15;
	private PopulatedBufferList<DecodeUnit> decodedUnits;
	
	public VideoDepacketizer(ConnectionStatusListener controlListener, int nominalPacketSize)
	{
		this.controlListener = controlListener;
		this.nominalPacketDataLength = nominalPacketSize - VideoPacket.HEADER_SIZE;
		
		decodedUnits = new PopulatedBufferList<DecodeUnit>(DU_LIMIT, new PopulatedBufferList.BufferFactory() {
			public Object createFreeBuffer() {
				return new DecodeUnit();
			}

			public void cleanupObject(Object o) {
				DecodeUnit du = (DecodeUnit) o;
				
				// Disassociate video packets from this DU
				for (VideoPacket pkt : du.getBackingPackets()) {
					pkt.decodeUnitRefCount.decrementAndGet();
				}
				du.clearBackingPackets();
			}
		});
	}
	
	private void dropAvcFrameState()
	{
		waitingForIdrFrame = true;
		
		// Count the number of consecutive frames dropped
		consecutiveFrameDrops++;
		
		// If we reach our limit, immediately request an IDR frame
		// and reset
		if (consecutiveFrameDrops == CONSECUTIVE_DROP_LIMIT) {
			LimeLog.warning("Reached consecutive drop limit");
			
			// Restart the count
			consecutiveFrameDrops = 0;
			
			// Request an IDR frame
			controlListener.connectionDetectedFrameLoss(0, 0);
		}
		
		cleanupAvcFrameState();
	}
	
	private void cleanupAvcFrameState()
	{
		if (packetSet != null) {
			for (VideoPacket pkt : packetSet) {
				pkt.decodeUnitRefCount.decrementAndGet();
			}
			packetSet = null;
		}
		
		avcFrameDataChain = null;
		avcFrameDataLength = 0;
	}
	
	private void reassembleAvcFrame(int frameNumber)
	{
		// This is the start of a new frame
		if (avcFrameDataChain != null && avcFrameDataLength != 0) {
			ByteBufferDescriptor firstBuffer = avcFrameDataChain.getFirst();
			
			int flags = 0;
			if (NAL.getSpecialSequenceDescriptor(firstBuffer, cachedSpecialDesc) && NAL.isAvcFrameStart(cachedSpecialDesc)) {
				switch (cachedSpecialDesc.data[cachedSpecialDesc.offset+cachedSpecialDesc.length]) {
				case 0x67:
				case 0x68:
					flags |= DecodeUnit.DU_FLAG_CODEC_CONFIG;
					break;
				case 0x65:
					flags |= DecodeUnit.DU_FLAG_SYNC_FRAME;
					break;
				}
			}
			
			// Construct the H264 decode unit
			DecodeUnit du = decodedUnits.pollFreeObject();
			if (du == null) {
				LimeLog.warning("Video decoder is too slow! Forced to drop decode units");

				// Invalidate all frames from the start of the DU queue
				controlListener.connectionSinkTooSlow(0, 0);
				
				// Remove existing frames
				decodedUnits.clearPopulatedObjects();
				
				// Clear frame state and wait for an IDR
				dropAvcFrameState();
				return;
			}
			
			// Initialize the free DU
			du.initialize(DecodeUnit.TYPE_H264, avcFrameDataChain,
					avcFrameDataLength, frameNumber, frameStartTime, flags, packetSet);
			
			// Packets now owned by the DU
			packetSet = null;
			
			controlListener.connectionReceivedFrame(frameNumber);
			
			// Submit the DU to the consumer
			decodedUnits.addPopulatedObject(du);

			// Clear old state
			cleanupAvcFrameState();
			
			// Clear frame drops
			consecutiveFrameDrops = 0;
		}
	}
	
	private void addInputDataSlow(VideoPacket packet, ByteBufferDescriptor location)
	{
		boolean isDecodingH264 = false;
		
		while (location.length != 0)
		{
			// Remember the start of the NAL data in this packet
			int start = location.offset;

			// Check for a special sequence
			if (NAL.getSpecialSequenceDescriptor(location, cachedSpecialDesc))
			{
				if (NAL.isAvcStartSequence(cachedSpecialDesc))
				{
					// We're decoding H264 now
					isDecodingH264 = true;
					
					// Check if it's the end of the last frame
					if (NAL.isAvcFrameStart(cachedSpecialDesc))
					{
						// Update the global state that we're decoding a new frame
						this.decodingFrame = true;
						
						// Reassemble any pending AVC NAL
						reassembleAvcFrame(packet.getFrameIndex());

						// Setup state for the new NAL
						avcFrameDataChain = new LinkedList<ByteBufferDescriptor>();
						avcFrameDataLength = 0;
						packetSet = new HashSet<VideoPacket>();
						
						if (cachedSpecialDesc.data[cachedSpecialDesc.offset+cachedSpecialDesc.length] == 0x65) {
							// This is the NALU code for I-frame data
							waitingForIdrFrame = false;
						}
					}

					// Skip the start sequence
					location.length -= cachedSpecialDesc.length;
					location.offset += cachedSpecialDesc.length;
				}
				else
				{
					// Check if this is padding after a full AVC frame
					if (isDecodingH264 && NAL.isPadding(cachedSpecialDesc)) {
						// The decode unit is complete
						reassembleAvcFrame(packet.getFrameIndex());
					}

					// Not decoding AVC
					isDecodingH264 = false;

					// Just skip this byte
					location.length--;
					location.offset++;
				}
			}

			// Move to the next special sequence
			while (location.length != 0)
			{
				// Catch the easy case first where byte 0 != 0x00
				if (location.data[location.offset] == 0x00)
				{
					// Check if this should end the current NAL
					if (NAL.getSpecialSequenceDescriptor(location, cachedSpecialDesc))
					{
						// Only stop if we're decoding something or this
						// isn't padding
						if (isDecodingH264 || !NAL.isPadding(cachedSpecialDesc))
						{
							break;
						}
					}
				}

				// This byte is part of the NAL data
				location.offset++;
				location.length--;
			}

			if (isDecodingH264 && avcFrameDataChain != null)
			{
				ByteBufferDescriptor data = new ByteBufferDescriptor(location.data, start, location.offset-start);
				
				if (packetSet.add(packet)) {
					packet.decodeUnitRefCount.incrementAndGet();
				}

				// Add a buffer descriptor describing the NAL data in this packet
				avcFrameDataChain.add(data);
				avcFrameDataLength += location.offset-start;
			}
		}
	}
	
	private void addInputDataFast(VideoPacket packet, ByteBufferDescriptor location, boolean firstPacket)
	{
		if (firstPacket) {
			// Setup state for the new frame
			frameStartTime = System.currentTimeMillis();
			avcFrameDataChain = new LinkedList<ByteBufferDescriptor>();
			avcFrameDataLength = 0;
			packetSet = new HashSet<VideoPacket>();
		}
		
		// Add the payload data to the chain
		avcFrameDataChain.add(new ByteBufferDescriptor(location));
		avcFrameDataLength += location.length;
		
		// The receive thread can't use this until we're done with it
		if (packetSet.add(packet)) {
			packet.decodeUnitRefCount.incrementAndGet();
		}
	}
	
	private static boolean isFirstPacket(int flags) {
		// Clear the picture data flag
		flags &= ~VideoPacket.FLAG_CONTAINS_PIC_DATA;
		
		// Check if it's just the start or both start and end of a frame
		return (flags == (VideoPacket.FLAG_SOF | VideoPacket.FLAG_EOF) ||
			flags == VideoPacket.FLAG_SOF);
	}
	
	public void addInputData(VideoPacket packet)
	{
		// Load our reassembly descriptor
		packet.initializePayloadDescriptor(cachedReassemblyDesc);
		
		int flags = packet.getFlags();
		
		int frameIndex = packet.getFrameIndex();
		boolean firstPacket = isFirstPacket(flags);
		
		// Drop duplicates or re-ordered packets
		int streamPacketIndex = packet.getStreamPacketIndex();
		if (SequenceHelper.isBeforeSigned((short)streamPacketIndex, (short)(lastPacketInStream + 1), false)) {
			return;
		}
		
		// Drop packets from a previously completed frame
		if (SequenceHelper.isBeforeSigned(frameIndex, nextFrameNumber, false)) {
			return;
		}
		
		// Look for a frame start before receiving a frame end
		if (firstPacket && decodingFrame)
		{
			LimeLog.warning("Network dropped end of a frame");
			nextFrameNumber = frameIndex;
			
			// Unexpected start of next frame before terminating the last
			waitingForNextSuccessfulFrame = true;
			waitingForIdrFrame = true;
			
			// Clear the old state and wait for an IDR
			dropAvcFrameState();
		}
		// Look for a non-frame start before a frame start
		else if (!firstPacket && !decodingFrame) {
			// Check if this looks like a real frame
			if (flags == VideoPacket.FLAG_CONTAINS_PIC_DATA ||
				flags == VideoPacket.FLAG_EOF ||
				cachedReassemblyDesc.length < nominalPacketDataLength)
			{
				LimeLog.warning("Network dropped beginning of a frame");
				nextFrameNumber = frameIndex + 1;
				
				waitingForNextSuccessfulFrame = true;
				
				dropAvcFrameState();
				decodingFrame = false;
				return;
			}
			else {
				// FEC data
				return;
			}
		}
		// Check sequencing of this frame to ensure we didn't
		// miss one in between
		else if (firstPacket) {
			// Make sure this is the next consecutive frame
			if (SequenceHelper.isBeforeSigned(nextFrameNumber, frameIndex, true)) {
				LimeLog.warning("Network dropped an entire frame");
				nextFrameNumber = frameIndex;
				
				// Wait until an IDR frame comes
				waitingForNextSuccessfulFrame = true;
				dropAvcFrameState();
			}
			else if (nextFrameNumber != frameIndex) {
				// Duplicate packet or FEC dup
				decodingFrame = false;
				return;
			}
			
			// We're now decoding a frame
			decodingFrame = true;
		}
		
		// If it's not the first packet of a frame
		// we need to drop it if the stream packet index
		// doesn't match
		if (!firstPacket && decodingFrame) {
			if (streamPacketIndex != (int)(lastPacketInStream + 1)) {
				LimeLog.warning("Network dropped middle of a frame");
				nextFrameNumber = frameIndex + 1;
				
				waitingForNextSuccessfulFrame = true;
				
				dropAvcFrameState();
				decodingFrame = false;
				
				return;
			}
		}
		
		// Notify the server of any packet losses
		if (streamPacketIndex != (int)(lastPacketInStream + 1)) {
			// Packets were lost so report this to the server
			controlListener.connectionLostPackets(lastPacketInStream, streamPacketIndex);
		}
		lastPacketInStream = streamPacketIndex;
		
		if (firstPacket
				&& NAL.getSpecialSequenceDescriptor(cachedReassemblyDesc, cachedSpecialDesc)
				&& NAL.isAvcFrameStart(cachedSpecialDesc)
				&& cachedSpecialDesc.data[cachedSpecialDesc.offset+cachedSpecialDesc.length] == 0x67)
		{
			// The slow path doesn't update the frame start time by itself
			frameStartTime = System.currentTimeMillis();
			
			// SPS and PPS prefix is padded between NALs, so we must decode it with the slow path
			addInputDataSlow(packet, cachedReassemblyDesc);
		}
		else
		{
			// Everything else can take the fast path
			addInputDataFast(packet, cachedReassemblyDesc, firstPacket);
		}
		
		if ((flags & VideoPacket.FLAG_EOF) != 0) {
	        // Move on to the next frame
	        decodingFrame = false;
	        nextFrameNumber = frameIndex + 1;
			
			// If waiting for next successful frame and we got here
			// with an end flag, we can send a message to the server
			if (waitingForNextSuccessfulFrame) {
				// This is the next successful frame after a loss event
				controlListener.connectionDetectedFrameLoss(startFrameNumber, nextFrameNumber - 1);
				waitingForNextSuccessfulFrame = false;
			}
			
			// If we need an IDR frame first, then drop this frame
			if (waitingForIdrFrame) {
				LimeLog.warning("Waiting for IDR frame");
				
				dropAvcFrameState();
				return;
			}
			
	        reassembleAvcFrame(frameIndex);
			
	        startFrameNumber = nextFrameNumber;
		}
	}
	
	public DecodeUnit takeNextDecodeUnit() throws InterruptedException
	{
		return decodedUnits.takePopulatedObject();
	}
	
	public DecodeUnit pollNextDecodeUnit()
	{
		return decodedUnits.pollPopulatedObject();
	}
	
	public void freeDecodeUnit(DecodeUnit du)
	{	
		decodedUnits.freePopulatedObject(du);
	}
}

class NAL {
	
	// This assumes that the buffer passed in is already a special sequence
	public static boolean isAvcStartSequence(ByteBufferDescriptor specialSeq)
	{
		// The start sequence is 00 00 01 or 00 00 00 01
		return (specialSeq.data[specialSeq.offset+specialSeq.length-1] == 0x01);
	}
	
	// This assumes that the buffer passed in is already a special sequence
	public static boolean isAvcFrameStart(ByteBufferDescriptor specialSeq)
	{
		if (specialSeq.length != 4)
			return false;
		
		// The frame start sequence is 00 00 00 01
		return (specialSeq.data[specialSeq.offset+specialSeq.length-1] == 0x01);
	}
	
	// This assumes that the buffer passed in is already a special sequence
    public static boolean isPadding(ByteBufferDescriptor specialSeq)
    {
            // The padding sequence is 00 00 00
            return (specialSeq.data[specialSeq.offset+specialSeq.length-1] == 0x00);
    }
	
	// Returns a buffer descriptor describing the start sequence
	public static boolean getSpecialSequenceDescriptor(ByteBufferDescriptor buffer, ByteBufferDescriptor outputDesc)
	{
		// NAL start sequence is 00 00 00 01 or 00 00 01
		if (buffer.length < 3)
			return false;
		
		// 00 00 is magic
		if (buffer.data[buffer.offset] == 0x00 &&
			buffer.data[buffer.offset+1] == 0x00)
		{
			// Another 00 could be the end of the special sequence
			// 00 00 00 or the middle of 00 00 00 01
			if (buffer.data[buffer.offset+2] == 0x00)
			{
				if (buffer.length >= 4 &&
					buffer.data[buffer.offset+3] == 0x01)
				{
					// It's the AVC start sequence 00 00 00 01
					outputDesc.reinitialize(buffer.data, buffer.offset, 4);
				}
				else
				{
					// It's 00 00 00
					outputDesc.reinitialize(buffer.data, buffer.offset, 3);
				}
				return true;
			}
			else if (buffer.data[buffer.offset+2] == 0x01 ||
					 buffer.data[buffer.offset+2] == 0x02)
			{
				// These are easy: 00 00 01 or 00 00 02
				outputDesc.reinitialize(buffer.data, buffer.offset, 3);
				return true;
			}
			else if (buffer.data[buffer.offset+2] == 0x03)
			{
				// 00 00 03 is special because it's a subsequence of the
				// NAL wrapping substitute for 00 00 00, 00 00 01, 00 00 02,
				// or 00 00 03 in the RBSP sequence. We need to check the next
				// byte to see whether it's 00, 01, 02, or 03 (a valid RBSP substitution)
				// or whether it's something else
				
				if (buffer.length < 4)
					return false;
				
				if (buffer.data[buffer.offset+3] >= 0x00 &&
					buffer.data[buffer.offset+3] <= 0x03)
				{
					// It's not really a special sequence after all
					return false;
				}
				else
				{
					// It's not a standard replacement so it's a special sequence
					outputDesc.reinitialize(buffer.data, buffer.offset, 3);
					return true;
				}
			}
		}
		
		return false;
	}
}
