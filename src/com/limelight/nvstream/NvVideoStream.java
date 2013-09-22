package com.limelight.nvstream;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;

public class NvVideoStream {
	public static final int PORT = 47998;
	public static final int FIRST_FRAME_PORT = 47996;
	
	private InputStream getFirstFrame(String host) throws UnknownHostException, IOException
	{
		Socket s = new Socket(host, FIRST_FRAME_PORT);
		return s.getInputStream();
	}
	
	public void start(final String host)
	{		
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					System.out.println("VID: Waiting for first frame");
					InputStream firstFrameStream = getFirstFrame(host);
					
					System.out.println(firstFrameStream.available());
					int i;
					for (i = 0; i < 98; i++)
					{
						if (firstFrameStream.read() == -1)
						{
							System.out.println("EOF on FF");
							break;
						}
					}
					System.out.println("VID: First frame read "+i);
				} catch (UnknownHostException e2) {
					// TODO Auto-generated catch block
					e2.printStackTrace();
					return;
				} catch (IOException e2) {
					// TODO Auto-generated catch block
					e2.printStackTrace();
					return;
				}
				
				final DatagramSocket ds;
				try {
					ds = new DatagramSocket(PORT);
				} catch (SocketException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
					return;
				}
				
				// Ping thread
				/*new Thread(new Runnable() {
					@Override
					public void run() {
						byte[] ping = new byte[]{0x50, 0x49, 0x4e, 0x47};
						for (;;)
						{
							DatagramPacket dgp = new DatagramPacket(ping, 0, ping.length);
							dgp.setSocketAddress(new InetSocketAddress(host, PORT));
							try {
								ds.send(dgp);
							} catch (IOException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
								break;
							}
							
							try {
								Thread.sleep(1000);
							} catch (InterruptedException e) {
								break;
							}
						}
					}
				}).start();*/

				for (;;)
				{
					DatagramPacket dp = new DatagramPacket(new byte[1500], 1500);
					
					try {
						ds.receive(dp);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						break;
					}
					
					System.out.println("Got UDP 47998: "+dp.getLength());
				}
			}
			
		}).start();
	}
}
