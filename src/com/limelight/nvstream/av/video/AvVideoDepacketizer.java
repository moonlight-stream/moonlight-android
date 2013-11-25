package com.limelight.nvstream.av.video;

import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;

import com.limelight.nvstream.av.AvByteBufferDescriptor;
import com.limelight.nvstream.av.AvDecodeUnit;
import com.limelight.nvstream.av.AvRtpPacket;
import com.limelight.nvstream.av.ConnectionStatusListener;

import android.media.MediaCodec;

public class AvVideoDepacketizer {
	
	// Current NAL state
	private LinkedList<AvByteBufferDescriptor> avcNalDataChain = null;
	private int avcNalDataLength = 0;
	private int currentlyDecoding;
	
	// Cached buffer descriptor to save on allocations
	// Only safe to use in decode thread!!!!
	private AvByteBufferDescriptor cachedDesc;
	
	// Sequencing state
	private short lastSequenceNumber;
	
	private ConnectionStatusListener controlListener;
	
	private static final int DU_LIMIT = 15;
	private LinkedBlockingQueue<AvDecodeUnit> decodedUnits = new LinkedBlockingQueue<AvDecodeUnit>(DU_LIMIT);
	
	public AvVideoDepacketizer(ConnectionStatusListener controlListener)
	{
		this.controlListener = controlListener;
		this.cachedDesc = new AvByteBufferDescriptor(null, 0, 0);
	}
	
	private void clearAvcNalState()
	{
		avcNalDataChain = null;
		avcNalDataLength = 0;
	}

	private void reassembleAvcNal()
	{
		// This is the start of a new NAL
		if (avcNalDataChain != null && avcNalDataLength != 0)
		{
			int flags = 0;
			
			// Check if this is a special NAL unit
			AvByteBufferDescriptor header = avcNalDataChain.getFirst();
			
			if (NAL.getSpecialSequenceDescriptor(header, cachedDesc))
			{
				// The next byte after the special sequence is the NAL header
				byte nalHeader = cachedDesc.data[cachedDesc.offset+cachedDesc.length];
				
				switch (nalHeader)
				{
				// SPS and PPS
				case 0x67:
				case 0x68:
					System.out.println("Codec config");
					flags |= MediaCodec.BUFFER_FLAG_CODEC_CONFIG;
					break;
					
				// IDR
				case 0x65:
					System.out.println("Reference frame");
					flags |= MediaCodec.BUFFER_FLAG_SYNC_FRAME;
					break;
					
				// non-IDR frame
				case 0x61:
					break;
					
				// Unknown type
				default:
					System.out.printf("Unknown NAL header: %02x %02x %02x %02x %02x\n",
						header.data[header.offset], header.data[header.offset+1],
						header.data[header.offset+2], header.data[header.offset+3],
						header.data[header.offset+4]);
					break;
				}
			}
			else
			{
				System.out.printf("Invalid NAL: %02x %02x %02x %02x %02x\n",
						header.data[header.offset], header.data[header.offset+1],
						header.data[header.offset+2], header.data[header.offset+3],
						header.data[header.offset+4]);
			}

			// Construct the H264 decode unit
			AvDecodeUnit du = new AvDecodeUnit(AvDecodeUnit.TYPE_H264, avcNalDataChain, avcNalDataLength, flags);
			if (!decodedUnits.offer(du)) {
				// We need a new IDR frame since we're discarding data now
				decodedUnits.clear();
				controlListener.connectionNeedsResync();
			}
			
			// Clear old state
			avcNalDataChain = null;
			avcNalDataLength = 0;
		}
	}
	
	public void addInputData(AvVideoPacket packet)
	{
		AvByteBufferDescriptor location = packet.getNewPayloadDescriptor();
		
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
					currentlyDecoding = AvDecodeUnit.TYPE_H264;
					
					// Check if it's the end of the last frame
					if (NAL.isAvcFrameStart(cachedDesc))
					{
						// Reassemble any pending AVC NAL
						reassembleAvcNal();
						
						// Setup state for the new NAL
						avcNalDataChain = new LinkedList<AvByteBufferDescriptor>();
						avcNalDataLength = 0;
					}
					
					// Skip the start sequence
					location.length -= cachedDesc.length;
					location.offset += cachedDesc.length;
				}
				else
				{
					// Check if this is padding after a full AVC frame
					if (currentlyDecoding == AvDecodeUnit.TYPE_H264 &&
						NAL.isPadding(cachedDesc)) {
						// The decode unit is complete
						reassembleAvcNal();
					}

					// Not decoding AVC
					currentlyDecoding = AvDecodeUnit.TYPE_UNKNOWN;

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
						if (currentlyDecoding != AvDecodeUnit.TYPE_UNKNOWN ||
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
			
			if (currentlyDecoding == AvDecodeUnit.TYPE_H264 && avcNalDataChain != null)
			{
				AvByteBufferDescriptor data = new AvByteBufferDescriptor(location.data, start, location.offset-start);
				
				// Add a buffer descriptor describing the NAL data in this packet
				avcNalDataChain.add(data);
				avcNalDataLength += location.offset-start;
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
			currentlyDecoding = AvDecodeUnit.TYPE_UNKNOWN;
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
	public static boolean isPadding(AvByteBufferDescriptor specialSeq)
	{
		// The padding sequence is 00 00 00
		return (specialSeq.data[specialSeq.offset+specialSeq.length-1] == 0x00);
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
