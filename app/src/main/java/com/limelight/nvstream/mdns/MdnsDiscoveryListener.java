package com.limelight.nvstream.mdns;

public interface MdnsDiscoveryListener {
	public void notifyComputerAdded(MdnsComputer computer);
	public void notifyComputerRemoved(MdnsComputer computer);
	public void notifyDiscoveryFailure(Exception e);
}
