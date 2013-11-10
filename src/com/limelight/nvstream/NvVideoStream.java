package com.limelight.nvstream;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;

import com.limelight.nvstream.av.AvByteBufferDescriptor;
import com.limelight.nvstream.av.AvDecodeUnit;
import com.limelight.nvstream.av.AvRtpPacket;
import com.limelight.nvstream.av.video.AvVideoDepacketizer;
import com.limelight.nvstream.av.video.AvVideoPacket;

import jlibrtp.Participant;
import jlibrtp.RTPSession;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.view.Surface;

public class NvVideoStream {
	public static final int RTP_PORT = 47998;
	public static final int RTCP_PORT = 47999;
	public static final int FIRST_FRAME_PORT = 47996;
	
	private ByteBuffer[] videoDecoderInputBuffers;
	private MediaCodec videoDecoder;
	
	private LinkedBlockingQueue<AvRtpPacket> packets = new LinkedBlockingQueue<AvRtpPacket>();
	
	private RTPSession session;
	private DatagramSocket rtp, rtcp;
	
	private LinkedList<Thread> threads = new LinkedList<Thread>();

	private AvVideoDepacketizer depacketizer = new AvVideoDepacketizer();
	
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
		if (rtcp != null) {
			rtcp.close();
		}
		
		// Wait for threads to terminate
		for (Thread t : threads) {
			try {
				t.join();
			} catch (InterruptedException e) { }
		}
		
		//session.endSession();
		videoDecoder.release();
		
		threads.clear();
	}
	
	public void trim()
	{
		depacketizer.trim();
	}
	
	private InputStream openFirstFrameInputStream(String host) throws UnknownHostException, IOException
	{
		Socket s = new Socket(host, FIRST_FRAME_PORT);
		return s.getInputStream();
	}
	
	private void readFirstFrame(String host) throws IOException
	{
		byte[] firstFrame = depacketizer.allocatePacketBuffer();
		System.out.println("VID: Waiting for first frame");
		InputStream firstFrameStream = openFirstFrameInputStream(host);
		
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
	}
	
	public void setupRtpSession(String host) throws SocketException
	{
		rtp = new DatagramSocket(RTP_PORT);
		rtcp = new DatagramSocket(RTCP_PORT);
		
		rtp.setReceiveBufferSize(2097152);
		System.out.println("RECV BUF: "+rtp.getReceiveBufferSize());
		System.out.println("SEND BUF: "+rtp.getSendBufferSize());
		
		session = new RTPSession(rtp, rtcp);
		session.addParticipant(new Participant(host, RTP_PORT, RTCP_PORT));
	}
	
	public void setupDecoders(Surface surface)
	{
		videoDecoder = MediaCodec.createDecoderByType("video/avc");
		MediaFormat videoFormat = MediaFormat.createVideoFormat("video/avc", 1280, 720);

		videoDecoder.configure(videoFormat, surface, null, 0);

		videoDecoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
		
		videoDecoder.start();

		videoDecoderInputBuffers = videoDecoder.getInputBuffers();
	}

	public void startVideoStream(final String host, final Surface surface)
	{
		// This thread becomes the output display thread
		Thread t = new Thread() {
			@Override
			public void run() {
				// Setup the decoder context
				setupDecoders(surface);
				
				// Open RTP sockets and start session
				try {
					setupRtpSession(host);
				} catch (SocketException e1) {
					e1.printStackTrace();
					return;
				}
				
				// Read the first frame to start the UDP video stream
				try {
					readFirstFrame(host);
				} catch (IOException e2) {
					e2.printStackTrace();
					abort();
					return;
				}
				
				// Start the receive thread early to avoid missing
				// early packets
				startReceiveThread();
				
				// Start the depacketizer thread to deal with the RTP data
				startDepacketizerThread();
				
				// Start decoding the data we're receiving
				startDecoderThread();
				
				// Start the keepalive ping to keep the stream going
				startUdpPingThread();
				
				// Render the frames that are coming out of the decoder
				outputDisplayLoop(this);
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
					
					switch (du.getType())
					{
						case AvDecodeUnit.TYPE_H264:
						{
							// Wait for an input buffer or thread termination
							while (!isInterrupted())
							{
								int inputIndex = videoDecoder.dequeueInputBuffer(100);
								if (inputIndex >= 0)
								{
									ByteBuffer buf = videoDecoderInputBuffers[inputIndex];
									
									// Clear old input data
									buf.clear();
									
									// Copy data from our buffer list into the input buffer
									for (AvByteBufferDescriptor desc : du.getBufferList())
									{
										buf.put(desc.data, desc.offset, desc.length);
									}
									
									depacketizer.releaseDecodeUnit(du);

									videoDecoder.queueInputBuffer(inputIndex,
												0, du.getDataLength(),
												0, du.getFlags());
									
									break;
								}
							}
						}
						break;
					
						default:
						{
							System.err.println("Unknown decode unit type");
							abort();
							return;
						}
					}
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
				final byte[] pingPacket = new byte[] {0x50, 0x49, 0x4E, 0x47};
				
				// RTP payload type is 127 (dynamic)
				session.payloadType(127);
				
				// Send PING every 100 ms
				while (!isInterrupted())
				{
					session.sendData(pingPacket);
					
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
	
	private void outputDisplayLoop(Thread t)
	{
		while (!t.isInterrupted())
		{
			BufferInfo info = new BufferInfo();
			int outIndex = videoDecoder.dequeueOutputBuffer(info, 100);
		    switch (outIndex) {
		    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
		    	System.out.println("Output buffers changed");
			    break;
		    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
		    	System.out.println("Output format changed");
		    	System.out.println("New output Format: " + videoDecoder.getOutputFormat());
		    	break;
		    default:
		      break;
		    }
		    if (outIndex >= 0) {
		    	videoDecoder.releaseOutputBuffer(outIndex, true);
		    }
		}
	}
}
