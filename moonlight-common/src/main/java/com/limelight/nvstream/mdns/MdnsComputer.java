package com.limelight.nvstream.mdns;

import java.net.Inet4Address;
import java.net.Inet6Address;

public class MdnsComputer {
	private Inet4Address v4Addr;
	private Inet6Address v6Addr;
	private String name;

	public MdnsComputer(String name, Inet4Address v4Addr, Inet6Address v6Addr) {
		this.name = name;
		this.v4Addr = v4Addr;
		this.v6Addr = v6Addr;
	}

	public String getName() {
		return name;
	}

	public Inet4Address getAddressV4() {
		return v4Addr;
	}

	public Inet6Address getAddressV6() {
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

			if ((other.v4Addr != null && v4Addr == null) ||
					(other.v4Addr == null && v4Addr != null) ||
					(other.v4Addr != null && !other.v4Addr.equals(v4Addr))) {
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
		return "["+name+" - "+v4Addr+" - "+v6Addr+"]";
	}
}
