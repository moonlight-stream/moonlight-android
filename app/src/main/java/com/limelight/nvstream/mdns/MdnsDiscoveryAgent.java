package com.limelight.nvstream.mdns;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import javax.jmdns.JmmDNS;
import javax.jmdns.NetworkTopologyDiscovery;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import javax.jmdns.impl.NetworkTopologyDiscoveryImpl;

import com.limelight.LimeLog;

public class MdnsDiscoveryAgent implements ServiceListener {
	public static final String SERVICE_TYPE = "_nvstream._tcp.local.";
	
	private MdnsDiscoveryListener listener;
	private Thread discoveryThread;
	private HashMap<InetAddress, MdnsComputer> computers = new HashMap<InetAddress, MdnsComputer>();
	private HashSet<String> pendingResolution = new HashSet<String>();
	
	// The resolver factory's instance member has a static lifetime which
	// means our ref count and listener must be static also.
	private static int resolverRefCount = 0;
	private static HashSet<ServiceListener> listeners = new HashSet<ServiceListener>();
	private static ServiceListener nvstreamListener = new ServiceListener() {
		@Override
		public void serviceAdded(ServiceEvent event) {
			HashSet<ServiceListener> localListeners;
			
			// Copy the listener set into a new set so we can invoke
			// the callbacks without holding the listeners monitor the
			// whole time.
			synchronized (listeners) {
				localListeners = new HashSet<ServiceListener>(listeners);
			}
			
			for (ServiceListener listener : localListeners) {
				listener.serviceAdded(event);
			}
		}

		@Override
		public void serviceRemoved(ServiceEvent event) {
			HashSet<ServiceListener> localListeners;
			
			// Copy the listener set into a new set so we can invoke
			// the callbacks without holding the listeners monitor the
			// whole time.
			synchronized (listeners) {
				localListeners = new HashSet<ServiceListener>(listeners);
			}
			
			for (ServiceListener listener : localListeners) {
				listener.serviceRemoved(event);
			}
		}

		@Override
		public void serviceResolved(ServiceEvent event) {
			HashSet<ServiceListener> localListeners;
			
			// Copy the listener set into a new set so we can invoke
			// the callbacks without holding the listeners monitor the
			// whole time.
			synchronized (listeners) {
				localListeners = new HashSet<ServiceListener>(listeners);
			}
			
			for (ServiceListener listener : localListeners) {
				listener.serviceResolved(event);
			}
		}
	};

	public static class MyNetworkTopologyDiscovery extends NetworkTopologyDiscoveryImpl {
		@Override
		public boolean useInetAddress(NetworkInterface networkInterface, InetAddress interfaceAddress) {
			// This is an copy of jmDNS's implementation, except we omit the multicast check, since
			// it seems at least some devices lie about interfaces not supporting multicast when they really do.
			try {
				if (!networkInterface.isUp()) {
					return false;
				}

				/*
				if (!networkInterface.supportsMulticast()) {
					return false;
				}
				*/

				if (networkInterface.isLoopback()) {
					return false;
				}

				return true;
			} catch (Exception exception) {
				return false;
			}
		}
	};

	static {
		// Override jmDNS's default topology discovery class with ours
		NetworkTopologyDiscovery.Factory.setClassDelegate(new NetworkTopologyDiscovery.Factory.ClassDelegate() {
			@Override
			public NetworkTopologyDiscovery newNetworkTopologyDiscovery() {
				return new MyNetworkTopologyDiscovery();
			}
		});
	}

	private static JmmDNS referenceResolver() {
		synchronized (MdnsDiscoveryAgent.class) {
			JmmDNS instance = JmmDNS.Factory.getInstance();
			if (++resolverRefCount == 1) {
				// This will cause the listener to be invoked for known hosts immediately.
				// JmDNS only supports one listener per service, so we have to do this here
				// with a static listener.
				instance.addServiceListener(SERVICE_TYPE, nvstreamListener);
			}
			return instance;
		}
	}

	private static void dereferenceResolver() {
		synchronized (MdnsDiscoveryAgent.class) {
			if (--resolverRefCount == 0) {
				try {
					JmmDNS.Factory.close();
				} catch (IOException e) {}
			}
		}
	}

	public MdnsDiscoveryAgent(MdnsDiscoveryListener listener) {
		this.listener = listener;
	}

