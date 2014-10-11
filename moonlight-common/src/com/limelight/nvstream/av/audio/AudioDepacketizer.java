package com.limelight.nvstream.av.audio;

import com.limelight.LimeLog;
import com.limelight.nvstream.av.ByteBufferDescriptor;
import com.limelight.nvstream.av.PopulatedBufferList;
import com.limelight.nvstream.av.RtpPacket;
import com.limelight.nvstream.av.SequenceHelper;

public class AudioDepacketizer {
	
	private static final int DU_LIMIT = 30;
	private PopulatedBufferList<ByteBufferDescriptor> decodedUnits;
	
	// Direct submit state
	private AudioRenderer directSubmitRenderer;
	private byte[] directSubmitData;
	
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
			decodedUnits = new PopulatedBufferList<ByteBufferDescriptor>(DU_LIMIT, new PopulatedBufferList.BufferFactory() {
				public Object createFreeBuffer() {
					return new ByteBufferDescriptor(new byte[OpusDecoder.getMaxOutputShorts()*2], 0, OpusDecoder.getMaxOutputShorts()*2);
				}

				public void cleanupObject(Object o) {
					// Nothing to do
				}
			});
		}
	}

	private void decodeData(byte[] data, int off, int len)
	{
		// Submit this data to the decoder
		int decodeLen;
		ByteBufferDescriptor bb;
		if (directSubmitData != null) {
			bb = null;
			decodeLen = OpusDecoder.decode(data, off, len, directSubmitData);
		}
		else {
			bb = decodedUnits.pollFreeObject();
			if (bb == null) {
				LimeLog.warning("Audio player too slow! Forced to drop decoded samples");
				decodedUnits.clearPopulatedObjects();
				bb = decodedUnits.pollFreeObject();
				if (bb == null) {
					LimeLog.severe("Audio player is leaking buffers!");
					return;
				}
			}
			decodeLen = OpusDecoder.decode(data, off, len, bb.data);
		}
		
		if (decodeLen > 0) {
			// Return value of decode is frames (shorts) decoded per channel
			decodeLen *= 2*OpusDecoder.getChannelCount();
			
			if (directSubmitRenderer != null) {
				directSubmitRenderer.playDecodedAudio(directSubmitData, 0, decodeLen);
			}
			else {
				bb.length = decodeLen;
				decodedUnits.addPopulatedObject(bb);
			}
		}
		else if (directSubmitRenderer == null) {
			decodedUnits.freePopulatedObject(bb);
		}
	}
	
	public void decodeInputData(RtpPacket packet)
	{
		short seq = packet.getRtpSequenceNumber();
		
		// Toss out the current NAL if we receive a packet that is
		// out of sequence
		if (lastSequenceNumber != 0 &&
			(short)(lastSequenceNumber + 1) != seq)
		{
			LimeLog.warning("Received OOS audio data (expected "+(lastSequenceNumber + 1)+", got "+seq+")");
			
			// Only tell the decoder if we got packets ahead of what we expected
			// If the packet is behind the current sequence number, drop it
			if (!SequenceHelper.isBeforeSigned(seq, (short)(lastSequenceNumber + 1), false)) {
				decodeData(null, 0, 0);
			}
			else {
				return;
			}
		}
		
		lastSequenceNumber = seq;
		
		// This is all the depacketizing we need to do
		packet.initializePayloadDescriptor(cachedDesc);
		decodeData(cachedDesc.data, cachedDesc.offset, cachedDesc.length);
	}
	
	public ByteBufferDescriptor getNextDecodedData() throws InterruptedException {
		return decodedUnits.takePopulatedObject();
	}
	
	public void freeDecodedData(ByteBufferDescriptor data) {
		decodedUnits.freePopulatedObject(data);
	}
}
