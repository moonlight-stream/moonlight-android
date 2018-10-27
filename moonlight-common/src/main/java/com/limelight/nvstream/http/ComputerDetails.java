package com.limelight.nvstream.http;

import java.util.UUID;


public class ComputerDetails {
	public enum State {
		ONLINE, OFFLINE, UNKNOWN
	}

	// Persistent attributes
	public UUID uuid;
	public String name;
	public String localAddress;
	public String remoteAddress;
	public String manualAddress;
	public String macAddress;

	// Transient attributes
	public State state;
	public String activeAddress;
	public PairingManager.PairState pairState;
	public int runningGameId;
	public String rawAppList;
	
	public ComputerDetails() {
		// Use defaults
		state = State.UNKNOWN;
	}
	
	public ComputerDetails(ComputerDetails details) {
		// Copy details from the other computer
		update(details);
	}
	
	public void update(ComputerDetails details) {
		this.state = details.state;
		this.name = details.name;
		this.uuid = details.uuid;
		if (details.activeAddress != null) {
			this.activeAddress = details.activeAddress;
		}
		if (details.localAddress != null) {
			this.localAddress = details.localAddress;
		}
		if (details.remoteAddress != null) {
			this.remoteAddress = details.remoteAddress;
		}
		if (details.manualAddress != null) {
			this.manualAddress = details.manualAddress;
		}
		if (details.macAddress != null && !details.macAddress.equals("00:00:00:00:00:00")) {
			this.macAddress = details.macAddress;
		}
		this.pairState = details.pairState;
		this.runningGameId = details.runningGameId;
		this.rawAppList = details.rawAppList;
	}
	
	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append("State: ").append(state).append("\n");
		str.append("Active Address: ").append(activeAddress).append("\n");
		str.append("Name: ").append(name).append("\n");
		str.append("UUID: ").append(uuid).append("\n");
		str.append("Local Address: ").append(localAddress).append("\n");
		str.append("Remote Address: ").append(remoteAddress).append("\n");
		str.append("Manual Address: ").append(manualAddress).append("\n");
		str.append("MAC Address: ").append(macAddress).append("\n");
		str.append("Pair State: ").append(pairState).append("\n");
		str.append("Running Game ID: ").append(runningGameId).append("\n");
		return str.toString();
	}
}
