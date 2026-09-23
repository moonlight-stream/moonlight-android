package com.limelight.ogl;

import android.graphics.SurfaceTexture;
import android.opengl.GLUtils;
import android.util.Log;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

/**
 * Small OpenGL ES 2.0 renderer that draws into a TextureView SurfaceTexture.
 *
 * The render thread is started explicitly by subclasses after their own fields are initialized.
 * Starting the thread from this base constructor is unsafe because Java can dispatch abstract
 * methods before subclass initialization has completed.
 */
public abstract class TextureSurfaceRenderer implements Runnable {
    private static final int EGL_OPENGL_ES2_BIT = 4;
    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
    private static final String LOG_TAG = "Moonlight.SBS.GL";

    protected final SurfaceTexture texture;
    protected int width;
    protected int height;

    private EGL10 egl;
    private EGLDisplay eglDisplay;
    private EGLContext eglContext;
    private EGLSurface eglSurface;
    private volatile boolean running;
    private Thread renderThread;

    private final OnGlReadyListener onGlReadyListener;

    public TextureSurfaceRenderer(SurfaceTexture texture, int width, int height, OnGlReadyListener listener) {
        this.texture = texture;
        this.width = width;
        this.height = height;
        this.onGlReadyListener = listener;
    }

    protected synchronized void startRendererThread() {
        if (renderThread != null) {
            return;
        }

        running = true;
        renderThread = new Thread(this, "SBS GL Renderer");
        renderThread.start();
    }

    @Override
    public void run() {
        try {
            initGL();
            initGLComponents();
            Log.d(LOG_TAG, "OpenGL init OK");

            if (onGlReadyListener != null) {
                onGlReadyListener.onGlReady();
            }

            while (running) {
                long loopStart = System.currentTimeMillis();

                if (draw()) {
                    egl.eglSwapBuffers(eglDisplay, eglSurface);
                }

                long waitDelta = 16 - (System.currentTimeMillis() - loopStart);
                if (waitDelta > 0) {
                    try {
                        Thread.sleep(waitDelta);
                    } catch (InterruptedException ignored) {
                        // Re-check running on the next loop.
                    }
                }
            }
        } finally {
            try {
                deinitGLComponents();
            } catch (Exception ignored) {
                // Best-effort cleanup.
            }
            deinitGL();
        }
    }

    public void onPause() {
        running = false;
        if (renderThread != null) {
            renderThread.interrupt();
        }
    }

    protected abstract boolean draw();
    protected abstract void initGLComponents();
    protected abstract void deinitGLComponents();

    private void initGL() {
        egl = (EGL10) EGLContext.getEGL();
        eglDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

        int[] version = new int[2];
        egl.eglInitialize(eglDisplay, version);

        EGLConfig eglConfig = chooseEglConfig();
        eglContext = createContext(egl, eglDisplay, eglConfig);
        eglSurface = egl.eglCreateWindowSurface(eglDisplay, eglConfig, texture, null);

        if (eglSurface == null || eglSurface == EGL10.EGL_NO_SURFACE) {
            throw new RuntimeException("GL surface error: " + GLUtils.getEGLErrorString(egl.eglGetError()));
        }

        if (!egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw new RuntimeException("GL make current error: " + GLUtils.getEGLErrorString(egl.eglGetError()));
        }
    }

    private void deinitGL() {
        if (egl == null) {
            return;
        }
        egl.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
        if (eglSurface != null) {
            egl.eglDestroySurface(eglDisplay, eglSurface);
        }
        if (eglContext != null) {
            egl.eglDestroyContext(eglDisplay, eglContext);
        }
        if (eglDisplay != null) {
            egl.eglTerminate(eglDisplay);
        }
        Log.d(LOG_TAG, "OpenGL deinit OK");
    }

    private EGLContext createContext(EGL10 egl, EGLDisplay eglDisplay, EGLConfig eglConfig) {
        int[] attribList = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE };
        return egl.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attribList);
    }

    private EGLConfig chooseEglConfig() {
        int[] configsCount = new int[1];
        EGLConfig[] configs = new EGLConfig[1];
        int[] configSpec = {
                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 0,
                EGL10.EGL_STENCIL_SIZE, 0,
                EGL10.EGL_NONE
        };

        if (!egl.eglChooseConfig(eglDisplay, configSpec, configs, 1, configsCount)) {
            throw new IllegalArgumentException("Failed to choose EGL config: " + GLUtils.getEGLErrorString(egl.eglGetError()));
        }
        if (configsCount[0] > 0) {
            return configs[0];
        }
        throw new IllegalArgumentException("No matching EGL config found");
    }

    public interface OnGlReadyListener {
        void onGlReady();
    }
}
