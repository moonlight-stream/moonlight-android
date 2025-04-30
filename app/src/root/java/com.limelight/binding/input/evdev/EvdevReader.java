package com.limelight.binding.input.evdev;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.limelight.LimeLog;

public class EvdevReader {
    private static void readAll(InputStream in, ByteBuffer bb) throws IOException {
        byte[] buf = bb.array();
        int ret;
        int offset = 0;

        while (offset < buf.length) {
            ret = in.read(buf, offset, buf.length-offset);
            if (ret <= 0) {
                throw new IOException("Read failed: "+ret);
            }

            offset += ret;
        }
    }

    // Takes a byte buffer to use to read the output into.
    // This buffer MUST be in native byte order and at least
    // EVDEV_MAX_EVENT_SIZE bytes long.
    public static EvdevEvent read(InputStream input) throws IOException {
        ByteBuffer bb;
        int packetLength;

        // Read the packet length
        bb = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder());
        readAll(input, bb);
        packetLength = bb.getInt();

        if (packetLength < EvdevEvent.EVDEV_MIN_EVENT_SIZE) {
            LimeLog.warning("Short read: "+packetLength);
            return null;
        }

        // Read the rest of the packet
        bb = ByteBuffer.allocate(packetLength).order(ByteOrder.nativeOrder());
        readAll(input, bb);

        // Throw away the time stamp
        if (packetLength == EvdevEvent.EVDEV_MAX_EVENT_SIZE) {
            bb.getLong();
            bb.getLong();
        } else {
            bb.getInt();
            bb.getInt();
        }

        return new EvdevEvent(bb.getShort(), bb.getShort(), bb.getInt());
    }
}
