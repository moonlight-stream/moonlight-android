package com.limelight.binding.input.evdev;

import java.nio.ByteBuffer;

import com.limelight.LimeLog;

public class EvdevHandler {

	private String absolutePath;
	private EvdevListener listener;
	private boolean shutdown = false;
	
	private Thread handlerThread = new Thread() {
		@Override
		public void run() {
			// All the finally blocks here make this code look like a mess
			// but it's important that we get this right to avoid causing
			// system-wide input problems.
			
			// Modify permissions to allow us access
			if (!EvdevReader.setPermissions(absolutePath, 0666)) {
				LimeLog.warning("Unable to chmod "+absolutePath);
				return;
			}
			
			try {
				// Open the /dev/input/eventX file
				int fd = EvdevReader.open(absolutePath);
				if (fd == -1) {
					LimeLog.warning("Unable to open "+absolutePath);
					return;
				}
				
				try {
					// Check if it's a mouse
					if (!EvdevReader.isMouse(fd)) {
						// We only handle mice
						return;
					}
					
					// Grab it for ourselves
					if (!EvdevReader.grab(fd)) {
						LimeLog.warning("Unable to grab "+absolutePath);
						return;
					}
					
					LimeLog.info("Grabbed device for raw mouse input: "+absolutePath);
					
					ByteBuffer buffer = ByteBuffer.allocate(EvdevEvent.EVDEV_MAX_EVENT_SIZE);
					
					try {
						while (!isInterrupted() && !shutdown) {
							EvdevEvent event = EvdevReader.read(fd, buffer);
							if (event == null) {
								return;
							}
							
							switch (event.type)
							{
							case EvdevEvent.EV_SYN:
								// Do nothing
								break;
								
							case EvdevEvent.EV_REL:
								switch (event.code)
								{
								case EvdevEvent.REL_X:
									listener.mouseMove(event.value, 0);
									break;
								case EvdevEvent.REL_Y:
									listener.mouseMove(0, event.value);
									break;
								}
								break;
								
							case EvdevEvent.EV_KEY:
								switch (event.code)
								{
								case EvdevEvent.BTN_LEFT:
									listener.mouseButtonEvent(EvdevListener.BUTTON_LEFT,
											event.value != 0);
									break;
								case EvdevEvent.BTN_MIDDLE:
									listener.mouseButtonEvent(EvdevListener.BUTTON_MIDDLE,
											event.value != 0);
									break;
								case EvdevEvent.BTN_RIGHT:
									listener.mouseButtonEvent(EvdevListener.BUTTON_RIGHT,
											event.value != 0);
									break;
								}
							}
						}
					} finally {
						// Release our grab
						EvdevReader.ungrab(fd);
					}
				} finally {
					// Close the file
					EvdevReader.close(fd);
				}
			} finally {
				// Set permissions back
				EvdevReader.setPermissions(absolutePath, 0066);
			}
		}
	};
	
	public EvdevHandler(String absolutePath, EvdevListener listener) {
		this.absolutePath = absolutePath;
		this.listener = listener;
	}
	
	public void start() {
		handlerThread.start();
	}
	
	public void stop() {
		shutdown = true;
		handlerThread.interrupt();
		
		try {
			handlerThread.join();
		} catch (InterruptedException e) {}
	}
	
	public void notifyDeleted() {
		stop();
	}
}
