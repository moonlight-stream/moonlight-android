package com.limelight.binding.input.evdev;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class EvdevHandler {

    private final EvdevListener listener;
    private final String libraryPath;

    private boolean shutdown = false;
    private InputStream evdevIn;
    private OutputStream evdevOut;
    private Process reader;

    private static final byte UNGRAB_REQUEST = 1;
    private static final byte REGRAB_REQUEST = 2;

    private final Thread handlerThread = new Thread() {
        @Override
        public void run() {
            int deltaX = 0;
            int deltaY = 0;
            byte deltaScroll = 0;

            // Launch the evdev reader shell
            ProcessBuilder builder = new ProcessBuilder("su", "-c", libraryPath+File.separatorChar+"libevdev_reader.so");
            builder.redirectErrorStream(false);

            try {
                reader = builder.start();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            evdevIn = reader.getInputStream();
            evdevOut = reader.getOutputStream();

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

    public EvdevHandler(Context context, EvdevListener listener) {
        this.listener = listener;
        this.libraryPath = context.getApplicationInfo().nativeLibraryDir;
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

        if (reader != null) {
            reader.destroy();
        }

        shutdown = true;
        handlerThread.interrupt();

        try {
            handlerThread.join();
        } catch (InterruptedException ignored) {}
    }
}
