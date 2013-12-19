package com.limelight.nvstream.av.audio;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;

import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.av.ByteBufferDescriptor;
import com.limelight.nvstream.av.RtpPacket;
import com.limelight.nvstream.av.ShortBufferDescriptor;

public class AudioStream {
	public static final int RTP_PORT = 48000;
	public static final int RTCP_PORT = 47999;
	
	public static final int RTP_RECV_BUFFER = 64 * 1024;
	
	private LinkedBlockingQueue<RtpPacket> packets = new LinkedBlockingQueue<RtpPacket>(100);
	
	private DatagramSocket rtp;
	
	private AudioDepacketizer depacketizer = new AudioDepacketizer();
	
	private LinkedList<Thread> threads = new LinkedList<Thread>();
	
	private boolean aborting = false;
	
	private InetAddress host;
	private NvConnectionListener connListener;
	private AudioRenderer streamListener;
	
	public AudioStream(InetAddress host, NvConnectionListener connListener, AudioRenderer streamListener)
	{
		this.host = host;
		this.connListener = connListener;
		this.streamListener = streamListener;
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

		streamListener.streamClosing();
		
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
		rtp = new DatagramSocket(null);
		rtp.setReuseAddress(true);
		rtp.setReceiveBufferSize(RTP_RECV_BUFFER);
		rtp.bind(new InetSocketAddress(RTP_PORT));
	}
	
	private void setupAudio()
	{
		int err;
		
		err = OpusDecoder.init();
		if (err != 0) {
			throw new IllegalStateException("Opus decoder failed to initialize");
		}
		
		streamListener.streamInitialized(OpusDecoder.getChannelCount(), OpusDecoder.getSampleRate());
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
					RtpPacket packet;
					
					try {
						packet = packets.take();
					} catch (InterruptedException e) {
						connListener.connectionTerminated(e);
						return;
					}
					
					depacketizer.decodeInputData(packet);
				}
			}
		};
		threads.add(t);
		t.setName("Audio - Depacketizer");
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
					ShortBufferDescriptor samples;
					
					try {
						samples = depacketizer.getNextDecodedData();
					} catch (InterruptedException e) {
						connListener.connectionTerminated(e);
						return;
					}
					
					streamListener.playDecodedAudio(samples.data, samples.offset, samples.length);
				}
			}
		};
		threads.add(t);
		t.setName("Audio - Player");
		t.start();
	}
	
	private void startReceiveThread()
	{
		// Receive thread
		Thread t = new Thread() {
			@Override
			public void run() {
				ByteBufferDescriptor desc = new ByteBufferDescriptor(new byte[1500], 0, 1500);
				DatagramPacket packet = new DatagramPacket(desc.data, desc.length);
				
				while (!isInterrupted())
				{
					try {
						rtp.receive(packet);
					} catch (IOException e) {
						connListener.connectionTerminated(e);
						return;
					}

					// Give the packet to the depacketizer thread
					desc.length = packet.getLength();
					if (packets.offer(new RtpPacket(desc))) {
						desc.reinitialize(new byte[1500], 0, 1500);
						packet.setData(desc.data, desc.offset, desc.length);
					}
				}
			}
		};
		threads.add(t);
		t.setName("Audio - Receive");
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
						connListener.connectionTerminated(e);
						return;
					}
					
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						connListener.connectionTerminated(e);
						return;
					}
				}
			}
		};
		threads.add(t);
		t.setPriority(Thread.MIN_PRIORITY);
		t.setName("Audio - Ping");
		t.start();
	}
}
