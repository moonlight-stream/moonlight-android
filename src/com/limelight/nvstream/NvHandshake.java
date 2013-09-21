package com.limelight.nvstream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;;

public class NvHandshake {
	public static final int PORT = 47991;
	
	// android
	public static final byte[] PLATFORM_HELLO =
		{
			(byte)0x61,
			(byte)0x6e,
			(byte)0x64,
			(byte)0x72,
			(byte)0x6f,
			(byte)0x69,
			(byte)0x64,
			(byte)0x03,
			(byte)0x01,
			(byte)0x00,
			(byte)0x00
		};
	
	public static final byte[] PACKET_2 =
		{
			(byte)0x01,
			(byte)0x03,
			(byte)0x02,
			(byte)0x00,
			(byte)0x08,
			(byte)0x00
		};
	
	public static final byte[] PACKET_3 =
		{
			(byte)0x04,
			(byte)0x01,
			(byte)0x00,
			(byte)0x00,
			
			(byte)0x00,
			(byte)0x00,
			(byte)0x00,
			(byte)0x00
		};
	
	public static final byte[] PACKET_4 =
		{
			(byte)0x01,
			(byte)0x01,
			(byte)0x00,
			(byte)0x00
		};
	
	private static void waitAndDiscardResponse(InputStream in) throws IOException
	{
		// Wait for response and discard response
		in.read();
		
		try {
			Thread.sleep(250);
		} catch (InterruptedException e) { }
		
		for (int i = 0; i < in.available(); i++)
			in.read();
	}
	
	public static void performHandshake(String host) throws UnknownHostException, IOException
	{
		Socket s = new Socket(host, PORT);
		OutputStream out = s.getOutputStream();
		InputStream in = s.getInputStream();
		
		// First packet
		out.write(new byte[]{0x07, 0x00, 0x00, 0x00});
		out.write(PLATFORM_HELLO);
		
		System.out.println("HS: Waiting for hello response");
		
		waitAndDiscardResponse(in);
		
		// Second packet
		out.write(PACKET_2);
		
		System.out.println("HS: Waiting stage 2 response");
		
		waitAndDiscardResponse(in);
		
		// Third packet
		out.write(PACKET_3);
		
		System.out.println("HS: Waiting for stage 3 response");
		
		waitAndDiscardResponse(in);
		
		// Fourth packet
		out.write(PACKET_4);
		out.flush();
		
		// Done
		s.close();
	}
}
