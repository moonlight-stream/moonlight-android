package com.limelight.nvstream.input;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
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

	
	private final static byte[] ENCRYPTED_HEADER = new byte[] {0x00, 0x00, 0x00, 0x20};
	
	public NvController(InetAddress host, SecretKey riKey)
	{
		this.host = host;
		try {
			// This cipher is guaranteed to be supported
			this.riCipher = Cipher.getInstance("AES/CBC/NoPadding");
			this.riCipher.init(Cipher.ENCRYPT_MODE, riKey, new IvParameterSpec(new byte[16]));
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
	
	private byte[] encryptAesInputData(byte[] data) throws Exception {
		// Input data is rounded to units of 32 bytes
		byte[] blockRoundedData = Arrays.copyOf(data, 32);
		return riCipher.update(blockRoundedData);
	}
	
	private void sendPacket(InputPacket packet) throws IOException {
		out.write(ENCRYPTED_HEADER);
		byte[] encryptedInput;
		try {
			encryptedInput = encryptAesInputData(packet.toWire());
		} catch (Exception e) {
			// Should never happen
			e.printStackTrace();
			return;
		}
		out.write(encryptedInput);
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
