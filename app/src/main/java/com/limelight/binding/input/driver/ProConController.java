package com.limelight.binding.input.driver;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.os.SystemClock;

import com.limelight.LimeLog;
import com.limelight.nvstream.input.ControllerPacket;
import com.limelight.nvstream.jni.MoonBridge;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

public class ProConController extends AbstractController {

    private static final int PACKET_SIZE = 64;
    private static final byte[] RUMBLE_NEUTRAL = {0x00, 0x01, 0x40, 0x40};
    private static final byte[] RUMBLE = {0x74, (byte) 0xBE, (byte) 0xBD, 0x6F};
    private static final int FACTORY_IMU_CALIBRATION_OFFSET = 0x6020;
    private static final int FACTORY_LS_CALIBRATION_OFFSET = 0x603D;
    private static final int FACTORY_RS_CALIBRATION_OFFSET = 0x6046;
    private static final int USER_IMU_MAGIC_OFFSET = 0x8026;
    private static final int USER_IMU_CALIBRATION_OFFSET = 0x8028;
    private static final int USER_LS_MAGIC_OFFSET = 0x8010;
    private static final int USER_LS_CALIBRATION_OFFSET = 0x8012;
    private static final int USER_RS_MAGIC_OFFSET = 0x801B;
    private static final int USER_RS_CALIBRATION_OFFSET = 0x801D;
    private static final int IMU_CALIBRATION_LENGTH = 24;
    private static final int STICK_CALIBRATION_LENGTH = 9;
    private static final int COMMAND_RETRIES = 10;

    private final UsbDevice device;
    private final UsbDeviceConnection connection;
    private UsbEndpoint inEndpt, outEndpt;
    private Thread inputThread;
    private boolean stopped = false;
    private byte sendPacketCount = 0;
    private final int[][][] stickCalibration = new int[2][2][3]; // [stick][axis][min, center, max]
    private final float[][][] stickExtends = new float[2][2][2]; // Pre-calculated scale for each axis

    public static boolean canClaimDevice(UsbDevice device) {
        return (device.getVendorId() == 0x057e && device.getProductId() == 0x2009);
    }

    public ProConController(UsbDevice device, UsbDeviceConnection connection, int deviceId, UsbDriverListener listener) {
        super(deviceId, listener, device.getVendorId(), device.getProductId());
        this.device = device;
        this.connection = connection;
        this.type = MoonBridge.LI_CTYPE_NINTENDO;
        this.capabilities = MoonBridge.LI_CCAP_GYRO | MoonBridge.LI_CCAP_ACCEL | MoonBridge.LI_CCAP_RUMBLE;
    }

    private Thread createInputThread() {
        return new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }

            boolean handshakeSuccess = handshake();

            if (!handshakeSuccess) {
                LimeLog.info("ProCon: Initial handshake failed!");
                ProConController.this.stop();
                return;
            }

            LimeLog.info("ProCon: handshake " + handshakeSuccess);
            LimeLog.info("ProCon: highspeed " + highSpeed());
            LimeLog.info("ProCon: handshake " + handshake());
            LimeLog.info("ProCon: loadstickcalibration " + loadStickCalibration());
            LimeLog.info("ProCon: enablevibration " + enableVibration(true));
            LimeLog.info("ProCon: setinutreportmode " + setInputReportMode((byte)0x30));
            LimeLog.info("ProCon: forceusb " + forceUSB());
            LimeLog.info("ProCon: setplayerled " + setPlayerLED(getControllerId() + 1));
            LimeLog.info("ProCon: enableimu " + enableIMU(true));

            LimeLog.info("ProCon: initialized!");

            notifyDeviceAdded();

