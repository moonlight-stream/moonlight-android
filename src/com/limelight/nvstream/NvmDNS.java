package com.limelight.nvstream;

import java.io.IOException;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

import java.net.UnknownHostException;
import java.util.HashSet;

import org.xbill.DNS.DClass;
import org.xbill.DNS.Header;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.Type;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdManager.DiscoveryListener;
import android.net.nsd.NsdManager.ResolveListener;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

/**
 * NvmDNS implements a clone of the NVidia Shield mDNS service for use on Limelight
 * @author yetanothername
 *
 */
public class NvmDNS implements Runnable  {

	public static String NVSTREAM_MDNS_QUERY = "_nvstream._tcp.local.";
	public static String NVSTREAM_MDNS_MULTICAST_GROUP = "224.0.0.251";
	public static InetAddress NVSTREAM_MDNS_MULTICAST_ADDRESS;
	public static final short NVSTREAM_MDNS_PORT = 5353;

	private HashSet<NvComputer> nvstream_mdns_responses;

	private MulticastSocket nvstream_socket;

	static {
		try {
			NVSTREAM_MDNS_MULTICAST_ADDRESS = InetAddress.getByName(NvmDNS.NVSTREAM_MDNS_MULTICAST_GROUP);
		} catch (UnknownHostException e) {
			NVSTREAM_MDNS_MULTICAST_ADDRESS = null;
		}
	}


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
	 * This sets up the query sockets and the list, as well as sends out the query and listens for responses
	 * @throws IOException When shit breaks 
	 */
	public NvmDNS() throws IOException {
		Log.v("NvmDNS", "Constructor entered");
		this.nvstream_mdns_responses = new HashSet<NvComputer>();
		Log.v("NvmDNS", "Constructor exited");
	}

	public void sendQueryAndWait() {
		Thread socketThread = new Thread(this);
		socketThread.start();
	}

	@Override
	public void run() {
		try {
			Log.v("NvmDNS UDP Loop", "mDNS Loop Started");

			this.nvstream_socket = new MulticastSocket(NvmDNS.NVSTREAM_MDNS_PORT);
			this.nvstream_socket.setLoopbackMode(false);
			this.nvstream_socket.joinGroup(NvmDNS.NVSTREAM_MDNS_MULTICAST_ADDRESS);

			Log.v("NvmDNS UDP Loop", "Multicast Socket Created @" + this.nvstream_socket.getLocalPort());

			Header queryHeader = new Header();
			queryHeader.setFlag(org.xbill.DNS.Flags.RA);
			queryHeader.setID(0);

			Record question = Record.newRecord(new Name(NvmDNS.NVSTREAM_MDNS_QUERY), Type.PTR, DClass.IN);

			Message query = new Message();
			query.setHeader(queryHeader);
			query.addRecord(question, Section.QUESTION);

			byte[] wireQuery = query.toWire();

			Log.v("NvmDNS UDP Loop", "Query: " + query.toString());

			DatagramPacket transmitPacket = new DatagramPacket(wireQuery, wireQuery.length);
			transmitPacket.setAddress(NvmDNS.NVSTREAM_MDNS_MULTICAST_ADDRESS);
			transmitPacket.setPort(NvmDNS.NVSTREAM_MDNS_PORT);



			this.nvstream_socket.send(transmitPacket);
			Log.v("NvmDNS UDP Loop", "Query Sent");

			byte[] data = new byte[1500];
			DatagramPacket packet = new DatagramPacket(data, data.length);
			Message message = null;

			while (true) {

				Log.d("NvmDNS UDP Loop", "Blocking on this.nvstream_query_socket.recieve()");
				this.nvstream_socket.receive(packet);
				Log.d("NvmDNS UDP Loop", "Blocking passed on this.nvstream_query_socket.recieve()");
				
				message = new Message(packet.getData());
				Record[] responses = message.getSectionArray(Section.ADDITIONAL);
				if (responses.length != 0 && message.getSectionArray(Section.ANSWER).length != 0 && 
						message.getSectionArray(Section.ANSWER)[0].getName().toString().equals(NvmDNS.NVSTREAM_MDNS_QUERY)) {
					
					Log.v("NvmDNS UDP Reply", "Got a packet from " + packet.getAddress().getCanonicalHostName());
					Log.v("NvmDNS UDP Reply", "Question: " + message.getSectionArray(Section.ANSWER)[0].getName().toString());
					Log.v("NvmDNS UDP Reply", "Response: " + responses[0].getName().toString());

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

					NvComputer computer = new NvComputer(responses[0].getName().toString(), packet.getAddress(), state, numOfApps, gpuType, mac, uniqueID);
					this.nvstream_mdns_responses.add(computer);
					Log.v("NvmDNS NvComputer", computer.toString());

				}
			}				


		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
