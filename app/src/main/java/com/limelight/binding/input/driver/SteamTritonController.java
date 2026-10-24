package com.limelight.binding.input.driver;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.os.SystemClock;

import com.limelight.LimeLog;

import java.util.ArrayList;

/**
 * USB driver for the 2026 Steam Controller (Valve codename "Triton"), connected either
 * directly over USB-C or through the wireless puck.
 *
 * Out of the box the controller behaves like a keyboard and mouse ("lizard mode"), so Android
 * never exposes it as a gamepad. This driver claims the controller's HID interface, tells it
 * to leave lizard mode with a feature report, and parses its vendor-specific input reports.
 *
 * Bluetooth LE is handled by {@link SteamTritonBleController}.
 */
public class SteamTritonController extends AbstractSteamTritonController {
    private static final int PRODUCT_ID_WIRED = 0x1302;
    private static final int PRODUCT_ID_PUCK = 0x1304;

    // The puck exposes one HID interface per paired controller on interfaces 2 through 5.
    private static final int PUCK_FIRST_INTERFACE = 2;
    private static final int PUCK_LAST_INTERFACE = 5;

    // USB HID class requests
    private static final int HID_REQ_TYPE_SET = 0x21; // host-to-device, class, interface
    private static final int HID_REQ_SET_REPORT = 0x09;
    private static final int HID_REPORT_TYPE_OUTPUT = 2;
    private static final int HID_REPORT_TYPE_FEATURE = 3;
    private static final int HID_SUBCLASS_BOOT = 1; // Boot keyboard/mouse interfaces

    // The controller stops rumbling after ~50 ms without a new command, so we resend while active.
    private static final long RUMBLE_REFRESH_INTERVAL_MS = 40;

    private static class HidStream {
        final UsbInterface iface;
        final UsbEndpoint inEndpoint;
        final UsbEndpoint outEndpoint;

        HidStream(UsbInterface iface, UsbEndpoint inEndpoint, UsbEndpoint outEndpoint) {
            this.iface = iface;
            this.inEndpoint = inEndpoint;
            this.outEndpoint = outEndpoint;
        }
    }

    private final UsbDevice device;
    private final UsbDeviceConnection connection;
    private final ArrayList<HidStream> streams = new ArrayList<>();
    private final ArrayList<Thread> threads = new ArrayList<>();
    private final Object rumbleLock = new Object();

    private int rumbleLow, rumbleHigh;

    public static boolean canClaimDevice(UsbDevice device) {
        return device.getVendorId() == VALVE_VENDOR_ID &&
                (device.getProductId() == PRODUCT_ID_WIRED || device.getProductId() == PRODUCT_ID_PUCK);
    }

    public SteamTritonController(UsbDevice device, UsbDeviceConnection connection, int deviceId, UsbDriverListener listener) {
        super(deviceId, listener, device.getVendorId(), device.getProductId());
        this.device = device;
        this.connection = connection;
    }

    private boolean isGamepadInterface(UsbInterface iface) {
        if (device.getProductId() == PRODUCT_ID_PUCK) {
            return iface.getId() >= PUCK_FIRST_INTERFACE && iface.getId() <= PUCK_LAST_INTERFACE;
        }

        // On the wired controller, skip the boot keyboard and mouse interfaces used by lizard mode
        // and take the remaining HID interface(s), which carry the vendor-specific gamepad reports.
        return (iface.getInterfaceClass() == UsbConstants.USB_CLASS_HID ||
                iface.getInterfaceClass() == UsbConstants.USB_CLASS_VENDOR_SPEC) &&
                iface.getInterfaceSubclass() != HID_SUBCLASS_BOOT;
    }

    @Override
    public boolean start() {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            if (!isGamepadInterface(iface)) {
                continue;
            }

            UsbEndpoint in = null, out = null;
            for (int j = 0; j < iface.getEndpointCount(); j++) {
                UsbEndpoint endpoint = iface.getEndpoint(j);
                if (endpoint.getType() != UsbConstants.USB_ENDPOINT_XFER_INT) {
                    continue;
                }

                if (endpoint.getDirection() == UsbConstants.USB_DIR_IN && in == null) {
                    in = endpoint;
                }
                else if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT && out == null) {
                    out = endpoint;
                }
            }

            if (in == null) {
                continue;
            }

            // Force the claim to detach the kernel's keyboard/mouse emulation driver
            if (!connection.claimInterface(iface, true)) {
                LimeLog.warning("Failed to claim Steam Controller interface " + iface.getId());
                continue;
            }

