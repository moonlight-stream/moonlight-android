package com.limelight.nvstream;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;

import com.limelight.nvstream.av.AvByteBufferDescriptor;
import com.limelight.nvstream.av.AvDecodeUnit;
import com.limelight.nvstream.av.AvRtpPacket;
import com.limelight.nvstream.av.video.AvVideoDepacketizer;
import com.limelight.nvstream.av.video.AvVideoPacket;
import com.limelight.nvstream.av.video.CpuDecoderRenderer;
import com.limelight.nvstream.av.video.DecoderRenderer;
import com.limelight.nvstream.av.video.MediaCodecDecoderRenderer;

import android.view.Surface;

public class NvVideoStream {
	public static final int RTP_PORT = 47998;
	public static final int RTCP_PORT = 47999;
	public static final int FIRST_FRAME_PORT = 47996;
	
	private LinkedBlockingQueue<AvRtpPacket> packets = new LinkedBlockingQueue<AvRtpPacket>();
	
	private DatagramSocket rtp;
	private Socket firstFrameSocket;
	
	private LinkedList<Thread> threads = new LinkedList<Thread>();

	private AvVideoDepacketizer depacketizer = new AvVideoDepacketizer();
	
	private DecoderRenderer decrend;
	private boolean startedRendering;
	
	private boolean aborting = false;
	
	public void abort()
	{
		if (aborting) {
			return;
		}
		
		aborting = true;
		
		// Interrupt threads
		for (Thread t : threads) {
			t.interrupt();
		}
		
		// Close the socket to interrupt the receive thread
		if (rtp != null) {
			rtp.close();
		}
		if (firstFrameSocket != null) {
			try {
				firstFrameSocket.close();
			} catch (IOException e) {}
		}
		
		// Wait for threads to terminate
		for (Thread t : threads) {
			try {
				t.join();
			} catch (InterruptedException e) { }
		}
		
		if (startedRendering) {
			decrend.stop();
		}
		
		if (decrend != null) {
			decrend.release();
		}
		
		threads.clear();
	}
	
	public void trim()
	{
		depacketizer.trim();
	}

	private void readFirstFrame(String host) throws IOException
	{
		byte[] firstFrame = depacketizer.allocatePacketBuffer();
		
		System.out.println("VID: Waiting for first frame");
		firstFrameSocket = new Socket(host, FIRST_FRAME_PORT);

		try {
			InputStream firstFrameStream = firstFrameSocket.getInputStream();
			
			int offset = 0;
			for (;;)
			{
				int bytesRead = firstFrameStream.read(firstFrame, offset, firstFrame.length-offset);
				
				if (bytesRead == -1)
					break;
				
				offset += bytesRead;
			}
			
			System.out.println("VID: First frame read ("+offset+" bytes)");
			depacketizer.addInputData(new AvVideoPacket(new AvByteBufferDescriptor(firstFrame, 0, offset)));
		} finally {
			firstFrameSocket.close();
			firstFrameSocket = null;
		}
	}
	
	public void setupRtpSession(String host) throws SocketException, UnknownHostException
	{
		rtp = new DatagramSocket(RTP_PORT);
		rtp.connect(InetAddress.getByName(host), RTP_PORT);
		rtp.setReceiveBufferSize(2097152);
		System.out.println("RECV BUF: "+rtp.getReceiveBufferSize());
		System.out.println("SEND BUF: "+rtp.getSendBufferSize());
	}
	
	public void setupDecoderRenderer(Surface renderTarget) {
		boolean requiresCpuDecoding = true;
		
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
			if (MediaCodecDecoderRenderer.hasWhitelistedDecoder()) {
				requiresCpuDecoding = false;
			}
		}
		
		if (requiresCpuDecoding) {
			decrend = new CpuDecoderRenderer();
		}
		else {
			decrend = new MediaCodecDecoderRenderer();
		}
		
		decrend.setup(1280, 720, renderTarget);
	}
	
	public void startVideoStream(final String host)
	{
		// Read the first frame to start the UDP video stream
		try {
			readFirstFrame(host);
		} catch (IOException e2) {
			abort();
			return;
		}
	}

	public void setupVideoStream(final String host, final Surface surface)
	{
		// This thread becomes the output display thread
		Thread t = new Thread() {
			@Override
			public void run() {
				// Setup the decoder and renderer
				setupDecoderRenderer(surface);
				
				// Open RTP sockets and start session
				try {
					setupRtpSession(host);
				} catch (SocketException e) {
					e.printStackTrace();
					return;
				} catch (UnknownHostException e) {
					e.printStackTrace();
					return;
				}
				
				// Start pinging before reading the first frame
				// so Shield Proxy knows we're here and sends us
				// the reference frame
				startUdpPingThread();
				
				// Start the receive thread early to avoid missing
				// early packets
				startReceiveThread();
				
				// Start the depacketizer thread to deal with the RTP data
				startDepacketizerThread();
				
				// Start decoding the data we're receiving
				startDecoderThread();
				
				// Start the renderer
				decrend.start();
				startedRendering = true;
			}
		};
		threads.add(t);
		t.start();
	}
	
	private void startDecoderThread()
	{
		Thread t = new Thread() {
			@Override
			public void run() {
				// Read the decode units generated from the RTP stream
				while (!isInterrupted())
				{
					AvDecodeUnit du;
					
					try {
						du = depacketizer.getNextDecodeUnit();
					} catch (InterruptedException e) {
						abort();
						return;
					}
					
					decrend.submitDecodeUnit(du);
					
					depacketizer.releaseDecodeUnit(du);
				}
			}
		};
		threads.add(t);
		t.start();
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
						abort();
						return;
					}
					
					// !!! We no longer own the data buffer at this point !!!
					depacketizer.addInputData(packet);
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
				DatagramPacket packet = new DatagramPacket(depacketizer.allocatePacketBuffer(), 1500);
				AvByteBufferDescriptor desc = new AvByteBufferDescriptor(null, 0, 0);
				
				while (!isInterrupted())
				{
					try {
						rtp.receive(packet);
					} catch (IOException e) {
						abort();
						return;
					}
					
					desc.length = packet.getLength();
					desc.offset = packet.getOffset();
					desc.data = packet.getData();
					
					// Give the packet to the depacketizer thread
					packets.add(new AvRtpPacket(desc));
					
					// Get a new buffer from the buffer pool
					packet.setData(depacketizer.allocatePacketBuffer(), 0, 1500);
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
				
				// Send PING every 100 ms
				while (!isInterrupted())
				{
					try {
						rtp.send(pingPacket);
					} catch (IOException e) {
						abort();
						return;
					}
					
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						abort();
						return;
					}
				}
			}
		};
		threads.add(t);
		t.start();
	}
}
