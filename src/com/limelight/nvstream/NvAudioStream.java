package com.limelight.nvstream;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

import jlibrtp.Participant;
import jlibrtp.RTPSession;

import com.limelight.nvstream.av.AvBufferDescriptor;
import com.limelight.nvstream.av.AvBufferPool;
import com.limelight.nvstream.av.AvRtpPacket;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.net.rtp.AudioGroup;
import android.net.rtp.AudioStream;
import android.view.Surface;

public class NvAudioStream {
	public static final int RTP_PORT = 48000;
	public static final int RTCP_PORT = 47999;
	
	private LinkedBlockingQueue<AvRtpPacket> packets = new LinkedBlockingQueue<AvRtpPacket>();
	
	private RTPSession session;
	private DatagramSocket rtp;
	
	private AvBufferPool pool = new AvBufferPool(1500);
	
	public void setupRtpSession(String host) throws SocketException
	{
		DatagramSocket rtcp;

		rtp = new DatagramSocket(RTP_PORT);
		rtcp = new DatagramSocket(RTCP_PORT);
		
		session = new RTPSession(rtp, rtcp);
		session.addParticipant(new Participant(host, RTP_PORT, RTCP_PORT));
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
	
	/*public void startStream(String host) throws SocketException, UnknownHostException
	{
		System.out.println("Starting audio group");
		group = new AudioGroup();
		group.setMode(AudioGroup.MODE_NORMAL);
		
		System.out.println("Starting audio stream");
		stream = new AudioStream(InetAddress.getByAddress(new byte[]{0,0,0,0}));
		stream.setMode(AudioStream.MODE_NORMAL);
		stream.associate(InetAddress.getByName(host), PORT);
		stream.setCodec(AudioCodec.PCMA);
		stream.join(group);
		
		for (AudioCodec c : AudioCodec.getCodecs())
			System.out.println(c.type + " " + c.fmtp + " " + c.rtpmap);
		
		System.out.println("Joined");
	}*/

}
