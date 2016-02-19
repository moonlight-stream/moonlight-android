package com.limelight.nvstream.av.video;

import com.limelight.LimeLog;
import com.limelight.nvstream.ConnectionContext;
import com.limelight.nvstream.av.ByteBufferDescriptor;
import com.limelight.nvstream.av.DecodeUnit;
import com.limelight.nvstream.av.ConnectionStatusListener;
import com.limelight.nvstream.av.SequenceHelper;
import com.limelight.nvstream.av.buffer.AbstractPopulatedBufferList;
import com.limelight.nvstream.av.buffer.AtomicPopulatedBufferList;
import com.limelight.nvstream.av.buffer.UnsynchronizedPopulatedBufferList;
import com.limelight.utils.TimeHelper;

public class VideoDepacketizer {
	
	// Current frame state
	private int frameDataLength = 0;
	private ByteBufferDescriptor frameDataChainHead;
	private ByteBufferDescriptor frameDataChainTail;
	private VideoPacket backingPacketHead;
	private VideoPacket backingPacketTail;
	
	// Sequencing state
	private int lastPacketInStream = -1;
	private int nextFrameNumber = 1;
	private int startFrameNumber = 0;
	private boolean waitingForNextSuccessfulFrame;
	private boolean waitingForIdrFrame = true;
	private long frameStartTime;
	private boolean decodingFrame;
	private boolean strictIdrFrameWait;
	
	// Cached objects
	private ByteBufferDescriptor cachedReassemblyDesc = new ByteBufferDescriptor(null, 0, 0);
	private ByteBufferDescriptor cachedSpecialDesc = new ByteBufferDescriptor(null, 0, 0);
	
	private ConnectionStatusListener controlListener;
	private final int nominalPacketDataLength;
	
	private static final int CONSECUTIVE_DROP_LIMIT = 120;
	private int consecutiveFrameDrops = 0;
	
	private static final int DU_LIMIT = 15;
	private AbstractPopulatedBufferList<DecodeUnit> decodedUnits;
	
	private final int frameHeaderOffset;
	
	public VideoDepacketizer(ConnectionContext context, ConnectionStatusListener controlListener, int nominalPacketSize)
	{
		this.controlListener = controlListener;
		this.nominalPacketDataLength = nominalPacketSize - VideoPacket.HEADER_SIZE;
		
		if (context.serverGeneration >= ConnectionContext.SERVER_GENERATION_5) {
			// Gen 5 servers have an 8 byte header in the data portion of the first
			// packet of each frame
			frameHeaderOffset = 8;
		}
		else {
			frameHeaderOffset = 0;
		}
		
		boolean unsynchronized;
		if (context.videoDecoderRenderer != null) {
			int videoCaps = context.videoDecoderRenderer.getCapabilities();
			this.strictIdrFrameWait = (videoCaps & VideoDecoderRenderer.CAPABILITY_REFERENCE_FRAME_INVALIDATION) == 0;
			unsynchronized = (videoCaps & VideoDecoderRenderer.CAPABILITY_DIRECT_SUBMIT) != 0;
		}
		else {
			// If there's no renderer, it doesn't matter if we synchronize or wait for IDRs
			this.strictIdrFrameWait = false;
			unsynchronized = true;
		}
		
		AbstractPopulatedBufferList.BufferFactory factory = new AbstractPopulatedBufferList.BufferFactory() {
			public Object createFreeBuffer() {
				return new DecodeUnit();
			}

			public void cleanupObject(Object o) {
				DecodeUnit du = (DecodeUnit) o;
				
				// Disassociate video packets from this DU
				VideoPacket pkt;
				while ((pkt = du.removeBackingPacketHead()) != null) {
					pkt.dereferencePacket();
				}
			}
		};
		
		if (unsynchronized) {
			decodedUnits = new UnsynchronizedPopulatedBufferList<DecodeUnit>(DU_LIMIT, factory);
		}
		else {
			decodedUnits = new AtomicPopulatedBufferList<DecodeUnit>(DU_LIMIT, factory);
		}
	}
	
	private void dropFrameState()
	{
		// We'll need an IDR frame now if we're in strict mode
		if (strictIdrFrameWait) {
			waitingForIdrFrame = true;	
		}
		
		// Count the number of consecutive frames dropped
		consecutiveFrameDrops++;
		
		// If we reach our limit, immediately request an IDR frame
		// and reset
		if (consecutiveFrameDrops == CONSECUTIVE_DROP_LIMIT) {
			LimeLog.warning("Reached consecutive drop limit");
			
			// Restart the count
			consecutiveFrameDrops = 0;
			
			// Request an IDR frame (0 tuple always generates an IDR frame)
			controlListener.connectionDetectedFrameLoss(0, 0);
		}
		
		cleanupFrameState();
	}
	
