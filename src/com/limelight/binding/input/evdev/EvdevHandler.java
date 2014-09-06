package com.limelight.binding.input.evdev;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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

				ByteBuffer buffer = ByteBuffer.allocate(EvdevEvent.EVDEV_MAX_EVENT_SIZE).order(ByteOrder.nativeOrder());

				try {
					int deltaX = 0;
					int deltaY = 0;
					byte deltaScroll = 0;

					while (!isInterrupted() && !shutdown) {
						EvdevEvent event = EvdevReader.read(fd, buffer);
						if (event == null) {
							return;
						}

						switch (event.type)
						{
						case EvdevEvent.EV_SYN:
							if (deltaX != 0 || deltaY != 0) {
								listener.mouseMove(deltaX, deltaY);
								deltaX = deltaY = 0;
							}
							if (deltaScroll != 0) {
								listener.mouseScroll(deltaScroll);
								deltaScroll = 0;
							}
							break;

						case EvdevEvent.EV_REL:
							switch (event.code)
							{
							case EvdevEvent.REL_X:
								deltaX = event.value;
								break;
							case EvdevEvent.REL_Y:
								deltaY = event.value;
								break;
							case EvdevEvent.REL_WHEEL:
								deltaScroll = (byte) event.value;
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
								
							case EvdevEvent.BTN_SIDE:
							case EvdevEvent.BTN_EXTRA:
							case EvdevEvent.BTN_FORWARD:
							case EvdevEvent.BTN_BACK:
							case EvdevEvent.BTN_TASK:
								// Other unhandled mouse buttons
								break;
								
							default:
								// We got some unrecognized button. This means
								// someone is trying to use the other device in this
								// "combination" input device. We'll try to handle
								// it via keyboard, but we're not going to disconnect
								// if we can't
								short keyCode = EvdevTranslator.translateEvdevKeyCode(event.code);
								if (keyCode != 0) {
									listener.keyboardEvent(event.value != 0, keyCode);
								}
								break;
							}
							break;
							
						case EvdevEvent.EV_MSC:
							break;
							
						default:
							// We got some unrecognized event. This means
							// someone is trying to use the other device in this
							// "combination" input device. We'll disconnect now
							return;
						}
					}
				} finally {
					// Release our grab
					EvdevReader.ungrab(fd);
				}
			} finally {
				// Close the file
				LimeLog.warning("Evdev handler is terminating for: "+absolutePath);
				EvdevReader.close(fd);
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
