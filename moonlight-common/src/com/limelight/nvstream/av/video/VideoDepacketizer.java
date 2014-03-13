package com.limelight.nvstream.av.video;

import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;

import com.limelight.LimeLog;
import com.limelight.nvstream.av.ByteBufferDescriptor;
import com.limelight.nvstream.av.DecodeUnit;
import com.limelight.nvstream.av.RtpPacket;
import com.limelight.nvstream.av.ConnectionStatusListener;

public class VideoDepacketizer {
	
	// Current frame state
	private LinkedList<ByteBufferDescriptor> avcFrameDataChain = null;
	private int avcFrameDataLength = 0;
	private int currentlyDecoding = DecodeUnit.TYPE_UNKNOWN;
	
	// Sequencing state
	private int nextFrameNumber = 1;
	private int nextPacketNumber;
	private int startFrameNumber = 1;
	private boolean waitingForNextSuccessfulFrame;
	private boolean gotNextFrameStart;
	
	// Cached objects
	private ByteBufferDescriptor cachedDesc = new ByteBufferDescriptor(null, 0, 0);
	
	private ConnectionStatusListener controlListener;
	private VideoDecoderRenderer directSubmitDr;
	
	private static final int DU_LIMIT = 15;
	private LinkedBlockingQueue<DecodeUnit> decodedUnits = new LinkedBlockingQueue<DecodeUnit>(DU_LIMIT);
	
	public VideoDepacketizer(VideoDecoderRenderer directSubmitDr, ConnectionStatusListener controlListener)
	{
		this.directSubmitDr = directSubmitDr;
		this.controlListener = controlListener;
	}
	
	private void clearAvcFrameState()
	{
		avcFrameDataChain = null;
		avcFrameDataLength = 0;
	}
	