	private void cleanupFrameState()
	{
		backingPacketTail = null;
		while (backingPacketHead != null) {
			backingPacketHead.dereferencePacket();
			backingPacketHead = backingPacketHead.nextPacket;
		}
		
		frameDataChainHead = frameDataChainTail = null;
		frameDataLength = 0;
	}
	
	private void reassembleFrame(int frameNumber)
	{
		// This is the start of a new frame
		if (frameDataChainHead != null) {
			ByteBufferDescriptor firstBuffer = frameDataChainHead;
			
			int flags = 0;
			if (NAL.getSpecialSequenceDescriptor(firstBuffer, cachedSpecialDesc) && NAL.isAnnexBFrameStart(cachedSpecialDesc)) {
				switch (cachedSpecialDesc.data[cachedSpecialDesc.offset+cachedSpecialDesc.length]) {
				
				// H265
				case 0x40: // VPS
				case 0x42: // SPS
				case 0x44: // PPS
					flags |= DecodeUnit.DU_FLAG_CODEC_CONFIG;
					break;
				case 0x26: // I-frame
					flags |= DecodeUnit.DU_FLAG_SYNC_FRAME;
					break;
				
				// H264
				case 0x67: // SPS
				case 0x68: // PPS
					flags |= DecodeUnit.DU_FLAG_CODEC_CONFIG;
					break;
				case 0x65: // I-frame
					flags |= DecodeUnit.DU_FLAG_SYNC_FRAME;
					break;
				}
			}
			
			// Construct the video decode unit
			DecodeUnit du = decodedUnits.pollFreeObject();
			if (du == null) {
				LimeLog.warning("Video decoder is too slow! Forced to drop decode units");

				// Invalidate all frames from the start of the DU queue
				// (0 tuple always generates an IDR frame)
				controlListener.connectionSinkTooSlow(0, 0);
				waitingForIdrFrame = true;
				
				// Remove existing frames
				decodedUnits.clearPopulatedObjects();
				
				// Clear frame state and wait for an IDR
				dropFrameState();
				return;
			}
			
			// Initialize the free DU
			du.initialize(frameDataChainHead, frameDataLength, frameNumber,
					frameStartTime, flags, backingPacketHead);
			
			// Packets now owned by the DU
			backingPacketTail = backingPacketHead = null;
			
			controlListener.connectionReceivedCompleteFrame(frameNumber);
			
			// Submit the DU to the consumer
			decodedUnits.addPopulatedObject(du);

			// Clear old state
			cleanupFrameState();
			
			// Clear frame drops
			consecutiveFrameDrops = 0;
		}
	}
	
	private void chainBufferToCurrentFrame(ByteBufferDescriptor desc) {
		desc.nextDescriptor = null;

		// Chain the packet
		if (frameDataChainTail != null) {
			frameDataChainTail.nextDescriptor = desc;
			frameDataChainTail = desc;
		}
		else {
			frameDataChainHead = frameDataChainTail = desc;
		}
		
		frameDataLength += desc.length;
	}
	
	private void chainPacketToCurrentFrame(VideoPacket packet) {
		packet.referencePacket();
		packet.nextPacket = null;

		// Chain the packet
		if (backingPacketTail != null) {
			backingPacketTail.nextPacket = packet;
			backingPacketTail = packet;
		}
		else {
			backingPacketHead = backingPacketTail = packet;
		}
	}
	
