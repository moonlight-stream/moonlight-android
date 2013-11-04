package com.limelight.nvstream;

import java.net.InetAddress;
import java.util.Locale;
import java.util.UUID;

public class NvComputer {
	private String mDNSResponse;
	private InetAddress ipAddress;
	private String ipAddressString;
	private int state;
	private int numOfApps;
	private String gpuType;
	private String mac;
	private UUID uniqueID;
	
	private int sessionID;
	private boolean paired;
	private boolean isBusy;
	
	
	public NvComputer(String mDNSResponse, InetAddress ipAddress, String ipAddressString, int state, int numOfApps, String gpuType, String mac, UUID uniqueID) {
		this.mDNSResponse = mDNSResponse;
		this.ipAddress = ipAddress;
		this.ipAddressString = ipAddressString;
		this.state = state;
		this.numOfApps = numOfApps;
		this.gpuType = gpuType;
		this.mac = mac;
		this.uniqueID = uniqueID;
	}
	
	public String getmDNSResponse() {
		return this.mDNSResponse;
	}
	
	public InetAddress getIpAddress() {
		return this.ipAddress;
	}
	
	public String getIpAddressString() {
		return this.ipAddressString;
	}

	public String getIPAddressString() {
		return this.ipAddress.getCanonicalHostName().toLowerCase(Locale.getDefault());
	}
	
	public int getState() {
		return this.state;
	}

	public int getNumOfApps() {
		return this.numOfApps;
	}

	public String getGpuType() {
		return this.gpuType;
	}

	public String getMac() {
		return this.mac;
	}

	public UUID getUniqueID() {
		return this.uniqueID;
	}
		
	public void updateAfterPairQuery(int sessionID, boolean paired, boolean isBusy) {
		
		this.sessionID = sessionID;
		this.paired = paired;
		this.isBusy = isBusy;
	}
	
	public int getSessionID() {
		return this.sessionID;
	}
	
	public boolean getPaired() {
		return this.paired;
	}
	
	public boolean getIsBusy() {
		return this.isBusy;
	}
	
	public int hashCode() {
		if (this.ipAddress == null) {
			return -1;
		} else {
			return this.ipAddressString.hashCode();
		}
	}
	
	public String toString() {
		StringBuilder returnStringBuilder = new StringBuilder();
		returnStringBuilder.append("NvComputer 0x");
		returnStringBuilder.append(Integer.toHexString(this.hashCode()).toUpperCase(Locale.getDefault()));
		returnStringBuilder.append("\n|- mDNS Hostname: ");
		returnStringBuilder.append(this.mDNSResponse);
		returnStringBuilder.append("\n|- IP Address: ");
		returnStringBuilder.append(this.ipAddress.toString());
		returnStringBuilder.append("\n|- Computer State: ");
		returnStringBuilder.append(this.state);
		returnStringBuilder.append("\n|- Number of Apps: ");
		returnStringBuilder.append(this.numOfApps);
		returnStringBuilder.append("\n|- GPU: ");
		returnStringBuilder.append(this.gpuType);
		returnStringBuilder.append("\n|- MAC: ");
		returnStringBuilder.append(this.mac);
		returnStringBuilder.append("\n\\- UniqueID: ");
		returnStringBuilder.append(this.uniqueID);
		returnStringBuilder.append("\n");
		return returnStringBuilder.toString();
	}
	
	public boolean equals(Object obj) {
		if (obj instanceof UUID) {
			return this.uniqueID.equals(obj);
		} else {
			return false;
		}
	}
}