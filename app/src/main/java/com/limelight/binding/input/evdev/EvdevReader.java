package com.limelight.binding.input.evdev;

import android.os.Build;

import java.nio.ByteBuffer;
import java.util.Locale;

import com.limelight.LimeLog;

public class EvdevReader {
    static {
        System.loadLibrary("evdev_reader");
    }

    public static void patchSeLinuxPolicies() {
        //
        // FIXME: We REALLY shouldn't being changing permissions on the input devices like this.
        // We should probably do something clever with a separate daemon and talk via a localhost
        // socket. We don't return the SELinux policies back to default after we're done which I feel
        // bad about, but we do chmod the input devices back so I don't think any additional attack surface
        // remains opened after streaming other than listing the /dev/input directory which you wouldn't
        // normally be able to do with SELinux enforcing on Lollipop.
        //
        // We need to modify SELinux policies to allow us to capture input devices on Lollipop and possibly other
        // more restrictive ROMs. Per Chainfire's SuperSU documentation, the supolicy binary is provided on
        // 4.4 and later to do live SELinux policy changes.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            EvdevShell shell = EvdevShell.getInstance();
            shell.runCommand("supolicy --live \"allow untrusted_app input_device dir { open getattr read search }\" " +
                    "\"allow untrusted_app input_device chr_file { open read write ioctl }\"");
        }
    }

    // Requires root to chmod /dev/input/eventX
    public static void setPermissions(String[] files, int octalPermissions) {
        EvdevShell shell = EvdevShell.getInstance();

        for (String file : files) {
            shell.runCommand(String.format((Locale)null, "chmod %o %s", octalPermissions, file));
        }
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
