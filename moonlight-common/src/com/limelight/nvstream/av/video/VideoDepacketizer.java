package com.limelight.nvstream.av.video;

import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;

import com.limelight.nvstream.av.ByteBufferDescriptor;
import com.limelight.nvstream.av.DecodeUnit;
import com.limelight.nvstream.av.RtpPacket;
import com.limelight.nvstream.av.ConnectionStatusListener;

public class VideoDepacketizer {
	
	// Current NAL state
	private LinkedList<ByteBufferDescriptor> avcNalDataChain = null;
	private int avcNalDataLength = 0;
	private int currentlyDecoding = DecodeUnit.TYPE_UNKNOWN;
	
	// Sequencing state
	private short lastSequenceNumber;
	
	// Cached objects
	private ByteBufferDescriptor cachedDesc = new ByteBufferDescriptor(null, 0, 0);
	
	private ConnectionStatusListener controlListener;
	
	private static final int DU_LIMIT = 15;
	private LinkedBlockingQueue<DecodeUnit> decodedUnits = new LinkedBlockingQueue<DecodeUnit>(DU_LIMIT);
	
	public VideoDepacketizer(ConnectionStatusListener controlListener)
	{
		this.controlListener = controlListener;
	}
	
	private void clearAvcNalState()
	{
		avcNalDataChain = null;
		avcNalDataLength = 0;
	}

	private void reassembleAvcNal()
	{
		// This is the start of a new NAL
		if (avcNalDataChain != null && avcNalDataLength != 0) {
			ByteBufferDescriptor header = avcNalDataChain.getFirst();
			
			// The SPS that comes in the current H264 bytestream doesn't set bitstream_restriction_flag
			// or max_dec_frame_buffering which increases decoding latency on (at least) Tegra
			// and Raspberry Pi. We manually modify the SPS here to speed-up decoding.
			if (header.data[header.offset+4] == 0x67) {
				// It's an SPS
				ByteBufferDescriptor newSps;
				switch (header.length) {
				case 26:
					System.out.println("Modifying SPS (26)");
					newSps = new ByteBufferDescriptor(new byte[header.length+2], 0, header.length+2);
					System.arraycopy(header.data, header.offset, newSps.data, 0, 24);
					newSps.data[24] = 0x11;
					newSps.data[25] = (byte)0xe3;
					newSps.data[26] = 0x06;
					newSps.data[27] = 0x50;
					break;
				case 27:
					System.out.println("Modifying SPS (27)");
					newSps = new ByteBufferDescriptor(new byte[header.length+2], 0, header.length+2);
					System.arraycopy(header.data, header.offset, newSps.data, 0, 25);
					newSps.data[25] = 0x04;
					newSps.data[26] = 0x78;
					newSps.data[27] = (byte) 0xc1;
					newSps.data[28] = (byte) 0x94;
					break;
				default:
					System.out.println("Unknown SPS of length "+header.length);
					newSps = header;
					break;
				}
				
				avcNalDataChain.clear();
				avcNalDataChain.add(newSps);
				avcNalDataLength = newSps.length;
			}
			
			// Construct the H264 decode unit
			DecodeUnit du = new DecodeUnit(DecodeUnit.TYPE_H264, avcNalDataChain, avcNalDataLength, 0);
			if (!decodedUnits.offer(du)) {
				// We need a new IDR frame since we're discarding data now
				System.out.println("Video decoder is too slow! Forced to drop decode units");
				decodedUnits.clear();
				controlListener.connectionNeedsResync();
			}

			// Clear old state
			clearAvcNalState();
		}
	}
	
	/* Currently unused pending bugfixes */
	public void addInputDataO1(VideoPacket packet)
	{
		ByteBufferDescriptor location = packet.getNewPayloadDescriptor();
		
		// SPS and PPS packet doesn't have standard headers, so submit it as is
		if (location.length < 968) {
			avcNalDataChain = new LinkedList<ByteBufferDescriptor>();
			avcNalDataLength = 0;

			avcNalDataChain.add(location);
			avcNalDataLength += location.length;
			
			reassembleAvcNal();
		}
		else {
			int packetIndex = packet.getPacketIndex();
			int packetsInFrame = packet.getTotalPackets();
			
			// Check if this is the first packet for a frame
			if (packetIndex == 0) {
				// Setup state for the new frame
				avcNalDataChain = new LinkedList<ByteBufferDescriptor>();
				avcNalDataLength = 0;
			}

			// Check if this packet falls in the range of packets in frame
			if (packetIndex >= packetsInFrame) {
				// This isn't H264 frame data
				return;
			}

			// Adjust the length to only contain valid data
			location.length = packet.getPayloadLength();
			
			// Add the payload data to the chain
			if (avcNalDataChain != null) {
				avcNalDataChain.add(location);
				avcNalDataLength += location.length;
			}
			
			// Reassemble the NALs if this was the last packet for this frame
			if (packetIndex + 1 == packetsInFrame) {
				reassembleAvcNal();
			}
		}
	}
	
	public void addInputData(VideoPacket packet)
	{
		ByteBufferDescriptor location = packet.getNewPayloadDescriptor();

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
						reassembleAvcNal();

						// Setup state for the new NAL
						avcNalDataChain = new LinkedList<ByteBufferDescriptor>();
						avcNalDataLength = 0;
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
						reassembleAvcNal();
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

			if (currentlyDecoding == DecodeUnit.TYPE_H264 && avcNalDataChain != null)
			{
				ByteBufferDescriptor data = new ByteBufferDescriptor(location.data, start, location.offset-start);

				// Add a buffer descriptor describing the NAL data in this packet
				avcNalDataChain.add(data);
				avcNalDataLength += location.offset-start;
			}
		}
	}

	
	public void addInputData(RtpPacket packet)
	{
		short seq = packet.getSequenceNumber();
		
		// Toss out the current NAL if we receive a packet that is
		// out of sequence
		if (lastSequenceNumber != 0 &&
			(short)(lastSequenceNumber + 1) != seq)
		{
			System.out.println("Received OOS video data (expected "+(lastSequenceNumber + 1)+", got "+seq+")");
			
			// Reset the depacketizer state
			clearAvcNalState();
			
			// Request an IDR frame
			controlListener.connectionNeedsResync();
		}
		
		lastSequenceNumber = seq;
		
		// Pass the payload to the non-sequencing parser
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
