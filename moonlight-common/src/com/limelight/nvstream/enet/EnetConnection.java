package com.limelight.nvstream.enet;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;

public class EnetConnection implements Closeable {
	private long enetPeer;
	private long enetClient;
	
	private static final int ENET_PACKET_FLAG_RELIABLE = 1;
	
	static {
		System.loadLibrary("jnienet");
		
		initializeEnet();
	}
	
	private EnetConnection() {}
	
	public static EnetConnection connect(String host, int port, int timeout) throws IOException {
		EnetConnection conn = new EnetConnection();
		
		conn.enetClient = createClient();
		if (conn.enetClient == 0) {
			throw new IOException("Unable to create ENet client");
		}
		
		conn.enetPeer = connectToPeer(conn.enetClient, host, port, timeout);
		if (conn.enetPeer == 0) {
			try {
				conn.close();
			} catch (IOException e) {}
			throw new IOException("Unable to connect to UDP port "+port);
		}
		
		return conn;
	}
	
	public void pumpSocket() throws IOException {
		int ret;
		while ((ret = readPacket(enetClient, null, 0, 0)) > 0);
		if (ret < 0) {
			throw new IOException("ENet connection failed");
		}
	}
	
	public ByteBuffer readPacket(int maxSize, int timeout) throws IOException {
		ByteBuffer buffer;
		byte[] array;
		int length;
		
		if (maxSize != 0) {
			buffer = ByteBuffer.allocate(maxSize);
			array = buffer.array();
			length = buffer.limit();
		}
		else {
			// The caller doesn't want the packet back
			buffer = null;
			array = null;
			length = 0;
		}
		
		int readLength = readPacket(enetClient, array, length, timeout);
		if (readLength > length && length != 0) {
			// This is a packet that was unexpectedly large compared to
			// what the caller was expected.
			throw new IOException("Received ENet packet too large: "+readLength);
		}
		else if (readLength <= 0) {
			// We either got nothing or a socket error
			throw new IOException("Failed to receive ENet packet");
		}
		else if (length == 0) {
			// We received a packet but the caller didn't want it back
			return null;
		}
		else {
			// A packet was received which matched the caller's expectations
			buffer.limit(readLength);
			return buffer;
		}
	}
	
	public void writePacket(ByteBuffer buffer) throws IOException {
		if (!writePacket(enetClient, enetPeer, buffer.array(), buffer.limit(), ENET_PACKET_FLAG_RELIABLE)) {
			throw new IOException("Failed to send ENet packet");
		}
	}

	@Override
	public void close() throws IOException {
		if (enetPeer != 0) {
			disconnectPeer(enetPeer);
			enetPeer = 0;
		}
		
		if (enetClient != 0) {
			destroyClient(enetClient);
			enetClient = 0;
		}
	}
	
	private static native int initializeEnet();
	private static native long createClient();
	private static native long connectToPeer(long client, String host, int port, int timeout);
	private static native int readPacket(long client, byte[] data, int length, int timeout);
	private static native boolean writePacket(long client, long peer, byte[] data, int length, int packetFlags);
	private static native void destroyClient(long client);
	private static native void disconnectPeer(long peer);
}
