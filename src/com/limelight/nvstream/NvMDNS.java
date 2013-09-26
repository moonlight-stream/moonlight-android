package com.limelight.nvstream;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.xbill.DNS.DClass;
import org.xbill.DNS.Header;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.Type;

/**
 * NvmDNS implements a clone of the NVidia Shield mDNS service for use on Limelight
 * @author yetanothername
 *
 */
public class NvmDNS implements Runnable  {
	/**
	 * The mDNS query that the Shield issues to look for computers
	 */
	public static String NVSTREAM_MDNS_QUERY = "_nvstream._tcp.local.";
	/**
	 * The multicast group NVSTREAM_MDNS_QUERY gets sent to
	 */
	public static String NVSTREAM_MDNS_MULTICAST_GROUP = "224.0.0.251";
	/**
	 * The IP Address that NVSTREAM_MDNS_MULTICAST_GROUP points to, in Java's IP object format
	 */
	public static InetAddress NVSTREAM_MDNS_MULTICAST_ADDRESS;
	/**
	 * The port that the NVSTREAM_MDNS_QUERY gets sent to at NVSTREAM_MULTICAST_GROUP
	 */
	public static final short NVSTREAM_MDNS_PORT = 5353;
	
	/**
	 * A collection of responses that we have received from our query
	 */
	private LinkedList<NvmDNSResponse> nvstream_mdns_responses;
	/**
	 * The send and receive socket for our queries
	 */
	private MulticastSocket nvstream_query_socket;
	
	/**
	 * We need to convert the IP Address into an IP Object
	 */
	static {
		try {
			NVSTREAM_MDNS_MULTICAST_ADDRESS = InetAddress.getByName(NvmDNS.NVSTREAM_MDNS_MULTICAST_GROUP);
		} catch (UnknownHostException e) {
			NVSTREAM_MDNS_MULTICAST_ADDRESS = null;
		}
	}
	
	/**
	 * This sets up the query sockets and the list, as well as sends out the query and listens for resposnes
	 * @throws IOException When shit breaks
	 */
	public NvmDNS() throws IOException {
		this.nvstream_mdns_responses = new LinkedList<NvmDNSResponse>();
		
		this.nvstream_query_socket = new MulticastSocket(NvmDNS.NVSTREAM_MDNS_PORT);
		this.nvstream_query_socket.setLoopbackMode(false);
		this.nvstream_query_socket.joinGroup(NvmDNS.NVSTREAM_MDNS_MULTICAST_ADDRESS);
		
		this.sendNVQuery();
		
		// Implement the socket reads in a different thread
		//this.recieveNVResponse();
		Thread t = new Thread(this);
		t.start();
		
	}
	
