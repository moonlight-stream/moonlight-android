package com.limelight.nvstream.av.audio;

import java.util.concurrent.LinkedBlockingQueue;

import com.limelight.nvstream.av.AvByteBufferDescriptor;
import com.limelight.nvstream.av.AvRtpPacket;
import com.limelight.nvstream.av.AvShortBufferDescriptor;

public class AvAudioDepacketizer {
	
	private static final int DU_LIMIT = 15;
	private LinkedBlockingQueue<AvShortBufferDescriptor> decodedUnits =
			new LinkedBlockingQueue<AvShortBufferDescriptor>(DU_LIMIT);
		
	// Sequencing state
	private short lastSequenceNumber;

	private void decodeData(byte[] data, int off, int len)
	{
		// Submit this data to the decoder
		short[] pcmData = new short[OpusDecoder.getMaxOutputShorts()];
		int decodeLen = OpusDecoder.decode(data, off, len, pcmData);
		
		if (decodeLen > 0) {
			// Return value of decode is frames decoded per channel
			decodeLen *= OpusDecoder.getChannelCount();
			
			// Put it on the decoded queue
			if (!decodedUnits.offer(new AvShortBufferDescriptor(pcmData, 0, decodeLen))) {
				// Clear out the queue
				decodedUnits.clear();
			}
		}
	}
	
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
			decodeData(null, 0, 0);
		}
		
		lastSequenceNumber = seq;
		
		// This is all the depacketizing we need to do
		AvByteBufferDescriptor rtpPayload = packet.getNewPayloadDescriptor();
		decodeData(rtpPayload.data, rtpPayload.offset, rtpPayload.length);
	}
	
	public AvShortBufferDescriptor getNextDecodedData() throws InterruptedException
	{
		return decodedUnits.take();
	}
}
