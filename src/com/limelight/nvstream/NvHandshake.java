package com.limelight.nvstream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

public class NvHandshake {
	public static final int PORT = 47991;
	
	public static final int HANDSHAKE_TIMEOUT = 5000;
	
	public static final byte[] PLATFORM_HELLO =
		{
			(byte)0x07,
			(byte)0x00,
			(byte)0x00,
			(byte)0x00,
			
			// android in ASCII
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
	
	private static boolean waitAndDiscardResponse(InputStream in)
	{
		// Wait for response and discard response
		try {
			in.read();
			
			// Wait for the full response to come in
			Thread.sleep(250);
			
			for (int i = 0; i < in.available(); i++)
				in.read();
			
		} catch (IOException e1) {
			return false;
		} catch (InterruptedException e) {
			return false;
		}
		
		return true;
	}

	public static boolean performHandshake(InetAddress host) throws IOException
	{
		Socket s = new Socket();
		s.connect(new InetSocketAddress(host, PORT), HANDSHAKE_TIMEOUT);
		s.setSoTimeout(HANDSHAKE_TIMEOUT);
		OutputStream out = s.getOutputStream();
		InputStream in = s.getInputStream();
		
		// First packet
		out.write(PLATFORM_HELLO);
		out.flush();
		
		if (!waitAndDiscardResponse(in)) {
			s.close();
			return false;
		}
		
		// Second packet
		out.write(PACKET_2);
		out.flush();
				
		if (!waitAndDiscardResponse(in)) {
			s.close();
			return false;
		}
		
		// Third packet
		out.write(PACKET_3);
		out.flush();
		
		if (!waitAndDiscardResponse(in)) {
			s.close();
			return false;
		}
		
		// Fourth packet
		out.write(PACKET_4);
		out.flush();
		
		// Done
		s.close();
		
		return true;
	}
}
