package com.limelight.computers;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.limelight.LimeLog;
import com.limelight.binding.PlatformBinding;
import com.limelight.discovery.DiscoveryService;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;
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
            }
        } catch (InterruptedException e) {
            releaseLocalDatabaseReference();
            throw e;
        } finally {
            activePolls.decrementAndGet();
        }

        // If it's online, update our persistent state
        if (details.state == ComputerDetails.State.ONLINE) {
            ComputerDetails existingComputer = dbManager.getComputerByUUID(details.uuid);

            // Check if it's in the database because it could have been
            // removed after this was issued
            if (!newPc && existingComputer == null) {
                // It's gone
                releaseLocalDatabaseReference();
                return false;
            }

            // If we already have an entry for this computer in the DB, we must
            // combine the existing data with this new data (which may be partially available
            // due to detecting the PC via mDNS) without the saved external address. If we
            // write to the DB without doing this first, we can overwrite our existing data.
            if (existingComputer != null) {
                existingComputer.update(details);
                dbManager.updateComputer(existingComputer);
            }
            else {
                dbManager.updateComputer(details);
            }
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
        t.setName("Polling thread for " + tuple.computer.name);
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

        public boolean addComputerBlocking(ComputerDetails fakeDetails) {
            return ComputerManagerService.this.addComputerBlocking(fakeDetails);
        }

        public void removeComputer(ComputerDetails computer) {
            ComputerManagerService.this.removeComputer(computer);
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

        public ComputerDetails getComputer(String uuid) {
            synchronized (pollingTuples) {
                for (PollingTuple tuple : pollingTuples) {
                    if (uuid.equals(tuple.computer.uuid)) {
                        return tuple.computer;
                    }
                }
            }

            return null;
        }

        public void invalidateStateForComputer(String uuid) {
            synchronized (pollingTuples) {
                for (PollingTuple tuple : pollingTuples) {
                    if (uuid.equals(tuple.computer.uuid)) {
                        // We need the network lock to prevent a concurrent poll
                        // from wiping this change out
                        synchronized (tuple.networkLock) {
                            tuple.computer.state = ComputerDetails.State.UNKNOWN;
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
                ComputerDetails details = new ComputerDetails();

                // Populate the computer template with mDNS info
                if (computer.getLocalAddress() != null) {
                    details.localAddress = computer.getLocalAddress().getHostAddress();

                    // Since we're on the same network, we can use STUN to find
                    // our WAN address, which is also very likely the WAN address
                    // of the PC. We can use this later to connect remotely.
                    if (computer.getLocalAddress() instanceof Inet4Address) {
                        details.remoteAddress = NvConnection.findExternalAddressForMdns("stun.moonlight-stream.org", 3478);
                    }
                }
                if (computer.getIpv6Address() != null) {
                    details.ipv6Address = computer.getIpv6Address().getHostAddress();
                }

                // Kick off a serverinfo poll on this machine
                if (!addComputerBlocking(details)) {
                    LimeLog.warning("Auto-discovered PC failed to respond: "+details);
                }
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

    private void addTuple(ComputerDetails details) {
        synchronized (pollingTuples) {
            for (PollingTuple tuple : pollingTuples) {
                // Check if this is the same computer
                if (tuple.computer.uuid.equals(details.uuid)) {
                    // Update the saved computer with potentially new details
                    tuple.computer.update(details);

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

    public boolean addComputerBlocking(ComputerDetails fakeDetails) {
        // Block while we try to fill the details
        try {
            // We cannot use runPoll() here because it will attempt to persist the state of the machine
            // in the database, which would be bad because we don't have our pinned cert loaded yet.
            if (pollComputer(fakeDetails)) {
                // See if we have record of this PC to pull its pinned cert
                synchronized (pollingTuples) {
                    for (PollingTuple tuple : pollingTuples) {
                        if (tuple.computer.uuid.equals(fakeDetails.uuid)) {
                            fakeDetails.serverCert = tuple.computer.serverCert;
                            break;
                        }
                    }
                }

                // Poll again, possibly with the pinned cert, to get accurate pairing information.
                // This will insert the host into the database too.
                runPoll(fakeDetails, true, 0);
            }
        } catch (InterruptedException e) {
            return false;
        }

        // If the machine is reachable, it was successful
        if (fakeDetails.state == ComputerDetails.State.ONLINE) {
            LimeLog.info("New PC ("+fakeDetails.name+") is UUID "+fakeDetails.uuid);

            // Start a polling thread for this machine
            addTuple(fakeDetails);
            return true;
        }
        else {
            return false;
        }
    }

    public void removeComputer(ComputerDetails computer) {
        if (!getLocalDatabaseReference()) {
            return;
        }

        // Remove it from the database
        dbManager.deleteComputer(computer);

        synchronized (pollingTuples) {
            // Remove the computer from the computer list
            for (PollingTuple tuple : pollingTuples) {
                if (tuple.computer.uuid.equals(computer.uuid)) {
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
            NvHTTP http = new NvHTTP(address, idManager.getUniqueId(), details.serverCert,
                    PlatformBinding.getCryptoProvider(ComputerManagerService.this));

            ComputerDetails newDetails = http.getComputerDetails();

            // Check if this is the PC we expected
            if (newDetails.uuid == null) {
                LimeLog.severe("Polling returned no UUID!");
                return null;
            }
            // details.uuid can be null on initial PC add
            else if (details.uuid != null && !details.uuid.equals(newDetails.uuid)) {
                // We got the wrong PC!
                LimeLog.info("Polling returned the wrong PC!");
                return null;
            }

            // Set the new active address
            newDetails.activeAddress = address;

            return newDetails;
        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Just try to establish a TCP connection to speculatively detect a running
    // GFE server
    private boolean fastPollIp(String address) {
        if (address == null) {
            // Don't bother if our address is null
            return false;
        }

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

    private String fastPollPc(final String localAddress, final String remoteAddress, final String manualAddress, final String ipv6Address) throws InterruptedException {
        final boolean[] remoteInfo = new boolean[2];
        final boolean[] localInfo = new boolean[2];
        final boolean[] manualInfo = new boolean[2];
        final boolean[] ipv6Info = new boolean[2];

        startFastPollThread(localAddress, localInfo);
        startFastPollThread(remoteAddress, remoteInfo);
        startFastPollThread(manualAddress, manualInfo);
        startFastPollThread(ipv6Address, ipv6Info);

        // Check local first
        synchronized (localInfo) {
            while (!localInfo[0]) {
                localInfo.wait(500);
            }

            if (localInfo[1]) {
                return localAddress;
            }
        }

        // Now manual
        synchronized (manualInfo) {
            while (!manualInfo[0]) {
                manualInfo.wait(500);
            }

            if (manualInfo[1]) {
                return manualAddress;
            }
        }

        // Now remote IPv4
        synchronized (remoteInfo) {
            while (!remoteInfo[0]) {
                remoteInfo.wait(500);
            }

            if (remoteInfo[1]) {
                return remoteAddress;
            }
        }

        // Now global IPv6
        synchronized (ipv6Info) {
            while (!ipv6Info[0]) {
                ipv6Info.wait(500);
            }

            if (ipv6Info[1]) {
                return ipv6Address;
            }
        }

        return null;
    }

    private boolean pollComputer(ComputerDetails details) throws InterruptedException {
        ComputerDetails polledDetails;

        // Do a TCP-level connection to the HTTP server to see if it's listening.
        // Do not write this address to details.activeAddress because:
        // a) it's only a candidate and may be wrong (multiple PCs behind a single router)
        // b) if it's null, it will be unexpectedly nulling the activeAddress of a possibly online PC
        LimeLog.info("Starting fast poll for "+details.name+" ("+details.localAddress +", "+details.remoteAddress +", "+details.manualAddress+", "+details.ipv6Address+")");
        String candidateAddress = fastPollPc(details.localAddress, details.remoteAddress, details.manualAddress, details.ipv6Address);
        LimeLog.info("Fast poll for "+details.name+" returned candidate address: "+candidateAddress);

        // If no connection could be established to either IP address, there's nothing we can do
        if (candidateAddress == null) {
            return false;
        }

        // Try using the active address from fast-poll
        polledDetails = tryPollIp(details, candidateAddress);
        if (polledDetails == null) {
            // If that failed, try all unique addresses except what we've
            // already tried
            HashSet<String> uniqueAddresses = new HashSet<>();
            uniqueAddresses.add(details.localAddress);
            uniqueAddresses.add(details.manualAddress);
            uniqueAddresses.add(details.remoteAddress);
            uniqueAddresses.add(details.ipv6Address);
            for (String addr : uniqueAddresses) {
                if (addr == null || addr.equals(candidateAddress)) {
                    continue;
                }
                polledDetails = tryPollIp(details, addr);
                if (polledDetails != null) {
                    break;
                }
            }
        }

        if (polledDetails != null) {
            details.update(polledDetails);
            return true;
        }
        else {
            return false;
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
                        // Can't poll if it's not online or paired
                        if (computer.state != ComputerDetails.State.ONLINE ||
                                computer.pairState != PairingManager.PairState.PAIRED) {
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
                                    computer.serverCert, PlatformBinding.getCryptoProvider(ComputerManagerService.this));

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
                            if (!appList.isEmpty() &&
                                    (!list.isEmpty() || emptyAppListResponses >= EMPTY_LIST_THRESHOLD)) {
                                // Open the cache file
                                OutputStream cacheOut = null;
                                try {
                                    cacheOut = CacheHelper.openCacheFileForOutput(getCacheDir(), "applist", computer.uuid);
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
                            else if (appList.isEmpty()) {
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
            thread.setName("App list polling thread for " + computer.name);
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