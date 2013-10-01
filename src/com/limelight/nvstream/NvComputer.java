package com.limelight.nvstream;

import java.net.InetAddress;
import java.util.HashSet;

public class NvComputer {
	private String mDNSResponse;
	private InetAddress ipAddress;
	private int state;
	private int numOfApps;
	private String gpuType;
	private String mac;
	private String uniqueID;
	
	private HashSet<NvComputerGame> games;
	private int sessionID;
	private boolean paired;
	private boolean isBusy;
	
	
	public NvComputer(String mDNSResponse, InetAddress ipAddress, int state, int numOfApps, String gpuType, String mac, String uniqueID) {
		this.mDNSResponse = mDNSResponse;
		this.ipAddress = ipAddress;
		this.state = state;
		this.numOfApps = numOfApps;
		this.gpuType = gpuType;
		this.mac = mac;
		this.uniqueID = uniqueID;
		
		this.games = new HashSet<NvComputerGame>();
	}
	
	public String getmDNSResponse() {
		return this.mDNSResponse;
	}
	
	public InetAddress getIpAddress() {
		return this.ipAddress;
	}

	public String getIPAddressString() {
		return this.ipAddress.getCanonicalHostName().toLowerCase();
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

	public String getUniqueID() {
		return this.uniqueID;
	}
	
	public boolean addGame(NvComputerGame game) {
		return this.games.add(game);
	}
		
	public HashSet<NvComputerGame> getGames() {
		return this.games;
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
			return this.ipAddress.getCanonicalHostName().hashCode();
		}
	}
	
	public String toString() {
		StringBuilder returnStringBuilder = new StringBuilder();
		returnStringBuilder.append("NvComputer 0x");
		returnStringBuilder.append(Integer.toHexString(this.hashCode()).toUpperCase());
		returnStringBuilder.append("\n\tmDNS Hostname: ");
		returnStringBuilder.append(this.mDNSResponse);
		returnStringBuilder.append("\n\tIP Address: ");
		returnStringBuilder.append(this.ipAddress.toString());
		returnStringBuilder.append("\n\tComputer State: ");
		returnStringBuilder.append(this.state);
		returnStringBuilder.append("\n\tNumber of Apps: ");
		returnStringBuilder.append(this.numOfApps);
		returnStringBuilder.append("\n\tGPU: ");
		returnStringBuilder.append(this.gpuType);
		returnStringBuilder.append("\n\tMAC: ");
		returnStringBuilder.append(this.mac);
		returnStringBuilder.append("\n\tUniqueID: ");
		returnStringBuilder.append(this.uniqueID);
		returnStringBuilder.append("\n");
		return returnStringBuilder.toString();
	}
	
	public boolean equals(Object obj) {
		NvComputer otherComputer = (NvComputer)obj;
		if (this.ipAddress == null && otherComputer.getIpAddress() == null) {
			return true;
		} else if (this.ipAddress == null || otherComputer.getIpAddress() == null) {
			return false;
		} else {
			return this.ipAddress.equals(otherComputer.getIpAddress());
		}
	}
	
	public class NvComputerGame {
		private Integer ID;
		private String appTitle;
		private Boolean isRunning;
		private Integer gameSession;
		private Integer winLogon;
		
		public NvComputerGame(int ID, String appTitle, boolean isRunning) {
			this.ID = ID;
			this.appTitle = appTitle;
			this.isRunning = isRunning;
		}
		
		public void launchedGame(int gameSession, int winLogon) {
			this.isRunning = true;
			this.gameSession = gameSession;
			this.winLogon = winLogon;
		}
		
		public Integer getID() {
			return this.ID;
		}
		
		public String getAppTitle() {
			return this.appTitle;
		}
		
		public Boolean getIsRunning() {
			return this.isRunning;
		}
		
		public Integer getGameSession() {
			return this.gameSession;
		}
		
		public Integer winLogon() {
			return this.winLogon;
		}
	}
}