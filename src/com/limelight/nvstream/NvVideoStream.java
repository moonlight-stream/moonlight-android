package com.limelight.nvstream;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import jlibrtp.DataFrame;
import jlibrtp.Participant;
import jlibrtp.RTPAppIntf;
import jlibrtp.RTPSession;

import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.view.Surface;

public class NvVideoStream implements RTPAppIntf {
	public static final int RTP_PORT = 47998;
	public static final int RTCP_PORT = 47999;
	public static final int FIRST_FRAME_PORT = 47996;
	
	private static final int FRAME_RATE = 60;
	private ByteBuffer[] decoderInputBuffers = null;
	private MediaCodec decoder;
	
	private int frameIndex = 0;
	
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

				decoder = MediaCodec.createDecoderByType("video/avc");
				MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", 1280, 720);
		
				decoder.configure(mediaFormat, surface, null, 0);
				decoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
				decoder.start();
				decoderInputBuffers = decoder.getInputBuffers();

				int inputIndex = decoder.dequeueInputBuffer(-1);
				if (inputIndex >= 0)
				{
					ByteBuffer buf = decoderInputBuffers[inputIndex];
					
					buf.clear();
					buf.put(firstFrame);
					
					decoder.queueInputBuffer(inputIndex,
							0, firstFrame.length,
							0, 0);
					frameIndex++;
				}
				
				final RTPSession session = new RTPSession(rtp, rtcp);
				session.addParticipant(new Participant(host, RTP_PORT, RTCP_PORT));
				//session.RTPSessionRegister(NvVideoStream.this, null, null);
				
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
				
				// Receive thread
				new Thread(new Runnable() {
					@Override
					public void run() {
						byte[] packet = new byte[1500];
						
						// Send PING every 100 ms
						for (;;)
						{
							DatagramPacket dp = new DatagramPacket(packet, 0, packet.length);
							
							try {
								rtp.receive(dp);
							} catch (IOException e) {
								e.printStackTrace();
								break;
							}
							
							System.out.println("in receiveData");
							int inputIndex = decoder.dequeueInputBuffer(-1);
							if (inputIndex >= 0)
							{
								ByteBuffer buf = decoderInputBuffers[inputIndex];
								NvVideoPacket nvVideo = new NvVideoPacket(dp.getData());
								
								buf.clear();
								buf.put(nvVideo.data);
							
								System.out.println(nvVideo);
								if (nvVideo.length == 0xc803) {
									decoder.queueInputBuffer(inputIndex,
											0, nvVideo.length,
											0, 0);
									frameIndex++;
								} else {
									decoder.queueInputBuffer(inputIndex,
											0, 0,
											0, 0);
								}
							}
						}
					}
				}).start();
				
				for (;;)
				{
					BufferInfo info = new BufferInfo();
					System.out.println("dequeuing outputbuffer");
					int outIndex = decoder.dequeueOutputBuffer(info, -1);
					System.out.println("done dequeuing output buffer");
				    switch (outIndex) {
				    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
				    	System.out.println("Output buffers changed");
					    break;
				    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
				    	System.out.println("Output format changed");
				    	//decoderOutputFormat = decoder.getOutputFormat();
				    	System.out.println("New output Format: " + decoder.getOutputFormat());
				    	break;
				    case MediaCodec.INFO_TRY_AGAIN_LATER:
				    	System.out.println("Try again later");
				    	break;
				    default:
				      break;
				    }
				    if (outIndex >= 0) {
				    	System.out.println("releasing output buffer");
				    	decoder.releaseOutputBuffer(outIndex, true);
				    	System.out.println("output buffer released");
				    }
			    	
				}
			}
		}).start();
	}

	@Override
	public void receiveData(DataFrame frame, Participant participant) {
	}

	@Override
	public void userEvent(int type, Participant[] participant) {
	}

	@Override
	public int frameSize(int payloadType) {
		return 1;
	}
	
    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private static long computePresentationTime(int frameIndex) {
        return 132 + frameIndex * 1000000 / FRAME_RATE;
    }
    
    class NvVideoPacket {
    	byte[] preamble;
    	short length;
    	byte[] extra;
    	byte[] data;
    	
    	public NvVideoPacket(byte[] payload)
    	{
    		ByteBuffer bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
    		
    		preamble = new byte[12+16];
    		extra = new byte[38];
    		
    		bb.get(preamble);
    		length = bb.getShort();
    		bb.get(extra);
    		data = new byte[length];
    		
    		if (bb.remaining() + length <= payload.length)
    			bb.get(data);
    	}
    	
    	public String toString()
    	{
    		return "";//String.format("Length: %d | %02x %02x %02x %02x %02x %02x %02x %02x",
    				//length, data[0], data[1], data[2], data[3], data[4], data[5], data[6], data[7]);
    	}
    }
}
