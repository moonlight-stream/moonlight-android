package com.limelight.nvstream;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;

import com.limelight.nvstream.av.AvByteBufferDescriptor;
import com.limelight.nvstream.av.AvDecodeUnit;
import com.limelight.nvstream.av.AvRtpPacket;
import com.limelight.nvstream.av.ConnectionStatusListener;
import com.limelight.nvstream.av.video.AvVideoDepacketizer;
import com.limelight.nvstream.av.video.AvVideoPacket;
import com.limelight.nvstream.av.video.CpuDecoderRenderer;
import com.limelight.nvstream.av.video.DecoderRenderer;
import com.limelight.nvstream.av.video.MediaCodecDecoderRenderer;

import android.os.Build;
import android.view.Surface;

public class NvVideoStream {
	public static final int RTP_PORT = 47998;
	public static final int RTCP_PORT = 47999;
	public static final int FIRST_FRAME_PORT = 47996;
	
	public static final int FIRST_FRAME_TIMEOUT = 5000;
	
	private LinkedBlockingQueue<AvRtpPacket> packets = new LinkedBlockingQueue<AvRtpPacket>(100);
	
	private InetAddress host;
	private DatagramSocket rtp;
	private Socket firstFrameSocket;
	
	private LinkedList<Thread> threads = new LinkedList<Thread>();

	private NvConnectionListener listener;
	private AvVideoDepacketizer depacketizer;
	
	private DecoderRenderer decrend;
	private boolean startedRendering;
	
	private boolean aborting = false;
	
	public NvVideoStream(InetAddress host, NvConnectionListener listener, ConnectionStatusListener avConnListener)
	{
		this.host = host;
		this.listener = listener;
		this.depacketizer = new AvVideoDepacketizer(avConnListener);
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
			decrend.stop();
		}
		
		if (decrend != null) {
			decrend.release();
		}
		
		threads.clear();
	}

	private void readFirstFrame() throws IOException
	{
		byte[] firstFrame = new byte[1500];
		
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
			
			depacketizer.addInputData(new AvVideoPacket(new AvByteBufferDescriptor(firstFrame, 0, offset)));
		} finally {
			firstFrameSocket.close();
			firstFrameSocket = null;
		}
	}
	
	public void setupRtpSession() throws SocketException
	{
		rtp = new DatagramSocket(RTP_PORT);
	}
	
	public void setupDecoderRenderer(Surface renderTarget) {		
		if (Build.HARDWARE.equals("goldfish")) {
			// Emulator - don't render video (it's slow!)
			decrend = null;
		}
		else if (MediaCodecDecoderRenderer.findSafeDecoder() != null) {
			// Hardware decoding
			decrend = new MediaCodecDecoderRenderer();
		}
		else {
			// Software decoding
			decrend = new CpuDecoderRenderer();
		}
		
		if (decrend != null) {
			decrend.setup(1280, 720, renderTarget);
		}
	}

	public void startVideoStream(final Surface surface) throws IOException
	{
		// Setup the decoder and renderer
		setupDecoderRenderer(surface);
		
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
		
		if (decrend != null) {
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
						listener.connectionTerminated(e);
						return;
					}
					
					decrend.submitDecodeUnit(du);
				}
			}
		};
		threads.add(t);
		t.setName("Video - Decoder");
		t.setPriority(Thread.MAX_PRIORITY);
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
						listener.connectionTerminated(e);
						return;
					}
					
					// !!! We no longer own the data buffer at this point !!!
					depacketizer.addInputData(packet);
				}
			}
		};
		threads.add(t);
		t.setName("Video - Depacketizer");
		t.start();
	}
	
	private void startReceiveThread()
	{
		// Receive thread
		Thread t = new Thread() {
			@Override
			public void run() {
				AvByteBufferDescriptor desc = new AvByteBufferDescriptor(new byte[1500], 0, 1500);
				DatagramPacket packet = new DatagramPacket(desc.data, desc.length);
				
				while (!isInterrupted())
				{
					try {
						rtp.receive(packet);
					} catch (IOException e) {
						listener.connectionTerminated(e);
						return;
					}
					
					// Give the packet to the depacketizer thread
					desc.length = packet.getLength();
					if (packets.offer(new AvRtpPacket(desc))) {
						desc.reinitialize(new byte[1500], 0, 1500);
						packet.setData(desc.data, desc.offset, desc.length);
					}
				}
			}
		};
		threads.add(t);
		t.setName("Video - Receive");
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
