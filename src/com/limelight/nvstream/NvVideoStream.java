package com.limelight.nvstream;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
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
					firstFrameStream.read();
					System.out.println("VID: First frame: "+firstFrameStream.available()+1);
					firstFrameStream.close();
					System.out.println("VID: Got first frame");
				} catch (UnknownHostException e2) {
					// TODO Auto-generated catch block
					e2.printStackTrace();
					return;
				} catch (IOException e2) {
					// TODO Auto-generated catch block
					e2.printStackTrace();
					return;
				}
				
				DatagramSocket ds;
				try {
					ds = new DatagramSocket(PORT);
				} catch (SocketException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
					return;
				}

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