	private void handleResolvedServiceInfo(ServiceInfo info) {
		synchronized (pendingResolution) {
			pendingResolution.remove(info.getName());
		}

		try {
			handleServiceInfo(info);
		} catch (UnsupportedEncodingException e) {
			// Invalid DNS response
			LimeLog.info("mDNS: Invalid response for machine: "+info.getName());
			return;
		}
	}

	private Inet6Address getLocalAddress(Inet6Address[] addresses) {
		for (Inet6Address addr : addresses) {
			if (addr.isLinkLocalAddress() || addr.isSiteLocalAddress()) {
				return addr;
			}
			// fc00::/7 - ULAs
			else if ((addr.getAddress()[0] & 0xfe) == 0xfc) {
				return addr;
			}
		}

		return null;
	}

	private Inet6Address getLinkLocalAddress(Inet6Address[] addresses) {
		for (Inet6Address addr : addresses) {
			if (addr.isLinkLocalAddress()) {
				LimeLog.info("Found link-local address: "+addr.getHostAddress());
				return addr;
			}
		}

		return null;
	}

	private Inet6Address getBestIpv6Address(Inet6Address[] addresses) {
		// First try to find a link local address, so we can match the interface identifier
		// with a global address (this will work for SLAAC but not DHCPv6).
		Inet6Address linkLocalAddr = getLinkLocalAddress(addresses);

		// We will try once to match a SLAAC interface suffix, then
		// pick the first matching address
		for (int tries = 0; tries < 2; tries++) {
			// We assume the addresses are already sorted in descending order
			// of preference from Bonjour.
			for (Inet6Address addr : addresses) {
				if (addr.isLinkLocalAddress() || addr.isSiteLocalAddress() || addr.isLoopbackAddress()) {
					// Link-local, site-local, and loopback aren't global
					LimeLog.info("Ignoring non-global address: "+addr.getHostAddress());
					continue;
				}

				byte[] addrBytes = addr.getAddress();

				// 2002::/16
				if (addrBytes[0] == 0x20 && addrBytes[1] == 0x02) {
					// 6to4 has horrible performance
					LimeLog.info("Ignoring 6to4 address: "+addr.getHostAddress());
					continue;
				}
				// 2001::/32
				else if (addrBytes[0] == 0x20 && addrBytes[1] == 0x01 && addrBytes[2] == 0x00 && addrBytes[3] == 0x00) {
					// Teredo also has horrible performance
					LimeLog.info("Ignoring Teredo address: "+addr.getHostAddress());
					continue;
				}
				// fc00::/7
				else if ((addrBytes[0] & 0xfe) == 0xfc) {
					// ULAs aren't global
					LimeLog.info("Ignoring ULA: "+addr.getHostAddress());
					continue;
				}

				// Compare the final 64-bit interface identifier and skip the address
				// if it doesn't match our link-local address.
				if (linkLocalAddr != null && tries == 0) {
					boolean matched = true;

					for (int i = 8; i < 16; i++) {
						if (linkLocalAddr.getAddress()[i] != addr.getAddress()[i]) {
							matched = false;
							break;
						}
					}

					if (!matched) {
						LimeLog.info("Ignoring non-matching global address: "+addr.getHostAddress());
						continue;
					}
				}

				return addr;
			}
		}

		return null;
	}

	private void handleServiceInfo(ServiceInfo info) throws UnsupportedEncodingException {
		Inet4Address v4Addrs[] = info.getInet4Addresses();
		Inet6Address v6Addrs[] = info.getInet6Addresses();

		LimeLog.info("mDNS: "+info.getName()+" has "+v4Addrs.length+" IPv4 addresses");
		LimeLog.info("mDNS: "+info.getName()+" has "+v6Addrs.length+" IPv6 addresses");

		Inet6Address v6GlobalAddr = getBestIpv6Address(v6Addrs);

		// Add a computer object for each IPv4 address reported by the PC
		for (Inet4Address v4Addr : v4Addrs) {
			synchronized (computers) {
				MdnsComputer computer = new MdnsComputer(info.getName(), v4Addr, v6GlobalAddr);
				if (computers.put(computer.getLocalAddress(), computer) == null) {
					// This was a new entry
					listener.notifyComputerAdded(computer);
				}
			}
		}

		// If there were no IPv4 addresses, use IPv6 for registration
		if (v4Addrs.length == 0) {
			Inet6Address v6LocalAddr = getLocalAddress(v6Addrs);

			if (v6LocalAddr != null || v6GlobalAddr != null) {
				MdnsComputer computer = new MdnsComputer(info.getName(), v6LocalAddr, v6GlobalAddr);
				if (computers.put(v6LocalAddr != null ?
						computer.getLocalAddress() : computer.getIpv6Address(), computer) == null) {
					// This was a new entry
					listener.notifyComputerAdded(computer);
				}
			}
		}
	}
	
