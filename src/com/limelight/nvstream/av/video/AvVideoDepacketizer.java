package com.limelight.nvstream.av.video;

import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;

import com.limelight.nvstream.av.AvByteBufferDescriptor;
import com.limelight.nvstream.av.AvDecodeUnit;
import com.limelight.nvstream.av.AvRtpPacket;
import com.limelight.nvstream.av.ConnectionStatusListener;

public class AvVideoDepacketizer {
	
	// Current NAL state
	private LinkedList<AvByteBufferDescriptor> avcNalDataChain = null;
	private int avcNalDataLength = 0;
	
	// Sequencing state
	private short lastSequenceNumber;
	
	private ConnectionStatusListener controlListener;
	
	private static final int DU_LIMIT = 15;
	private LinkedBlockingQueue<AvDecodeUnit> decodedUnits = new LinkedBlockingQueue<AvDecodeUnit>(DU_LIMIT);
	
	public AvVideoDepacketizer(ConnectionStatusListener controlListener)
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
			// Construct the H264 decode unit
			AvDecodeUnit du = new AvDecodeUnit(AvDecodeUnit.TYPE_H264, avcNalDataChain, avcNalDataLength, 0);
			if (!decodedUnits.offer(du)) {
				// We need a new IDR frame since we're discarding data now
				decodedUnits.clear();
				controlListener.connectionNeedsResync();
			}
			
			// Clear old state
			clearAvcNalState();
		}
	}
	
	public void addInputData(AvVideoPacket packet)
	{
		AvByteBufferDescriptor location = packet.getNewPayloadDescriptor();
		
		// SPS and PPS packet doesn't have standard headers, so submit it as is
		if (location.length < 968) {
			avcNalDataChain = new LinkedList<AvByteBufferDescriptor>();
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
				avcNalDataChain = new LinkedList<AvByteBufferDescriptor>();
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
	
	public void addInputData(AvRtpPacket packet)
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
		AvByteBufferDescriptor rtpPayload = packet.getNewPayloadDescriptor();
		addInputData(new AvVideoPacket(rtpPayload));
	}
	
	public AvDecodeUnit getNextDecodeUnit() throws InterruptedException
	{
		return decodedUnits.take();
	}
}

class NAL {
	
	// This assumes that the buffer passed in is already a special sequence
	public static boolean isAvcStartSequence(AvByteBufferDescriptor specialSeq)
	{
		// The start sequence is 00 00 01 or 00 00 00 01
		return (specialSeq.data[specialSeq.offset+specialSeq.length-1] == 0x01);
	}
	
	// This assumes that the buffer passed in is already a special sequence
	public static boolean isAvcFrameStart(AvByteBufferDescriptor specialSeq)
	{
		if (specialSeq.length != 4)
			return false;
		
		// The frame start sequence is 00 00 00 01
		return (specialSeq.data[specialSeq.offset+specialSeq.length-1] == 0x01);
	}
	
	// Returns a buffer descriptor describing the start sequence
	public static boolean getSpecialSequenceDescriptor(AvByteBufferDescriptor buffer, AvByteBufferDescriptor outputDesc)
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
