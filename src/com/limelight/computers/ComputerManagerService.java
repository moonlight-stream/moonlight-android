package com.limelight.computers;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.limelight.LimeLog;
import com.limelight.binding.PlatformBinding;
import com.limelight.discovery.DiscoveryService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.mdns.MdnsComputer;
import com.limelight.nvstream.mdns.MdnsDiscoveryListener;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.IBinder;

public class ComputerManagerService extends Service {
	private static final int MAX_CONCURRENT_REQUESTS = 4;
	private static final int POLLING_PERIOD_MS = 5000;
	private static final int MDNS_QUERY_PERIOD_MS = 1000;
	
	private ComputerManagerBinder binder = new ComputerManagerBinder();
	
	private ComputerDatabaseManager dbManager;
	private AtomicInteger dbRefCount = new AtomicInteger(0);
	
	private IdentityManager idManager;
	private ThreadPoolExecutor pollingPool;
	private Timer pollingTimer;
	private ComputerManagerListener listener = null;
	private AtomicInteger activePolls = new AtomicInteger(0);
	private boolean stopped;

	private DiscoveryService.DiscoveryBinder discoveryBinder;
	private ServiceConnection discoveryServiceConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder binder) {
			synchronized (discoveryServiceConnection) {
				DiscoveryService.DiscoveryBinder privateBinder = ((DiscoveryService.DiscoveryBinder)binder);
				
				// Set us as the event listener
				privateBinder.setListener(createDiscoveryListener());
				
				// Signal a possible waiter that we're all setup
				discoveryBinder = privateBinder;
				discoveryServiceConnection.notifyAll();
			}
		}

		public void onServiceDisconnected(ComponentName className) {
			discoveryBinder = null;
		}
	};
	
	public class ComputerManagerBinder extends Binder {
		public void startPolling(ComputerManagerListener listener) {
			// Not stopped
			stopped = false;
			
			// Set the listener
			ComputerManagerService.this.listener = listener;
			
			// Start mDNS autodiscovery too
			discoveryBinder.startDiscovery(MDNS_QUERY_PERIOD_MS);
			
			// Start polling known machines
			pollingTimer = new Timer();
			pollingTimer.schedule(getTimerTask(), 0, POLLING_PERIOD_MS);
		}
		
		public void waitForReady() {
			synchronized (discoveryServiceConnection) {
				try {
					while (discoveryBinder == null) {
						// Wait for the bind notification
						discoveryServiceConnection.wait(1000);
					}
				} catch (InterruptedException e) {
				}
			}
		}
		
		public void waitForPollingStopped() {
			while (activePolls.get() != 0) {
				try {
					Thread.sleep(250);
				} catch (InterruptedException e) {}
			}
		}
		
		public boolean addComputerBlocking(InetAddress addr) {
			return ComputerManagerService.this.addComputerBlocking(addr);
		}
		
		public void addComputer(InetAddress addr) {
			ComputerManagerService.this.addComputer(addr);
		}
		
		public void removeComputer(String name) {
			ComputerManagerService.this.removeComputer(name);
		}
		
		public void stopPolling() {
			// Just call the unbind handler to cleanup
			ComputerManagerService.this.onUnbind(null);
		}
		
		public String getUniqueId() {
			return idManager.getUniqueId();
		}
	}
	
	@Override
	public boolean onUnbind(Intent intent) {
		// Stopped now
		stopped = true;
		
		// Stop mDNS autodiscovery
		discoveryBinder.stopDiscovery();
		
		// Stop polling
		if (pollingTimer != null) {
			pollingTimer.cancel();
			pollingTimer = null;
		}
		
		// Remove the listener
		listener = null;
		
		return false;
	}
	
	private MdnsDiscoveryListener createDiscoveryListener() {
		return new MdnsDiscoveryListener() {
			@Override
			public void notifyComputerAdded(MdnsComputer computer) {
				// Kick off a serverinfo poll on this machine
				addComputer(computer.getAddress());
			}

			@Override
			public void notifyComputerRemoved(MdnsComputer computer) {
				// Nothing to do here
			}

			@Override
			public void notifyDiscoveryFailure(Exception e) {
				LimeLog.severe("mDNS discovery failed");
				e.printStackTrace();
			}
		};
	}
	
	public void addComputer(InetAddress addr) {
		// Setup a placeholder
		ComputerDetails fakeDetails = new ComputerDetails();
		fakeDetails.localIp = addr;
		fakeDetails.remoteIp = addr;
		
		// Put it in the thread pool to process later
		pollingPool.execute(getPollingRunnable(fakeDetails));
	}
	
	public boolean addComputerBlocking(InetAddress addr) {
		// Setup a placeholder
		ComputerDetails fakeDetails = new ComputerDetails();
		fakeDetails.localIp = addr;
		fakeDetails.remoteIp = addr;
		
		// Block while we try to fill the details
		getPollingRunnable(fakeDetails).run();
		
		// If the machine is reachable, it was successful
		return fakeDetails.state == ComputerDetails.State.ONLINE;
	}
	
	public void removeComputer(String name) {
		if (!getLocalDatabaseReference()) {
			return;
		}
		
		// Remove it from the database
		dbManager.deleteComputer(name);
		
		releaseLocalDatabaseReference();
	}
	
	private boolean getLocalDatabaseReference() {
		if (dbRefCount.get() == 0) {
			return false;
		}
		
		dbRefCount.incrementAndGet();
		return true;
	}
	
	private void releaseLocalDatabaseReference() {
		if (dbRefCount.decrementAndGet() == 0) {
			dbManager.close();
		}
	}
	
	private TimerTask getTimerTask() {
		return new TimerTask() {
			@Override
			public void run() {
				if (!getLocalDatabaseReference()) {
					return;
				}
				List<ComputerDetails> computerList = dbManager.getAllComputers();
				releaseLocalDatabaseReference();
				
				for (ComputerDetails computer : computerList) {
					pollingPool.execute(getPollingRunnable(computer));
				}
			}
		};
	}
	
	private int getActiveNetworkType() {
		ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);		
		NetworkInfo activeNetworkInfo = connMgr.getActiveNetworkInfo();
		if (activeNetworkInfo == null) {
			return -1;
		}
		
		return activeNetworkInfo.getType();
	}
	
	private InterfaceAddress getActiveInterfaceAddress() {
		String matchingPrefix;
		
		switch (getActiveNetworkType())
		{
		case ConnectivityManager.TYPE_ETHERNET:
			matchingPrefix = "eth";
			break;
		case ConnectivityManager.TYPE_WIFI:
			matchingPrefix = "wlan";
			break;

		default:
			// Must be on Ethernet or Wifi to consider that we can send large packets
			return null;
		}

		// Try to find the interface that corresponds to the active network
		try {
			Enumeration<NetworkInterface> ifaceList = NetworkInterface.getNetworkInterfaces();
			while (ifaceList.hasMoreElements()) {
				NetworkInterface iface = ifaceList.nextElement();

				// Look for an interface that matches the prefix we expect
				if (iface.isUp() && iface.getName().startsWith(matchingPrefix)) {
					// Find the IPv4 address for the interface
					for (InterfaceAddress addr : iface.getInterfaceAddresses()) {
						if (!(addr.getAddress() instanceof Inet4Address)) {
							// Skip non-IPv4 addresses
							continue;
						}
						
						// Found the right address on the right interface
						return addr;
					}
				}
			}
		} catch (SocketException e) {
			e.printStackTrace();
		}

		// We didn't find the interface or something else went wrong
		return null;
	}
	
	private boolean isOnSameSubnet(InetAddress targetAddress, InetAddress localAddress, short networkPrefixLength) {
		byte[] targetBytes = targetAddress.getAddress();
		byte[] localBytes = localAddress.getAddress();

		for (int byteIndex = 0; networkPrefixLength > 0; byteIndex++) {
			byte target = targetBytes[byteIndex];
			byte local = localBytes[byteIndex];
			
			if (networkPrefixLength >= 8) {
				// Do a full byte comparison
				if (target != local) {
					return false;
				}
				
				networkPrefixLength -= 8;
			}
			else {
				target &= (byte)(0xFF << (8 - networkPrefixLength));
				local &= (byte)(0xFF << (8 - networkPrefixLength));
				
				// Do a masked comparison
				if (target != local) {
					return false;
				}
				
				networkPrefixLength = 0;
			}
		}
		
		return true;
	}
	
	private ComputerDetails tryPollIp(InetAddress ipAddr) {
		try {
			NvHTTP http = new NvHTTP(ipAddr, idManager.getUniqueId(),
					null, PlatformBinding.getCryptoProvider(ComputerManagerService.this));
			
			return http.getComputerDetails();
		} catch (Exception e) {
			return null;
		}
	}
	
	private boolean pollComputer(ComputerDetails details, boolean localFirst) {
		ComputerDetails polledDetails;
		
		if (localFirst) {
			polledDetails = tryPollIp(details.localIp);
		}
		else {
			polledDetails = tryPollIp(details.remoteIp);
		}
		
		if (polledDetails == null && !details.localIp.equals(details.remoteIp)) {
			// Failed, so let's try the fallback
			if (!localFirst) {
				polledDetails = tryPollIp(details.localIp);
			}
			else {
				polledDetails = tryPollIp(details.remoteIp);
			}
			
			// The fallback poll worked
			if (polledDetails != null) {
				polledDetails.reachability = !localFirst ? ComputerDetails.Reachability.LOCAL :
					ComputerDetails.Reachability.REMOTE;
			}
		}
		else if (polledDetails != null) {
			polledDetails.reachability = localFirst ? ComputerDetails.Reachability.LOCAL :
				ComputerDetails.Reachability.REMOTE;
		}
		
		// Machine was unreachable both tries
		if (polledDetails == null) {
			return false;
		}
		
		// If we got here, it's reachable
		details.update(polledDetails);
		return true;
	}
	
	private boolean doPollMachine(ComputerDetails details) {
		// Get the network type
		int networkType = getActiveNetworkType();
		switch (networkType) {
		// We'll check local first on these if we find
		// we're on the same subnet
		case ConnectivityManager.TYPE_ETHERNET:
		case ConnectivityManager.TYPE_WIFI:
			InterfaceAddress ifaceAddr = getActiveInterfaceAddress();
			if (ifaceAddr != null) {
				if (isOnSameSubnet(details.localIp, ifaceAddr.getAddress(), ifaceAddr.getNetworkPrefixLength())) {
					// It's on the same subnet, so poll local first
					LimeLog.info("Machine looks local; trying local IP first");
					return pollComputer(details, true);
				}
			}
			// Fall through to remote first
		default:
			LimeLog.info("Machine looks remote; trying remote IP first");
			return pollComputer(details, false);
		}
	}
	
	private Runnable getPollingRunnable(final ComputerDetails details) {
		return new Runnable() {

			@Override
			public void run() {
				boolean newPc = (details.name == null);
				
				if (stopped) {
					return;
				}
				
				if (!getLocalDatabaseReference()) {
					return;
				}
				
				activePolls.incrementAndGet();
				
				// Poll the machine
				if (!doPollMachine(details)) {
					details.state = ComputerDetails.State.OFFLINE;
					details.reachability = ComputerDetails.Reachability.OFFLINE;
				}
				
				activePolls.decrementAndGet();

				// If it's online, update our persistent state
				if (details.state == ComputerDetails.State.ONLINE) {
					if (!newPc) {
						// Check if it's in the database because it could have been
						// removed after this was issued
						if (dbManager.getComputerByName(details.name) == null) {
							// It's gone
							releaseLocalDatabaseReference();
							return;
						}
					}
					
					dbManager.updateComputer(details);
				}
				
				// Don't call the listener if this is a failed lookup of a new PC
				if ((!newPc || details.state == ComputerDetails.State.ONLINE) && listener != null) {
					listener.notifyComputerUpdated(details);
				}
				
				releaseLocalDatabaseReference();
			}
		};
	}
	
	@Override
	public void onCreate() {
		// Bind to the discovery service
		bindService(new Intent(this, DiscoveryService.class),
				discoveryServiceConnection, Service.BIND_AUTO_CREATE);
		
		// Create the thread pool for updating computer state
		pollingPool = new ThreadPoolExecutor(MAX_CONCURRENT_REQUESTS, MAX_CONCURRENT_REQUESTS,
				Long.MAX_VALUE, TimeUnit.DAYS, new LinkedBlockingQueue<Runnable>(),
				new ThreadPoolExecutor.DiscardPolicy());
		
		// Lookup or generate this device's UID
		idManager = new IdentityManager(this);
		
		// Initialize the DB
		dbManager = new ComputerDatabaseManager(this);
		dbRefCount.set(1);
	}
	
	@Override
	public void onDestroy() {
		if (discoveryBinder != null) {
			// Unbind from the discovery service
			unbindService(discoveryServiceConnection);
		}
		
		// Stop the thread pool
		pollingPool.shutdownNow();
		
		// FIXME: Should await termination here but we have timeout issues in HttpURLConnection
		
		// Remove the initial DB reference
		releaseLocalDatabaseReference();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}
}