	public void startDiscovery(final int discoveryIntervalMs) {
		// Kill any existing discovery before starting a new one
		stopDiscovery();
		
		// Add our listener to the set
		synchronized (listeners) {
			listeners.add(MdnsDiscoveryAgent.this);
		}
		
		discoveryThread = new Thread() {
			@Override
			public void run() {
				// This may result in listener callbacks so we must register
				// our listener first.
				JmmDNS resolver = referenceResolver();
				
				try {
					while (!Thread.interrupted()) {
						// Start an mDNS request
						resolver.requestServiceInfo(SERVICE_TYPE, null, discoveryIntervalMs);
						
						// Run service resolution again for pending machines
						ArrayList<String> pendingNames;
						synchronized (pendingResolution) {
							pendingNames = new ArrayList<String>(pendingResolution);
						}
						for (String name : pendingNames) {
							LimeLog.info("mDNS: Retrying service resolution for machine: "+name);
							ServiceInfo[] infos = resolver.getServiceInfos(SERVICE_TYPE, name, 500);
							if (infos != null && infos.length != 0) {
								LimeLog.info("mDNS: Resolved (retry) with "+infos.length+" service entries");
								for (ServiceInfo svcinfo : infos) {
									handleResolvedServiceInfo(svcinfo);
								}
							}
						}
						
						// Wait for the next polling interval
						try {
							Thread.sleep(discoveryIntervalMs);
						} catch (InterruptedException e) {
							break;
						}
					}
				}
				finally {
					// Dereference the resolver
					dereferenceResolver();
				}
			}
		};
		discoveryThread.setName("mDNS Discovery Thread");
		discoveryThread.start();
	}
	
	public void stopDiscovery() {
		// Remove our listener from the set
		synchronized (listeners) {
			listeners.remove(MdnsDiscoveryAgent.this);
		}
		
		// If there's already a running thread, interrupt it
		if (discoveryThread != null) {
			discoveryThread.interrupt();
			discoveryThread = null;
		}
	}
	
	public List<MdnsComputer> getComputerSet() {
		synchronized (computers) {
			return new ArrayList<MdnsComputer>(computers.values());
		}
	}

	@Override
	public void serviceAdded(ServiceEvent event) {
		LimeLog.info("mDNS: Machine appeared: "+event.getInfo().getName());

		ServiceInfo info = event.getDNS().getServiceInfo(SERVICE_TYPE, event.getInfo().getName(), 500);
		if (info == null) {
			// This machine is pending resolution
			synchronized (pendingResolution) {
				pendingResolution.add(event.getInfo().getName());
			}
			return;
		}
		
		LimeLog.info("mDNS: Resolved (blocking)");
		handleResolvedServiceInfo(info);
	}

	@Override
	public void serviceRemoved(ServiceEvent event) {
		LimeLog.info("mDNS: Machine disappeared: "+event.getInfo().getName());

		Inet4Address v4Addrs[] = event.getInfo().getInet4Addresses();
		for (Inet4Address addr : v4Addrs) {
			synchronized (computers) {
				MdnsComputer computer = computers.remove(addr);
				if (computer != null) {
					listener.notifyComputerRemoved(computer);
					break;
				}
			}
		}

		Inet6Address v6Addrs[] = event.getInfo().getInet6Addresses();
		for (Inet6Address addr : v6Addrs) {
			synchronized (computers) {
				MdnsComputer computer = computers.remove(addr);
				if (computer != null) {
					listener.notifyComputerRemoved(computer);
					break;
				}
			}
		}
	}

	@Override
	public void serviceResolved(ServiceEvent event) {
		// We handle this synchronously
	}
}
