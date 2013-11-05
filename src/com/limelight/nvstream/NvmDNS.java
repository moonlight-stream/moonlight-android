package com.limelight.nvstream;

import java.io.IOException;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.xbill.DNS.DClass;
import org.xbill.DNS.Header;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.Section;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

import android.os.AsyncTask;
import android.util.Log;

public class NvmDNS extends AsyncTask<Void, Integer, Void> {

	public static String MDNS_QUERY = "_nvstream._tcp.local.";
	public static String MDNS_MULTICAST_GROUP = "224.0.0.251";
	public static InetAddress MDNS_MULTICAST_ADDRESS;
	public static final short MDNS_PORT = 5353;

	public static final int WAIT_MS = 100;

	private HashSet<NvComputer> responses;
	private MulticastSocket socket;

	static {
		try {
			MDNS_MULTICAST_ADDRESS = InetAddress.getByName(NvmDNS.MDNS_MULTICAST_GROUP);
		} catch (UnknownHostException e) {
			MDNS_MULTICAST_ADDRESS = null;
		}
	}

	public NvmDNS() {
		this.responses = new HashSet<NvComputer>();

		// Create our Socket Connection
		try {
			this.socket = new MulticastSocket(NvmDNS.MDNS_PORT);
			this.socket.setLoopbackMode(false);
			this.socket.joinGroup(NvmDNS.MDNS_MULTICAST_ADDRESS);
			Log.v("NvmDNS Socket Constructor", "Created mDNS listening socket");
		} catch (IOException e) {
			Log.e("NvmDNS Socket Constructor", "There was an error creating the DNS socket.");
			Log.e("NvmDNS Socket Constructor", e.getMessage());
		}
	}

	public Set<NvComputer> getComputers() {
		return Collections.unmodifiableSet(this.responses);
	}

	private void sendQuery() {
		Header queryHeader = new Header();

		// If we set the RA (Recursion Available) flag and our message ID to 0
		// then the packet matches the real mDNS query packet as displayed in Wireshark 
		queryHeader.setFlag(org.xbill.DNS.Flags.RA);
		queryHeader.setID(0);

		Record question = null;
		try {
			// We need to create our "Question" DNS query that is a pointer record to
			// the mDNS Query "Name"
			question = Record.newRecord(new Name(NvmDNS.MDNS_QUERY), Type.PTR, DClass.IN);
		} catch (TextParseException e) {
			Log.e("NvmDNS Query", e.getMessage());
			return;
		}

		// We combine our header and our question into a single message
		Message query = new Message();
		query.setHeader(queryHeader);
		query.addRecord(question, Section.QUESTION);

		// Convert the message into Network Byte Order
		byte[] wireQuery = query.toWire();
		Log.i("NvmDNS Query", query.toString());

		// Convert our byte array into a Packet
		DatagramPacket transmitPacket = new DatagramPacket(wireQuery, wireQuery.length);
		transmitPacket.setAddress(NvmDNS.MDNS_MULTICAST_ADDRESS);
		transmitPacket.setPort(NvmDNS.MDNS_PORT);

		// And (attempt) to send the packet
		try {
			Log.d("NvmDNS Query", "Blocking on this.nvstream_socket.send(transmitPacket)");
			this.socket.send(transmitPacket);
			Log.d("NvmDNS Query", "Passed this.nvstream_socket.send(transmitPacket)");
		} catch (IOException e) {
			Log.e("NvmDNS Query", "There was an error sending the DNS query.");
			Log.e("NvmDNS Query", e.getMessage());
		}
	}

	public void waitForResponses() {
		Log.v("NvmDNS Response", "mDNS Loop Started");

		// We support up to 1500 byte packets
		byte[] data = new byte[1500];
		DatagramPacket packet = new DatagramPacket(data, data.length);

		Message message = null;

		while (!this.socket.isClosed()) {
			// Attempt to receive a packet/response
			try {
				Log.d("NvmDNS Response", "Blocking on this.nvstream_query_socket.recieve()");
				this.socket.receive(packet);
				Log.d("NvmDNS Response", "Blocking passed on this.nvstream_query_socket.recieve()");
				message = new Message(packet.getData());
				this.parseRecord(message, packet.getAddress());
			} catch (IOException e) {
				if (this.socket.isClosed()) {
					Log.e("NvmDNS Response", "The socket was closed on us. The timer must have been reached.");
					return;
				} else {
					Log.e("NvmDNS Response", "There was an error receiving the response.");
					Log.e("NvmDNS Response", e.getMessage());
					continue;
				}
			}
		}		
	}