	private void addInputDataSlow(VideoPacket packet, ByteBufferDescriptor location)
	{
		boolean isDecodingVideoData = false;
		
		while (location.length != 0)
		{
			// Remember the start of the NAL data in this packet
			int start = location.offset;

			// Check for a special sequence
			if (NAL.getSpecialSequenceDescriptor(location, cachedSpecialDesc))
			{
				if (NAL.isAnnexBStartSequence(cachedSpecialDesc))
				{
					// We're decoding video data now
					isDecodingVideoData = true;
					
					// Check if it's the end of the last frame
					if (NAL.isAnnexBFrameStart(cachedSpecialDesc))
					{
						// Update the global state that we're decoding a new frame
						this.decodingFrame = true;
						
						// Reassemble any pending NAL
						reassembleFrame(packet.getFrameIndex());
						
						if (cachedSpecialDesc.data[cachedSpecialDesc.offset+cachedSpecialDesc.length] == 0x65 || // H264 I-Frame
								cachedSpecialDesc.data[cachedSpecialDesc.offset+cachedSpecialDesc.length] == 0x26) { // H265 I-Frame
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
					// Check if this is padding after a full video frame
					if (isDecodingVideoData && NAL.isPadding(cachedSpecialDesc)) {
						// The decode unit is complete
						reassembleFrame(packet.getFrameIndex());
					}

					// Not decoding video
					isDecodingVideoData = false;

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
						if (isDecodingVideoData || !NAL.isPadding(cachedSpecialDesc))
						{
							break;
						}
					}
				}

				// This byte is part of the NAL data
				location.offset++;
				location.length--;
			}

			if (isDecodingVideoData && decodingFrame)
			{
				// The slow path may result in multiple decode units per packet.
				// The VideoPacket objects only support being in 1 DU list, so we'll
				// copy this data into a new array rather than reference the packet, if
				// this NALU ends before the end of the frame. Only copying if this doesn't
				// go to the end of the frame means we'll be only copying the SPS and PPS which
				// are quite small, while the actual I-frame data is referenced via the packet.
				if (location.length != 0) {
					// Copy the packet data into a new array
					byte[] dataCopy = new byte[location.offset-start];
					System.arraycopy(location.data, start, dataCopy, 0, dataCopy.length);

					// Chain a descriptor referencing the copied data
					chainBufferToCurrentFrame(new ByteBufferDescriptor(dataCopy, 0, dataCopy.length));
				}
				else {
					// Chain this packet to the current frame
					chainPacketToCurrentFrame(packet);
					
					// Add a buffer descriptor describing the NAL data in this packet
					chainBufferToCurrentFrame(new ByteBufferDescriptor(location.data, start, location.offset-start));
				}
			}
		}
	}
	
	private void addInputDataFast(VideoPacket packet, ByteBufferDescriptor location, boolean firstPacket)
	{
		if (firstPacket) {
			// Setup state for the new frame
			frameStartTime = TimeHelper.getMonotonicMillis();
		}
		
		// Add the payload data to the chain
		chainBufferToCurrentFrame(new ByteBufferDescriptor(location));
		
		// The receive thread can't use this until we're done with it
		chainPacketToCurrentFrame(packet);
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
		
		// Notify the listener of the latest frame we've seen from the PC
		controlListener.connectionSawFrame(frameIndex);
		
		// Look for a frame start before receiving a frame end
		if (firstPacket && decodingFrame)
		{
			LimeLog.warning("Network dropped end of a frame");
			nextFrameNumber = frameIndex;
			
			// Unexpected start of next frame before terminating the last
			waitingForNextSuccessfulFrame = true;
			
			// Clear the old state and wait for an IDR
			dropFrameState();
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
				
				dropFrameState();
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
				dropFrameState();
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
				
				dropFrameState();
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
		
		// If this is the first packet, skip the frame header (if one exists)
		if (firstPacket) {
			cachedReassemblyDesc.offset += frameHeaderOffset;
			cachedReassemblyDesc.length -= frameHeaderOffset;
		}
		
		if (firstPacket && isIdrFrameStart(cachedReassemblyDesc))
		{
			// The slow path doesn't update the frame start time by itself
			frameStartTime = TimeHelper.getMonotonicMillis();
			
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
				
				dropFrameState();
				return;
			}
			
	        reassembleFrame(frameIndex);
			
	        startFrameNumber = nextFrameNumber;
		}
	}
	
	private boolean isIdrFrameStart(ByteBufferDescriptor desc) {
		return NAL.getSpecialSequenceDescriptor(desc, cachedSpecialDesc) &&
				NAL.isAnnexBFrameStart(cachedSpecialDesc) &&
				(cachedSpecialDesc.data[cachedSpecialDesc.offset+cachedSpecialDesc.length] == 0x67 || // H264 SPS
				 cachedSpecialDesc.data[cachedSpecialDesc.offset+cachedSpecialDesc.length] == 0x40); // H265 VPS
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
	public static boolean isAnnexBStartSequence(ByteBufferDescriptor specialSeq)
	{
		// The start sequence is 00 00 01 or 00 00 00 01
		return (specialSeq.data[specialSeq.offset+specialSeq.length-1] == 0x01);
	}
	
	// This assumes that the buffer passed in is already a special sequence
	public static boolean isAnnexBFrameStart(ByteBufferDescriptor specialSeq)
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
					// It's the Annex B start sequence 00 00 00 01
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
