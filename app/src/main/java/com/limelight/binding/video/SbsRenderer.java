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
import com.limelight.preferences.SbsCalibrationSnapshot;

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
    private static final int MAX_LENS_CORRECTION_PERCENTAGE = 100;
    private static final float MAX_DISTORTION_COEFFICIENT = 0.4f;
    private static final int MAX_CHROMATIC_CORRECTION_PERCENTAGE = 100;
    private static final float MAX_CHROMATIC_CORRECTION_COEFFICIENT = 0.02f;

    private static final float[] QUAD_VERTICES = {
            -1.0f, -1.0f, 0.0f, 0.0f,
             1.0f, -1.0f, 1.0f, 0.0f,
            -1.0f,  1.0f, 0.0f, 1.0f,
             1.0f,  1.0f, 1.0f, 1.0f,
    };

    private static final String VERTEX_SHADER =
            "attribute vec2 aPosition;\n" +
            "attribute vec2 aTextureCoordinate;\n" +
            "varying vec2 vEyeCoordinate;\n" +
            "void main() {\n" +
            "  gl_Position = vec4(aPosition, 0.0, 1.0);\n" +
            "  vEyeCoordinate = aTextureCoordinate * 2.0 - vec2(1.0);\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "uniform samplerExternalOES uTexture;\n" +
            "uniform mat4 uTextureMatrix;\n" +
            "uniform mat3 uInverseHomography;\n" +
            "uniform vec2 uImageCenter;\n" +
            "uniform vec2 uImageHalfSize;\n" +
            "uniform vec2 uLensScale;\n" +
            "uniform vec2 uDistortionCoefficient;\n" +
            "uniform vec2 uChromaticCorrectionCoefficient;\n" +
            "varying vec2 vEyeCoordinate;\n" +
            "bool mapTextureCoordinate(vec2 placedCoordinate, out vec2 textureCoordinate) {\n" +
            "  vec3 sourceHomogeneous = uInverseHomography * vec3(placedCoordinate, 1.0);\n" +
            "  if (sourceHomogeneous.z <= 0.0) {\n" +
            "    return false;\n" +
            "  }\n" +
            "  vec2 sourceCoordinate = sourceHomogeneous.xy / sourceHomogeneous.z;\n" +
            "  if (abs(sourceCoordinate.x) > 1.0 || abs(sourceCoordinate.y) > 1.0) {\n" +
            "    return false;\n" +
            "  }\n" +
            "  vec2 videoCoordinate = sourceCoordinate * 0.5 + vec2(0.5);\n" +
            "  textureCoordinate =\n" +
            "      (uTextureMatrix * vec4(videoCoordinate, 0.0, 1.0)).xy;\n" +
            "  return true;\n" +
            "}\n" +
            "void main() {\n" +
            "  vec2 radialPosition = vEyeCoordinate * uLensScale;\n" +
            "  float radiusSquared = dot(radialPosition, radialPosition);\n" +
            "  vec2 distortionFactor = vec2(1.0) + uDistortionCoefficient *\n" +
            "      radiusSquared;\n" +
            "  vec2 greenPlacedCoordinate =\n" +
            "      (vEyeCoordinate * distortionFactor - uImageCenter) /\n" +
            "      uImageHalfSize;\n" +
            "  vec2 greenTextureCoordinate;\n" +
            "  if (!mapTextureCoordinate(greenPlacedCoordinate, greenTextureCoordinate)) {\n" +
            "    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);\n" +
            "  } else if (all(lessThan(abs(uChromaticCorrectionCoefficient),\n" +
            "      vec2(0.000001)))) {\n" +
            "    gl_FragColor = texture2D(uTexture, greenTextureCoordinate);\n" +
            "  } else {\n" +
            "    vec2 chromaticOffset =\n" +
            "        uChromaticCorrectionCoefficient * radiusSquared;\n" +
            "    vec2 redPlacedCoordinate =\n" +
            "        (vEyeCoordinate * (distortionFactor + chromaticOffset) - uImageCenter) /\n" +
            "        uImageHalfSize;\n" +
            "    vec2 bluePlacedCoordinate =\n" +
            "        (vEyeCoordinate * (distortionFactor - chromaticOffset) - uImageCenter) /\n" +
            "        uImageHalfSize;\n" +
            "    vec2 redTextureCoordinate;\n" +
            "    vec2 blueTextureCoordinate;\n" +
            "    if (!mapTextureCoordinate(redPlacedCoordinate, redTextureCoordinate) ||\n" +
            "        !mapTextureCoordinate(bluePlacedCoordinate, blueTextureCoordinate)) {\n" +
            "      gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);\n" +
            "    } else {\n" +
            "      vec4 redSample = texture2D(uTexture, redTextureCoordinate);\n" +
            "      vec4 greenSample = texture2D(uTexture, greenTextureCoordinate);\n" +
            "      vec4 blueSample = texture2D(uTexture, blueTextureCoordinate);\n" +
            "      gl_FragColor = vec4(redSample.r, greenSample.g, blueSample.b, greenSample.a);\n" +
            "    }\n" +
            "  }\n" +
            "}\n";

    private final Surface outputSurface;
    private int outputWidth;
    private int outputHeight;
    private final int videoWidth;
    private final int videoHeight;
    private final SbsCalibrationController calibrationController;
    private final FailureListener failureListener;
    private final HandlerThread renderThread;
    private final Handler renderHandler;
    private final float[] textureMatrix = new float[16];
    private final float[] leftInverseHomography = new float[9];
    private final float[] rightInverseHomography = new float[9];
    private SbsCalibrationSnapshot matrixSnapshot;

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
    private int textureSamplerHandle;
    private int textureMatrixHandle;
    private int inverseHomographyHandle;
    private int imageCenterHandle;
    private int imageHalfSizeHandle;
    private int lensScaleHandle;
    private int distortionCoefficientHandle;
    private int chromaticCorrectionCoefficientHandle;
    private volatile boolean outputPaused;
    private boolean renderPending;
    private boolean stopped;
    private boolean failureReported;

    public SbsRenderer(Surface outputSurface, int outputWidth, int outputHeight,
                       int videoWidth, int videoHeight,
                       SbsCalibrationController calibrationController,
                       FailureListener failureListener) throws Exception {
        this.outputSurface = outputSurface;
        this.outputWidth = outputWidth;
        this.outputHeight = outputHeight;
        this.videoWidth = videoWidth;
        this.videoHeight = videoHeight;
        this.calibrationController = calibrationController;
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
        textureSamplerHandle = GLES20.glGetUniformLocation(shaderProgram, "uTexture");
        textureMatrixHandle = GLES20.glGetUniformLocation(shaderProgram, "uTextureMatrix");
        inverseHomographyHandle = GLES20.glGetUniformLocation(shaderProgram, "uInverseHomography");
        imageCenterHandle = GLES20.glGetUniformLocation(shaderProgram, "uImageCenter");
        imageHalfSizeHandle = GLES20.glGetUniformLocation(shaderProgram, "uImageHalfSize");
        lensScaleHandle = GLES20.glGetUniformLocation(shaderProgram, "uLensScale");
        distortionCoefficientHandle = GLES20.glGetUniformLocation(shaderProgram, "uDistortionCoefficient");
        chromaticCorrectionCoefficientHandle = GLES20.glGetUniformLocation(
                shaderProgram, "uChromaticCorrectionCoefficient");

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
            GLES20.glUniform1i(textureSamplerHandle, 0);
            GLES20.glUniformMatrix4fv(textureMatrixHandle, 1, false, textureMatrix, 0);

            // A single immutable snapshot prevents HTTP updates from tearing across one frame.
            SbsCalibrationSnapshot calibration = calibrationController.getLiveSnapshot();
            GLES20.glUniform2f(distortionCoefficientHandle,
                    getLensCorrectionCoefficient(
                            calibration.lensHorizontalEnabled,
                            calibration.lensHorizontalCorrectionPercentage),
                    getLensCorrectionCoefficient(
                            calibration.lensVerticalEnabled,
                            calibration.lensVerticalCorrectionPercentage));
            GLES20.glUniform2f(chromaticCorrectionCoefficientHandle,
                    getChromaticCorrectionCoefficient(
                            calibration.chromaticHorizontalEnabled,
                            calibration.chromaticHorizontalCorrectionPercentage),
                    getChromaticCorrectionCoefficient(
                            calibration.chromaticVerticalEnabled,
                            calibration.chromaticVerticalCorrectionPercentage));

            vertexBuffer.position(0);
            GLES20.glEnableVertexAttribArray(positionHandle);
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false,
                    4 * Float.BYTES, vertexBuffer);
            vertexBuffer.position(2);
            GLES20.glEnableVertexAttribArray(textureCoordinateHandle);
            GLES20.glVertexAttribPointer(textureCoordinateHandle, 2, GLES20.GL_FLOAT, false,
                    4 * Float.BYTES, vertexBuffer);

            drawEyes(calibration);

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

    private void drawEyes(SbsCalibrationSnapshot calibration) {
        int eyeWidth = outputWidth / 2;
        float videoAspectRatio = (float) videoWidth / videoHeight;
        int fittedWidth = eyeWidth;
        int fittedHeight = Math.round(fittedWidth / videoAspectRatio);
        if (fittedHeight > outputHeight) {
            fittedHeight = outputHeight;
            fittedWidth = Math.round(fittedHeight * videoAspectRatio);
        }

        int imageWidth = Math.max(1,
                Math.round(fittedWidth * calibration.scalePercentage / 100.0f));
        int imageHeight = Math.max(1,
                Math.round(fittedHeight * calibration.scalePercentage / 100.0f));
        int horizontalMargin = eyeWidth - imageWidth;
        int separationOffset = Math.round((calibration.separationPercentage - 50) /
                50.0f * horizontalMargin / 2.0f);
        int verticalMargin = outputHeight - imageHeight;
        int imageY = Math.round(verticalMargin *
                (100 - calibration.verticalPositionPercentage) / 100.0f);

        // Lens coordinates are fixed to each physical eye viewport, independent of image placement.
        float lensRadius = Math.max(1.0f, Math.min(eyeWidth, outputHeight) / 2.0f);
        GLES20.glUniform2f(lensScaleHandle,
                eyeWidth / (2.0f * lensRadius), outputHeight / (2.0f * lensRadius));

        float imageHalfWidth = (float) imageWidth / eyeWidth;
        float imageHalfHeight = (float) imageHeight / outputHeight;
        float verticalCenter = (imageY + imageHeight / 2.0f) * 2.0f / outputHeight - 1.0f;
        float leftCenter = -2.0f * separationOffset / eyeWidth +
                2.0f * calibration.leftHorizontalCenterPercentage() / 100.0f;
        float rightCenter = 2.0f * separationOffset / eyeWidth +
                2.0f * calibration.rightHorizontalCenterPercentage() / 100.0f;
        float leftVerticalCenter = verticalCenter +
                2.0f * calibration.leftVerticalOffsetPercentage / 100.0f;
        float rightVerticalCenter = verticalCenter +
                2.0f * calibration.rightVerticalOffsetPercentage / 100.0f;

        if (matrixSnapshot != calibration) {
            buildInverseHomography(calibration.leftYawDegrees(), calibration.leftPitchDegrees(),
                    videoAspectRatio, leftInverseHomography);
            buildInverseHomography(calibration.rightYawDegrees(), calibration.rightPitchDegrees(),
                    videoAspectRatio, rightInverseHomography);
            matrixSnapshot = calibration;
        }

        GLES20.glUniform2f(imageHalfSizeHandle, imageHalfWidth, imageHalfHeight);
        GLES20.glViewport(0, 0, eyeWidth, outputHeight);
        drawEye(leftCenter, leftVerticalCenter, leftInverseHomography);
        GLES20.glViewport(eyeWidth, 0, outputWidth - eyeWidth, outputHeight);
        drawEye(rightCenter, rightVerticalCenter, rightInverseHomography);
    }

    private void drawEye(float horizontalCenter, float verticalCenter,
                         float[] inverseHomography) {
        GLES20.glUniform2f(imageCenterHandle, horizontalCenter, verticalCenter);
        GLES20.glUniformMatrix3fv(inverseHomographyHandle, 1, false, inverseHomography, 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    static float getChromaticCorrectionCoefficient(boolean enabled, int percentage) {
        if (!enabled) {
            return 0.0f;
        }
        return percentage * MAX_CHROMATIC_CORRECTION_COEFFICIENT /
                MAX_CHROMATIC_CORRECTION_PERCENTAGE;
    }

    static float getLensCorrectionCoefficient(boolean enabled, int percentage) {
        if (!enabled) {
            return 0.0f;
        }
        return percentage * MAX_DISTORTION_COEFFICIENT /
                MAX_LENS_CORRECTION_PERCENTAGE;
    }

    static void buildInverseHomography(float yawDegrees, float pitchDegrees,
                                       float aspectRatio, float[] output) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        float cosineYaw = (float) Math.cos(yaw);
        float sineYaw = (float) Math.sin(yaw);
        float cosinePitch = (float) Math.cos(pitch);
        float sinePitch = (float) Math.sin(pitch);

        // Pinhole projection of a centered planar quad. Camera distance controls perspective
        // strength without introducing a stereo camera or another rendering pass.
        float cameraDistance = 3.0f;
        float h00 = cameraDistance * cosineYaw;
        float h01 = cameraDistance * sineYaw * sinePitch / aspectRatio;
        float h02 = 0.0f;
        float h10 = 0.0f;
        float h11 = cameraDistance * cosinePitch;
        float h12 = 0.0f;
        float h20 = sineYaw * aspectRatio;
        float h21 = -cosineYaw * sinePitch;
        float h22 = cameraDistance;

        float determinant = h00 * (h11 * h22 - h12 * h21) -
                h01 * (h10 * h22 - h12 * h20) +
                h02 * (h10 * h21 - h11 * h20);
        float inverseDeterminant = 1.0f / determinant;
        float i00 = (h11 * h22 - h12 * h21) * inverseDeterminant;
        float i01 = (h02 * h21 - h01 * h22) * inverseDeterminant;
        float i02 = (h01 * h12 - h02 * h11) * inverseDeterminant;
        float i10 = (h12 * h20 - h10 * h22) * inverseDeterminant;
        float i11 = (h00 * h22 - h02 * h20) * inverseDeterminant;
        float i12 = (h02 * h10 - h00 * h12) * inverseDeterminant;
        float i20 = (h10 * h21 - h11 * h20) * inverseDeterminant;
        float i21 = (h01 * h20 - h00 * h21) * inverseDeterminant;
        float i22 = (h00 * h11 - h01 * h10) * inverseDeterminant;

        // GLES matrices are column-major.
        output[0] = i00;
        output[1] = i10;
        output[2] = i20;
        output[3] = i01;
        output[4] = i11;
        output[5] = i21;
        output[6] = i02;
        output[7] = i12;
        output[8] = i22;
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
