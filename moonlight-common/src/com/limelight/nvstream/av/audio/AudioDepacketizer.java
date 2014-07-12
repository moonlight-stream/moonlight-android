package com.limelight.nvstream.av.audio;

import java.util.concurrent.LinkedBlockingQueue;

import com.limelight.LimeLog;
import com.limelight.nvstream.av.ByteBufferDescriptor;
import com.limelight.nvstream.av.RtpPacket;

public class AudioDepacketizer {
	
	private static final int DU_LIMIT = 15;
	private LinkedBlockingQueue<ByteBufferDescriptor> decodedUnits =
			new LinkedBlockingQueue<ByteBufferDescriptor>(DU_LIMIT);
	
	// Direct submit state
	private AudioRenderer directSubmitRenderer;
	private byte[] directSubmitData;
	
	// Non-direct submit state
	private byte[][] pcmRing;
	private int ringIndex;
	
	// Cached objects
	private ByteBufferDescriptor cachedDesc = new ByteBufferDescriptor(null, 0, 0);
	
	// Sequencing state
	private short lastSequenceNumber;
	
	public AudioDepacketizer(AudioRenderer directSubmitRenderer)
	{
		this.directSubmitRenderer = directSubmitRenderer;
		if (directSubmitRenderer != null) {
			this.directSubmitData = new byte[OpusDecoder.getMaxOutputShorts()*2];
		}
		else {
			pcmRing = new byte[DU_LIMIT][OpusDecoder.getMaxOutputShorts()*2];
 		}
	}

	private void decodeData(byte[] data, int off, int len)
	{
		// Submit this data to the decoder
		int decodeLen;
		byte[] pcmData;
		if (directSubmitData != null) {
			pcmData = null;
			decodeLen = OpusDecoder.decode(data, off, len, directSubmitData);
		}
		else {
			pcmData = pcmRing[ringIndex];
			decodeLen = OpusDecoder.decode(data, off, len, pcmData);
		}
		
		if (decodeLen > 0) {
			// Return value of decode is frames (shorts) decoded per channel
			decodeLen *= 2*OpusDecoder.getChannelCount();
			
			if (directSubmitRenderer != null) {
				directSubmitRenderer.playDecodedAudio(directSubmitData, 0, decodeLen);
			}
			else if (!decodedUnits.offer(new ByteBufferDescriptor(pcmData, 0, decodeLen))) {
				LimeLog.warning("Audio player too slow! Forced to drop decoded samples");
				// Clear out the queue
				decodedUnits.clear();
			}
			else {
				// Frame successfully submitted for playback
				ringIndex = (ringIndex + 1) % DU_LIMIT;
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
			LimeLog.warning("Received OOS audio data (expected "+(lastSequenceNumber + 1)+", got "+seq+")");
			decodeData(null, 0, 0);
		}
		
		lastSequenceNumber = seq;
		
		// This is all the depacketizing we need to do
		packet.initializePayloadDescriptor(cachedDesc);
		decodeData(cachedDesc.data, cachedDesc.offset, cachedDesc.length);
	}
	
	public ByteBufferDescriptor getNextDecodedData() throws InterruptedException
	{
		return decodedUnits.take();
	}
}