            streams.add(new HidStream(iface, in, out));
        }

        if (streams.isEmpty()) {
            LimeLog.warning("No usable Steam Controller interfaces found");
            return false;
        }

        // Take the controller out of lizard mode before we start listening
        boolean lizardDisabled = false;
        for (HidStream stream : streams) {
            lizardDisabled |= disableLizardMode(stream);
        }
        if (!lizardDisabled) {
            LimeLog.warning("Unable to disable Steam Controller lizard mode");
            return false;
        }

        for (HidStream stream : streams) {
            Thread t = createInputThread(stream);
            threads.add(t);
            t.start();
        }

        Thread maintenance = createMaintenanceThread();
        threads.add(maintenance);
        maintenance.start();

        return true;
    }

    private Thread createInputThread(final HidStream stream) {
        return new Thread() {
            @Override
            public void run() {
                byte[] buffer = new byte[Math.max(64, stream.inEndpoint.getMaxPacketSize())];

                while (!isInterrupted() && !stopped) {
                    int res;

                    // There is no way to tell a failed device from an expired timeout, so we
                    // assume the device failed if the transfer fails before the timeout elapses.
                    do {
                        long lastMillis = SystemClock.uptimeMillis();
                        res = connection.bulkTransfer(stream.inEndpoint, buffer, buffer.length, 3000);

                        if (res == 0) {
                            res = -1;
                        }

                        if (res == -1 && SystemClock.uptimeMillis() - lastMillis < 1000) {
                            LimeLog.warning("Detected device I/O error");
                            SteamTritonController.this.stop();
                            break;
                        }
                    } while (res == -1 && !isInterrupted() && !stopped);

                    if (res == -1 || stopped) {
                        break;
                    }

                    processInputReport(buffer, res);
                }
            }
        };
    }

    private Thread createMaintenanceThread() {
        return new Thread() {
            @Override
            public void run() {
                try {
                    // Give the old InputDevice time to go away (if any) before we claim its
                    // controller number, as the Xbox drivers do.
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    return;
                }

                // Report that we're added _before_ reporting input
                notifyDeviceAdded();
                added = true;

                long lastLizardRefresh = SystemClock.uptimeMillis();
                while (!isInterrupted() && !stopped) {
                    try {
                        Thread.sleep(RUMBLE_REFRESH_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        return;
                    }

                    long now = SystemClock.uptimeMillis();
                    if (now - lastLizardRefresh >= LIZARD_REFRESH_INTERVAL_MS) {
                        lastLizardRefresh = now;
                        for (HidStream stream : streams) {
                            disableLizardMode(stream);
                        }
                    }

                    int low, high;
                    synchronized (rumbleLock) {
                        low = rumbleLow;
                        high = rumbleHigh;
                    }
                    if (low != 0 || high != 0) {
                        sendRumble(low, high);
                    }
                }
            }
        };
    }

    private boolean disableLizardMode(HidStream stream) {
        byte[] report = buildLizardModeOffReport();

        int res = connection.controlTransfer(HID_REQ_TYPE_SET, HID_REQ_SET_REPORT,
                (HID_REPORT_TYPE_FEATURE << 8) | FEATURE_REPORT_ID,
                stream.iface.getId(), report, report.length, 1000);
        if (res != report.length) {
            LimeLog.warning("Lizard mode feature report failed: " + res);
            return false;
        }

        return true;
    }

    private void sendRumble(int low, int high) {
        writeOutputReport(buildRumbleReport(low, high));
    }

    @Override
    protected void sendHaptic(byte[] report) {
        writeOutputReport(report);
    }

    private void writeOutputReport(byte[] report) {
        HidStream stream = streams.isEmpty() ? null : streams.get(0);
        if (stream == null || stopped) {
            return;
        }

        int res;
        if (stream.outEndpoint != null) {
            res = connection.bulkTransfer(stream.outEndpoint, report, report.length, 100);
        }
        else {
            res = connection.controlTransfer(HID_REQ_TYPE_SET, HID_REQ_SET_REPORT,
                    (HID_REPORT_TYPE_OUTPUT << 8) | (report[0] & 0xFF),
                    stream.iface.getId(), report, report.length, 100);
        }

        if (res != report.length) {
            LimeLog.warning("Output report transfer failed: " + res);
        }
    }

    @Override
    public void rumble(short lowFreqMotor, short highFreqMotor) {
        int low = lowFreqMotor & 0xFFFF;
        int high = highFreqMotor & 0xFFFF;

        synchronized (rumbleLock) {
            rumbleLow = low;
            rumbleHigh = high;
        }

        // Send immediately; the maintenance thread keeps it alive while non-zero
        sendRumble(low, high);
    }

    @Override
    public void stop() {
        if (stopped) {
            return;
        }

        // Cancel any rumble effects while the connection is still open
        synchronized (rumbleLock) {
            rumbleLow = 0;
            rumbleHigh = 0;
        }
        sendRumble(0, 0);

        releaseHeldInput();

        stopped = true;

        for (Thread t : threads) {
            t.interrupt();
        }
        threads.clear();

        connection.close();

        notifyDeviceRemoved();
    }
}
