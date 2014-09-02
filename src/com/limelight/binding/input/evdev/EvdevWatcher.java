package com.limelight.binding.input.evdev;

import java.io.File;
import java.util.HashMap;

import com.limelight.LimeLog;

import android.os.FileObserver;

public class EvdevWatcher {
	private static final String PATH = "/dev/input";
	private static final String REQUIRED_FILE_PREFIX = "event";
	
	private HashMap<String, EvdevHandler> handlers = new HashMap<String, EvdevHandler>();
	private boolean shutdown = false;
	private boolean init = false;
	private EvdevListener listener;
	private Thread startThread;
	
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
						EvdevReader.setPermissions(0666);
					}
					
					EvdevHandler handler = new EvdevHandler(PATH + "/" + fileName, listener);
					handler.start();
					
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
	
	public void start() {
		startThread = new Thread() {
			@Override
			public void run() {
				// Get permissions to read the eventX files
				EvdevReader.setPermissions(0666);
				init = true;
				
				// Rundown existing files and generate synthetic events
				File devInputDir = new File(PATH);
				File[] files = devInputDir.listFiles();
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
				EvdevReader.setPermissions(0066);
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
			
			// Stop all handlers
			for (EvdevHandler handler : handlers.values()) {
				handler.stop();
			}
		}
	}
}
