package com.limelight.computers;

import java.net.InetAddress;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.limelight.LimeLog;
import com.limelight.binding.PlatformBinding;
import com.limelight.discovery.DiscoveryService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.mdns.MdnsComputer;
import com.limelight.nvstream.mdns.MdnsDiscoveryListener;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;

public class ComputerManagerService extends Service {
	private static final int MAX_CONCURRENT_REQUESTS = 4;
	private static final int POLLING_PERIOD_MS = 5000;
	private static final int MDNS_QUERY_PERIOD_MS = 1000;
	
	private ComputerManagerBinder binder = new ComputerManagerBinder();
	
	private ComputerDatabaseManager dbManager;
	private IdentityManager idManager;
	private ThreadPoolExecutor pollingPool;
	private Timer pollingTimer;
	private ComputerManagerListener listener = null;

	private DiscoveryService.DiscoveryBinder discoveryBinder;
	private ServiceConnection discoveryServiceConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder binder) {
			synchronized (discoveryServiceConnection) {
				discoveryBinder = ((DiscoveryService.DiscoveryBinder)binder);
				
				// Set us as the event listener
				discoveryBinder.setListener(createDiscoveryListener());
				
				// Signal a possible waiter that we're all setup
				discoveryServiceConnection.notifyAll();
			}
		}

		public void onServiceDisconnected(ComponentName className) {
			discoveryBinder = null;
		}
	};
	
	public class ComputerManagerBinder extends Binder {
		public void startPolling(ComputerManagerListener listener) {
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
				LimeLog.severe("Added computer: "+computer.getName());
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
		// Remove it from the database
		dbManager.deleteComputer(name);
	}
	
	private TimerTask getTimerTask() {
		return new TimerTask() {
			@Override
			public void run() {
				List<ComputerDetails> computerList = dbManager.getAllComputers();
				for (ComputerDetails computer : computerList) {
					pollingPool.execute(getPollingRunnable(computer));
				}
			}
		};
	}
	
	private Runnable getPollingRunnable(final ComputerDetails details) {
		return new Runnable() {

			@Override
			public void run() {
				boolean newPc = details.name == null;
				
				try {
					// Try the local IP first
					NvHTTP http = new NvHTTP(details.localIp, idManager.getUniqueId(),
							null, PlatformBinding.getCryptoProvider(ComputerManagerService.this));
					
					ComputerDetails localDetails = http.getComputerDetails();
					
					// If we got here, it's reachable
					details.reachability = ComputerDetails.Reachability.LOCAL;
					details.update(localDetails);
				} catch (Exception e) {
					// This isn't horrible yet; we'll try remote
					try {
						NvHTTP http = new NvHTTP(details.remoteIp, idManager.getUniqueId(),
								null, PlatformBinding.getCryptoProvider(ComputerManagerService.this));
						
						ComputerDetails remoteDetails = http.getComputerDetails();
						
						// If we got here, it's reachable
						details.reachability = ComputerDetails.Reachability.REMOTE;
						details.update(remoteDetails);
					} catch (Exception e1) {
						// No good, it's offline
						details.state = ComputerDetails.State.OFFLINE;
						details.reachability = ComputerDetails.Reachability.OFFLINE;
					}
				}
				
				// If it's online, update our persistent state
				if (details.state == ComputerDetails.State.ONLINE) {
					if (!newPc) {
						// Check if it's in the database because it could have been
						// removed after this was issued
						if (dbManager.getComputerByName(details.name) == null) {
							// It's gone
							return;
						}
					}
					
					dbManager.updateComputer(details);
				}
				
				// Update anyone listening
				if (listener != null) {
					listener.notifyComputerUpdated(details);
				}
			}
		};
	}
	
	@Override
	public void onCreate() {
		// Bind to the discovery service
		bindService(new Intent(this, DiscoveryService.class),
				discoveryServiceConnection, Service.BIND_AUTO_CREATE);
		
		// Create the thread pool for updating computer state
		pollingPool = new ThreadPoolExecutor(1, MAX_CONCURRENT_REQUESTS, Long.MAX_VALUE, TimeUnit.DAYS,
				new LinkedBlockingQueue<Runnable>(), new ThreadPoolExecutor.DiscardPolicy());
		
		// Lookup or generate this device's UID
		idManager = new IdentityManager(this);
		
		// Initialize the DB
		dbManager = new ComputerDatabaseManager(this);
	}
	
	@Override
	public void onDestroy() {
		if (discoveryBinder != null) {
			// Unbind from the discovery service
			unbindService(discoveryServiceConnection);
		}
		
		// Stop the thread pool
		pollingPool.shutdownNow();
		try {
			pollingPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		} catch (InterruptedException e) {}
		
		// Close the DB
		dbManager.close();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}
}
