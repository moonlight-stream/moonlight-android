package com.limelight.binding.video;

import android.content.Context;

import com.limelight.LimeLog;
import com.limelight.preferences.SbsCalibrationSnapshot;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class SbsCalibrationServer {
    public interface StatusListener {
        void onStatusChanged(Status status);
    }

    public static final class Status {
        public enum State { DISABLED, STARTING, RUNNING, FAILED }

        public final State state;
        public final int port;
        public final String detail;

        private Status(State state, int port, String detail) {
            this.state = state;
            this.port = port;
            this.detail = detail;
        }

        public static Status disabled(int port) {
            if (!isValidPort(port)) {
                return new Status(State.DISABLED, port,
                        "Disabled — invalid configured TCP port; use 1024-65535");
            }
            return new Status(State.DISABLED, port, "Disabled — " + getDisplayUrl(port));
        }

        public static Status starting(int port) {
            return new Status(State.STARTING, port, "Starting — " + getDisplayUrl(port));
        }

        public static Status running(int port) {
            return new Status(State.RUNNING, port, "Listening at " + getDisplayUrl(port));
        }

        public static Status failed(int port, String detail) {
            return new Status(State.FAILED, port, detail);
        }
    }

    private static final int MIN_PORT = 1024;
    private static final int MAX_PORT = 65535;
    private static final int MAX_HEADER_BYTES = 16 * 1024;
    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final int CLIENT_TIMEOUT_MILLIS = 5000;

    private final Context applicationContext;
    private final SbsCalibrationController controller;
    private final StatusListener statusListener;
    private volatile Status status;
    private ServerSocket serverSocket;
    private int generation;

    public SbsCalibrationServer(Context context, SbsCalibrationController controller,
                                StatusListener statusListener, int initialPort) {
        applicationContext = context.getApplicationContext();
        this.controller = controller;
        this.statusListener = statusListener;
        status = Status.disabled(initialPort);
    }

    public Status getStatus() {
        return status;
    }

    public synchronized void start(int port) {
        stopSocketLocked();
        generation++;
        int startGeneration = generation;
        if (!isValidPort(port)) {
            publishStatus(Status.failed(port, "Invalid TCP port; use 1024-65535"));
            return;
        }

        publishStatus(Status.starting(port));
        Thread thread = new Thread(() -> runServer(startGeneration, port),
                "SBS Calibration HTTP");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop(int port) {
        generation++;
        stopSocketLocked();
        publishStatus(Status.disabled(port));
    }

    private void runServer(int startGeneration, int port) {
        ServerSocket listeningSocket = null;
        try {
            listeningSocket = new ServerSocket();
            listeningSocket.setReuseAddress(true);
            listeningSocket.bind(new InetSocketAddress(port));
            synchronized (this) {
                if (startGeneration != generation) {
                    closeQuietly(listeningSocket);
                    return;
                }
                serverSocket = listeningSocket;
                publishStatus(Status.running(port));
            }

            while (isCurrentGeneration(startGeneration)) {
                try (Socket client = listeningSocket.accept()) {
                    client.setSoTimeout(CLIENT_TIMEOUT_MILLIS);
                    handleClient(client);
                } catch (SocketException e) {
                    if (isCurrentGeneration(startGeneration)) {
                        throw e;
                    }
                } catch (IOException | RuntimeException e) {
                    LimeLog.warning("SBS calibration request failed: " + e.getMessage());
                }
            }
        } catch (IOException | RuntimeException e) {
            synchronized (this) {
                if (startGeneration == generation) {
                    publishStatus(Status.failed(port, "Bind failed: " + safeMessage(e)));
                }
            }
        } finally {
            closeQuietly(listeningSocket);
            synchronized (this) {
                if (serverSocket == listeningSocket) {
                    serverSocket = null;
                }
            }
        }
    }

    private synchronized boolean isCurrentGeneration(int candidate) {
        return candidate == generation;
    }

    private void handleClient(Socket client) throws IOException {
        BufferedInputStream input = new BufferedInputStream(client.getInputStream());
        String requestLine = readLine(input, MAX_HEADER_BYTES);
        if (requestLine == null || requestLine.isEmpty()) {
            return;
        }

        String[] requestParts = requestLine.split(" ", 3);
        if (requestParts.length != 3) {
            writeResponse(client.getOutputStream(), 400, "text/plain; charset=utf-8",
                    "Malformed request");
            return;
        }

        int headerBytes = requestLine.length() + 2;
        int contentLength = 0;
        while (true) {
            String header = readLine(input, MAX_HEADER_BYTES - headerBytes);
            if (header == null) {
                throw new IOException("Unexpected end of headers");
            }
            headerBytes += header.length() + 2;
            if (headerBytes > MAX_HEADER_BYTES) {
                throw new IOException("Headers too large");
            }
            if (header.isEmpty()) {
                break;
            }
            int separator = header.indexOf(':');
            if (separator > 0 && "content-length".equalsIgnoreCase(header.substring(0, separator).trim())) {
                try {
                    contentLength = Integer.parseInt(header.substring(separator + 1).trim());
                } catch (NumberFormatException e) {
                    writeResponse(client.getOutputStream(), 400, "text/plain; charset=utf-8",
                            "Invalid Content-Length");
                    return;
                }
            }
        }

        if (contentLength < 0 || contentLength > MAX_BODY_BYTES) {
            writeResponse(client.getOutputStream(), 413, "text/plain; charset=utf-8",
                    "Request body too large");
            return;
        }
        byte[] bodyBytes = new byte[contentLength];
        int offset = 0;
        while (offset < contentLength) {
            int read = input.read(bodyBytes, offset, contentLength - offset);
            if (read < 0) {
                throw new IOException("Unexpected end of request body");
            }
            offset += read;
        }

        String method = requestParts[0];
        String path = requestParts[1];
        String body = new String(bodyBytes, StandardCharsets.UTF_8);
        route(client.getOutputStream(), method, path, body);
    }

    private void route(OutputStream output, String method, String path, String body) throws IOException {
        try {
            if ("GET".equals(method) && ("/".equals(path) || "/index.html".equals(path))) {
                writeResponse(output, 200, "text/html; charset=utf-8", loadPage());
            } else if ("GET".equals(method) && "/api/state".equals(path)) {
                writeJson(output, 200, controller.getLiveSnapshot());
            } else if ("POST".equals(method) && "/api/preview".equals(path)) {
                Map<String, String> parameters = parseForm(body);
                long revision = parseLong(parameters, "revision");
                if (!controller.preview(parseSnapshot(parameters), revision)) {
                    writeResponse(output, 409, "text/plain; charset=utf-8",
                            "Stale preview request");
                    return;
                }
                writeJson(output, 200, controller.getLiveSnapshot());
            } else if ("POST".equals(method) && "/api/save".equals(path)) {
                writeJson(output, 200, controller.save(parseSnapshot(parseForm(body))));
            } else if ("POST".equals(method) && "/api/revert".equals(path)) {
                writeJson(output, 200, controller.revert());
            } else if ("POST".equals(method) && "/api/reset".equals(path)) {
                writeJson(output, 200, controller.reset());
            } else {
                writeResponse(output, 404, "text/plain; charset=utf-8", "Not found");
            }
        } catch (IllegalArgumentException e) {
            writeResponse(output, 400, "text/plain; charset=utf-8", e.getMessage());
        } catch (IllegalStateException e) {
            writeResponse(output, 500, "text/plain; charset=utf-8", e.getMessage());
        }
    }

    static Map<String, String> parseForm(String body) {
        if (body.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> parameters = new HashMap<>();
        for (String pair : body.split("&")) {
            int separator = pair.indexOf('=');
            String encodedKey = separator >= 0 ? pair.substring(0, separator) : pair;
            String encodedValue = separator >= 0 ? pair.substring(separator + 1) : "";
            String key = decodeFormComponent(encodedKey);
            String value = decodeFormComponent(encodedValue);
            parameters.put(key, value);
        }
        return parameters;
    }

    private static String decodeFormComponent(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError("UTF-8 is unavailable", e);
        }
    }

    static SbsCalibrationSnapshot parseSnapshot(Map<String, String> parameters) {
        return SbsCalibrationSnapshot.create(
                parseInt(parameters, "scale"),
                parseInt(parameters, "separation"),
                parseInt(parameters, "verticalPosition"),
                parseInt(parameters, "lensCorrection"),
                parseBoolean(parameters, "chromaticHorizontalEnabled"),
                parseInt(parameters, "chromaticHorizontalCorrection"),
                parseBoolean(parameters, "chromaticVerticalEnabled"),
                parseInt(parameters, "chromaticVerticalCorrection"),
                parseFloat(parameters, "commonHorizontalOffset"),
                parseFloat(parameters, "leftHorizontalOffset"),
                parseFloat(parameters, "rightHorizontalOffset"),
                parseFloat(parameters, "leftVerticalOffset"),
                parseFloat(parameters, "rightVerticalOffset"),
                parseFloat(parameters, "commonYaw"),
                parseFloat(parameters, "commonPitch"),
                parseFloat(parameters, "leftYawCorrection"),
                parseFloat(parameters, "rightYawCorrection"),
                parseFloat(parameters, "leftPitchCorrection"),
                parseFloat(parameters, "rightPitchCorrection"));
    }

    public static boolean isValidPort(int port) {
        return port >= MIN_PORT && port <= MAX_PORT;
    }

    private String loadPage() throws IOException {
        try (InputStream input = applicationContext.getAssets().open("sbs_calibration.html");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String readLine(InputStream input, int remainingLimit) throws IOException {
        if (remainingLimit <= 0) {
            throw new IOException("Headers too large");
        }
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int previous = -1;
        while (line.size() < remainingLimit) {
            int current = input.read();
            if (current < 0) {
                return line.size() == 0 ? null : line.toString(StandardCharsets.US_ASCII.name());
            }
            if (previous == '\r' && current == '\n') {
                byte[] bytes = line.toByteArray();
                return new String(bytes, 0, bytes.length - 1, StandardCharsets.US_ASCII);
            }
            line.write(current);
            previous = current;
        }
        throw new IOException("Headers too large");
    }

    private static int parseInt(Map<String, String> parameters, String name) {
        try {
            return Integer.parseInt(required(parameters, name));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + name);
        }
    }

    private static long parseLong(Map<String, String> parameters, String name) {
        try {
            return Long.parseLong(required(parameters, name));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + name);
        }
    }

    private static float parseFloat(Map<String, String> parameters, String name) {
        try {
            return Float.parseFloat(required(parameters, name));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + name);
        }
    }

    private static boolean parseBoolean(Map<String, String> parameters, String name) {
        String value = required(parameters, name);
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid " + name);
    }

    private static String required(Map<String, String> parameters, String name) {
        String value = parameters.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Missing " + name);
        }
        return value;
    }

    private static void writeJson(OutputStream output, int statusCode,
                                  SbsCalibrationSnapshot snapshot) throws IOException {
        writeResponse(output, statusCode, "application/json; charset=utf-8", toJson(snapshot));
    }

    static String toJson(SbsCalibrationSnapshot snapshot) {
        return String.format(Locale.US,
                "{\"scale\":%d,\"separation\":%d,\"verticalPosition\":%d," +
                "\"lensCorrection\":%d,\"chromaticHorizontalEnabled\":%b," +
                "\"chromaticHorizontalCorrection\":%d," +
                "\"chromaticVerticalEnabled\":%b," +
                "\"chromaticVerticalCorrection\":%d," +
                "\"commonHorizontalOffset\":%.3f,\"leftHorizontalOffset\":%.3f," +
                "\"rightHorizontalOffset\":%.3f,\"leftVerticalOffset\":%.3f," +
                "\"rightVerticalOffset\":%.3f,\"commonYaw\":%.3f,\"commonPitch\":%.3f," +
                "\"leftYawCorrection\":%.3f,\"rightYawCorrection\":%.3f," +
                "\"leftPitchCorrection\":%.3f,\"rightPitchCorrection\":%.3f}",
                snapshot.scalePercentage, snapshot.separationPercentage,
                snapshot.verticalPositionPercentage, snapshot.lensCorrectionPercentage,
                snapshot.chromaticHorizontalEnabled,
                snapshot.chromaticHorizontalCorrectionPercentage,
                snapshot.chromaticVerticalEnabled,
                snapshot.chromaticVerticalCorrectionPercentage,
                snapshot.commonHorizontalOffsetPercentage,
                snapshot.leftHorizontalOffsetPercentage, snapshot.rightHorizontalOffsetPercentage,
                snapshot.leftVerticalOffsetPercentage, snapshot.rightVerticalOffsetPercentage,
                snapshot.commonYawDegrees, snapshot.commonPitchDegrees,
                snapshot.leftYawCorrectionDegrees, snapshot.rightYawCorrectionDegrees,
                snapshot.leftPitchCorrectionDegrees, snapshot.rightPitchCorrectionDegrees);
    }

    private static void writeResponse(OutputStream output, int statusCode, String contentType,
                                      String body) throws IOException {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        String reason = statusCode == 200 ? "OK" : statusCode == 400 ? "Bad Request" :
                statusCode == 404 ? "Not Found" : statusCode == 409 ? "Conflict" :
                        statusCode == 413 ? "Payload Too Large" : "Internal Server Error";
        String headers = "HTTP/1.1 " + statusCode + " " + reason + "\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " + bodyBytes.length + "\r\n" +
                "Cache-Control: no-store\r\n" +
                "X-Content-Type-Options: nosniff\r\n" +
                "Connection: close\r\n\r\n";
        output.write(headers.getBytes(StandardCharsets.US_ASCII));
        output.write(bodyBytes);
        output.flush();
    }

    private synchronized void stopSocketLocked() {
        closeQuietly(serverSocket);
        serverSocket = null;
    }

    private void publishStatus(Status newStatus) {
        status = newStatus;
        statusListener.onStatusChanged(newStatus);
    }

    private static void closeQuietly(ServerSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isEmpty() ? exception.getClass().getSimpleName() : message;
    }

    public static String getDisplayUrl(int port) {
        try {
            for (int pass = 0; pass < 2; pass++) {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                while (interfaces != null && interfaces.hasMoreElements()) {
                    NetworkInterface networkInterface = interfaces.nextElement();
                    String name = networkInterface.getName().toLowerCase(Locale.US);
                    boolean lanInterface = name.startsWith("wlan") || name.startsWith("wifi") ||
                            name.startsWith("eth");
                    if ((pass == 0) != lanInterface || !networkInterface.isUp() ||
                            networkInterface.isLoopback()) {
                        continue;
                    }
                    Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress address = addresses.nextElement();
                        if (address instanceof Inet4Address && !address.isLoopbackAddress() &&
                                !address.isLinkLocalAddress()) {
                            return "http://" + address.getHostAddress() + ":" + port;
                        }
                    }
                }
            }
        } catch (SocketException | SecurityException ignored) {
        }
        return "http://PHONE_IP:" + port;
    }
}
