package com.limelight.binding.input.evdev;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import com.limelight.LimeLog;

public class EvdevReader {
	static {
		System.loadLibrary("evdev_reader");
	}
	
	// Requires root to chmod /dev/input/eventX
	public static boolean setPermissions(String[] files, int octalPermissions) {		
		ProcessBuilder builder = new ProcessBuilder("su");
		
		try {
			Process p = builder.start();
			
			OutputStream stdin = p.getOutputStream();
			for (String file : files) {
				stdin.write(String.format("chmod %o %s\n", octalPermissions, file).getBytes("UTF-8"));
			}
			stdin.write("exit\n".getBytes("UTF-8"));
			stdin.flush();
			
			p.waitFor();
			p.destroy();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		return false;
	}
	
	// Returns the fd to be passed to other function or -1 on error
	public static native int open(String fileName);
	
	// Prevent other apps (including Android itself) from using the device while "grabbed"
	public static native boolean grab(int fd);
	public static native boolean ungrab(int fd);
	
	// Used for checking device capabilities
	public static native boolean hasRelAxis(int fd, short axis);
	public static native boolean hasAbsAxis(int fd, short axis);
	public static native boolean hasKey(int fd, short key);
	
	public static boolean isMouse(int fd) {
		// This is the same check that Android does in EventHub.cpp
		return hasRelAxis(fd, EvdevEvent.REL_X) &&
				hasRelAxis(fd, EvdevEvent.REL_Y) &&
				hasKey(fd, EvdevEvent.BTN_LEFT);
	}
	
	public static boolean isAlphaKeyboard(int fd) {
		// This is the same check that Android does in EventHub.cpp
		return hasKey(fd, EvdevEvent.KEY_Q);
	}
	
	public static boolean isGamepad(int fd) {
		return hasKey(fd, EvdevEvent.BTN_GAMEPAD);
	}
	
	// Returns the bytes read or -1 on error
	private static native int read(int fd, byte[] buffer);
	
	// Takes a byte buffer to use to read the output into.
	// This buffer MUST be in native byte order and at least
	// EVDEV_MAX_EVENT_SIZE bytes long.
	public static EvdevEvent read(int fd, ByteBuffer buffer) {
		int bytesRead = read(fd, buffer.array());
		if (bytesRead < 0) {
			LimeLog.warning("Failed to read: "+bytesRead);
			return null;
		}
		else if (bytesRead < EvdevEvent.EVDEV_MIN_EVENT_SIZE) {
			LimeLog.warning("Short read: "+bytesRead);
			return null;
		}
		
		buffer.limit(bytesRead);
		buffer.rewind();
		
		// Throw away the time stamp
		if (bytesRead == EvdevEvent.EVDEV_MAX_EVENT_SIZE) {
			buffer.getLong();
			buffer.getLong();
		} else {
			buffer.getInt();
			buffer.getInt();
		}
		
		return new EvdevEvent(buffer.getShort(), buffer.getShort(), buffer.getInt());
	}
	
	// Closes the fd from open()
	public static native int close(int fd);
}
