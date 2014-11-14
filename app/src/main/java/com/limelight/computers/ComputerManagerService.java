package com.limelight.computers;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.List;
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
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;

public class ComputerManagerService extends Service {
	private static final int POLLING_PERIOD_MS = 5000;
	private static final int MDNS_QUERY_PERIOD_MS = 1000;
	
	private ComputerManagerBinder binder = new ComputerManagerBinder();
	
	private ComputerDatabaseManager dbManager;
	private AtomicInteger dbRefCount = new AtomicInteger(0);
	
	private IdentityManager idManager;
	private final HashMap<ComputerDetails, Thread> pollingThreads = new HashMap<ComputerDetails, Thread>();
	private ComputerManagerListener listener = null;
	private AtomicInteger activePolls = new AtomicInteger(0);

	private DiscoveryService.DiscoveryBinder discoveryBinder;
	private final ServiceConnection discoveryServiceConnection = new ServiceConnection() {
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

    // Returns true if the details object was modified
    private boolean runPoll(ComputerDetails details)
    {
        boolean newPc = (details.name == null);

        if (!getLocalDatabaseReference()) {
            return false;
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
                    return true;
                }
            }

            dbManager.updateComputer(details);
        }

        // Don't call the listener if this is a failed lookup of a new PC
        if ((!newPc || details.state == ComputerDetails.State.ONLINE) && listener != null) {
            listener.notifyComputerUpdated(details);
        }

        releaseLocalDatabaseReference();
        return true;
    }

    private Thread createPollingThread(final ComputerDetails details) {
        Thread t = new Thread() {
            @Override
            public void run() {
                while (!isInterrupted()) {
                    ComputerDetails originalDetails = new ComputerDetails();
                    originalDetails.update(details);

                    // Check if this poll has modified the details
                    if (runPoll(details) && !originalDetails.equals(details)) {
                        // Replace our thread entry with the new one
                        synchronized (pollingThreads) {
                            pollingThreads.remove(originalDetails);
                            pollingThreads.put(details, this);
                        }
                    }

                    // Wait until the next polling interval
                    try {
                        Thread.sleep(POLLING_PERIOD_MS);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        };
        t.setName("Polling thread for "+details.localIp.getHostAddress());
        return t;
    }
	
	public class ComputerManagerBinder extends Binder {
		public void startPolling(ComputerManagerListener listener) {
			// Set the listener
			ComputerManagerService.this.listener = listener;
			
			// Start mDNS autodiscovery too
			discoveryBinder.startDiscovery(MDNS_QUERY_PERIOD_MS);
			
			// Start polling known machines
            if (!getLocalDatabaseReference()) {
                return;
            }
            List<ComputerDetails> computerList = dbManager.getAllComputers();
            releaseLocalDatabaseReference();

            synchronized (pollingThreads) {
                for (ComputerDetails computer : computerList) {
                    // This polling thread might already be there
                    if (!pollingThreads.containsKey(computer)) {
                        // Report this computer initially
                        listener.notifyComputerUpdated(computer);

                        Thread t = createPollingThread(computer);
                        pollingThreads.put(computer, t);
                        t.start();
                    }
                }
            }
		}
		
		public void waitForReady() {
			synchronized (discoveryServiceConnection) {
				try {
					while (discoveryBinder == null) {
						// Wait for the bind notification
						discoveryServiceConnection.wait(1000);
					}
				} catch (InterruptedException ignored) {
				}
			}
		}
		
		public void waitForPollingStopped() {
			while (activePolls.get() != 0) {
				try {
					Thread.sleep(250);
				} catch (InterruptedException ignored) {}
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
        synchronized (pollingThreads) {
            for (Thread t : pollingThreads.values()) {
                t.interrupt();
            }
            pollingThreads.clear();
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

		// Spawn a thread for this computer
        synchronized (pollingThreads) {
            // This polling thread might already be there
            if (!pollingThreads.containsKey(fakeDetails)) {
                Thread t = createPollingThread(fakeDetails);
                pollingThreads.put(fakeDetails, t);
                t.start();
            }
        }
	}

	public boolean addComputerBlocking(InetAddress addr) {
		// Setup a placeholder
		ComputerDetails fakeDetails = new ComputerDetails();
		fakeDetails.localIp = addr;
		fakeDetails.remoteIp = addr;
		
		// Block while we try to fill the details
        runPoll(fakeDetails);
		
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
		return pollComputer(details, true);
	}
	
	@Override
	public void onCreate() {
		// Bind to the discovery service
		bindService(new Intent(this, DiscoveryService.class),
				discoveryServiceConnection, Service.BIND_AUTO_CREATE);
		
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
		
		// FIXME: Should await termination here but we have timeout issues in HttpURLConnection
		
		// Remove the initial DB reference
		releaseLocalDatabaseReference();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}
}
