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
		out.write(new NvInputPacket(buttonFlags, leftTrigger,
				rightTrigger, leftStickX, leftStickY,
				rightStickX, rightStickY).toWire());
		out.flush();
	}
}
