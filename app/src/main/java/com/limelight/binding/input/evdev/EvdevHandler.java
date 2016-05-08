package com.limelight.binding.input.evdev;

import android.app.Activity;
import android.widget.Toast;

import com.limelight.LimeLog;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class EvdevHandler {

    private final EvdevListener listener;
    private final String libraryPath;

    private boolean shutdown = false;
    private InputStream evdevIn;
    private OutputStream evdevOut;
    private Process su;
    private ServerSocket servSock;
    private Socket evdevSock;
    private Activity activity;

    private static final byte UNGRAB_REQUEST = 1;
    private static final byte REGRAB_REQUEST = 2;

    private final Thread handlerThread = new Thread() {
        @Override
        public void run() {
            int deltaX = 0;
            int deltaY = 0;
            byte deltaScroll = 0;

            // Bind a local listening socket for evdevreader to connect to
            try {
                servSock = new ServerSocket(0, 1);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            // Launch a su shell
            ProcessBuilder builder = new ProcessBuilder("su");
            builder.redirectErrorStream(true);

            try {
                su = builder.start();
            } catch (IOException e) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(activity, "This device is not rooted - Mouse capture is unavailable", Toast.LENGTH_LONG).show();
                    }
                });
                e.printStackTrace();
                return;
            }

            // Start evdevreader
            DataOutputStream suOut = new DataOutputStream(su.getOutputStream());
            try {
                suOut.writeChars(libraryPath+File.separatorChar+"libevdev_reader.so "+servSock.getLocalPort()+"\n");
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            // Wait for evdevreader's connection
            LimeLog.info("Waiting for EvdevReader connection to port "+servSock.getLocalPort());
            try {
                evdevSock = servSock.accept();
                evdevIn = evdevSock.getInputStream();
                evdevOut = evdevSock.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
            LimeLog.info("EvdevReader connected from port "+evdevSock.getPort());

            while (!isInterrupted() && !shutdown) {
                EvdevEvent event;
                try {
                    event = EvdevReader.read(evdevIn);
                } catch (IOException e) {
                    event = null;
                }
                if (event == null) {
                    break;
                }

                switch (event.type) {
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
                        switch (event.code) {
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
                        switch (event.code) {
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
                }
            }
        }
    };

    public EvdevHandler(Activity activity, EvdevListener listener) {
        this.listener = listener;
        this.activity = activity;
        this.libraryPath = activity.getApplicationInfo().nativeLibraryDir;
    }

    public void regrabAll() {
        if (!shutdown && evdevOut != null) {
            try {
                evdevOut.write(REGRAB_REQUEST);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void ungrabAll() {
        if (!shutdown && evdevOut != null) {
            try {
                evdevOut.write(UNGRAB_REQUEST);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void start() {
        handlerThread.start();
    }

    public void stop() {
        // We need to stop the process in this context otherwise
        // we could get stuck waiting on output from the process
        // in order to terminate it.

        shutdown = true;
        handlerThread.interrupt();

        if (servSock != null) {
            try {
                servSock.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (evdevSock != null) {
            try {
                evdevSock.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (evdevIn != null) {
            try {
                evdevIn.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (evdevOut != null) {
            try {
                evdevOut.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (su != null) {
            su.destroy();
        }

        try {
            handlerThread.join();
        } catch (InterruptedException ignored) {}
    }
}
