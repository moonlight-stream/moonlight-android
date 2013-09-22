package com.limelight.nvstream;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

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
	
	private MediaCodec codec;
	
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

				codec = MediaCodec.createDecoderByType("video/avc");
				MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", 1280, 720);
				codec.configure(mediaFormat, surface, null, 0);
				codec.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
				codec.start();
		

				int inputIndex = codec.dequeueInputBuffer(-1);
				if (inputIndex >= 0)
				{
					ByteBuffer buf = codec.getInputBuffers()[inputIndex];
					
					buf.clear();
					buf.put(firstFrame);
					
					codec.queueInputBuffer(inputIndex,
							0, firstFrame.length,
							100, MediaCodec.BUFFER_FLAG_CODEC_CONFIG);
				}
				
				RTPSession session = new RTPSession(rtp, rtcp);
				session.addParticipant(new Participant(host, RTP_PORT, RTCP_PORT));
				session.RTPSessionRegister(NvVideoStream.this, null, null);
				
				for (;;)
				{
					BufferInfo info = new BufferInfo();
					int outIndex = codec.dequeueOutputBuffer(info, -1);
				    switch (outIndex) {
				    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
				    	System.out.println("Output buffers changed");
					    break;
				    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
				    	System.out.println("Output format changed");
				    	break;
				    case MediaCodec.INFO_TRY_AGAIN_LATER:
				    	System.out.println("Try again later");
				    	break;
				    default:
				    	if (outIndex >= 0)
				    	{
				    		codec.releaseOutputBuffer(outIndex, true);
				    	}
				      break;
				    }
				}
			}
		}).start();
	}

	@Override
	public void receiveData(DataFrame frame, Participant participant) {
		
		ByteBuffer[] codecInputBuffers = codec.getInputBuffers();

		int inputIndex = codec.dequeueInputBuffer(-1);
		if (inputIndex >= 0)
		{
			ByteBuffer buf = codecInputBuffers[inputIndex];
			
			buf.clear();
			buf.put(frame.getConcatenatedData());
			
			if (buf.position() != 1024)
			{
				System.out.println("Data length: "+buf.position());
				System.out.println(buf.get()+" "+buf.get()+" "+buf.get());
			}

			codec.queueInputBuffer(inputIndex,
					0, buf.position(),
					10000000, 0);
		}
	}

	@Override
	public void userEvent(int type, Participant[] participant) {
	}

	@Override
	public int frameSize(int payloadType) {
		return 1;
	}
}
