package com.limelight.nvstream.av;

import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;

import android.media.MediaCodec;

public class AvDepacketizer {
	
	// Current NAL state
	private LinkedList<AvBufferDescriptor> avcNalDataChain = null;
	private int avcNalDataLength = 0;
	private LinkedList<AvBufferDescriptor> aacNalDataChain = null;
	private int aacNalDataLength = 0;
	private int currentlyDecoding;
	
	// Sequencing state
	private short lastSequenceNumber;
	
	private LinkedBlockingQueue<AvDecodeUnit> decodedUnits = new LinkedBlockingQueue<AvDecodeUnit>();
	
	private void reassembleAacNal()
	{
		// This is the start of a new AAC NAL
		if (aacNalDataChain != null && aacNalDataLength != 0)
		{
			System.out.println("Assembling AAC NAL: "+aacNalDataLength);
			
			/*AvBufferDescriptor header = aacNalDataChain.getFirst();
			for (int i = 0; i < header.length; i++)
				System.out.printf("%02x ", header.data[header.offset+i]);
			System.out.println();*/
			
			// Construct the AAC decode unit
			AvDecodeUnit du = new AvDecodeUnit(AvDecodeUnit.TYPE_AAC, aacNalDataChain, aacNalDataLength, 0);
			decodedUnits.add(du);
			
			// Clear old state
			aacNalDataChain = null;
			aacNalDataLength = 0;
		}
	}
	
	private void reassembleAvcNal()
	{
		// This is the start of a new NAL
		if (avcNalDataChain != null && avcNalDataLength != 0)
		{
			int flags = 0;
			
			// Check if this is a special NAL unit
			AvBufferDescriptor header = avcNalDataChain.getFirst();
			AvBufferDescriptor specialSeq = NAL.getSpecialSequenceDescriptor(header);
			
			if (specialSeq != null)
			{
				// The next byte after the special sequence is the NAL header
				byte nalHeader = specialSeq.data[specialSeq.offset+specialSeq.length];
				
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
			decodedUnits.add(du);
			
			// Clear old state
			avcNalDataChain = null;
			avcNalDataLength = 0;
		}
	}
	
	public void addInputData(AvPacket packet)
	{
		AvBufferDescriptor location = packet.getNewPayloadDescriptor();
		
		while (location.length != 0)
		{
			// Remember the start of the NAL data in this packet
			int start = location.offset;
			
			// Check for a special sequence
			AvBufferDescriptor specialSeq = NAL.getSpecialSequenceDescriptor(location);
			if (specialSeq != null)
			{
				if (NAL.isAvcStartSequence(specialSeq))
				{
					// We're decoding H264 now
					currentlyDecoding = AvDecodeUnit.TYPE_H264;
					
					// Check if it's the end of the last frame
					if (NAL.isAvcFrameStart(specialSeq))
					{
						// Reassemble any pending AVC NAL
						reassembleAvcNal();
						
						// Setup state for the new NAL
						avcNalDataChain = new LinkedList<AvBufferDescriptor>();
						avcNalDataLength = 0;
					}
				}
				else if (NAL.isAacStartSequence(specialSeq))
				{
					// We're decoding AAC now
					currentlyDecoding = AvDecodeUnit.TYPE_AAC;
					
					// Reassemble any pending AAC NAL
					reassembleAacNal();
					
					// Setup state for the new NAL
					aacNalDataChain = new LinkedList<AvBufferDescriptor>();
					aacNalDataLength = 0;
				}
				else
				{
					// Not either sequence we want
					//currentlyDecoding = AvDecodeUnit.TYPE_UNKNOWN;
				}
				
				// Skip the start sequence
				location.length -= specialSeq.length;
				location.offset += specialSeq.length;
			}
			
			// Move to the next special sequence
			while (location.length != 0)
			{
				specialSeq = NAL.getSpecialSequenceDescriptor(location);
				
				// Check if this should end the current NAL
				if (specialSeq != null)
				{
					break;
				}
				else
				{
					// This byte is part of the NAL data
					location.offset++;
					location.length--;
				}
			}
			
			AvBufferDescriptor data = new AvBufferDescriptor(location.data, start, location.offset-start);
			
			if (currentlyDecoding == AvDecodeUnit.TYPE_H264 && avcNalDataChain != null)
			{
				// Add a buffer descriptor describing the NAL data in this packet
				avcNalDataChain.add(data);
				avcNalDataLength += location.offset-start;
			}
			else if (currentlyDecoding == AvDecodeUnit.TYPE_AAC && aacNalDataChain != null)
			{
				// Add a buffer descriptor describing the NAL data in this packet
				aacNalDataChain.add(data);
				aacNalDataLength += location.offset-start;
			}
		}
	}
	
	public void addInputData(AvRtpPacket packet)
	{
		short seq = packet.getSequenceNumber();
		
		// Toss out the current NAL if we receive a packet that is
		// out of sequence
		if (lastSequenceNumber != 0 &&
			lastSequenceNumber + 1 != seq)
		{
			System.out.println("Received OOS data (expected "+(lastSequenceNumber + 1)+", got "+seq+")");
			
			// Reset the depacketizer state
			currentlyDecoding = AvDecodeUnit.TYPE_UNKNOWN;
			avcNalDataChain = null;
			avcNalDataLength = 0;
			aacNalDataChain = null;
			aacNalDataLength = 0;
		}
		
		lastSequenceNumber = seq;
		
		// Pass the payload to the non-sequencing parser
		AvBufferDescriptor rtpPayload = packet.getNewPayloadDescriptor();
		addInputData(new AvPacket(rtpPayload));
	}
	
	public AvDecodeUnit getNextDecodeUnit() throws InterruptedException
	{
		return decodedUnits.take();
	}
}

class NAL {
	
