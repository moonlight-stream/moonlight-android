package com.limelight.nvstream;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class NvMDNS {
	
	
	
	public static String NVSTREAM_MDNS_QUERY = "_nvstream._tcp.local.";
	public static final short MDNS_PORT = 5353;
	public static final int REPLY_TIMEOUT = 3000;
	public static final int REPLY_TRIES = 10;
	public static InetAddress MDNS_ADDRESS;
	
	static {
		try {
			// 224.0.0.251 is the mDNS multicast address
			MDNS_ADDRESS = InetAddress.getByName("224.0.0.251");
		} catch (UnknownHostException e) {
			MDNS_ADDRESS = null;
		}
	}
	
	// TODO: Implement this shit
	
}
