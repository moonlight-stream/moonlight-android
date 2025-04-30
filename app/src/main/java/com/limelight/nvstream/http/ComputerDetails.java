package com.limelight.nvstream.http;

import androidx.annotation.NonNull;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;


public class ComputerDetails {
    public enum State {
        ONLINE, OFFLINE, UNKNOWN
    }

    public static class AddressTuple {
        public String address;
        public int port;

        public AddressTuple(String address, int port) {
            if (address == null) {
                throw new IllegalArgumentException("Address cannot be null");
            }
            if (port <= 0) {
                throw new IllegalArgumentException("Invalid port");
            }

            // If this was an escaped IPv6 address, remove the brackets
            if (address.startsWith("[") && address.endsWith("]")) {
                address = address.substring(1, address.length() - 1);
            }

            this.address = address;
            this.port = port;
        }

        @Override
        public int hashCode() {
            return Objects.hash(address, port);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof AddressTuple)) {
                return false;
            }

            AddressTuple that = (AddressTuple) obj;
            return address.equals(that.address) && port == that.port;
        }

        public String toString() {
            if (address.contains(":")) {
                // IPv6
                return "[" + address + "]:" + port;
            }
            else {
                // IPv4 and hostnames
                return address + ":" + port;
            }
        }
    }

    // Persistent attributes
    public String uuid;
    public String name;
    public AddressTuple localAddress;
    public AddressTuple remoteAddress;
    public AddressTuple manualAddress;
    public AddressTuple ipv6Address;
    public String macAddress;
    public X509Certificate serverCert;

    // Transient attributes
    public State state;
    public int permission = -1;
    public AddressTuple activeAddress;
    public int httpsPort;
    public int externalPort;
    public PairingManager.PairState pairState;
    public int runningGameId;
    public String rawAppList;
    public boolean nvidiaServer;

    // VDisplay info
    public boolean vDisplaySupported = false;
    public boolean vDisplayDriverReady = false;

    // Server commands
    public List<String> serverCommands;

    public ComputerDetails() {
        // Use defaults
        state = State.UNKNOWN;
    }

    public ComputerDetails(ComputerDetails details) {
        // Copy details from the other computer
        update(details);
    }

    public int guessExternalPort() {
        if (externalPort != 0) {
            return externalPort;
        }
        else if (remoteAddress != null) {
            return remoteAddress.port;
        }
        else if (activeAddress != null) {
            return activeAddress.port;
        }
        else if (ipv6Address != null) {
            return ipv6Address.port;
        }
        else if (localAddress != null) {
            return localAddress.port;
        }
        else {
            return NvHTTP.DEFAULT_HTTP_PORT;
        }
    }

    public void update(ComputerDetails details) {
        this.state = details.state;
        this.name = details.name;
        this.uuid = details.uuid;
        this.permission = details.permission;
        if (details.activeAddress != null) {
            this.activeAddress = details.activeAddress;
        }
        // We can get IPv4 loopback addresses with GS IPv6 Forwarder
        if (details.localAddress != null && !details.localAddress.address.startsWith("127.")) {
            this.localAddress = details.localAddress;
        }
        if (details.remoteAddress != null) {
            this.remoteAddress = details.remoteAddress;
        }
        else if (this.remoteAddress != null && details.externalPort != 0) {
            // If we have a remote address already (perhaps via STUN) but our updated details
            // don't have a new one (because GFE doesn't send one), propagate the external
            // port to the current remote address. We may have tried to guess it previously.
            this.remoteAddress.port = details.externalPort;
        }
        if (details.manualAddress != null) {
            this.manualAddress = details.manualAddress;
        }
        if (details.ipv6Address != null) {
            this.ipv6Address = details.ipv6Address;
        }
        if (details.macAddress != null && !details.macAddress.equals("00:00:00:00:00:00")) {
            this.macAddress = details.macAddress;
        }
        if (details.serverCert != null) {
            this.serverCert = details.serverCert;
        }
        this.externalPort = details.externalPort;
        this.httpsPort = details.httpsPort;
        this.pairState = details.pairState;
        this.runningGameId = details.runningGameId;
        this.nvidiaServer = details.nvidiaServer;
        this.rawAppList = details.rawAppList;

        this.vDisplayDriverReady = details.vDisplayDriverReady;
        this.vDisplaySupported = details.vDisplaySupported;

        this.serverCommands = details.serverCommands;
    }

    @NonNull
    @Override
    public String toString() {
        /*
         * Permissions:
             enum class PERM: uint32_t {
                 _reserved        = 1,

                 _input           = _reserved << 8,   // Input permission group
                 input_controller = _input << 0,      // Allow controller input
                 input_touch      = _input << 1,      // Allow touch input
                 input_pen        = _input << 2,      // Allow pen input
                 input_mouse      = _input << 3,      // Allow mouse input
                 input_kbd        = _input << 4,      // Allow keyboard input
                 _all_inputs      = input_controller | input_touch | input_pen | input_mouse | input_kbd,

                 _operation       = _input << 8,      // Operation permission group
                 clipboard_set    = _operation << 0,  // Allow set clipboard from client
                 clipboard_read   = _operation << 1,  // Allow read clipboard from host
                 file_upload      = _operation << 2,  // Allow upload files to host
                 file_dwnload     = _operation << 3,  // Allow download files from host
                 server_cmd       = _operation << 4,  // Allow execute server cmd
                 _all_opeiations  = clipboard_set | clipboard_read | file_upload | file_dwnload | server_cmd,

                 _action          = _operation << 8,  // Action permission group
                 list             = _action << 0,     // Allow list apps
                 view             = _action << 1,     // Allow view streams
                 launch           = _action << 2,     // Allow launch apps
                 _allow_view      = view | launch,    // Launch contains view permission
                 _all_actions     = list | view | launch,

                 _default         = view | list,      // Default permissions for new clients
                 _no              = 0,                // No permissions are granted
                 _all             = _all_inputs | _all_opeiations | _all_actions, // All current permissions
             };
         */

        String permissionsStr = permission < 0 ? "N/A\n" : "0x" + Integer.toHexString(permission) + "\n" +
                " - Controller Input: " + ((permission & 0x00000100) != 0) + "\n" +
                " - Touch Input: " + ((permission & 0x00000200) != 0) + "\n" +
                " - Pen Input: " + ((permission & 0x00000400) != 0) + "\n" +
                " - Mouse Input: " + ((permission & 0x00000800) != 0) + "\n" +
                " - Keyboard Input: " + ((permission & 0x00001000) != 0) + "\n" +
                "\n" +
//                " - Set Clipboard: " + ((permission & 0x00010000) != 0) + "\n" +
//                " - Read Clipboard: " + ((permission & 0x00020000) != 0) + "\n" +
//                " - Upload Files: " + ((permission & 0x00040000) != 0) + "\n" +
//                " - Download Files: " + ((permission & 0x00080000) != 0) + "\n" +
                " - Server Command: " + ((permission & 0x00100000) != 0) + "\n" +
                "\n" +
                " - List Apps: " + ((permission & 0x01000000) != 0) + "\n" +
                " - View Streams: " + ((permission & (0x02000000 | 0x01000000)) != 0) + "\n" +
                " - Launch Apps: " + ((permission & (0x04000000 | 0x02000000 | 0x01000000)) != 0) + "\n";

        return "Name: " + name + "\n" +
                "State: " + state + "\n" +
                "Active Address: " + activeAddress + "\n" +
                "UUID: " + uuid + "\n" +
                "\nPermissions: " + permissionsStr + "\n" +
                "Local Address: " + localAddress + "\n" +
                "Remote Address: " + remoteAddress + "\n" +
                "IPv6 Address: " + ipv6Address + "\n" +
                "Manual Address: " + manualAddress + "\n" +
                "MAC Address: " + macAddress + "\n" +
                "Pair State: " + pairState + "\n" +
                "Running Game ID: " + runningGameId + "\n" +
                "HTTPS Port: " + httpsPort + "\n";
    }
}
