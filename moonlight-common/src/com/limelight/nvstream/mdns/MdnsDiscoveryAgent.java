package com.limelight.nvstream.mdns;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.jmdns.JmmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

import com.limelight.LimeLog;

public class MdnsDiscoveryAgent {
	public static final String SERVICE_TYPE = "_nvstream._tcp.local.";
	
	private JmmDNS resolver;
	private HashMap<InetAddress, MdnsComputer> computers;
	private MdnsDiscoveryListener listener;
	private HashSet<String> pendingResolution;
	private boolean stop;
	private ServiceListener nvstreamListener = new ServiceListener() {
		public void serviceAdded(ServiceEvent event) {
			LimeLog.info("mDNS: Machine appeared: "+event.getInfo().getName());
			
			ServiceInfo info = event.getDNS().getServiceInfo(SERVICE_TYPE, event.getInfo().getName(), 500);
			if (info == null) {
				// This machine is pending resolution
				pendingResolution.add(event.getInfo().getName());
				return;
			}
			
			LimeLog.info("mDNS: Resolved (blocking)");
			handleResolvedServiceInfo(info);
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
			LimeLog.info("mDNS: Machine resolved (callback): "+event.getInfo().getName());
			handleResolvedServiceInfo(event.getInfo());
		}
	};
	
	private void handleResolvedServiceInfo(ServiceInfo info) {
		MdnsComputer computer;
				
		pendingResolution.remove(info.getName());
		
		try {
			computer = parseServerInfo(info);
			if (computer == null) {
				LimeLog.info("mDNS: Invalid response for machine: "+info.getName());
				return;
			}
		} catch (UnsupportedEncodingException e) {
			// Invalid DNS response
			LimeLog.info("mDNS: Invalid response for machine: "+info.getName());
			return;
		}
		
		synchronized (computers) {
			if (computers.put(computer.getAddress(), computer) == null) {
				// This was a new entry
				listener.notifyComputerAdded(computer);
			}
		}
	}
	
	private static MdnsComputer parseServerInfo(ServiceInfo info) throws UnsupportedEncodingException {	
		Inet4Address addrs[] = info.getInet4Addresses();
		if (addrs.length == 0) {
			LimeLog.info("mDNS: "+info.getName()+" is missing addresses");
			return null;
		}
		
		Inet4Address address = addrs[0];
		String name = info.getName();
		
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
					// Stop if requested
					if (stop) {
						// There will be no further timer invocations now
						t.cancel();
						
						if (resolver != null) {
							resolver.removeServiceListener(SERVICE_TYPE, nvstreamListener);
							try {
								JmmDNS.Factory.close();
							} catch (IOException e) {}
							resolver = null;
						}
						return;
					}
					
					// Perform initialization
					if (resolver == null) {
						resolver = JmmDNS.Factory.getInstance();
						
						// This will cause the listener to be invoked for known hosts immediately.
						// We do this in the timer callback to be off the main thread when this happens.
						resolver.addServiceListener(SERVICE_TYPE, nvstreamListener);
					}
					
					// Send another mDNS query
					resolver.requestServiceInfo(SERVICE_TYPE, null, discoveryIntervalMs);

					// Run service resolution again for pending machines
					ArrayList<String> pendingNames = new ArrayList<String>(pendingResolution);
					for (String name : pendingNames) {
						LimeLog.info("mDNS: Retrying service resolution for machine: "+name);
						ServiceInfo[] infos = resolver.getServiceInfos(SERVICE_TYPE, name, 500);
						if (infos != null && infos.length != 0) {
							LimeLog.info("mDNS: Resolved (retry) with "+infos.length+" service entries");
							handleResolvedServiceInfo(infos[0]);
						}
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
