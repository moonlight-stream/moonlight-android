package com.limelight.nvstream.av;

import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;

public class AvParser {
	
	// Current NAL state
	private LinkedList<AvBufferDescriptor> nalDataChain;
	private int nalDataLength;
	
	private LinkedBlockingQueue<AvDecodeUnit> decodedUnits = new LinkedBlockingQueue<AvDecodeUnit>();
	
	private void reassembleNal()
	{
		// This is the start of a new NAL
		if (nalDataChain != null && nalDataLength != 0)
		{	
			// Construct the H264 decode unit
			AvDecodeUnit du = new AvDecodeUnit(AvDecodeUnit.TYPE_H264, nalDataChain, nalDataLength);
			decodedUnits.add(du);
			
			// Clear old state
			nalDataChain = null;
			nalDataLength = 0;
		}
	}
	
	public void addInputData(AvPacket packet)
	{		
		// This payload buffer descriptor belongs to us
		AvBufferDescriptor location = packet.getNewPayloadDescriptor();
		int payloadLength = location.length;
		
		while (location.length != 0)
		{
			// Remember the start of the NAL data in this packet
			int start = location.offset;
			
			// Check for the start sequence
			if (H264NAL.hasStartSequence(location))
			{
				// Reassemble any pending NAL
				reassembleNal();
				
				// Setup state for the new NAL
				nalDataChain = new LinkedList<AvBufferDescriptor>();
				nalDataLength = 0;
				
				// Skip the start sequence
				location.length -= 4;
				location.offset += 4;
			}
			
			// If there's a NAL assembly in progress, add the current data
			if (nalDataChain != null)
			{
				// FIXME: This is a hack to make parsing full packets
				// take less time. We assume if they don't start with
				// a NAL start sequence, they're full of NAL data
				if (payloadLength == 968)
				{
					location.offset += location.length;
					location.length = 0;
				}
				else
				{
					System.out.println("Using slow parsing case");
					while (location.length != 0)
					{
						// Check if this should end the current NAL
						if (H264NAL.hasStartSequence(location))
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
				}

				// Add a buffer descriptor describing the NAL data in this packet
				nalDataChain.add(new AvBufferDescriptor(location.data, start, location.offset-start));
				nalDataLength += location.offset-start;
			}
			else
			{
				// Otherwise, skip the data
				location.offset++;
				location.length--;
			}
		}
	}
	
	public AvDecodeUnit getNextDecodeUnit() throws InterruptedException
	{
		return decodedUnits.take();
	}
}

class H264NAL {
	public static boolean shouldTerminateNal(AvBufferDescriptor buffer)
	{
		if (buffer.length < 4)
			return false;
		
		if (buffer.data[buffer.offset] != 0x00 ||
			buffer.data[buffer.offset+1] != 0x00 ||
			buffer.data[buffer.offset+2] != 0x00)
		{
			return false;
		}
		
		return true;
	}
	
	public static boolean hasStartSequence(AvBufferDescriptor buffer)
	{
		// NAL start sequence is 00 00 00 01
		if (!shouldTerminateNal(buffer))
			return false;
		
		if (buffer.data[buffer.offset+3] != 0x01)
			return false;
		
		return true;
	}
}
