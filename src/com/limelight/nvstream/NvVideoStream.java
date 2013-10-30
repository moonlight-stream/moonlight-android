package com.limelight.nvstream;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.limelight.nvstream.av.AvBufferDescriptor;
import com.limelight.nvstream.av.AvDecodeUnit;
import com.limelight.nvstream.av.AvPacket;
import com.limelight.nvstream.av.AvParser;

import jlibrtp.DataFrame;
import jlibrtp.Participant;
import jlibrtp.RTPAppIntf;
import jlibrtp.RTPSession;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.view.Surface;

public class NvVideoStream {
	public static final int RTP_PORT = 47998;
	public static final int RTCP_PORT = 47999;
	public static final int FIRST_FRAME_PORT = 47996;
	
	private static final int FRAME_RATE = 60;
	private ByteBuffer[] videoDecoderInputBuffers = null;
	private MediaCodec videoDecoder;
	
	private AvParser parser = new AvParser();
	
	private InputStream getFirstFrame(String host) throws UnknownHostException, IOException
	{
		Socket s = new Socket(host, FIRST_FRAME_PORT);
		return s.getInputStream();
	}

	public void startVideoStream(final String host, final Surface surface)
	{		
		new Thread(new Runnable() {

			@Override
			public void run() {
				
				byte[] firstFrame = new byte[98];
				try {
					System.out.println("VID: Waiting for first frame");
					InputStream firstFrameStream = getFirstFrame(host);
					
					int offset = 0;
					do
					{
						offset = firstFrameStream.read(firstFrame, offset, firstFrame.length-offset);
					} while (offset != firstFrame.length);
					System.out.println("VID: First frame read ");
				} catch (UnknownHostException e2) {
					// TODO Auto-generated catch block
					e2.printStackTrace();
					return;
				} catch (IOException e2) {
					// TODO Auto-generated catch block
					e2.printStackTrace();
					return;
				}
				
				final DatagramSocket rtp, rtcp;
				try {
					rtp = new DatagramSocket(RTP_PORT);
					rtcp = new DatagramSocket(RTCP_PORT);
				} catch (SocketException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
					return;
				}

				videoDecoder = MediaCodec.createDecoderByType("video/avc");
				MediaFormat videoFormat = MediaFormat.createVideoFormat("video/avc", 1280, 720);
		
				videoDecoder.configure(videoFormat, surface, null, 0);
				videoDecoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
				videoDecoder.start();
				videoDecoderInputBuffers = videoDecoder.getInputBuffers();
				
				final RTPSession session = new RTPSession(rtp, rtcp);
				session.addParticipant(new Participant(host, RTP_PORT, RTCP_PORT));
				
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
				
				// Decoder thread
				new Thread(new Runnable() {
					@Override
					public void run() {
						// Read the decode units generated from the RTP stream
						for (;;)
						{
							AvDecodeUnit du;
							try {
								du = parser.getNextDecodeUnit();
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
										}
									
										videoDecoder.queueInputBuffer(inputIndex,
													0, du.getDataLength(),
													0, 0);
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
				
				// Receive thread
				new Thread(new Runnable() {

					@Override
					public void run() {
						byte[] buffer = new byte[1500];
						AvBufferDescriptor desc = new AvBufferDescriptor(null, 0, 0);
						
						for (;;)
						{
							DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
							
							try {
								rtp.receive(packet);
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
								return;
							}
							
							desc.length = packet.getLength();
							desc.offset = packet.getOffset();
							desc.data = packet.getData();
							
							// Skip the RTP header
							desc.offset += 12;
							desc.length -= 12;
							
							// Give the data to the AV parser
							parser.addInputData(new AvPacket(desc));
							
						}
					}
					
				}).start();
				
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
		}).start();
	}
	
    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private static long computePresentationTime(int frameIndex) {
        return 132 + frameIndex * 1000000 / FRAME_RATE;
    }
}
