package com.limelight.binding.video;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import com.limelight.LimeLog;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class SbsRenderer implements SurfaceTexture.OnFrameAvailableListener {
    public interface FailureListener {
        void onFailure(Exception exception);
    }

    private static final int START_TIMEOUT_SECONDS = 5;

    private static final float[] QUAD_VERTICES = {
            -1.0f, -1.0f, 0.0f, 0.0f,
             1.0f, -1.0f, 1.0f, 0.0f,
            -1.0f,  1.0f, 0.0f, 1.0f,
             1.0f,  1.0f, 1.0f, 1.0f,
    };

    private static final String VERTEX_SHADER =
            "attribute vec2 aPosition;\n" +
            "attribute vec2 aTextureCoordinate;\n" +
            "uniform mat4 uTextureMatrix;\n" +
            "varying vec2 vTextureCoordinate;\n" +
            "void main() {\n" +
            "  gl_Position = vec4(aPosition, 0.0, 1.0);\n" +
            "  vTextureCoordinate = (uTextureMatrix * vec4(aTextureCoordinate, 0.0, 1.0)).xy;\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "uniform samplerExternalOES uTexture;\n" +
            "varying vec2 vTextureCoordinate;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(uTexture, vTextureCoordinate);\n" +
            "}\n";

    private final Surface outputSurface;
    private int outputWidth;
    private int outputHeight;
    private final int videoWidth;
    private final int videoHeight;
    private final int scalePercentage;
    private final int separationPercentage;
    private final int verticalPositionPercentage;
    private final FailureListener failureListener;
    private final HandlerThread renderThread;
    private final Handler renderHandler;
    private final float[] textureMatrix = new float[16];

    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
    private SurfaceTexture decoderSurfaceTexture;
    private Surface decoderSurface;
    private FloatBuffer vertexBuffer;
    private int textureId;
    private int shaderProgram;
    private int positionHandle;
    private int textureCoordinateHandle;
    private int textureMatrixHandle;
    private volatile boolean outputPaused;
    private boolean renderPending;
    private boolean stopped;
    private boolean failureReported;

    public SbsRenderer(Surface outputSurface, int outputWidth, int outputHeight,
                       int videoWidth, int videoHeight, int scalePercentage,
                       int separationPercentage, int verticalPositionPercentage,
                       FailureListener failureListener) throws Exception {
        this.outputSurface = outputSurface;
        this.outputWidth = outputWidth;
        this.outputHeight = outputHeight;
        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;
        this.scalePercentage = scalePercentage;
        this.separationPercentage = separationPercentage;
        this.verticalPositionPercentage = verticalPositionPercentage;
        this.failureListener = failureListener;

        renderThread = new HandlerThread("Video - SBS Renderer");
        renderThread.start();
        renderHandler = new Handler(renderThread.getLooper());

        CountDownLatch initialized = new CountDownLatch(1);
        AtomicReference<Exception> initializationException = new AtomicReference<>();
        renderHandler.post(() -> {
            try {
                initialize();
            } catch (Exception e) {
                initializationException.set(e);
            } finally {
                initialized.countDown();
            }
        });

        initialized.await();

        if (initializationException.get() != null) {
            stop();
            throw initializationException.get();
        }
    }

    public Surface getDecoderSurface() {
        return decoderSurface;
    }

    public void pauseOutput() {
        outputPaused = true;
    }

    public void updateOutputSize(int width, int height) {
        renderHandler.post(() -> {
            outputWidth = width;
            outputHeight = height;
        });
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        if (!renderPending && !outputPaused && !stopped) {
            renderPending = true;
            renderHandler.post(this::renderFrame);
        }
    }

    public void stop() {
        synchronized (this) {
            if (stopped) {
                return;
            }
            stopped = true;
        }

        CountDownLatch released = new CountDownLatch(1);
        if (!renderHandler.post(() -> {
            release();
            released.countDown();
            renderThread.quitSafely();
        })) {
            return;
        }

        try {
            released.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            renderThread.join(TimeUnit.SECONDS.toMillis(START_TIMEOUT_SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void initialize() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        checkEgl(eglDisplay != EGL14.EGL_NO_DISPLAY, "Unable to get EGL display");
        checkEgl(EGL14.eglInitialize(eglDisplay, null, 0, null, 0), "Unable to initialize EGL");

        int[] configAttributes = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_NONE,
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] configCount = new int[1];
        checkEgl(EGL14.eglChooseConfig(eglDisplay, configAttributes, 0, configs, 0,
                configs.length, configCount, 0) && configCount[0] > 0, "Unable to choose EGL config");

        int[] contextAttributes = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE,
        };
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT,
                contextAttributes, 0);
        checkEgl(eglContext != EGL14.EGL_NO_CONTEXT, "Unable to create EGL context");

        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], outputSurface,
                new int[] { EGL14.EGL_NONE }, 0);
        checkEgl(eglSurface != EGL14.EGL_NO_SURFACE, "Unable to create EGL surface");
        makeCurrent();

        String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
        if (extensions == null || !extensions.contains("GL_OES_EGL_image_external")) {
            throw new IllegalStateException("GL_OES_EGL_image_external is unavailable");
        }

        shaderProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        positionHandle = GLES20.glGetAttribLocation(shaderProgram, "aPosition");
        textureCoordinateHandle = GLES20.glGetAttribLocation(shaderProgram, "aTextureCoordinate");
        textureMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "uTextureMatrix");

        vertexBuffer = ByteBuffer.allocateDirect(QUAD_VERTICES.length * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        vertexBuffer.put(QUAD_VERTICES).position(0);

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        textureId = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        decoderSurfaceTexture = new SurfaceTexture(textureId);
        decoderSurfaceTexture.setDefaultBufferSize(videoWidth, videoHeight);
        decoderSurfaceTexture.setOnFrameAvailableListener(this, renderHandler);
        decoderSurface = new Surface(decoderSurfaceTexture);

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        checkEgl(EGL14.eglSwapBuffers(eglDisplay, eglSurface), "Unable to clear SBS output surface");
    }

    private void renderFrame() {
        renderPending = false;
        if (outputPaused || stopped || decoderSurfaceTexture == null) {
            return;
        }

        try {
            makeCurrent();
            decoderSurfaceTexture.updateTexImage();
            decoderSurfaceTexture.getTransformMatrix(textureMatrix);

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            GLES20.glUseProgram(shaderProgram);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glUniform1i(GLES20.glGetUniformLocation(shaderProgram, "uTexture"), 0);
            GLES20.glUniformMatrix4fv(textureMatrixHandle, 1, false, textureMatrix, 0);

            vertexBuffer.position(0);
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false,
                    4 * Float.BYTES, vertexBuffer);
            vertexBuffer.position(2);
            GLES20.glEnableVertexAttribArray(textureCoordinateHandle);
            GLES20.glVertexAttribPointer(textureCoordinateHandle, 2, GLES20.GL_FLOAT, false,
                    4 * Float.BYTES, vertexBuffer);

            drawEyes();

            GLES20.glDisableVertexAttribArray(positionHandle);
            GLES20.glDisableVertexAttribArray(textureCoordinateHandle);
            if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
                LimeLog.warning("Unable to swap SBS output buffers: 0x" +
                        Integer.toHexString(EGL14.eglGetError()));
                outputPaused = true;
                reportFailure(new IllegalStateException("Unable to swap SBS output buffers"));
            }
        } catch (RuntimeException e) {
            LimeLog.severe("SBS rendering failed: " + e);
            outputPaused = true;
            reportFailure(e);
        }
    }

    private void reportFailure(Exception exception) {
        if (!failureReported) {
            failureReported = true;
            failureListener.onFailure(exception);
        }
    }

    private void drawEyes() {
        int eyeWidth = outputWidth / 2;
        float videoAspectRatio = (float) videoWidth / videoHeight;
        int fittedWidth = eyeWidth;
        int fittedHeight = Math.round(fittedWidth / videoAspectRatio);
        if (fittedHeight > outputHeight) {
            fittedHeight = outputHeight;
            fittedWidth = Math.round(fittedHeight * videoAspectRatio);
        }

        int imageWidth = Math.max(1, Math.round(fittedWidth * scalePercentage / 100.0f));
        int imageHeight = Math.max(1, Math.round(fittedHeight * scalePercentage / 100.0f));
        int horizontalMargin = eyeWidth - imageWidth;
        int separationOffset = Math.round((separationPercentage - 50) / 50.0f * horizontalMargin / 2.0f);
        int verticalMargin = outputHeight - imageHeight;
        int imageY = Math.round(verticalMargin * (100 - verticalPositionPercentage) / 100.0f);

        int leftX = horizontalMargin / 2 - separationOffset;
        int rightX = eyeWidth + horizontalMargin / 2 + separationOffset;
        GLES20.glViewport(leftX, imageY, imageWidth, imageHeight);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glViewport(rightX, imageY, imageWidth, imageHeight);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    private void makeCurrent() {
        checkEgl(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext),
                "Unable to make EGL context current");
    }

    private void release() {
        renderPending = false;
        if (decoderSurfaceTexture != null) {
            decoderSurfaceTexture.setOnFrameAvailableListener(null);
        }
        if (decoderSurface != null) {
            decoderSurface.release();
            decoderSurface = null;
        }
        if (decoderSurfaceTexture != null) {
            decoderSurfaceTexture.release();
            decoderSurfaceTexture = null;
        }

        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            if (eglContext != EGL14.EGL_NO_CONTEXT && eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext);
                if (textureId != 0) {
                    GLES20.glDeleteTextures(1, new int[] { textureId }, 0);
                    textureId = 0;
                }
                if (shaderProgram != 0) {
                    GLES20.glDeleteProgram(shaderProgram);
                    shaderProgram = 0;
                }
            }
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT);
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface);
                eglSurface = EGL14.EGL_NO_SURFACE;
            }
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext);
                eglContext = EGL14.EGL_NO_CONTEXT;
            }
            EGL14.eglTerminate(eglDisplay);
            eglDisplay = EGL14.EGL_NO_DISPLAY;
        }
    }

    private static int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);
        if (linkStatus[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            throw new IllegalStateException("Unable to link SBS shader: " + log);
        }
        return program;
    }

    private static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);
        if (compileStatus[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("Unable to compile SBS shader: " + log);
        }
        return shader;
    }

    private static void checkEgl(boolean success, String message) {
        if (!success) {
            throw new IllegalStateException(message + ": 0x" + Integer.toHexString(EGL14.eglGetError()));
        }
    }
}
