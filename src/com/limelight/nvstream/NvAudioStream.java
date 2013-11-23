package com.limelight.nvstream;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.concurrent.ArrayBlockingQueue;

import com.limelight.nvstream.av.AvByteBufferDescriptor;
import com.limelight.nvstream.av.AvRtpPacket;
import com.limelight.nvstream.av.AvShortBufferDescriptor;
import com.limelight.nvstream.av.audio.AvAudioDepacketizer;
import com.limelight.nvstream.av.audio.OpusDecoder;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

public class NvAudioStream {
	public static final int RTP_PORT = 48000;
	public static final int RTCP_PORT = 47999;
	
	private ArrayBlockingQueue<AvRtpPacket> packets = new ArrayBlockingQueue<AvRtpPacket>(100);
	
	private AudioTrack track;
	
	private DatagramSocket rtp;
	
	private AvAudioDepacketizer depacketizer = new AvAudioDepacketizer();
	
	private LinkedList<Thread> threads = new LinkedList<Thread>();
	
	private boolean aborting = false;
	
	private InetAddress host;
	private NvConnectionListener listener;
	
	public NvAudioStream(InetAddress host, NvConnectionListener listener)
	{
		this.host = host;
		this.listener = listener;
	}
	
	public void abort()
	{
		if (aborting) {
			return;
		}
		
		aborting = true;
		
		for (Thread t : threads) {
			t.interrupt();
		}
		
		// Close the socket to interrupt the receive thread
		if (rtp != null) {
			rtp.close();
		}
		
		// Wait for threads to terminate
		for (Thread t : threads) {
			try {
				t.join();
			} catch (InterruptedException e) { }
		}

		if (track != null) {
			track.release();
		}
		
		threads.clear();
	}
	
	public void startAudioStream() throws SocketException
	{
		setupRtpSession();
		
		setupAudio();
		
		startReceiveThread();
		
		startDepacketizerThread();
		
		startDecoderThread();
		
		startUdpPingThread();
	}
	
	private void setupRtpSession() throws SocketException
	{
		rtp = new DatagramSocket(RTP_PORT);
	}
	
	private void setupAudio()
	{
		int channelConfig;
		int err;
		
		err = OpusDecoder.init();
		if (err != 0) {
			throw new IllegalStateException("Opus decoder failed to initialize");
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
			throw new IllegalStateException("Opus decoder returned unhandled channel count");
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
		Thread t = new Thread() {
			@Override
			public void run() {
				while (!isInterrupted())
				{
					AvRtpPacket packet;
					
					try {
						packet = packets.take();
					} catch (InterruptedException e) {
						listener.connectionTerminated(e);
						return;
					}
					
					depacketizer.decodeInputData(packet);
				}
			}
		};
		threads.add(t);
		t.start();
	}
	
	private void startDecoderThread()
	{
		// Decoder thread
		Thread t = new Thread() {
			@Override
			public void run() {
				while (!isInterrupted())
				{
					AvShortBufferDescriptor samples;
					
					try {
						samples = depacketizer.getNextDecodedData();
					} catch (InterruptedException e) {
						listener.connectionTerminated(e);
						return;
					}
					
					track.write(samples.data, samples.offset, samples.length);
				}
			}
		};
		threads.add(t);
		t.start();
	}
	
	private void startReceiveThread()
	{
		// Receive thread
		Thread t = new Thread() {
			@Override
			public void run() {
				DatagramPacket packet = new DatagramPacket(new byte[1500], 1500);
				AvByteBufferDescriptor desc = new AvByteBufferDescriptor(null, 0, 0);
				
				while (!isInterrupted())
				{
					try {
						rtp.receive(packet);
					} catch (IOException e) {
						listener.connectionTerminated(e);
						return;
					}
					
					desc.length = packet.getLength();
					desc.offset = packet.getOffset();
					desc.data = packet.getData();
					
					// Give the packet to the depacketizer thread
					if (packets.offer(new AvRtpPacket(desc))) {
						// Get a new buffer from the buffer pool
						packet.setData(new byte[1500], 0, 1500);
					}
				}
			}
		};
		threads.add(t);
		t.start();
	}
	
	private void startUdpPingThread()
	{
		// Ping thread
		Thread t = new Thread() {
			@Override
			public void run() {
				// PING in ASCII
				final byte[] pingPacketData = new byte[] {0x50, 0x49, 0x4E, 0x47};
				DatagramPacket pingPacket = new DatagramPacket(pingPacketData, pingPacketData.length);
				pingPacket.setSocketAddress(new InetSocketAddress(host, RTP_PORT));
				
				// Send PING every 100 ms
				while (!isInterrupted())
				{
					try {
						rtp.send(pingPacket);
					} catch (IOException e) {
						listener.connectionTerminated(e);
						return;
					}
					
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						listener.connectionTerminated(e);
						return;
					}
				}
			}
		};
		threads.add(t);
		t.start();
	}
}
