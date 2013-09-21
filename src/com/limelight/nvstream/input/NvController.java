package com.limelight.nvstream.input;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class NvController {
	
	public final static int PORT = 35043;
	
	private Socket s;
	private OutputStream out;
	
	public NvController(String host) throws UnknownHostException, IOException
	{
		s = new Socket(host, PORT);
		out = s.getOutputStream();
	}
	
	// Example
	public void sendLeftButton() throws IOException
	{
		out.write(new NvInputPacket(NvInputPacket.LEFT_FLAG, (byte)0, (byte)0, (short)0, (short)0).toWire());
	}
	
	// Example
	public void sendRightButton() throws IOException
	{
		out.write(new NvInputPacket(NvInputPacket.RIGHT_FLAG, (byte)0, (byte)0, (short)0, (short)0).toWire());
	}
	
	// Example
	public void clearButtons() throws IOException
	{
		out.write(new NvInputPacket((short)0, (byte)0, (byte)0, (short)0, (short)0).toWire());
	}
}
