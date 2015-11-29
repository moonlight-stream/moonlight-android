package com.limelight.nvstream.mdns;

import java.net.InetAddress;

public class MdnsComputer {
	private InetAddress ipAddr;
	private String name;
	
	public MdnsComputer(String name, InetAddress addr) {
		this.name = name;
		this.ipAddr = addr;
	}
	
	public String getName() {
		return name;
	}
	
	public InetAddress getAddress() {
		return ipAddr;
	}
	
	@Override
	public int hashCode() {
		return name.hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof MdnsComputer) {
			MdnsComputer other = (MdnsComputer)o;
			if (other.ipAddr.equals(ipAddr) &&
				other.name.equals(name)) {
				return true;
			}
		}
		
		return false;
	}
	
	@Override
	public String toString() {
		return "["+name+" - "+ipAddr+"]";
	}
}
