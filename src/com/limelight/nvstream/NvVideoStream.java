package com.limelight.nvstream;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

import com.limelight.nvstream.av.AvBufferDescriptor;
import com.limelight.nvstream.av.AvBufferPool;
import com.limelight.nvstream.av.AvDecodeUnit;
import com.limelight.nvstream.av.AvPacket;
import com.limelight.nvstream.av.AvDepacketizer;
import com.limelight.nvstream.av.AvRtpPacket;

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
	
	private ByteBuffer[] videoDecoderInputBuffers = null;
	private MediaCodec videoDecoder;
	
	private LinkedBlockingQueue<AvRtpPacket> packets = new LinkedBlockingQueue<AvRtpPacket>();
	
	private RTPSession session;
	private DatagramSocket rtp;
	
	private AvBufferPool pool = new AvBufferPool(1500);
	
	private AvDepacketizer depacketizer = new AvDepacketizer();
	
	private InputStream openFirstFrameInputStream(String host) throws UnknownHostException, IOException
	{
		Socket s = new Socket(host, FIRST_FRAME_PORT);
		return s.getInputStream();
	}
	
	private void readFirstFrame(String host) throws IOException
	{
		byte[] firstFrame = pool.allocate();
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
		
		// FIXME: Investigate: putting these NALs into the data stream
		// causes the picture to get messed up
		//depacketizer.addInputData(new AvPacket(new AvBufferDescriptor(firstFrame, 0, offset)));
	}
	
	public void setupRtpSession(String host) throws SocketException
	{
		DatagramSocket rtcp;

		rtp = new DatagramSocket(RTP_PORT);
		
		rtp.setReceiveBufferSize(2097152);
		System.out.println("RECV BUF: "+rtp.getReceiveBufferSize());
		System.out.println("SEND BUF: "+rtp.getSendBufferSize());

		
		rtcp = new DatagramSocket(RTCP_PORT);
		
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
		new Thread(new Runnable() {

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
				
				// Start the receive thread early to avoid missing
				// early packets
				startReceiveThread();
				
				// Start the keepalive ping to keep the stream going
				startUdpPingThread();
				
				// Start the depacketizer thread to deal with the RTP data
				startDepacketizerThread();
				
				// Start decoding the data we're receiving
				startDecoderThread();
				
				// Read the first frame to start the UDP video stream
				try {
					readFirstFrame(host);
				} catch (IOException e2) {
					e2.printStackTrace();
					return;
				}
				
				// Render the frames that are coming out of the decoder
				outputDisplayLoop();
			}
		}).start();
	}
	
	private void startDecoderThread()
	{
		// Decoder thread
		new Thread(new Runnable() {
			@Override
			public void run() {
				// Read the decode units generated from the RTP stream
				for (;;)
				{
					AvDecodeUnit du;
					try {
						du = depacketizer.getNextDecodeUnit();
					} catch (InterruptedException e) {
						e.printStackTrace();
						return;
					}
					
					switch (du.getType())
					{
						case AvDecodeUnit.TYPE_H264:
						{
							int inputIndex = videoDecoder.dequeueInputBuffer(-1);
							if (inputIndex >= 0)
							{
								ByteBuffer buf = videoDecoderInputBuffers[inputIndex];
								
								// Clear old input data
								buf.clear();
								
								// Copy data from our buffer list into the input buffer
								for (AvBufferDescriptor desc : du.getBufferList())
								{
									buf.put(desc.data, desc.offset, desc.length);
									
									// Release the buffer back to the buffer pool
									pool.free(desc.data);
								}

								videoDecoder.queueInputBuffer(inputIndex,
											0, du.getDataLength(),
											0, du.getFlags());
							}
						}
						break;
					
						default:
						{
							System.out.println("Unknown decode unit type");
						}
						break;
					}
				}
			}
		}).start();
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
					depacketizer.addInputData(packet);
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
	
	private void outputDisplayLoop()
	{
		for (;;)
		{
			BufferInfo info = new BufferInfo();
			int outIndex = videoDecoder.dequeueOutputBuffer(info, -1);
		    switch (outIndex) {
		    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
		    	System.out.println("Output buffers changed");
			    break;
		    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
		    	System.out.println("Output format changed");
		    	System.out.println("New output Format: " + videoDecoder.getOutputFormat());
		    	break;
		    case MediaCodec.INFO_TRY_AGAIN_LATER:
		    	System.out.println("Try again later");
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
