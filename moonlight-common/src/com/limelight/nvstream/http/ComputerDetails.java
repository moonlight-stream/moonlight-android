package com.limelight.nvstream.http;

import java.net.InetAddress;
import java.util.UUID;


public class ComputerDetails {
	public enum State {
		ONLINE, OFFLINE, UNKNOWN
	}
	public enum Reachability {
		LOCAL, REMOTE, OFFLINE, UNKNOWN
	}
	
	public State state;
	public Reachability reachability;
	public String name;
	public UUID uuid;
	public InetAddress localIp;
	public InetAddress remoteIp;
	public PairingManager.PairState pairState;
	public String macAddress;
	public int runningGameId;
	
	public ComputerDetails() {
		// Use defaults
		state = State.UNKNOWN;
		reachability = Reachability.UNKNOWN;
	}
	
	public ComputerDetails(ComputerDetails details) {
		// Copy details from the other computer
		update(details);
	}
	
	public void update(ComputerDetails details) {
		this.state = details.state;
		this.reachability = details.reachability;
		this.name = details.name;
		this.uuid = details.uuid;
		this.localIp = details.localIp;
		this.remoteIp = details.remoteIp;
		this.macAddress = details.macAddress;
		this.pairState = details.pairState;
		this.runningGameId = details.runningGameId;
	}
	
	@Override
	public String toString() {
		StringBuilder str = new StringBuilder();
		str.append("State: ").append(state).append("\n");
		str.append("Reachability: ").append(reachability).append("\n");
		str.append("Name: ").append(name).append("\n");
		str.append("UUID: ").append(uuid).append("\n");
		str.append("Local IP: ").append(localIp).append("\n");
		str.append("Remote IP: ").append(remoteIp).append("\n");
		str.append("MAC Address: ").append(macAddress).append("\n");
		str.append("Pair State: ").append(pairState).append("\n");
		str.append("Running Game ID: ").append(runningGameId).append("\n");
		return str.toString();
	}
}