            while (!Thread.currentThread().isInterrupted() && !stopped) {
                byte[] buffer = new byte[64];
                int res;
                do {
                    long lastMillis = SystemClock.uptimeMillis();
                    res = connection.bulkTransfer(inEndpt, buffer, buffer.length, 1000);
                    if (res == 0) {
                        res = -1;
                    }
                    if (res == -1 && SystemClock.uptimeMillis() - lastMillis < 1000) {
                        LimeLog.warning("Detected device I/O error");
                        ProConController.this.stop();
                        break;
                    }
                } while (res == -1 && !Thread.currentThread().isInterrupted() && !stopped);

                if (res == -1 || stopped) {
                    break;
                }

                if (handleRead(ByteBuffer.wrap(buffer, 0, res).order(ByteOrder.LITTLE_ENDIAN))) {
                    reportInput();
                    reportMotion();
                }
            }
        });
    }

    private boolean sendData(byte[] data, int size) {
        return connection.bulkTransfer(outEndpt, data, size, 100) == size;
    }

    private boolean sendCommand(byte id, boolean waitReply) {
        byte[] data = new byte[] {(byte)0x80, id};
        for (int i = 0; i < COMMAND_RETRIES; i++) {
            if (!sendData(data, data.length)) {
                continue;
            }
            if (!waitReply) {
                return true;
            }

            byte[] buffer = new byte[PACKET_SIZE];
            int res;
            int retries = 0;
            do {
                res = connection.bulkTransfer(inEndpt, buffer, buffer.length, 100);
                if (res > 0 && (buffer[0] & 0xFF) == 0x81 && (buffer[1] & 0xFF) == id) {
                    return true;
                }
                retries += 1;
            } while (retries < 20 && res > 0 && !Thread.currentThread().isInterrupted() && !stopped);
        }

        return false;
    }

    private boolean sendSubcommand(byte subcommand, byte[] payload, byte[] buffer) {
        byte[] data = new byte[11 + payload.length];
        data[0] = 0x01;  // Rumble and subcommand
        data[1] = sendPacketCount++;  // Counter (increments per call)
        if (sendPacketCount > 0xF) {
            sendPacketCount = 0;
        }

        data[10] = subcommand;
        System.arraycopy(payload, 0, data, 11, payload.length);

        for (int i = 0; i < COMMAND_RETRIES; i++) {
            if (!sendData(data, data.length)) {
                continue;
            }

//            LimeLog.warning("ProCon: Sent: " + toHexadecimal(data, data.length));

            // Wait for response
            int res;
            int retries = 0;
            do {
                res = connection.bulkTransfer(inEndpt, buffer, buffer.length, 100);
                if (res < 0 || buffer[0] != 0x21 || buffer[14] != subcommand) {
                    retries += 1;
                } else {
                    return true;
                }
            } while (retries < 20 && res > 0 && !Thread.currentThread().isInterrupted() && !stopped);
            LimeLog.warning("ProCon: Failed to get subcmd reply: " + res + " bytes received, " + String.format((Locale)null, "0x%02x, 0x%02x", buffer[0], buffer[14]));
            return false;
        }

        return false;
    }

    private boolean handshake() {
        return sendCommand((byte)0x02, true);
    }

    private boolean highSpeed() {
        return sendCommand((byte)0x03, true);
    }

    private boolean forceUSB() {
        return sendCommand((byte)0x04, true);
    }

    private boolean setInputReportMode(byte mode) {
        final byte[] data = new byte[] {mode};
        return sendSubcommand((byte) 0x03, data, new byte[PACKET_SIZE]);
    }

    private boolean setPlayerLED(int id) {
        final byte[] data = new byte[] {(byte)(id & 0b1111)};
        return sendSubcommand((byte)0x30, data, new byte[PACKET_SIZE]);
    }

    private boolean enableIMU(boolean enable) {
        byte[] data = new byte[]{(byte)(enable ? 0x01 : 0x00)};
        return sendSubcommand((byte)0x40, data, new byte[PACKET_SIZE]);
    }

    private boolean enableVibration(boolean enable) {
        byte[] data = new byte[]{(byte)(enable ? 0x01 : 0x00)};
        return sendSubcommand((byte)0x48, data, new byte[PACKET_SIZE]);
    }

    public boolean start() {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface iface = device.getInterface(i);
            if (!connection.claimInterface(iface, true)) {
                LimeLog.warning("Failed to claim interfaces");
                return false;
            }
        }

        UsbInterface iface = device.getInterface(0);
        for (int i = 0; i < iface.getEndpointCount(); i++) {
            UsbEndpoint endpt = iface.getEndpoint(i);
            if (endpt.getDirection() == UsbConstants.USB_DIR_IN) {
                inEndpt = endpt;
            } else if (endpt.getDirection() == UsbConstants.USB_DIR_OUT) {
                outEndpt = endpt;
            }
        }

        if (inEndpt == null || outEndpt == null) {
            LimeLog.warning("Missing required endpoint");
            return false;
        }

        inputThread = createInputThread();
        inputThread.start();

        return true;
    }

    public void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        rumble((short) 0, (short) 0);
        if (inputThread != null) {
            inputThread.interrupt();
            inputThread = null;
        }

