package com.limelight.binding.input.evdev;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import com.limelight.LimeLog;

import android.os.FileObserver;

@SuppressWarnings("ALL")
public class EvdevWatcher {
    private static final String PATH = "/dev/input";
    private static final String REQUIRED_FILE_PREFIX = "event";

    private final HashMap<String, EvdevHandler> handlers = new HashMap<String, EvdevHandler>();
    private boolean shutdown = false;
    private boolean init = false;
    private boolean ungrabbed = false;
    private EvdevListener listener;
    private Thread startThread;

    private static boolean patchedSeLinuxPolicies = false;

    private FileObserver observer = new FileObserver(PATH, FileObserver.CREATE | FileObserver.DELETE) {
        @Override
        public void onEvent(int event, String fileName) {
            if (fileName == null) {
                return;
            }

            if (!fileName.startsWith(REQUIRED_FILE_PREFIX)) {
                return;
            }

            synchronized (handlers) {
                if (shutdown) {
                    return;
                }

                if ((event & FileObserver.CREATE) != 0) {
                    LimeLog.info("Starting evdev handler for "+fileName);

                    if (!init) {
                        // If this a real new device, update permissions again so we can read it
                        EvdevReader.setPermissions(new String[]{PATH + "/" + fileName}, 0666);
                    }

                    EvdevHandler handler = new EvdevHandler(PATH + "/" + fileName, listener);

                    // If we're ungrabbed now, don't start the handler
                    if (!ungrabbed) {
                        handler.start();
                    }

                    handlers.put(fileName, handler);
                }

                if ((event & FileObserver.DELETE) != 0) {
                    LimeLog.info("Halting evdev handler for "+fileName);

                    EvdevHandler handler = handlers.remove(fileName);
                    if (handler != null) {
                        handler.notifyDeleted();
                    }
                }
            }
        }
    };

    public EvdevWatcher(EvdevListener listener) {
        this.listener = listener;
    }

    private File[] rundownWithPermissionsChange(int newPermissions) {
        // Rundown existing files
        File devInputDir = new File(PATH);
        File[] files = devInputDir.listFiles();
        if (files == null) {
            return new File[0];
        }

        // Set desired permissions
        String[] filePaths = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            filePaths[i] = files[i].getAbsolutePath();
        }
        EvdevReader.setPermissions(filePaths, newPermissions);

        return files;
    }

    public void ungrabAll() {
        synchronized (handlers) {
            // Note that we're ungrabbed for now
            ungrabbed = true;

            // Stop all handlers
            for (EvdevHandler handler : handlers.values()) {
                handler.stop();
            }
        }
    }

    public void regrabAll() {
        synchronized (handlers) {
            // We're regrabbing everything now
            ungrabbed = false;

            for (Map.Entry<String, EvdevHandler> entry : handlers.entrySet()) {
                // We need to recreate each entry since we can't reuse a stopped one
                entry.setValue(new EvdevHandler(PATH + "/" + entry.getKey(), listener));
                entry.getValue().start();
            }
        }
    }

    public void start() {
        startThread = new Thread() {
            @Override
            public void run() {
                // Initialize the root shell
                EvdevShell.getInstance().startShell();

                // Patch SELinux policies (if needed)
                if (!patchedSeLinuxPolicies) {
                    EvdevReader.patchSeLinuxPolicies();
                    patchedSeLinuxPolicies = true;
                }

                // List all files and allow us access
                File[] files = rundownWithPermissionsChange(0666);

                init = true;
                for (File f : files) {
                    observer.onEvent(FileObserver.CREATE, f.getName());
                }

                // Done with initial onEvent calls
                init = false;

                // Start watching for new files
                observer.startWatching();

                synchronized (startThread) {
                    // Wait to be awoken again by shutdown()
                    try {
                        startThread.wait();
                    } catch (InterruptedException e) {}
                }

                // Giveup eventX permissions
                rundownWithPermissionsChange(0660);

                // Kill the root shell
                try {
                    EvdevShell.getInstance().stopShell();
                } catch (InterruptedException e) {}
            }
        };
        startThread.start();
    }

    public void shutdown() {
        // Let start thread cleanup on it's own sweet time
        synchronized (startThread) {
            startThread.notify();
        }

        // Stop the observer
        observer.stopWatching();

        synchronized (handlers) {
            // Stop creating new handlers
            shutdown = true;

            // If we've already ungrabbed, there's nothing else to do
            if (ungrabbed) {
                return;
            }

            // Stop all handlers
            for (EvdevHandler handler : handlers.values()) {
                handler.stop();
            }
        }
    }
}
