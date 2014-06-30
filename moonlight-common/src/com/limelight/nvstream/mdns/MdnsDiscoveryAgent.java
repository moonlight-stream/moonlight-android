package com.limelight.nvstream.mdns;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import com.jmdns.JmDNS;
import com.jmdns.ServiceEvent;
import com.jmdns.ServiceListener;

import com.limelight.LimeLog;

public class MdnsDiscoveryAgent {
	public static final String SERVICE_TYPE = "_nvstream._tcp.local.";
	
	private JmDNS resolver;
	private HashMap<InetAddress, MdnsComputer> computers;
	private Timer discoveryTimer;
	private MdnsDiscoveryListener listener;
	private ArrayList<String> pendingResolution;
	private ServiceListener nvstreamListener = new ServiceListener() {
		public void serviceAdded(ServiceEvent event) {
			LimeLog.info("mDNS: Machine appeared: "+event.getInfo().getName());
			
			// This machine is pending resolution until we get the serviceResolved callback
			pendingResolution.add(event.getInfo().getName());
			
			// We call this to kick the resolver
			resolver.getServiceInfo(SERVICE_TYPE, event.getInfo().getName());
		}

		public void serviceRemoved(ServiceEvent event) {
			LimeLog.info("mDNS: Machine disappeared: "+event.getInfo().getName());
			
			Inet4Address addrs[] = event.getInfo().getInet4Addresses();
			for (Inet4Address addr : addrs) {
				synchronized (computers) {
					MdnsComputer computer = computers.remove(addr);
					if (computer != null) {
						listener.notifyComputerRemoved(computer);
						break;
					}
				}
			}
		}

		public void serviceResolved(ServiceEvent event) {
			MdnsComputer computer;
			
			LimeLog.info("mDNS: Machine resolved: "+event.getInfo().getName());
			
			pendingResolution.remove(event.getInfo().getName());
			
			try {
				computer = parseMdnsResponse(event);
			} catch (UnsupportedEncodingException e) {
				// Invalid DNS response
				return;
			}
			
			synchronized (computers) {
				if (computers.put(computer.getAddress(), computer) == null) {
					// This was a new entry
					listener.notifyComputerAdded(computer);
				}
			}
		}
	};
	
	private static ArrayList<String> parseTxtBytes(byte[] txtBytes) throws UnsupportedEncodingException {
		int i = 0;
		ArrayList<String> strings = new ArrayList<String>();
		
		while (i < txtBytes.length) {
			int length = txtBytes[i++];
			
			byte[] stringData = Arrays.copyOfRange(txtBytes, i, i+length);
			strings.add(new String(stringData, "UTF-8"));
			
			i += length;
		}
		
		return strings;
	}
	
	private static MdnsComputer parseMdnsResponse(ServiceEvent event) throws UnsupportedEncodingException {	
		Inet4Address addrs[] = event.getInfo().getInet4Addresses();
		if (addrs.length == 0) {
			return null;
		}
		
		Inet4Address address = addrs[0];
		String name = event.getInfo().getName();
		ArrayList<String> txtStrs = parseTxtBytes(event.getInfo().getTextBytes());
		String uniqueId = null;
		for (String txtStr : txtStrs) {
			if (txtStr.startsWith("SERVICE_UNIQUEID=")) {
				uniqueId = txtStr.substring(txtStr.indexOf("=")+1);
				break;
			}
		}
		
		if (uniqueId == null) {
			return null;
		}
		
		UUID uuid;
		try {
			// fromString() throws an exception for a
			// malformed string
			uuid = UUID.fromString(uniqueId);
		} catch (IllegalArgumentException e) {
			// UUID must be properly formed
			return null;
		}
		
		return new MdnsComputer(name, uuid, address);
	}
	
	private MdnsDiscoveryAgent(MdnsDiscoveryListener listener) {
		computers = new HashMap<InetAddress, MdnsComputer>();
		discoveryTimer = new Timer();
		pendingResolution = new ArrayList<String>();
		this.listener = listener;
	}
	
	public static MdnsDiscoveryAgent createDiscoveryAgent(MdnsDiscoveryListener listener) throws IOException {
		MdnsDiscoveryAgent agent = new MdnsDiscoveryAgent(listener);
		
		agent.resolver = JmDNS.create();
		
		return agent;
	}
	
	public void startDiscovery(final int discoveryIntervalMs) {
		resolver.addServiceListener(SERVICE_TYPE, nvstreamListener);
		
		discoveryTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				// Send another mDNS query
				resolver.requestServiceInfo(SERVICE_TYPE, null, discoveryIntervalMs);
				
				// Run service resolution again for pending machines
				ArrayList<String> pendingNames = new ArrayList<String>(pendingResolution);
				for (String name : pendingNames) {
					LimeLog.info("mDNS: Retrying service resolution for machine: "+name);
					resolver.getServiceInfo(SERVICE_TYPE, name);
				}
			}
		}, 0, discoveryIntervalMs);
	}
	
	public void stopDiscovery() {
		resolver.removeServiceListener(SERVICE_TYPE, nvstreamListener);
		
		discoveryTimer.cancel();
	}
	
	public List<MdnsComputer> getComputerSet() {
		synchronized (computers) {
			return new ArrayList<MdnsComputer>(computers.values());
		}
	}
}
