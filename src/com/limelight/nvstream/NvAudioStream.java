package com.limelight.nvstream;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

import jlibrtp.Participant;
import jlibrtp.RTPSession;

import com.limelight.nvstream.av.AvBufferDescriptor;
import com.limelight.nvstream.av.AvBufferPool;
import com.limelight.nvstream.av.AvDecodeUnit;
import com.limelight.nvstream.av.AvRtpPacket;
import com.limelight.nvstream.av.audio.AvAudioDepacketizer;
import com.limelight.nvstream.av.audio.OpusDecoder;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.net.rtp.AudioGroup;
import android.net.rtp.AudioStream;
import android.view.Surface;

public class NvAudioStream {
	public static final int RTP_PORT = 48000;
	public static final int RTCP_PORT = 47999;
	
	private LinkedBlockingQueue<AvRtpPacket> packets = new LinkedBlockingQueue<AvRtpPacket>();
	
	private AudioTrack track;
	
	private RTPSession session;
	private DatagramSocket rtp;
	
	private AvAudioDepacketizer depacketizer = new AvAudioDepacketizer();
	
	private AvBufferPool pool = new AvBufferPool(1500);
	
	public void startAudioStream(final String host)
	{		
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					setupRtpSession(host);
				} catch (SocketException e) {
					e.printStackTrace();
					return;
				}
				
				setupAudio();
				
				startReceiveThread();
				
				startDepacketizerThread();
				
				startUdpPingThread();
				
				startDecoderThread();
			}
			
		}).start();
	}
	
	private void setupRtpSession(String host) throws SocketException
	{
		rtp = new DatagramSocket(RTP_PORT);
		
		session = new RTPSession(rtp, null);
		session.addParticipant(new Participant(host, RTP_PORT, 0));
	}
	
	private void setupAudio()
	{
		int channelConfig;
		int err;
		
		err = OpusDecoder.init();
		if (err == 0) {
			System.out.println("Opus decoder initialized");
		}
		else {
			System.err.println("Opus decoder init failed: "+err);
			return;
		}
		
		switch (OpusDecoder.getChannelCount())
		{
		case 1:
			channelConfig = AudioFormat.CHANNEL_OUT_MONO;
			break;
		case 2:
			channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
			break;
		default:
			System.err.println("Unsupported channel count");
			return;
		}
				
		track = new AudioTrack(AudioManager.STREAM_MUSIC,
				OpusDecoder.getSampleRate(),
				channelConfig,
				AudioFormat.ENCODING_PCM_16BIT,
				1024, // 1KB buffer
				AudioTrack.MODE_STREAM);
		
		track.play();
	}
	
	private void startDepacketizerThread()
	{
		// This thread lessens the work on the receive thread
		// so it can spend more time waiting for data
		new Thread(new Runnable() {
			@Override
			public void run() {
				for (;;)
				{
					AvRtpPacket packet;
					
					try {
						packet = packets.take();
					} catch (InterruptedException e) {
						e.printStackTrace();
						return;
					}
					
					// !!! We no longer own the data buffer at this point !!!
					depacketizer.decodeInputData(packet);
				}
			}
		}).start();
	}
	
	private void startDecoderThread()
	{
		// Decoder thread
		new Thread(new Runnable() {
			@Override
			public void run() {
				for (;;)
				{
					short[] samples;
					try {
						samples = depacketizer.getNextDecodedData();
					} catch (InterruptedException e) {
						e.printStackTrace();
						return;
					}
					
					track.write(samples, 0, samples.length);
				}
			}
		}).start();
	}
	
	private void startReceiveThread()
	{
		// Receive thread
		new Thread(new Runnable() {
			@Override
			public void run() {
				DatagramPacket packet = new DatagramPacket(pool.allocate(), 1500);
				AvBufferDescriptor desc = new AvBufferDescriptor(null, 0, 0);
				
				for (;;)
				{
					try {
						rtp.receive(packet);
					} catch (IOException e) {
						e.printStackTrace();
						return;
					}
					
					desc.length = packet.getLength();
					desc.offset = packet.getOffset();
					desc.data = packet.getData();
					
					// Give the packet to the depacketizer thread
					packets.add(new AvRtpPacket(desc));
					
					// Get a new buffer from the buffer pool
					packet.setData(pool.allocate(), 0, 1500);
				}
			}
		}).start();
	}
	
	private void startUdpPingThread()
	{
		// Ping thread
		new Thread(new Runnable() {
			@Override
			public void run() {
				// PING in ASCII
				final byte[] pingPacket = new byte[] {0x50, 0x49, 0x4E, 0x47};
				
				// RTP payload type is 127 (dynamic)
				session.payloadType(127);
				
				// Send PING every 100 ms
				for (;;)
				{
					session.sendData(pingPacket);
					
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						break;
					}
				}
			}
		}).start();
	}
}
