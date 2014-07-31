package com.limelight.nvstream.input;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

public class NvController {
	
	public final static int PORT = 35043;
	
	public final static int CONTROLLER_TIMEOUT = 3000;
	
	private InetAddress host;
	private Socket s;
	private OutputStream out;
	private Cipher riCipher;
	
	public NvController(InetAddress host, SecretKey riKey, int riKeyId)
	{
		this.host = host;
		try {
			// This cipher is guaranteed to be supported
			this.riCipher = Cipher.getInstance("AES/CBC/NoPadding");
			
			ByteBuffer bb = ByteBuffer.allocate(16);
			bb.putInt(riKeyId);
			
			this.riCipher.init(Cipher.ENCRYPT_MODE, riKey, new IvParameterSpec(bb.array()));
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		} catch (InvalidAlgorithmParameterException e) {
			e.printStackTrace();
		}
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
	
	private static int getPaddedSize(int length) {
		return ((length + 15) / 16) * 16;
	}
	
	private static byte[] padData(byte[] data) {
		// This implements the PKCS7 padding algorithm
		
		if ((data.length % 16) == 0) {
			// Already a multiple of 16
			return data;
		}
		
		byte[] padded = Arrays.copyOf(data, getPaddedSize(data.length));
		byte paddingByte = (byte)(16 - (data.length % 16));
		
		for (int i = data.length; i < padded.length; i++) {
			padded[i] = paddingByte;
		}
		
		return padded;
	}
	
	private byte[] encryptAesInputData(byte[] data) throws Exception {
		return riCipher.update(padData(data));
	}
	
	private void sendPacket(InputPacket packet) throws IOException {
		byte[] toWire = packet.toWire();
		
		// Pad to 16 byte chunks
		int paddedLength = getPaddedSize(toWire.length);
		
		// Allocate a byte buffer to represent the final packet
		ByteBuffer bb = ByteBuffer.allocate(4 + paddedLength);
		bb.putInt(paddedLength);
		try {
			bb.put(encryptAesInputData(toWire));
		} catch (Exception e) {
			// Should never happen
			e.printStackTrace();
			return;
		}
		
		// Send the packet
		out.write(bb.array());
		out.flush();
	}
	
	public void sendControllerInput(short buttonFlags, byte leftTrigger, byte rightTrigger,
			short leftStickX, short leftStickY, short rightStickX, short rightStickY) throws IOException
	{
		sendPacket(new ControllerPacket(buttonFlags, leftTrigger,
				rightTrigger, leftStickX, leftStickY,
				rightStickX, rightStickY));
	}
	
	public void sendMouseButtonDown(byte mouseButton) throws IOException
	{
		sendPacket(new MouseButtonPacket(true, mouseButton));
	}
	
	public void sendMouseButtonUp(byte mouseButton) throws IOException
	{
		sendPacket(new MouseButtonPacket(false, mouseButton));
	}
	
	public void sendMouseMove(short deltaX, short deltaY) throws IOException
	{
		sendPacket(new MouseMovePacket(deltaX, deltaY));
	}
	
	public void sendKeyboardInput(short keyMap, byte keyDirection, byte modifier) throws IOException 
	{
		sendPacket(new KeyboardPacket(keyMap, keyDirection, modifier));
	}
}
