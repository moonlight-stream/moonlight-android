package com.limelight.computers;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.limelight.LimeLog;
import com.limelight.binding.PlatformBinding;
import com.limelight.discovery.DiscoveryService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.mdns.MdnsComputer;
import com.limelight.nvstream.mdns.MdnsDiscoveryListener;
import com.limelight.utils.CacheHelper;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;

public class ComputerManagerService extends Service {
	private static final int POLLING_PERIOD_MS = 3000;
	private static final int MDNS_QUERY_PERIOD_MS = 1000;
	
	private ComputerManagerBinder binder = new ComputerManagerBinder();
	
	private ComputerDatabaseManager dbManager;
	private AtomicInteger dbRefCount = new AtomicInteger(0);
	
	private IdentityManager idManager;
	private final LinkedList<PollingTuple> pollingTuples = new LinkedList<PollingTuple>();
	private ComputerManagerListener listener = null;
	private AtomicInteger activePolls = new AtomicInteger(0);
    private boolean pollingActive = false;

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
        boolean newPc = details.name.isEmpty();

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
                while (!isInterrupted() && pollingActive) {
                    // Check if this poll has modified the details
                    runPoll(details);

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
            // Polling is active
            pollingActive = true;

			// Set the listener
			ComputerManagerService.this.listener = listener;
			
			// Start mDNS autodiscovery too
			discoveryBinder.startDiscovery(MDNS_QUERY_PERIOD_MS);

            synchronized (pollingTuples) {
                for (PollingTuple tuple : pollingTuples) {
                    // This polling thread might already be there
                    if (tuple.thread == null) {
                        // Report this computer initially
                        listener.notifyComputerUpdated(tuple.computer);

                        tuple.thread = createPollingThread(tuple.computer);
                        tuple.thread.start();
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

        public ApplistPoller createAppListPoller(ComputerDetails computer) {
            return new ApplistPoller(computer);
        }
		
		public String getUniqueId() {
			return idManager.getUniqueId();
		}

        public ComputerDetails getComputer(UUID uuid) {
            synchronized (pollingTuples) {
                for (PollingTuple tuple : pollingTuples) {
                    if (uuid.equals(tuple.computer.uuid)) {
                        return tuple.computer;
                    }
                }
            }

            return null;
        }
	}
	
	@Override
	public boolean onUnbind(Intent intent) {
		// Stop mDNS autodiscovery
		discoveryBinder.stopDiscovery();
		
		// Stop polling
        pollingActive = false;
        synchronized (pollingTuples) {
            for (PollingTuple tuple : pollingTuples) {
                if (tuple.thread != null) {
                    // Interrupt and remove the thread
                    tuple.thread.interrupt();
                    tuple.thread = null;
                }
            }
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
        fakeDetails.name = "";

        addTuple(fakeDetails);
	}

    private void addTuple(ComputerDetails details) {
        synchronized (pollingTuples) {
            for (PollingTuple tuple : pollingTuples) {
                // Check if this is the same computer
                if (tuple.computer == details ||
                        // If there's no name on one of these computers, compare with the local IP
                        ((details.name.isEmpty() || tuple.computer.name.isEmpty()) &&
                                tuple.computer.localIp.equals(details.localIp)) ||
                        // If there is a name on both computers, compare with name
                        ((!details.name.isEmpty() && !tuple.computer.name.isEmpty()) &&
                                tuple.computer.name.equals(details.name))) {

                    // Update details anyway in case this machine has been re-added by IP
                    // after not being reachable by our existing information
                    tuple.computer.localIp = details.localIp;
                    tuple.computer.remoteIp = details.remoteIp;

                    // Start a polling thread if polling is active
                    if (pollingActive && tuple.thread == null) {
                        tuple.thread = createPollingThread(details);
                        tuple.thread.start();
                    }

                    // Found an entry so we're done
                    return;
                }
            }

            // If we got here, we didn't find an entry
            PollingTuple tuple = new PollingTuple(details, pollingActive ? createPollingThread(details) : null);
            pollingTuples.add(tuple);
            if (tuple.thread != null) {
                tuple.thread.start();
            }
        }
    }

	public boolean addComputerBlocking(InetAddress addr) {
		// Setup a placeholder
		ComputerDetails fakeDetails = new ComputerDetails();
		fakeDetails.localIp = addr;
		fakeDetails.remoteIp = addr;
        fakeDetails.name = "";
		
		// Block while we try to fill the details
        runPoll(fakeDetails);
		
		// If the machine is reachable, it was successful
		if (fakeDetails.state == ComputerDetails.State.ONLINE) {
            // Start a polling thread for this machine
            addTuple(fakeDetails);
            return true;
        }
        else {
            return false;
        }
	}
	
	public void removeComputer(String name) {
		if (!getLocalDatabaseReference()) {
			return;
		}
		
		// Remove it from the database
		dbManager.deleteComputer(name);

        synchronized (pollingTuples) {
            // Remove the computer from the computer list
            for (PollingTuple tuple : pollingTuples) {
                if (tuple.computer.name.equals(name)) {
                    if (tuple.thread != null) {
                        // Interrupt the thread on this entry
                        tuple.thread.interrupt();
                    }
                    pollingTuples.remove(tuple);
                    break;
                }
            }
        }
		
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
	
	private ComputerDetails tryPollIp(ComputerDetails details, InetAddress ipAddr) {
		try {
			NvHTTP http = new NvHTTP(ipAddr, idManager.getUniqueId(),
					null, PlatformBinding.getCryptoProvider(ComputerManagerService.this));

			ComputerDetails newDetails = http.getComputerDetails();

            // Check if this is the PC we expected
            if (details.uuid != null && newDetails.uuid != null &&
                    !details.uuid.equals(newDetails.uuid)) {
                // We got the wrong PC!
                LimeLog.info("Polling returned the wrong PC!");
                return null;
            }

            return newDetails;
		} catch (Exception e) {
			return null;
		}
	}
	
	private boolean pollComputer(ComputerDetails details, boolean localFirst) {
		ComputerDetails polledDetails;

        // If the local address is routable across the Internet,
        // always consider this PC remote to be conservative
        if (details.localIp.equals(details.remoteIp)) {
            localFirst = false;
        }
		
		if (localFirst) {
			polledDetails = tryPollIp(details, details.localIp);
		}
		else {
			polledDetails = tryPollIp(details, details.remoteIp);
		}
		
		if (polledDetails == null && !details.localIp.equals(details.remoteIp)) {
			// Failed, so let's try the fallback
			if (!localFirst) {
				polledDetails = tryPollIp(details, details.localIp);
			}
			else {
				polledDetails = tryPollIp(details, details.remoteIp);
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
        if (details.reachability == ComputerDetails.Reachability.UNKNOWN ||
            details.reachability == ComputerDetails.Reachability.OFFLINE) {
            // Always try local first to avoid potential UDP issues when
            // attempting to stream via the router's external IP address
            // behind its NAT
            return pollComputer(details, true);
        }
        else {
            // If we're already reached a machine via a particular IP address,
            // always try that one first
            return pollComputer(details, details.reachability == ComputerDetails.Reachability.LOCAL);
        }
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

        // Grab known machines into our computer list
        if (!getLocalDatabaseReference()) {
            return;
        }

        for (ComputerDetails computer : dbManager.getAllComputers()) {
            // Add tuples for each computer
            addTuple(computer);
        }

        releaseLocalDatabaseReference();
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

    public class ApplistPoller {
        private Thread thread;
        private ComputerDetails computer;
        private Object pollEvent = new Object();

        public ApplistPoller(ComputerDetails computer) {
            this.computer = computer;
        }

        public void pollNow() {
            synchronized (pollEvent) {
                pollEvent.notify();
            }
        }

        private boolean waitPollingDelay() {
            try {
                synchronized (pollEvent) {
                    pollEvent.wait(POLLING_PERIOD_MS);
                }
            } catch (InterruptedException e) {
                return false;
            }

            return thread != null && !thread.isInterrupted();
        }

        public void start() {
            thread = new Thread() {
                @Override
                public void run() {
                    do {
                        InetAddress selectedAddr;

                        // Can't poll if it's not online
                        if (computer.state != ComputerDetails.State.ONLINE) {
                            listener.notifyComputerUpdated(computer);
                            continue;
                        }

                        // Can't poll if there's no UUID yet
                        if (computer.uuid == null) {
                            continue;
                        }

                        if (computer.reachability == ComputerDetails.Reachability.LOCAL) {
                            selectedAddr = computer.localIp;
                        }
                        else {
                            selectedAddr = computer.remoteIp;
                        }

                        NvHTTP http = new NvHTTP(selectedAddr, idManager.getUniqueId(),
                                null, PlatformBinding.getCryptoProvider(ComputerManagerService.this));

                        try {
                            // Query the app list from the server
                            String appList = http.getAppListRaw();

                            // Open the cache file
                            LimeLog.info("Updating app list from "+computer.uuid.toString());
                            FileOutputStream cacheOut = CacheHelper.openCacheFileForOutput(getCacheDir(), "applist", computer.uuid.toString());
                            CacheHelper.writeStringToOutputStream(cacheOut, appList);
                            cacheOut.close();

                            // Update the computer
                            computer.rawAppList = appList;

                            // Notify that the app list has been updated
                            // and ensure that the thread is still active
                            if (listener != null && thread != null) {
                                listener.notifyComputerUpdated(computer);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } while (waitPollingDelay());
                }
            };
            thread.start();
        }

        public void stop() {
            if (thread != null) {
                thread.interrupt();

                // Don't join here because we might be blocked on network I/O

                thread = null;
            }
        }
    }
}

class PollingTuple {
    public Thread thread;
    public ComputerDetails computer;

    public PollingTuple(ComputerDetails computer, Thread thread) {
        this.computer = computer;
        this.thread = thread;
    }
}