	/**
	 * Sends out the NVSTREAM_MDNS_QUERY to the NVSTREAM_MDNS_MULTICAST_ADDRESS
	 * @return True if our query was sent, false otherwise
	 */
	private boolean sendNVQuery() {
		try {
			Header queryHeader = new Header();
			queryHeader.setFlag(org.xbill.DNS.Flags.RA);
			queryHeader.setID(0);
			
			Record question = Record.newRecord(new Name(NvmDNS.NVSTREAM_MDNS_QUERY), Type.PTR, DClass.IN);
						
			Message query = new Message();
			query.setHeader(queryHeader);
			query.addRecord(question, Section.QUESTION);
			
			byte[] wireQuery = query.toWire();
			
			DatagramPacket packet = new DatagramPacket(wireQuery, wireQuery.length);
			packet.setAddress(NvmDNS.NVSTREAM_MDNS_MULTICAST_ADDRESS);
			packet.setPort(NvmDNS.NVSTREAM_MDNS_PORT);
			
			this.nvstream_query_socket.send(packet);
			
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	@Override
	public void run() {
		try {
			this.recieveNVResponse();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	
	private void recieveNVResponse() throws IOException {
		byte[] data = new byte[1500];
		DatagramPacket packet = new DatagramPacket(data, data.length);
		Message message = null;
			
		while (true) {
		
			this.nvstream_query_socket.receive(packet);
			message = new Message(packet.getData());
			Record[] responses = message.getSectionArray(Section.ADDITIONAL);
			
			if (responses.length != 0 && message.getSectionArray(Section.ANSWER)[0].getName().toString().equals(NvmDNS.NVSTREAM_MDNS_QUERY)) {
				System.out.println("\nGot a packet from " + packet.getAddress().getCanonicalHostName());
				System.out.println("\tQuestion: " + message.getSectionArray(Section.ANSWER)[0].getName());
				System.out.println("\tResponse: " + responses[0].getName());
							

				String[] txtRecords = responses[0].rdataToString().split("\" \"");
				
				// No, but really, there has to be a better way of doing this...
				txtRecords[0] = txtRecords[0].substring(1);
				txtRecords[txtRecords.length - 1] = txtRecords[txtRecords.length - 1].split("\"")[0];
			
				int state = -1;
				int numOfApps = -1;
				String gpuType = "Intel(R) Extreme Graphics 2";
				String mac = "DE:AD:BE:EF:CA:FE";
				String uniqueID = "4";
				
				for (int i = 0; i < txtRecords.length; i++) {
					System.out.println("\t\t" + txtRecords[i]);
					if (i == 0) {
						state = Integer.parseInt(txtRecords[i].split("=")[1]);
					} else if (i == 1) {
						numOfApps = Integer.parseInt(txtRecords[i].split("=")[1]);
					} else if (i == 2) {
						gpuType = txtRecords[i].split("=")[1];
					} else if (i == 3) {
						mac = txtRecords[i].split("=")[1];
					} else if (i == 4) {
						uniqueID = txtRecords[i].split("=")[1];
					}
				}
				
				this.nvstream_mdns_responses.add(new NvmDNSResponse(packet.getAddress(), state, numOfApps, gpuType, mac, uniqueID));
			}
		}
	}
	
	public List<NvmDNSResponse> getmDNSResponses() {
		return Collections.unmodifiableList(this.nvstream_mdns_responses);
	}
	
	public class NvmDNSResponse {
		private InetAddress ipAddress;
		private int state;
		private int numOfApps;
		private String gpuType;
		private String mac;
		private String uniqueID;
		
		public NvmDNSResponse(InetAddress ipAddress, int state, int numOfApps, String gpuType, String mac, String uniqueID) {
			this.ipAddress = ipAddress;
			this.state = state;
			this.numOfApps = numOfApps;
			this.gpuType = gpuType;
			this.mac = mac;
			this.uniqueID = uniqueID;
		}
		
		public InetAddress getIpAddress() {
			return ipAddress;
		}

		public int getState() {
			return state;
		}

		public int getNumOfApps() {
			return numOfApps;
		}

		public String getGpuType() {
			return gpuType;
		}

		public String getMac() {
			return mac;
		}

		public String getUniqueID() {
			return uniqueID;
		}
		
		public String toString() {
			StringBuilder returnStringBuilder = new StringBuilder();
			returnStringBuilder.append("`IP Address: ");
			returnStringBuilder.append(this.ipAddress.toString());
			returnStringBuilder.append(" State: ");
			returnStringBuilder.append(this.state);
			returnStringBuilder.append(" Apps: ");
			returnStringBuilder.append(this.numOfApps);
			returnStringBuilder.append(" GPU: \'");
			returnStringBuilder.append(this.gpuType);
			returnStringBuilder.append("\' MAC: \'");
			returnStringBuilder.append(this.mac);
			returnStringBuilder.append("\' UniqueID: \'");
			returnStringBuilder.append(this.uniqueID);
			returnStringBuilder.append("\'`\n");
			return returnStringBuilder.toString();
		}
	}
}
