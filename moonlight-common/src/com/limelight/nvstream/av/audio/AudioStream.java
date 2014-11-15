package com.limelight.nvstream.av.audio;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.LinkedList;

import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.av.ByteBufferDescriptor;
import com.limelight.nvstream.av.RtpPacket;
import com.limelight.nvstream.av.RtpReorderQueue;

public class AudioStream {
	public static final int RTP_PORT = 48000;
	public static final int RTCP_PORT = 47999;
	
	public static final int RTP_RECV_BUFFER = 64 * 1024;
	public static final int MAX_PACKET_SIZE = 100;
	
	private DatagramSocket rtp;
	
	private AudioDepacketizer depacketizer;
	
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
	
	public boolean startAudioStream() throws SocketException
	{
		setupRtpSession();
		
		if (!setupAudio()) {
			abort();
			return false;
		}
		
		startReceiveThread();
		
		if ((streamListener.getCapabilities() & AudioRenderer.CAPABILITY_DIRECT_SUBMIT) == 0) {
			startDecoderThread();
		}
		
		startUdpPingThread();
		
		return true;
	}
	
	private void setupRtpSession() throws SocketException
	{
		rtp = new DatagramSocket();
		rtp.setReceiveBufferSize(RTP_RECV_BUFFER);
	}
	
	private boolean setupAudio()
	{
		int err;
		
		err = OpusDecoder.init();
		if (err != 0) {
			throw new IllegalStateException("Opus decoder failed to initialize");
		}
		
		if (!streamListener.streamInitialized(OpusDecoder.getChannelCount(), OpusDecoder.getSampleRate())) {
			return false;
		}
		
		if ((streamListener.getCapabilities() & AudioRenderer.CAPABILITY_DIRECT_SUBMIT) != 0) {
			depacketizer = new AudioDepacketizer(streamListener);
		}
		else {
			depacketizer = new AudioDepacketizer(null);
		}
		
		return true;
	}
	
	private void startDecoderThread()
	{
		// Decoder thread
		Thread t = new Thread() {
			@Override
			public void run() {
				
				while (!isInterrupted())
				{
					ByteBufferDescriptor samples;
					
					try {
						samples = depacketizer.getNextDecodedData();
					} catch (InterruptedException e) {
						connListener.connectionTerminated(e);
						return;
					}
					
					streamListener.playDecodedAudio(samples.data, samples.offset, samples.length);
					depacketizer.freeDecodedData(samples);
				}
			}
		};
		threads.add(t);
		t.setName("Audio - Player");
		t.setPriority(Thread.NORM_PRIORITY + 2);
		t.start();
	}
	
	private void startReceiveThread()
	{
		// Receive thread
		Thread t = new Thread() {
			@Override
			public void run() {
				byte[] buffer = new byte[MAX_PACKET_SIZE];
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
				RtpPacket queuedPacket, rtpPacket = new RtpPacket(buffer);
				RtpReorderQueue rtpQueue = new RtpReorderQueue();
				RtpReorderQueue.RtpQueueStatus queueStatus;
				
				while (!isInterrupted())
				{
					try {
						rtp.receive(packet);
						
						// DecodeInputData() doesn't hold onto the buffer so we are free to reuse it
						rtpPacket.initializeWithLength(packet.getLength());
						
						// Throw away non-audio packets before queuing
						if (rtpPacket.getPacketType() != 97) {
							// Only type 97 is audio
							packet.setLength(MAX_PACKET_SIZE);
							continue;
						}
						
						queueStatus = rtpQueue.addPacket(rtpPacket);
						if (queueStatus == RtpReorderQueue.RtpQueueStatus.HANDLE_IMMEDIATELY) {
							// Send directly to the depacketizer
							depacketizer.decodeInputData(rtpPacket);
							packet.setLength(MAX_PACKET_SIZE);
						}
						else {
							if (queueStatus != RtpReorderQueue.RtpQueueStatus.REJECTED) {
								// The queue consumed our packet, so we must allocate a new one
								buffer = new byte[MAX_PACKET_SIZE];
								packet = new DatagramPacket(buffer, buffer.length);
								rtpPacket = new RtpPacket(buffer);
							}
							else {
								packet.setLength(MAX_PACKET_SIZE);
							}
							
							// If packets are ready, pull them and send them to the depacketizer
							if (queueStatus == RtpReorderQueue.RtpQueueStatus.QUEUED_PACKETS_READY) {
								while ((queuedPacket = (RtpPacket) rtpQueue.getQueuedPacket()) != null) {
									depacketizer.decodeInputData(queuedPacket);
								}
							}
						}
					} catch (IOException e) {
						connListener.connectionTerminated(e);
						return;
					}
				}
			}
		};
		threads.add(t);
		t.setName("Audio - Receive");
		t.setPriority(Thread.NORM_PRIORITY + 1);
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
				
				// Send PING every 500 ms
				while (!isInterrupted())
				{
					try {
						rtp.send(pingPacket);
					} catch (IOException e) {
						connListener.connectionTerminated(e);
						return;
					}
					
					try {
						Thread.sleep(500);
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