	private void reassembleAvcFrame(int frameNumber)
	{
		// This is the start of a new frame
		if (avcFrameDataChain != null && avcFrameDataLength != 0) {
			int flags = 0;
			
			ByteBufferDescriptor firstBuffer = avcFrameDataChain.getFirst();
			
			if (NAL.getSpecialSequenceDescriptor(firstBuffer, cachedDesc) && NAL.isAvcFrameStart(cachedDesc)) {
				switch (cachedDesc.data[cachedDesc.offset+cachedDesc.length]) {
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
			DecodeUnit du = new DecodeUnit(DecodeUnit.TYPE_H264, avcFrameDataChain, avcFrameDataLength, flags, frameNumber);
			if (directSubmitDr != null) {
				// Submit directly to the decoder
				directSubmitDr.submitDecodeUnit(du);
			}
			else if (!decodedUnits.offer(du)) {
				LimeLog.warning("Video decoder is too slow! Forced to drop decode units");
				
				// Invalidate all frames from the start of the DU queue
				controlListener.connectionSinkTooSlow(decodedUnits.remove().getFrameNumber(), frameNumber);
				
				// Remove existing frames
				decodedUnits.clear();
				
				// Add this frame
				decodedUnits.add(du);
			}

			// Clear old state
			clearAvcFrameState();
		}
	}
	
	public void addInputDataSlow(VideoPacket packet, ByteBufferDescriptor location)
	{
		while (location.length != 0)
		{
			// Remember the start of the NAL data in this packet
			int start = location.offset;

			// Check for a special sequence
			if (NAL.getSpecialSequenceDescriptor(location, cachedDesc))
			{
				if (NAL.isAvcStartSequence(cachedDesc))
				{
					// We're decoding H264 now
					currentlyDecoding = DecodeUnit.TYPE_H264;

					// Check if it's the end of the last frame
					if (NAL.isAvcFrameStart(cachedDesc))
					{
						// Reassemble any pending AVC NAL
						reassembleAvcFrame(packet.getFrameIndex());

						// Setup state for the new NAL
						avcFrameDataChain = new LinkedList<ByteBufferDescriptor>();
						avcFrameDataLength = 0;
					}

					// Skip the start sequence
					location.length -= cachedDesc.length;
					location.offset += cachedDesc.length;
				}
				else
				{
					// Check if this is padding after a full AVC frame
					if (currentlyDecoding == DecodeUnit.TYPE_H264 &&
						NAL.isPadding(cachedDesc)) {
						// The decode unit is complete
						reassembleAvcFrame(packet.getFrameIndex());
					}

					// Not decoding AVC
					currentlyDecoding = DecodeUnit.TYPE_UNKNOWN;

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
					if (NAL.getSpecialSequenceDescriptor(location, cachedDesc))
					{
						// Only stop if we're decoding something or this
						// isn't padding
						if (currentlyDecoding != DecodeUnit.TYPE_UNKNOWN ||
							!NAL.isPadding(cachedDesc))
						{
							break;
						}
					}
				}

				// This byte is part of the NAL data
				location.offset++;
				location.length--;
			}

			if (currentlyDecoding == DecodeUnit.TYPE_H264 && avcFrameDataChain != null)
			{
				ByteBufferDescriptor data = new ByteBufferDescriptor(location.data, start, location.offset-start);

				// Add a buffer descriptor describing the NAL data in this packet
				avcFrameDataChain.add(data);
				avcFrameDataLength += location.offset-start;
			}
		}
	}
	
	public void addInputDataFast(VideoPacket packet, ByteBufferDescriptor location, boolean firstPacket)
	{
		if (firstPacket) {
			// Setup state for the new frame
			avcFrameDataChain = new LinkedList<ByteBufferDescriptor>();
			avcFrameDataLength = 0;
		}
		
		// Add the payload data to the chain
		avcFrameDataChain.add(location);
		avcFrameDataLength += location.length;
	}
	
	public void addInputData(VideoPacket packet)
	{	
		ByteBufferDescriptor location = packet.getNewPayloadDescriptor();
		
		// Runt packets get decoded using the slow path
		// These packets stand alone so there's no need to verify
		// sequencing before submitting
		if (location.length < 968) {
			addInputDataSlow(packet, location);
            return;
		}
		
		int frameIndex = packet.getFrameIndex();
		int packetIndex = packet.getPacketIndex();
		int packetsInFrame = packet.getTotalPackets();
		
		// We can use FEC to correct single packet errors
		// on single packet frames because we just get a
		// duplicate of the original packet
		if (packetsInFrame == 1 && packetIndex == 1 &&
			nextPacketNumber == 0 && frameIndex == nextFrameNumber) {
			LimeLog.info("Using FEC for error correction");
			nextPacketNumber = 1;
		}
		// Discard the rest of the FEC data until we know how to use it
		else if (packetIndex >= packetsInFrame) {
			return;
		}
		
		// Check that this is the next frame
		boolean firstPacket = (packet.getFlags() & VideoPacket.FLAG_SOF) != 0;
		if (frameIndex > nextFrameNumber) {
			// Nope, but we can still work with it if it's
			// the start of the next frame
			if (firstPacket) {
				LimeLog.warning("Got start of frame "+frameIndex+
						" when expecting packet "+nextPacketNumber+
						" of frame "+nextFrameNumber);
				nextFrameNumber = frameIndex;
				nextPacketNumber = 0;
				clearAvcFrameState();
				
				// Tell the encoder when we're done decoding this frame
				// that we lost some previous frames
				waitingForNextSuccessfulFrame = true;
				gotNextFrameStart = false;
			}
			else {
				LimeLog.warning("Got packet "+packetIndex+" of frame "+frameIndex+
						" when expecting packet "+nextPacketNumber+
						" of frame "+nextFrameNumber);
				// We dropped the start of this frame too
				waitingForNextSuccessfulFrame = true;
				gotNextFrameStart = false;
				
				// Try to pickup on the next frame
				nextFrameNumber = frameIndex + 1;
				nextPacketNumber = 0;
				clearAvcFrameState();
				return;
			}
		}
		else if (frameIndex < nextFrameNumber) {
			LimeLog.info("Frame "+frameIndex+" is behind our current frame number "+nextFrameNumber);
			// Discard the frame silently if it's behind our current sequence number
			return;
		}
		
		// We know it's the right frame, now check the packet number
		if (packetIndex != nextPacketNumber) {
			LimeLog.warning("Frame "+frameIndex+": expected packet "+nextPacketNumber+" but got "+packetIndex);
			// At this point, we're guaranteed that it's not FEC data that we lost
			waitingForNextSuccessfulFrame = true;
			gotNextFrameStart = false;
			
			// Skip this frame
			nextFrameNumber++;
			nextPacketNumber = 0;
			clearAvcFrameState();
			return;
		}
		
		if (waitingForNextSuccessfulFrame) {
			if (!gotNextFrameStart) {
				if (!firstPacket) {
					// We're waiting for the next frame, but this one is a fragment of a frame
					// so we must discard it and wait for the next one
					LimeLog.warning("Expected start of frame "+frameIndex);
					
					nextFrameNumber = frameIndex + 1;
					nextPacketNumber = 0;
					clearAvcFrameState();
					return;
				}
				else {
					gotNextFrameStart = true;
				}
			}
		}
		
		nextPacketNumber++;
		
		// Remove extra padding
		location.length = packet.getPayloadLength();
		
		if (firstPacket)
		{
			if (NAL.getSpecialSequenceDescriptor(location, cachedDesc) && NAL.isAvcFrameStart(cachedDesc)
				&& cachedDesc.data[cachedDesc.offset+cachedDesc.length] == 0x67)
			{
				// SPS and PPS prefix is padded between NALs, so we must decode it with the slow path
				clearAvcFrameState();
				addInputDataSlow(packet, location);
				return;
			}
		}

		addInputDataFast(packet, location, firstPacket);
		
		// We can't use the EOF flag here because real frames can be split across
		// multiple "frames" when packetized to fit under the bandwidth ceiling
		if (packetIndex + 1 >= packetsInFrame) {
	        nextFrameNumber++;
	        nextPacketNumber = 0;
		}
		
		if ((packet.getFlags() & VideoPacket.FLAG_EOF) != 0) {
	        reassembleAvcFrame(packet.getFrameIndex());
			
			if (waitingForNextSuccessfulFrame) {
				// This is the next successful frame after a loss event
				controlListener.connectionDetectedFrameLoss(startFrameNumber, nextFrameNumber - 1);
				waitingForNextSuccessfulFrame = false;
			}
			
	        startFrameNumber = nextFrameNumber;
		}
	}
	
	public void addInputData(RtpPacket packet)
	{
		ByteBufferDescriptor rtpPayload = packet.getNewPayloadDescriptor();
		addInputData(new VideoPacket(rtpPayload));
	}
	
	public DecodeUnit getNextDecodeUnit() throws InterruptedException
	{
		return decodedUnits.take();
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
