package com.limelight.nvstream.mdns;

import java.net.Inet6Address;
import java.net.InetAddress;

public class MdnsComputer {
	private InetAddress localAddr;
	private Inet6Address v6Addr;
	private String name;

	public MdnsComputer(String name, InetAddress localAddress, Inet6Address v6Addr) {
		this.name = name;
		this.localAddr = localAddress;
		this.v6Addr = v6Addr;
	}

	public String getName() {
		return name;
	}

	public InetAddress getLocalAddress() {
		return localAddr;
	}

	public Inet6Address getIpv6Address() {
		return v6Addr;
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof MdnsComputer) {
			MdnsComputer other = (MdnsComputer)o;

			if (!other.name.equals(name)) {
				return false;
			}

			if ((other.localAddr != null && localAddr == null) ||
					(other.localAddr == null && localAddr != null) ||
					(other.localAddr != null && !other.localAddr.equals(localAddr))) {
				return false;
			}

			if ((other.v6Addr != null && v6Addr == null) ||
					(other.v6Addr == null && v6Addr != null) ||
					(other.v6Addr != null && !other.v6Addr.equals(v6Addr))) {
				return false;
			}

			return true;
		}

		return false;
	}

	@Override
	public String toString() {
		return "["+name+" - "+localAddr+" - "+v6Addr+"]";
	}
}