//        for (int i = 0; i < device.getInterfaceCount(); i++) {
//            UsbInterface iface = device.getInterface(i);
//            connection.releaseInterface(iface);
//        }
        connection.close();
        notifyDeviceRemoved();
    }


    @Override
    public void rumble(short lowFreqMotor, short highFreqMotor) {
        byte[] data = new byte[10];
        data[0] = 0x10;  // Rumble command
        data[1] = sendPacketCount++;  // Counter (increments per call)
        if (sendPacketCount > 0xF) {
            sendPacketCount = 0;
        }

        if (lowFreqMotor != 0) {
            data[4] = data[8] = (byte)(0x50 - (lowFreqMotor & 0xFFFF >> 12));
            data[5] = data[9] = (byte)((((lowFreqMotor & 0xFFFF) >> 8) / 5) + 0x40);
        }
        if (highFreqMotor != 0) {
            data[6] = (byte)((0x70 - ((highFreqMotor & 0xFFFF) >> 10) & -0x04));
            data[7] = (byte)(((highFreqMotor & 0xFFFF) >> 8) * 0xC8 / 0xFF);
        }

        data[2] |= 0x00;
        data[3] |= 0x01;
        data[5] |= 0x40;
        data[6] |= 0x00;
        data[7] |= 0x01;
        data[9] |= 0x40;

        sendData(data, data.length);
    }

    @Override
    public void rumbleTriggers(short leftTrigger, short rightTrigger) {
        // ProCon does not support trigger-specific rumble
    }

    protected boolean handleRead(ByteBuffer buffer) {
        if (buffer.remaining() < PACKET_SIZE) {
            return false;
        }

        if (buffer.get(0) != 0x30) {
            return false;
        }

        buttonFlags = 0;
        // Nintendo layout is swapped
        setButtonFlag(ControllerPacket.B_FLAG, buffer.get(3) & 0x08);
        setButtonFlag(ControllerPacket.A_FLAG, buffer.get(3) & 0x04);
        setButtonFlag(ControllerPacket.Y_FLAG, buffer.get(3) & 0x02);
        setButtonFlag(ControllerPacket.X_FLAG, buffer.get(3) & 0x01);
        setButtonFlag(ControllerPacket.UP_FLAG, buffer.get(5) & 0x02);
        setButtonFlag(ControllerPacket.DOWN_FLAG, buffer.get(5) & 0x01);
        setButtonFlag(ControllerPacket.LEFT_FLAG, buffer.get(5) & 0x08);
        setButtonFlag(ControllerPacket.RIGHT_FLAG, buffer.get(5) & 0x04);
        setButtonFlag(ControllerPacket.BACK_FLAG, buffer.get(4) & 0x01);
        setButtonFlag(ControllerPacket.PLAY_FLAG, buffer.get(4) & 0x02);
        setButtonFlag(ControllerPacket.MISC_FLAG, buffer.get(4) & 0x20); // Screenshot
        setButtonFlag(ControllerPacket.SPECIAL_BUTTON_FLAG, buffer.get(4) & 0x10); // Home
        setButtonFlag(ControllerPacket.LB_FLAG, buffer.get(5) & 0x40);
        setButtonFlag(ControllerPacket.RB_FLAG, buffer.get(3) & 0x40);
        setButtonFlag(ControllerPacket.LS_CLK_FLAG, buffer.get(4) & 0x08);
        setButtonFlag(ControllerPacket.RS_CLK_FLAG, buffer.get(4) & 0x04);

        leftTrigger = ((buffer.get(5) & 0x80) != 0) ? 1 : 0;
        rightTrigger = ((buffer.get(3) & 0x80) != 0) ? 1 : 0;

        int _leftStickX = buffer.get(6) & 0xFF | ((buffer.get(7) & 0x0F) << 8);
        int _leftStickY = ((buffer.get(7) & 0xF0) >> 4) | (buffer.get(8) << 4);
        int _rightStickX = buffer.get(9) & 0xFF | ((buffer.get(10) & 0x0F) << 8);
        int _rightStickY = ((buffer.get(10) & 0xF0) >> 4) | (buffer.get(11) << 4);

        leftStickX = applyStickCalibration(_leftStickX, 0, 0);
        leftStickY = applyStickCalibration(-_leftStickY - 1, 0, 1);
        rightStickX = applyStickCalibration(_rightStickX, 1, 0);
        rightStickY = applyStickCalibration(-_rightStickY - 1, 1, 1);

        accelX = buffer.getShort(37) / 4096.0f;
        accelY = buffer.getShort(39) / 4096.0f;
        accelZ = buffer.getShort(41) / 4096.0f;
        gyroZ = -buffer.getShort(43) / 16.0f;
        gyroX = -buffer.getShort(45) / 16.0f;
        gyroY = buffer.getShort(47) / 16.0f;

        return true;
    }

    private boolean spiFlashRead(int offset, int length, byte[] buffer) {
        // SPI Read Address (Little Endian)
        byte[] address = {
                (byte) (offset & 0xFF),
                (byte) ((offset >> 8) & 0xFF),
                (byte) ((offset >> 16) & 0xFF),
                (byte) ((offset >> 24) & 0xFF),
                (byte) length
        };

        if (!sendSubcommand((byte) 0x10, address, buffer)) {
            LimeLog.warning("ProCon: Failed to receive SPI Flash data.");
            return false;
        }

        return true;
    }

    private boolean checkUserCalMagic(int offset) {
        byte[] buffer = new byte[PACKET_SIZE];

        if (!spiFlashRead(offset, 2, buffer)) {
            return false;
        }

        return ((buffer[20] & 0xFF) == 0xB2) && ((buffer[21] & 0xFF) == 0xA1);
    }

    private boolean loadStickCalibration() {
        byte[] buffer = new byte[PACKET_SIZE];

        int ls_addr = FACTORY_LS_CALIBRATION_OFFSET;
        int rs_addr = FACTORY_RS_CALIBRATION_OFFSET;

        if (checkUserCalMagic(USER_LS_MAGIC_OFFSET)) {
            ls_addr = USER_LS_CALIBRATION_OFFSET;
            LimeLog.info("ProCon: LS has user calibration!");
        }
        if (checkUserCalMagic(USER_RS_MAGIC_OFFSET)) {
            rs_addr = USER_RS_CALIBRATION_OFFSET;
            LimeLog.info("ProCon: RS has user calibration!");
        }

        boolean ls_calibrated = false;
        if (spiFlashRead(ls_addr, STICK_CALIBRATION_LENGTH, buffer)) {
            // read offset 20
            int x_max = (buffer[20] & 0xFF) | ((buffer[21] & 0x0F) << 8);
            int y_max = ((buffer[21] & 0xF0) >> 4) | ((buffer[22] & 0xFF) << 4);
            int x_center = (buffer[23] & 0xFF) | ((buffer[24] & 0x0F) << 8);
            int y_center = ((buffer[24] & 0xF0) >> 4) | ((buffer[25] & 0xFF) << 4);
            int x_min = (buffer[26] & 0xFF) | ((buffer[27] & 0x0F) << 8);
            int y_min = ((buffer[27] & 0xF0) >> 4) | ((buffer[28] & 0xFF) << 4);
            stickCalibration[0][0][0] = x_center - x_min; // Min
            stickCalibration[0][0][1] = x_center; // Center
            stickCalibration[0][0][2] = x_center + x_max; // Max
            stickCalibration[0][1][0] = 0x1000 - y_center - y_max; // Min
            stickCalibration[0][1][1] = 0x1000 - y_center; // Center
            stickCalibration[0][1][2] = 0x1000 - y_center + y_min; // Max
            stickExtends[0][0][0] = (float) ((x_center - stickCalibration[0][0][0]) * -0.7);
            stickExtends[0][0][1] = (float) ((stickCalibration[0][0][2] - x_center) * 0.7);
            stickExtends[0][1][0] = (float) ((y_center - stickCalibration[0][1][0]) * -0.7);
            stickExtends[0][1][1] = (float) ((stickCalibration[0][1][2] - y_center) * 0.7);

            ls_calibrated = true;
        }

        if (!ls_calibrated) {
            applyDefaultCalibration(0);
        }

        boolean rs_calibrated = false;
        if (spiFlashRead(rs_addr, STICK_CALIBRATION_LENGTH, buffer)) {
            // read offset 20
            int x_center = (buffer[20] & 0xFF) | ((buffer[21] & 0x0F) << 8);
            int y_center = ((buffer[21] & 0xF0) >> 4) | ((buffer[22] & 0xFF) << 4);
            int x_min = (buffer[23] & 0xFF) | ((buffer[24] & 0x0F) << 8);
            int y_min = ((buffer[24] & 0xF0) >> 4) | ((buffer[25] & 0xFF) << 4);
            int x_max = (buffer[26] & 0xFF) | ((buffer[27] & 0x0F) << 8);
            int y_max = ((buffer[27] & 0xF0) >> 4) | ((buffer[28] & 0xFF) << 4);
            stickCalibration[1][0][0] = x_center - x_min; // Min
            stickCalibration[1][0][1] = x_center; // Center
            stickCalibration[1][0][2] = x_center + x_max; // Max
            stickCalibration[1][1][0] = 0x1000 - y_center - y_max; // Min
            stickCalibration[1][1][1] = 0x1000 - y_center; // Center
            stickCalibration[1][1][2] = 0x1000 - y_center + y_min; // Max
            stickExtends[1][0][0] = (float) ((x_center - stickCalibration[1][0][0]) * -0.7);
            stickExtends[1][0][1] = (float) ((stickCalibration[1][0][2] - x_center) * 0.7);
            stickExtends[1][1][0] = (float) ((y_center - stickCalibration[1][1][0]) * -0.7);
            stickExtends[1][1][1] = (float) ((stickCalibration[1][1][2] - y_center) * 0.7);

            rs_calibrated = true;
        }

        if (!rs_calibrated) {
            applyDefaultCalibration(1);
        }

//        LimeLog.info(String.format("ProCon: LS X: %04x, %04x, %04x", stickCalibration[0][0][0], stickCalibration[0][0][1], stickCalibration[0][0][2]));
//        LimeLog.info(String.format("ProCon: LS Y: %04x, %04x, %04x", stickCalibration[0][1][0], stickCalibration[0][1][1], stickCalibration[0][1][2]));
//        LimeLog.info(String.format("ProCon: RS X: %04x, %04x, %04x", stickCalibration[1][0][0], stickCalibration[1][0][1], stickCalibration[1][0][2]));
//        LimeLog.info(String.format("ProCon: RS Y: %04x, %04x, %04x", stickCalibration[1][1][0], stickCalibration[1][1][1], stickCalibration[1][1][2]));

        return true;
    }

    private void applyDefaultCalibration(int stick) {
        for (int axis = 0; axis < 2; axis++) {
            stickCalibration[stick][axis][0] = 0x000;  // Min
            stickCalibration[stick][axis][1] = 0x800;  // Center
            stickCalibration[stick][axis][2] = 0xFFF;  // Max

            stickExtends[stick][axis][0] = -0x700;
            stickExtends[stick][axis][1] = 0x700;
        }
    }

    private float applyStickCalibration(int value, int stick, int axis) {
        int center = stickCalibration[stick][axis][1];

        if (value < 0) {
            value += 0x1000;
        }

        value -= center;

        if (value < stickExtends[stick][axis][0]) {
            stickExtends[stick][axis][0] = value;
            return -1;
        } else if (value > stickExtends[stick][axis][1]) {
            stickExtends[stick][axis][1] = value;
            return 1;
        }

        if (value > 0) {
            return value / stickExtends[stick][axis][1];
        } else {
            return -value / stickExtends[stick][axis][0];
        }
    }
}
