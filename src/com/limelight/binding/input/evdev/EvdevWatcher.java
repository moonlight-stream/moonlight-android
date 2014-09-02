package com.limelight.binding.input.evdev;

import java.io.File;
import java.util.HashMap;

import android.os.FileObserver;

public class EvdevWatcher {
	private static final String PATH = "/dev/input";
	private static final String REQUIRED_FILE_PREFIX = "event";
	
	private HashMap<String, EvdevHandler> handlers = new HashMap<String, EvdevHandler>();
	private boolean shutdown = false;
	private EvdevListener listener;
	
	private FileObserver observer = new FileObserver(PATH, FileObserver.CREATE | FileObserver.DELETE) {
		@Override
		public void onEvent(int event, String fileName) {
			if (!fileName.startsWith(REQUIRED_FILE_PREFIX)) {
				return;
			}
			
			synchronized (handlers) {
				if (shutdown) {
					return;
				}
				
				if ((event & FileObserver.CREATE) != 0) {
					EvdevHandler handler = new EvdevHandler(PATH + "/" + fileName, listener);
					handler.start();
					
					handlers.put(fileName, handler);
				}
				
				if ((event & FileObserver.DELETE) != 0) {
					EvdevHandler handler = handlers.get(fileName);
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
		// Start watching for new files
		observer.startWatching();
		
		// Rundown existing files and generate synthetic events
		File devInputDir = new File(PATH);
		File[] files = devInputDir.listFiles();
		for (File f : files) {
			observer.onEvent(FileObserver.CREATE, f.getName());
		}
	}
	
	public void shutdown() {
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