	// This assumes that the buffer passed in is already a special sequence
	public static boolean isAvcStartSequence(AvBufferDescriptor specialSeq)
	{
		if (specialSeq.length != 3 && specialSeq.length != 4)
			return false;
		
		// The start sequence is 00 00 01 or 00 00 00 01
		return (specialSeq.data[specialSeq.offset+specialSeq.length-1] == 0x01);
	}
	
	// This assumes that the buffer passed in is already a special sequence
	public static boolean isAacStartSequence(AvBufferDescriptor specialSeq)
	{
		if (specialSeq.length != 3)
			return false;
		
		// The start sequence is 00 00 03
		return (specialSeq.data[specialSeq.offset+specialSeq.length-1] == 0x03);
	}
	
	// This assumes that the buffer passed in is already a special sequence
	public static boolean isAvcFrameStart(AvBufferDescriptor specialSeq)
	{
		if (specialSeq.length != 4)
			return false;
		
		// The frame start sequence is 00 00 00 01
		return (specialSeq.data[specialSeq.offset+specialSeq.length-1] == 0x01);
	}
	
	// Returns a buffer descriptor describing the start sequence
	public static AvBufferDescriptor getSpecialSequenceDescriptor(AvBufferDescriptor buffer)
	{
		// NAL start sequence is 00 00 00 01 or 00 00 01
		if (buffer.length < 3)
			return null;
		
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
					return new AvBufferDescriptor(buffer.data, buffer.offset, 4);
				}
				else
				{
					// It's 00 00 00
					return new AvBufferDescriptor(buffer.data, buffer.offset, 3);
				}
			}
			else if (buffer.data[buffer.offset+2] == 0x01 ||
					 buffer.data[buffer.offset+2] == 0x02)
			{
				// These are easy: 00 00 01 or 00 00 02
				return new AvBufferDescriptor(buffer.data, buffer.offset, 3);
			}
			else if (buffer.data[buffer.offset+2] == 0x03)
			{
				// 00 00 03 is special because it's a subsequence of the
				// NAL wrapping substitute for 00 00 00, 00 00 01, 00 00 02,
				// or 00 00 03 in the RBSP sequence. We need to check the next
				// byte to see whether it's 00, 01, 02, or 03 (a valid RBSP substitution)
				// or whether it's something else
				
				if (buffer.length < 4)
					return null;
				
				if (buffer.data[buffer.offset+3] >= 0x00 &&
					buffer.data[buffer.offset+3] <= 0x03)
				{
					// It's not really a special sequence after all
					return null;
				}
				else
				{
					// It's not a standard replacement so it's a special sequence
					return new AvBufferDescriptor(buffer.data, buffer.offset, 3);
				}
			}
		}
		
		return null;
	}
}
