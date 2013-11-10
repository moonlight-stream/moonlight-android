package com.limelight.nvstream.av.audio;

import java.util.concurrent.LinkedBlockingQueue;

import com.limelight.nvstream.av.AvByteBufferDescriptor;
import com.limelight.nvstream.av.AvRtpPacket;
import com.limelight.nvstream.av.AvShortBufferDescriptor;
import com.limelight.nvstream.av.AvShortBufferPool;

public class AvAudioDepacketizer {
	private LinkedBlockingQueue<AvShortBufferDescriptor> decodedUnits =
			new LinkedBlockingQueue<AvShortBufferDescriptor>();
	
	private AvShortBufferPool pool = new AvShortBufferPool(OpusDecoder.getMaxOutputShorts());
	
	// Sequencing state
	private short lastSequenceNumber;
	
	public void decodeInputData(AvRtpPacket packet)
	{
		short seq = packet.getSequenceNumber();
		
		if (packet.getPacketType() != 97) {
			// Only type 97 is audio
			return;
		}
		
		// Toss out the current NAL if we receive a packet that is
		// out of sequence
		if (lastSequenceNumber != 0 &&
			(short)(lastSequenceNumber + 1) != seq)
		{
			System.out.println("Received OOS audio data (expected "+(lastSequenceNumber + 1)+", got "+seq+")");
			
			// Tell the decoder about this packet loss
			//OpusDecoder.decode(null, 0, 0, null);
		}
		
		lastSequenceNumber = seq;		
		
		// This is all the depacketizing we need to do
		AvByteBufferDescriptor rtpPayload = packet.getNewPayloadDescriptor();

		// Submit this data to the decoder
		short[] pcmData = pool.allocate();
		int decodeLen = OpusDecoder.decode(rtpPayload.data, rtpPayload.offset, rtpPayload.length, pcmData);
		
		if (decodeLen > 0) {
			// Return value of decode is frames decoded per channel
			decodeLen *= OpusDecoder.getChannelCount();
			
			// Put it on the decoded queue
			decodedUnits.add(new AvShortBufferDescriptor(pcmData, 0, decodeLen));
		}
	}
	
	public void releaseBuffer(AvShortBufferDescriptor decodedData)
	{
		pool.free(decodedData.data);
	}
	
	public AvShortBufferDescriptor getNextDecodedData() throws InterruptedException
	{
		return decodedUnits.take();
	}
}
