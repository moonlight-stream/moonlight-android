package com.limelight.nvstream.input;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

public class NvController {
	
	public final static int PORT = 35043;
	
	private Socket s;
	private OutputStream out;
	
	public NvController(String host) throws UnknownHostException, IOException
	{
		s = new Socket(host, PORT);
		s.setTcpNoDelay(true);
		out = s.getOutputStream();
	}
	
	public void sendControllerInput(short buttonFlags, byte leftTrigger, byte rightTrigger,
			short leftStickX, short leftStickY, short rightStickX, short rightStickY) throws IOException
	{
		out.write(new NvControllerPacket(buttonFlags, leftTrigger,
				rightTrigger, leftStickX, leftStickY,
				rightStickX, rightStickY).toWire());
		out.flush();
	}
	
	public void sendMouseButtonDown() throws IOException
	{
		out.write(new NvMouseButtonPacket(true).toWire());
		out.flush();
	}
	
	public void sendMouseButtonUp() throws IOException
	{
		out.write(new NvMouseButtonPacket(false).toWire());
		out.flush();
	}
	
	public void sendMouseMove(short deltaX, short deltaY) throws IOException
	{
		out.write(new NvMouseMovePacket(deltaX, deltaY).toWire());
		out.flush();
	}
}
