package com.limelight.binding.input.evdev;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Scanner;
import java.util.UUID;

public class EvdevShell {
    private OutputStream stdin;
    private InputStream stdout;
    private Process shell;
    private final String uuidString = UUID.randomUUID().toString();

    private static final EvdevShell globalShell = new EvdevShell();

    public static EvdevShell getInstance() {
        return globalShell;
    }

    public void startShell() {
        ProcessBuilder builder = new ProcessBuilder("su");

        try {
            // Redirect stderr to stdout
            builder.redirectErrorStream(true);
            shell = builder.start();

            stdin = shell.getOutputStream();
            stdout = shell.getInputStream();
        } catch (IOException e) {
            // This is unexpected
            e.printStackTrace();

            // Kill the shell if it spawned
            if (stdin != null) {
                try {
                    stdin.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                } finally {
                    stdin = null;
                }
            }
            if (stdout != null) {
                try {
                    stdout.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                } finally {
                    stdout = null;
                }
            }
            if (shell != null) {
                shell.destroy();
                shell = null;
            }
        }
    }

    public void runCommand(String command) {
        if (shell == null) {
            // Shell never started
            return;
        }

        try {
            // Write the command followed by an echo with our UUID
            stdin.write((command+'\n').getBytes("UTF-8"));
            stdin.write(("echo "+uuidString+'\n').getBytes("UTF-8"));
            stdin.flush();

            // This is the only command in flight so we can use a scanner
            // without worrying about it eating too many characters
            Scanner scanner = new Scanner(stdout);
            while (scanner.hasNext()) {
                if (scanner.next().contains(uuidString)) {
                    // Our command ran
                    return;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stopShell() throws InterruptedException {
        boolean exitWritten = false;

        if (shell == null) {
            // Shell never started
            return;
        }

        try {
            stdin.write("exit\n".getBytes("UTF-8"));
            exitWritten = true;
        } catch (IOException e) {
            // We'll destroy the process without
            // waiting for it to terminate since
            // we don't know whether our exit command made it
            e.printStackTrace();
        }

        if (exitWritten) {
            try {
                shell.waitFor();
            } finally {
                shell.destroy();
            }
        }
        else {
            shell.destroy();
        }
    }
}
