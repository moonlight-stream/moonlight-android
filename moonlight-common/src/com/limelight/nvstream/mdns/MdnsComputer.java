package com.limelight.nvstream.mdns;

import java.net.InetAddress;
import java.util.UUID;

public class MdnsComputer {
	private InetAddress ipAddr;
	private UUID uniqueId;
	private String name;
	
	public MdnsComputer(String name, UUID uniqueId, InetAddress addr) {
		this.name = name;
		this.uniqueId = uniqueId;
		this.ipAddr = addr;
	}
	
	public String getName() {
		return name;
	}
	
	public UUID getUniqueId() {
		return uniqueId;
	}
	
	public InetAddress getAddress() {
		return ipAddr;
	}
	
	@Override
	public int hashCode() {
		return uniqueId.hashCode();
	}
	
	@Override
	public boolean equals(Object o) {
		if (o instanceof MdnsComputer) {
			MdnsComputer other = (MdnsComputer)o;
			if (other.uniqueId.equals(uniqueId) &&
				other.ipAddr.equals(ipAddr) &&
				other.name.equals(name)) {
				return true;
			}
		}
		
		return false;
	}
	
	@Override
	public String toString() {
		return "["+name+" - "+uniqueId+" - "+ipAddr+"]";
	}
}
