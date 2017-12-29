package com.limelight.computers;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.limelight.LimeLog;
import com.limelight.binding.PlatformBinding;
import com.limelight.discovery.DiscoveryService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.mdns.MdnsComputer;
import com.limelight.nvstream.mdns.MdnsDiscoveryListener;
import com.limelight.utils.CacheHelper;
import com.limelight.utils.ServerHelper;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;

import org.xmlpull.v1.XmlPullParserException;

public class ComputerManagerService extends Service {
    private static final int SERVERINFO_POLLING_PERIOD_MS = 1500;
    private static final int APPLIST_POLLING_PERIOD_MS = 30000;
    private static final int APPLIST_FAILED_POLLING_RETRY_MS = 2000;
    private static final int MDNS_QUERY_PERIOD_MS = 1000;
    private static final int FAST_POLL_TIMEOUT = 1000;
    private static final int OFFLINE_POLL_TRIES = 5;
    private static final int INITIAL_POLL_TRIES = 2;
    private static final int EMPTY_LIST_THRESHOLD = 3;
    private static final int POLL_DATA_TTL_MS = 30000;

    private final ComputerManagerBinder binder = new ComputerManagerBinder();

    private ComputerDatabaseManager dbManager;
    private final AtomicInteger dbRefCount = new AtomicInteger(0);

