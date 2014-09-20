package com.limelight.nvstream.mdns;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.jmdns.JmDNS;
import com.jmdns.ServiceEvent;
import com.jmdns.ServiceListener;

import com.limelight.LimeLog;

public class MdnsDiscoveryAgent {
	public static final String SERVICE_TYPE = "_nvstream._tcp.local.";
	
	private JmDNS resolver;
	private HashMap<InetAddress, MdnsComputer> computers;
	private MdnsDiscoveryListener listener;
	private HashSet<String> pendingResolution;
	private boolean stop;
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
				if (computer == null) {
					LimeLog.info("mDNS: Invalid response for machine: "+event.getInfo().getName());
					return;
				}
			} catch (UnsupportedEncodingException e) {
				// Invalid DNS response
				LimeLog.info("mDNS: Invalid response for machine: "+event.getInfo().getName());
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
	
	private static MdnsComputer parseMdnsResponse(ServiceEvent event) throws UnsupportedEncodingException {	
		Inet4Address addrs[] = event.getInfo().getInet4Addresses();
		if (addrs.length == 0) {
			LimeLog.info("Missing addresses");
			return null;
		}
		
		Inet4Address address = addrs[0];
		String name = event.getInfo().getName();
		
		return new MdnsComputer(name, address);
	}
	
	public MdnsDiscoveryAgent(MdnsDiscoveryListener listener) {
		computers = new HashMap<InetAddress, MdnsComputer>();
		pendingResolution = new HashSet<String>();
		this.listener = listener;
	}
	
	public void startDiscovery(final int discoveryIntervalMs) {
		stop = false;
		final Timer t = new Timer();
		t.schedule(new TimerTask() {
			@Override
			public void run() {
				synchronized (MdnsDiscoveryAgent.this) {
					// Close the old resolver
					if (resolver != null) {
						try {
							resolver.close();
						} catch (IOException e) {}
						resolver = null;
					}
					
					// Stop if requested
					if (stop) {
						// There will be no further timer invocations now
						t.cancel();
						return;
					}
					
					// Create a new resolver
					try {
						resolver = JmDNS.create(new InetSocketAddress(0).getAddress());
					} catch (IOException e) {
						// This is fine; the network is probably not up
						return;
					}
					
					// Send another mDNS query
					resolver.addServiceListener(SERVICE_TYPE, nvstreamListener);
					resolver.requestServiceInfo(SERVICE_TYPE, null, discoveryIntervalMs);

					// Run service resolution again for pending machines
					ArrayList<String> pendingNames = new ArrayList<String>(pendingResolution);
					for (String name : pendingNames) {
						LimeLog.info("mDNS: Retrying service resolution for machine: "+name);
						resolver.getServiceInfo(SERVICE_TYPE, name);
					}
				}
			}
		}, 0, discoveryIntervalMs);
	}
	
	public void stopDiscovery() {
		// Trigger a stop on the next timer expiration
		stop = true;
	}
	
	public List<MdnsComputer> getComputerSet() {
		synchronized (computers) {
			return new ArrayList<MdnsComputer>(computers.values());
		}
	}
}
