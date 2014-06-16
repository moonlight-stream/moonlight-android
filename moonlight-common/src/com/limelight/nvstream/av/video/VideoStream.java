package com.limelight.nvstream.av.video;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.LinkedList;

import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.StreamConfiguration;
import com.limelight.nvstream.av.ByteBufferDescriptor;
import com.limelight.nvstream.av.DecodeUnit;
import com.limelight.nvstream.av.RtpPacket;
import com.limelight.nvstream.av.ConnectionStatusListener;

public class VideoStream {
	public static final int RTP_PORT = 47998;
	public static final int RTCP_PORT = 47999;
	public static final int FIRST_FRAME_PORT = 47996;
	
	public static final int FIRST_FRAME_TIMEOUT = 5000;
	public static final int RTP_RECV_BUFFER = 256 * 1024;
	
	public static final int MAX_PACKET_SIZE = 1050;
	
	// The ring size MUST be greater than or equal to
	// the maximum number of packets in a fully
	// presentable frame
	public static final int VIDEO_RING_SIZE = 384;
	
	private InetAddress host;
	private DatagramSocket rtp;
	private Socket firstFrameSocket;
	
	private LinkedList<Thread> threads = new LinkedList<Thread>();

	private NvConnectionListener listener;
	private ConnectionStatusListener avConnListener;
	private VideoDepacketizer depacketizer;
	private StreamConfiguration streamConfig;
	
	private VideoDecoderRenderer decRend;
	private boolean startedRendering;
	
	private boolean aborting = false;
	
	public VideoStream(InetAddress host, NvConnectionListener listener, ConnectionStatusListener avConnListener, StreamConfiguration streamConfig)
	{
		this.host = host;
		this.listener = listener;
		this.avConnListener = avConnListener;
		this.streamConfig = streamConfig;
	}
	
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
			decRend.stop();
		}
		
		if (decRend != null) {
			decRend.release();
		}
		
		threads.clear();
	}

	private void readFirstFrame() throws IOException
	{
		byte[] firstFrame = new byte[MAX_PACKET_SIZE];
		
		firstFrameSocket = new Socket();
		firstFrameSocket.setSoTimeout(FIRST_FRAME_TIMEOUT);
		
		try {
			firstFrameSocket.connect(new InetSocketAddress(host, FIRST_FRAME_PORT), FIRST_FRAME_TIMEOUT);
			InputStream firstFrameStream = firstFrameSocket.getInputStream();
			
			int offset = 0;
			for (;;)
			{
				int bytesRead = firstFrameStream.read(firstFrame, offset, firstFrame.length-offset);
				
				if (bytesRead == -1)
					break;
				
				offset += bytesRead;
			}
			
			depacketizer.addInputData(new VideoPacket(new ByteBufferDescriptor(firstFrame, 0, offset)));
		} finally {
			firstFrameSocket.close();
			firstFrameSocket = null;
		}
	}
	
	public void setupRtpSession() throws SocketException
	{
		rtp = new DatagramSocket(null);
		rtp.setReuseAddress(true);
		rtp.setReceiveBufferSize(RTP_RECV_BUFFER);
		rtp.bind(new InetSocketAddress(RTP_PORT));
	}
	
	public void setupDecoderRenderer(VideoDecoderRenderer decRend, Object renderTarget, int drFlags) {
		this.decRend = decRend;
		if (decRend != null) {
			decRend.setup(streamConfig.getWidth(), streamConfig.getHeight(),
					60, renderTarget, drFlags);
			
			if ((decRend.getCapabilities() & VideoDecoderRenderer.CAPABILITY_DIRECT_SUBMIT) != 0) {
				depacketizer = new VideoDepacketizer(decRend, avConnListener);
			}
			else {
				depacketizer = new VideoDepacketizer(null, avConnListener);
			}
		}
	}

	public void startVideoStream(VideoDecoderRenderer decRend, Object renderTarget, int drFlags) throws IOException
	{
		// Setup the decoder and renderer
		setupDecoderRenderer(decRend, renderTarget, drFlags);
		
		// Open RTP sockets and start session
		setupRtpSession();
		
		// Start pinging before reading the first frame
		// so Shield Proxy knows we're here and sends us
		// the reference frame
		startUdpPingThread();
		
		// Read the first frame to start the UDP video stream
		// This MUST be called before the normal UDP receive thread
		// starts in order to avoid state corruption caused by two
		// threads simultaneously adding input data.
		readFirstFrame();
		
		if (decRend != null) {
			// Start the receive thread early to avoid missing
			// early packets
			startReceiveThread();
			
			// Start a decode thread if we're not doing direct submit
			if ((decRend.getCapabilities() & VideoDecoderRenderer.CAPABILITY_DIRECT_SUBMIT) == 0) {
				startDecoderThread();
			}
			
			// Start the renderer
			decRend.start();
			startedRendering = true;
		}
	}
	
	private void startDecoderThread()
	{
		Thread t = new Thread() {
			@Override
			public void run() {
				// Read the decode units generated from the RTP stream
				while (!isInterrupted())
				{
					DecodeUnit du;
					
					try {
						du = depacketizer.getNextDecodeUnit();
					} catch (InterruptedException e) {
						listener.connectionTerminated(e);
						return;
					}
					
					decRend.submitDecodeUnit(du);
				}
			}
		};
		threads.add(t);
		t.setName("Video - Decoder");
		t.setPriority(Thread.MAX_PRIORITY);
		t.start();
	}
	
	private void startReceiveThread()
	{
		// Receive thread
		Thread t = new Thread() {
			@Override
			public void run() {
				ByteBufferDescriptor ring[] = new ByteBufferDescriptor[VIDEO_RING_SIZE];
				int ringIndex = 0;
				
				// Preinitialize the ring buffer
				for (int i = 0; i < VIDEO_RING_SIZE; i++) {
					ring[i] = new ByteBufferDescriptor(new byte[MAX_PACKET_SIZE], 0, MAX_PACKET_SIZE);
				}

				ByteBufferDescriptor desc;
				DatagramPacket packet = new DatagramPacket(new byte[1], 1); // Placeholder array
				while (!isInterrupted())
				{
					try {
						// Pull the next buffer in the ring and reset it
						desc = ring[ringIndex];
						desc.length = MAX_PACKET_SIZE;
						desc.offset = 0;

						// Read the video data off the network
						packet.setData(desc.data, desc.offset, desc.length);
						rtp.receive(packet);
						
						// Submit video data to the depacketizer
						desc.length = packet.getLength();
						depacketizer.addInputData(new RtpPacket(desc));
						ringIndex = (ringIndex + 1) % VIDEO_RING_SIZE;
					} catch (IOException e) {
						listener.connectionTerminated(e);
						return;
					}
				}
			}
		};
		threads.add(t);
		t.setName("Video - Receive");
		t.setPriority(Thread.MAX_PRIORITY);
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
		t.setName("Video - Ping");
		t.setPriority(Thread.MIN_PRIORITY);
		t.start();
	}
}