    private IdentityManager idManager;
    private final LinkedList<PollingTuple> pollingTuples = new LinkedList<>();
    private ComputerManagerListener listener = null;
    private final AtomicInteger activePolls = new AtomicInteger(0);
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
    private boolean runPoll(ComputerDetails details, boolean newPc, int offlineCount) throws InterruptedException {
        if (!getLocalDatabaseReference()) {
            return false;
        }

        final int pollTriesBeforeOffline = details.state == ComputerDetails.State.UNKNOWN ?
                INITIAL_POLL_TRIES : OFFLINE_POLL_TRIES;

        activePolls.incrementAndGet();

        // Poll the machine
        try {
            if (!pollComputer(details)) {
                if (!newPc && offlineCount < pollTriesBeforeOffline) {
                    // Return without calling the listener
                    releaseLocalDatabaseReference();
                    return false;
                }

                details.state = ComputerDetails.State.OFFLINE;
                details.reachability = ComputerDetails.Reachability.OFFLINE;
            }
        } catch (InterruptedException e) {
            releaseLocalDatabaseReference();
            throw e;
        } finally {
            activePolls.decrementAndGet();
        }

        // If it's online, update our persistent state
        if (details.state == ComputerDetails.State.ONLINE) {
            if (!newPc) {
                // Check if it's in the database because it could have been
                // removed after this was issued
                if (dbManager.getComputerByName(details.name) == null) {
                    // It's gone
                    releaseLocalDatabaseReference();
                    return false;
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

    private Thread createPollingThread(final PollingTuple tuple) {
        Thread t = new Thread() {
            @Override
            public void run() {

                int offlineCount = 0;
                while (!isInterrupted() && pollingActive && tuple.thread == this) {
                    try {
                        // Only allow one request to the machine at a time
                        synchronized (tuple.networkLock) {
                            // Check if this poll has modified the details
                            if (!runPoll(tuple.computer, false, offlineCount)) {
                                LimeLog.warning(tuple.computer.name + " is offline (try " + offlineCount + ")");
                                offlineCount++;
                            } else {
                                tuple.lastSuccessfulPollMs = System.currentTimeMillis();
                                offlineCount = 0;
                            }
                        }

                        // Wait until the next polling interval
                        Thread.sleep(SERVERINFO_POLLING_PERIOD_MS);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        };
        t.setName("Polling thread for " + tuple.computer.localAddress);
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
                    // Enforce the poll data TTL
                    if (System.currentTimeMillis() - tuple.lastSuccessfulPollMs > POLL_DATA_TTL_MS) {
                        LimeLog.info("Timing out polled state for "+tuple.computer.name);
                        tuple.computer.state = ComputerDetails.State.UNKNOWN;
                        tuple.computer.reachability = ComputerDetails.Reachability.UNKNOWN;
                    }

                    // Report this computer initially
                    listener.notifyComputerUpdated(tuple.computer);

                    // This polling thread might already be there
                    if (tuple.thread == null) {
                        tuple.thread = createPollingThread(tuple);
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

        public boolean addComputerBlocking(String addr, boolean manuallyAdded) {
            return ComputerManagerService.this.addComputerBlocking(addr, manuallyAdded);
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

        public void invalidateStateForComputer(UUID uuid) {
            synchronized (pollingTuples) {
                for (PollingTuple tuple : pollingTuples) {
                    if (uuid.equals(tuple.computer.uuid)) {
                        // We need the network lock to prevent a concurrent poll
                        // from wiping this change out
                        synchronized (tuple.networkLock) {
                            tuple.computer.state = ComputerDetails.State.UNKNOWN;
                            tuple.computer.reachability = ComputerDetails.Reachability.UNKNOWN;
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (discoveryBinder != null) {
            // Stop mDNS autodiscovery
            discoveryBinder.stopDiscovery();
        }

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
                addComputerBlocking(computer.getAddress().getHostAddress(), false);
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

    private void addTuple(ComputerDetails details, boolean manuallyAdded) {
        synchronized (pollingTuples) {
            for (PollingTuple tuple : pollingTuples) {
                // Check if this is the same computer
                if (tuple.computer.uuid.equals(details.uuid)) {
                    if (manuallyAdded) {
                        // Update details anyway in case this machine has been re-added by IP
                        // after not being reachable by our existing information
                        tuple.computer.localAddress = details.localAddress;
                        tuple.computer.remoteAddress = details.remoteAddress;
                    }
                    else {
                        // This indicates that mDNS discovered this address, so we
                        // should only apply the local address.
                        tuple.computer.localAddress = details.localAddress;
                    }

                    // Start a polling thread if polling is active
                    if (pollingActive && tuple.thread == null) {
                        tuple.thread = createPollingThread(tuple);
                        tuple.thread.start();
                    }

                    // Found an entry so we're done
                    return;
                }
            }

            // If we got here, we didn't find an entry
            PollingTuple tuple = new PollingTuple(details, null);
            if (pollingActive) {
                tuple.thread = createPollingThread(tuple);
            }
            pollingTuples.add(tuple);
            if (tuple.thread != null) {
                tuple.thread.start();
            }
        }
    }

    public boolean addComputerBlocking(String addr, boolean manuallyAdded) {
        // Setup a placeholder
        ComputerDetails fakeDetails = new ComputerDetails();
        fakeDetails.localAddress = addr;
        fakeDetails.remoteAddress = addr;

        // Block while we try to fill the details
        try {
            runPoll(fakeDetails, true, 0);
        } catch (InterruptedException e) {
            return false;
        }

        // If the machine is reachable, it was successful
        if (fakeDetails.state == ComputerDetails.State.ONLINE) {
            LimeLog.info("New PC ("+fakeDetails.name+") is UUID "+fakeDetails.uuid);

            // Start a polling thread for this machine
            addTuple(fakeDetails, manuallyAdded);
            return true;
        }
        else {
            if (!manuallyAdded) {
                LimeLog.warning("Auto-discovered PC failed to respond: "+addr);
            }

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
                        tuple.thread = null;
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

    private ComputerDetails tryPollIp(ComputerDetails details, String address) {
        // Fast poll this address first to determine if we can connect at the TCP layer
        if (!fastPollIp(address)) {
            return null;
        }

        try {
            NvHTTP http = new NvHTTP(address, idManager.getUniqueId(),
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

    // Just try to establish a TCP connection to speculatively detect a running
    // GFE server
    private boolean fastPollIp(String address) {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(address, NvHTTP.HTTPS_PORT), FAST_POLL_TIMEOUT);
            s.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void startFastPollThread(final String address, final boolean[] info) {
        Thread t = new Thread() {
            @Override
            public void run() {
                boolean pollRes = fastPollIp(address);

                synchronized (info) {
                    info[0] = true; // Done
                    info[1] = pollRes; // Polling result

                    info.notify();
                }
            }
        };
        t.setName("Fast Poll - "+address);
        t.start();
    }

    private ComputerDetails.Reachability fastPollPc(final String localAddress, final String remoteAddress) throws InterruptedException {
        final boolean[] remoteInfo = new boolean[2];
        final boolean[] localInfo = new boolean[2];

        startFastPollThread(localAddress, localInfo);
        startFastPollThread(remoteAddress, remoteInfo);

        // Check local first
        synchronized (localInfo) {
            while (!localInfo[0]) {
                localInfo.wait(500);
            }

            if (localInfo[1]) {
                return ComputerDetails.Reachability.LOCAL;
            }
        }

        // Now remote
        synchronized (remoteInfo) {
            while (!remoteInfo[0]) {
                remoteInfo.wait(500);
            }

            if (remoteInfo[1]) {
                return ComputerDetails.Reachability.REMOTE;
            }
        }

        return ComputerDetails.Reachability.OFFLINE;
    }

    private static boolean isAddressLikelyLocal(String str) {
        try {
            // This will tend to be wrong for IPv6 but falling back to
            // remote will be fine in that case. For IPv4, it should be
            // pretty accurate due to NAT prevalence.
            InetAddress addr = InetAddress.getByName(str);
            return addr.isSiteLocalAddress() || addr.isLinkLocalAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return false;
        }
    }

    private ReachabilityTuple pollForReachability(ComputerDetails details) throws InterruptedException {
        ComputerDetails polledDetails;
        ComputerDetails.Reachability reachability;

        if (details.localAddress.equals(details.remoteAddress)) {
            reachability = isAddressLikelyLocal(details.localAddress) ?
                    ComputerDetails.Reachability.LOCAL : ComputerDetails.Reachability.REMOTE;
        }
        else {
            // Do a TCP-level connection to the HTTP server to see if it's listening
            LimeLog.info("Starting fast poll for "+details.name+" ("+details.localAddress +", "+details.remoteAddress +")");
            reachability = fastPollPc(details.localAddress, details.remoteAddress);
            LimeLog.info("Fast poll for "+details.name+" returned "+reachability.toString());

            // If no connection could be established to either IP address, there's nothing we can do
            if (reachability == ComputerDetails.Reachability.OFFLINE) {
                return null;
            }
        }

        boolean localFirst = (reachability == ComputerDetails.Reachability.LOCAL);

        if (localFirst) {
            polledDetails = tryPollIp(details, details.localAddress);
        }
        else {
            polledDetails = tryPollIp(details, details.remoteAddress);
        }

        String reachableAddr = null;
        if (polledDetails == null && !details.localAddress.equals(details.remoteAddress)) {
            // Failed, so let's try the fallback
            if (!localFirst) {
                polledDetails = tryPollIp(details, details.localAddress);
            }
            else {
                polledDetails = tryPollIp(details, details.remoteAddress);
            }

            if (polledDetails != null) {
                // The fallback poll worked
                reachableAddr = !localFirst ? details.localAddress : details.remoteAddress;
            }
        }
        else if (polledDetails != null) {
            reachableAddr = localFirst ? details.localAddress : details.remoteAddress;
        }

        if (reachableAddr == null) {
            return null;
        }

        // If both addresses are the same, guess whether we're local based on
        // IP address heuristics.
        if (reachableAddr.equals(polledDetails.localAddress) &&
                reachableAddr.equals(polledDetails.remoteAddress)) {
            polledDetails.reachability = isAddressLikelyLocal(reachableAddr) ?
                    ComputerDetails.Reachability.LOCAL : ComputerDetails.Reachability.REMOTE;
        }
        else if (polledDetails.remoteAddress.equals(reachableAddr)) {
            polledDetails.reachability = ComputerDetails.Reachability.REMOTE;
        }
        else if (polledDetails.localAddress.equals(reachableAddr)) {
            polledDetails.reachability = ComputerDetails.Reachability.LOCAL;
        }
        else {
            polledDetails.reachability = ComputerDetails.Reachability.UNKNOWN;
        }

        return new ReachabilityTuple(polledDetails, reachableAddr);
    }

    private boolean pollComputer(ComputerDetails details) throws InterruptedException {
        ReachabilityTuple initialReachTuple = pollForReachability(details);
        if (initialReachTuple == null) {
            return false;
        }

        if (initialReachTuple.computer.reachability == ComputerDetails.Reachability.UNKNOWN) {
            // Neither IP address reported in the serverinfo response was the one we used.
            // Poll again to see if we can contact this machine on either of its reported addresses.
            ReachabilityTuple confirmationReachTuple = pollForReachability(initialReachTuple.computer);
            if (confirmationReachTuple == null) {
                // Neither of those seem to work, so we'll hold onto the address that did work
                initialReachTuple.computer.localAddress = initialReachTuple.reachableAddress;
                initialReachTuple.computer.reachability = ComputerDetails.Reachability.LOCAL;
            }
            else {
                // We got it on one of the returned addresses; replace the original reach tuple
                // with the new one
                initialReachTuple = confirmationReachTuple;
            }
        }

        // Save some details about the old state of the PC that we may wish
        // to restore later.
        String savedMacAddress = details.macAddress;
        String savedLocalAddress = details.localAddress;
        String savedRemoteAddress = details.remoteAddress;

        // If we got here, it's reachable
        details.update(initialReachTuple.computer);

        // If the new MAC address is empty, restore the old one (workaround for GFE bug)
        if (details.macAddress.equals("00:00:00:00:00:00") && savedMacAddress != null) {
            LimeLog.info("MAC address was empty; using existing value: "+savedMacAddress);
            details.macAddress = savedMacAddress;
        }

        // We never want to lose IP addresses by polling server info. If we get a poll back
        // where localAddress == remoteAddress but savedLocalAddress != savedRemoteAddress,
        // then we've lost an address in the polling and we should restore the one that's missing.
        if (details.localAddress.equals(details.remoteAddress) &&
                !savedLocalAddress.equals(savedRemoteAddress)) {
            if (details.localAddress.equals(savedLocalAddress)) {
                // Local addresses are identical, so put the old remote address back
                details.remoteAddress = savedRemoteAddress;
            }
            else if (details.remoteAddress.equals(savedRemoteAddress)) {
                // Remote addresses are identical, so put the old local address back
                details.localAddress = savedLocalAddress;
            }
            else {
                // Neither IP address match. Let's restore the remote address to be safe.
                details.remoteAddress = savedRemoteAddress;
            }

            // Now update the reachability so the correct address is used
            if (details.localAddress.equals(initialReachTuple.reachableAddress)) {
                details.reachability = ComputerDetails.Reachability.LOCAL;
            }
            else {
                details.reachability = ComputerDetails.Reachability.REMOTE;
            }
        }

        return true;
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
            addTuple(computer, true);
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
        private final ComputerDetails computer;
        private final Object pollEvent = new Object();
        private boolean receivedAppList = false;

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
                    if (receivedAppList) {
                        // If we've already reported an app list successfully,
                        // wait the full polling period
                        pollEvent.wait(APPLIST_POLLING_PERIOD_MS);
                    }
                    else {
                        // If we've failed to get an app list so far, retry much earlier
                        pollEvent.wait(APPLIST_FAILED_POLLING_RETRY_MS);
                    }
                }
            } catch (InterruptedException e) {
                return false;
            }

            return thread != null && !thread.isInterrupted();
        }

        private PollingTuple getPollingTuple(ComputerDetails details) {
            synchronized (pollingTuples) {
                for (PollingTuple tuple : pollingTuples) {
                    if (details.uuid.equals(tuple.computer.uuid)) {
                        return tuple;
                    }
                }
            }

            return null;
        }

        public void start() {
            thread = new Thread() {
                @Override
                public void run() {
                    int emptyAppListResponses = 0;
                    do {
                        // Can't poll if it's not online
                        if (computer.state != ComputerDetails.State.ONLINE) {
                            if (listener != null) {
                                listener.notifyComputerUpdated(computer);
                            }
                            continue;
                        }

                        // Can't poll if there's no UUID yet
                        if (computer.uuid == null) {
                            continue;
                        }

                        PollingTuple tuple = getPollingTuple(computer);

                        try {
                            NvHTTP http = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer), idManager.getUniqueId(),
                                    null, PlatformBinding.getCryptoProvider(ComputerManagerService.this));

                            String appList;
                            if (tuple != null) {
                                // If we're polling this machine too, grab the network lock
                                // while doing the app list request to prevent other requests
                                // from being issued in the meantime.
                                synchronized (tuple.networkLock) {
                                    appList = http.getAppListRaw();
                                }
                            }
                            else {
                                // No polling is happening now, so we just call it directly
                                appList = http.getAppListRaw();
                            }

                            List<NvApp> list = NvHTTP.getAppListByReader(new StringReader(appList));
                            if (list.isEmpty()) {
                                LimeLog.warning("Empty app list received from "+computer.uuid);

                                // The app list might actually be empty, so if we get an empty response a few times
                                // in a row, we'll go ahead and believe it.
                                emptyAppListResponses++;
                            }
                            if (appList != null && !appList.isEmpty() &&
                                    (!list.isEmpty() || emptyAppListResponses >= EMPTY_LIST_THRESHOLD)) {
                                // Open the cache file
                                OutputStream cacheOut = null;
                                try {
                                    cacheOut = CacheHelper.openCacheFileForOutput(getCacheDir(), "applist", computer.uuid.toString());
                                    CacheHelper.writeStringToOutputStream(cacheOut, appList);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } finally {
                                    try {
                                        if (cacheOut != null) {
                                            cacheOut.close();
                                        }
                                    } catch (IOException ignored) {}
                                }

                                // Reset empty count if it wasn't empty this time
                                if (!list.isEmpty()) {
                                    emptyAppListResponses = 0;
                                }

                                // Update the computer
                                computer.rawAppList = appList;
                                receivedAppList = true;

                                // Notify that the app list has been updated
                                // and ensure that the thread is still active
                                if (listener != null && thread != null) {
                                    listener.notifyComputerUpdated(computer);
                                }
                            }
                            else if (appList == null || appList.isEmpty()) {
                                LimeLog.warning("Null app list received from "+computer.uuid);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (XmlPullParserException e) {
                            e.printStackTrace();
                        }
                    } while (waitPollingDelay());
                }
            };
            thread.setName("App list polling thread for " + computer.localAddress);
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
    public final ComputerDetails computer;
    public final Object networkLock;
    public long lastSuccessfulPollMs;

    public PollingTuple(ComputerDetails computer, Thread thread) {
        this.computer = computer;
        this.thread = thread;
        this.networkLock = new Object();
    }
}

class ReachabilityTuple {
    public final String reachableAddress;
    public final ComputerDetails computer;

    public ReachabilityTuple(ComputerDetails computer, String reachableAddress) {
        this.computer = computer;
        this.reachableAddress = reachableAddress;
    }
}