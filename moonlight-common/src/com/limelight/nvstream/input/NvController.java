package com.limelight.nvstream.input;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

public class NvController {
	
	public final static int PORT = 35043;
	
	public final static int CONTROLLER_TIMEOUT = 3000;
	
	private InetAddress host;
	private Socket s;
	private OutputStream out;
	
	public NvController(InetAddress host)
	{
		this.host = host;
	}
	
	public void initialize() throws IOException
	{
		s = new Socket();
		s.connect(new InetSocketAddress(host, PORT), CONTROLLER_TIMEOUT);
		s.setTcpNoDelay(true);
		out = s.getOutputStream();
	}
	
	public void close()
	{
		try {
			s.close();
		} catch (IOException e) {}
	}
	
	public void sendControllerInput(short buttonFlags, byte leftTrigger, byte rightTrigger,
			short leftStickX, short leftStickY, short rightStickX, short rightStickY) throws IOException
	{
		out.write(new ControllerPacket(buttonFlags, leftTrigger,
				rightTrigger, leftStickX, leftStickY,
				rightStickX, rightStickY).toWire());
		out.flush();
	}
	
	public void sendMouseButtonDown() throws IOException
	{
		out.write(new MouseButtonPacket(true).toWire());
		out.flush();
	}
	
	public void sendMouseButtonUp() throws IOException
	{
		out.write(new MouseButtonPacket(false).toWire());
		out.flush();
	}
	
	public void sendMouseMove(short deltaX, short deltaY) throws IOException
	{
		out.write(new MouseMovePacket(deltaX, deltaY).toWire());
		out.flush();
	}
	
	public void sendKeyboardInput(short keyMap, byte keyDirection, byte modifier) throws IOException 
	{
		out.write(new KeyboardPacket(keyMap, keyDirection, modifier).toWire());
		out.flush();
	}
}
