package com.limelight.nvstream.av.audio;

import java.util.concurrent.LinkedBlockingQueue;

import com.limelight.nvstream.av.AvBufferDescriptor;
import com.limelight.nvstream.av.AvRtpPacket;

public class AvAudioDepacketizer {
	private LinkedBlockingQueue<short[]> decodedUnits = new LinkedBlockingQueue<short[]>();
	
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
			
			// Tell the decoder about this
			//OpusDecoder.decode(null, 0, 0, null);
		}
		
		lastSequenceNumber = seq;		
		
		// This is all the depacketizing we need to do
		AvBufferDescriptor rtpPayload = packet.getNewPayloadDescriptor();

		// Submit this data to the decoder
		short[] pcmData = new short[OpusDecoder.getMaxOutputShorts()];
		
		int decodeLen = OpusDecoder.decode(rtpPayload.data, rtpPayload.offset, rtpPayload.length, pcmData);
		
		// Return value of decode is frames decoded per channel
		decodeLen *= OpusDecoder.getChannelCount();
		
		if (decodeLen > 0) {
			// Jank!
			short[] trimmedPcmData = new short[decodeLen];
			System.arraycopy(pcmData, 0, trimmedPcmData, 0, decodeLen);
			decodedUnits.add(trimmedPcmData);
		}
	}
	
	public short[] getNextDecodedData() throws InterruptedException
	{
		return decodedUnits.take();
	}
}
