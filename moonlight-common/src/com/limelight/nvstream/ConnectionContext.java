package com.limelight.nvstream;

import java.net.InetAddress;

import javax.crypto.SecretKey;

public class ConnectionContext {
	// Gen 3 servers are 2.1.1 - 2.2.1
	public static final int SERVER_GENERATION_3 = 3;
	
	// Gen 4 servers are 2.2.2+
	public static final int SERVER_GENERATION_4 = 4;	
	
	public InetAddress serverAddress;
	public StreamConfiguration streamConfig;
	public NvConnectionListener connListener;
	public SecretKey riKey;
	public int riKeyId;
	
	public int serverGeneration;
}