	private void parseRecord(Message message, InetAddress address) {
		// We really only care about the ADDITIONAL section (specifically the text records)
		Record[] responses = message.getSectionArray(Section.ADDITIONAL);
		// We only want to process records that actually have a length, have an ANSWER
		// section that has stuff in it and that the ANSWER to our query is what we sent
		if (responses.length != 0 && 
				message.getSectionArray(Section.ANSWER).length != 0 && 
				message.getSectionArray(Section.ANSWER)[0].getName().toString().equals(NvmDNS.MDNS_QUERY)) {

			Log.v("NvmDNS Response", "Got a packet from " + address.getCanonicalHostName());
			Log.v("NvmDNS Response", "Question: " + message.getSectionArray(Section.ANSWER)[0].getName().toString());
			Log.v("NvmDNS Response", "Response: " + responses[0].getName().toString());
			
					
			// TODO: The DNS entry we get is "XENITH._nvstream._tcp.local."
			// And the .'s in there are not actually periods. Or something.
			String hostname = responses[0].getName().toString();
	
			// The records can be returned in any order, so we need to figure out which one is the TXTRecord
			// We get three records back: A TXTRecord, a SRVRecord and an ARecord
			TXTRecord txtRecord = null;

			for (Record record : responses) {
				Log.v("NvmDNS Response", "We recieved a DNS repsonse with a " + record.getClass().getName() + " record.");
				if (record instanceof TXTRecord) {
					txtRecord = (TXTRecord)record;
				}
			}

			if (txtRecord == null) {
				Log.e("NvmDNS Response", "We recieved a malformed DNS repsonse with no TXTRecord");
				return;
			}
			
			this.parseTXTRecord(txtRecord, address, hostname);
		}
	}

	private void parseTXTRecord(TXTRecord txtRecord, InetAddress address, String hostname) {
		// The DNS library we are using does not use inferred generics :(
		@SuppressWarnings("unchecked")
		ArrayList<String> txtRecordStringList =  new ArrayList<String>(txtRecord.getStrings());

		if (txtRecordStringList.size() != 5) {
			Log.e("NvmDNS Response", "We recieved a malformed DNS repsonse with the improper amount of TXTRecord Entries.");
			return;
		}

		// The general format of the text records is:
		// 	SERVICE_STATE=1
		//	SERVICE_NUMOFAPPS=5
		//	SERVICE_GPUTYPE=GeForce GTX 760 x2
		// 	SERVICE_MAC=DE:AD:BE:EF:CA:FE
		//	SERVICE_UNIQUEID={A Wild UUID Appeared!}
		// Every single record I've seen so far has been in this format
		try {
			int serviceState = Integer.parseInt(this.parseTXTRecordField(txtRecordStringList.get(0), "SERVICE_STATE"));
			int numberOfApps = Integer.parseInt(this.parseTXTRecordField(txtRecordStringList.get(1), "SERVICE_NUMOFAPPS"));
			String gpuType   = this.parseTXTRecordField(txtRecordStringList.get(2), "SERVICE_GPUTYPE");
			String mac		 = this.parseTXTRecordField(txtRecordStringList.get(3), "SERVICE_MAC");
			UUID uniqueID	 = UUID.fromString(this.parseTXTRecordField(txtRecordStringList.get(4), "SERVICE_UNIQUEID"));
			
			// We need to resolve the hostname in this thread so that we can use it in the GUI
			address.getCanonicalHostName();

			NvComputer computer = new NvComputer(hostname, address, serviceState, numberOfApps, gpuType, mac, uniqueID);
			this.responses.add(computer);
		} catch (ArrayIndexOutOfBoundsException e) {
			Log.e("NvmDNS Response", "We recieved a malformed DNS repsonse.");
		}
	}
	
	private String parseTXTRecordField(String field, String key) {
		// Make sure that our key=value pair actually has our key in it
		if (!field.contains(key)) {
			return "";
		}
		
		// Make sure that our key=value pair only has one "=" in it.
		if (field.indexOf('=') != field.lastIndexOf('=')) {
			return "";
		}
		
		String[] split = field.split("=");
		
		if (split.length != 2) {
			return "";
		}
		
		return split[1];
	}
	
	// What follows is an implementation of Android's AsyncTask.
	// The first step is to send our query, then we start our
	// RX thread to parse responses. However we only want to accept
	// responses for a limited amount of time so we start a new thread
	// to kill the socket after a set amount of time
	// Then we return control to the foreground thread
	
	@Override
	protected Void doInBackground(Void... thisParameterIsUseless) {
		Log.v("NvmDNS ASync", "doInBackground entered");

		this.sendQuery();


		// We want to run our wait thread for an amount of time then close the socket.
		new Thread(new Runnable() {
			@Override
			public void run() {
				Log.v("NvmDNS Wait", "Going to sleep for " + NvmDNS.WAIT_MS + "ms");
				try {
					Thread.sleep(NvmDNS.WAIT_MS);
				} catch (InterruptedException e) {
					Log.e("NvmDNS Wait", "Woke up from sleep before time.");
					Log.e("NvmDNS Wait", e.getMessage());
				}
				Log.v("NvmDNS Wait", "Woke up from sleep");
				NvmDNS.this.socket.close();
				Log.v("NvmDNS Wait", "Socket Closed");
			}
		}).start();

		this.waitForResponses();

		Log.v("NvmDNS ASync", "doInBackground exit");
		return null; 
	}

	@Override
	protected void onProgressUpdate(Integer... progress) {
		Log.v("NvmDNS ASync", "onProgressUpdate");
	}

	@Override
	protected void onPostExecute(Void moreUselessParameters) {
		Log.v("NvmDNS ASync", "onPostExecute");
		for (NvComputer computer : this.responses) {
			Log.i("NvmDNS NvComputer", computer.toString());
		}
	}
}
