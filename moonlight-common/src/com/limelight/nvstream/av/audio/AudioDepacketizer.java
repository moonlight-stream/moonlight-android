package com.limelight.nvstream.av.audio;

import java.util.concurrent.LinkedBlockingQueue;

import com.limelight.nvstream.av.ByteBufferDescriptor;
import com.limelight.nvstream.av.RtpPacket;

public class AudioDepacketizer {
	
	private static final int DU_LIMIT = 15;
	private LinkedBlockingQueue<ByteBufferDescriptor> decodedUnits =
			new LinkedBlockingQueue<ByteBufferDescriptor>(DU_LIMIT);
		
	// Sequencing state
	private short lastSequenceNumber;

	private void decodeData(byte[] data, int off, int len)
	{
		// Submit this data to the decoder
		byte[] pcmData = new byte[OpusDecoder.getMaxOutputShorts()*2];
		int decodeLen = OpusDecoder.decode(data, off, len, pcmData);
		
		if (decodeLen > 0) {
			// Return value of decode is frames (shorts) decoded per channel
			decodeLen *= 2*OpusDecoder.getChannelCount();
			
			// Put it on the decoded queue
			if (!decodedUnits.offer(new ByteBufferDescriptor(pcmData, 0, decodeLen))) {
				System.out.println("Audio player too slow! Forced to drop decoded samples");
				// Clear out the queue
				decodedUnits.clear();
			}
		}
	}
	
	public void decodeInputData(RtpPacket packet)
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
		ByteBufferDescriptor rtpPayload = packet.getNewPayloadDescriptor();
		decodeData(rtpPayload.data, rtpPayload.offset, rtpPayload.length);
	}
	
	public ByteBufferDescriptor getNextDecodedData() throws InterruptedException
	{
		return decodedUnits.take();
	}
}
