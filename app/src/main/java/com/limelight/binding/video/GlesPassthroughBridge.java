package com.limelight.binding.video;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES31;
import android.view.Surface;
import com.limelight.LimeLog;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * This class acts as a bridge between the MediaCodec decoder and the final display surface.
 * It implements a Two-Pass OpenGL ES 3.1 Pipeline to support standard sampler2D shaders (like SGSR1).
 * 
 * Pass 1: Renders the hardware-decoded OES texture (samplerExternalOES) into a standard 2D texture via an FBO.
 * Pass 2: Applies the custom post-processing shader (e.g., SGSR1) on the 2D texture and renders it to the display.
 */
public class GlesPassthroughBridge implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = "GlesBridge";

    // --- PASS 1: OES to 2D Texture ---
    // Vertex shader for the first pass (hardware decode to FBO)
    private static final String BLIT_VERTEX_SHADER =
            "#version 310 es\n" +
            "uniform mat4 uSTMatrix;\n" +
            "in vec4 aPosition;\n" +
            "in vec4 aTexCoord;\n" +
            "out vec2 vTexCoord;\n" +
            "void main() {\n" +
            "  gl_Position = aPosition;\n" +
            "  vTexCoord = (uSTMatrix * aTexCoord).xy;\n" + // Apply the SurfaceTexture transform matrix
            "}\n";

    // Fragment shader for the first pass (reads from OES texture)
    private static final String BLIT_FRAGMENT_SHADER =
            "#version 310 es\n" +
            "#extension GL_OES_EGL_image_external_essl3 : require\n" +
            "precision mediump float;\n" +
            "in vec2 vTexCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "  fragColor = texture(sTexture, vTexCoord);\n" +
            "}\n";

    // Fallback shader used if the external custom shader (e.g., SGSR) fails to load
    private static final String FALLBACK_POST_FRAGMENT_SHADER =
            "#version 310 es\n" +
            "precision mediump float;\n" +
            "in vec2 v_texCoord;\n" +
            "in vec2 v_imgCoord;\n" +
            "uniform sampler2D sTexture;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "  fragColor = texture(sTexture, v_texCoord);\n" +
            "}\n";

    private final Context context;

    // EGL State
    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
    
    // Shader Programs
    private int blitProgram = 0;
    private int postProgram = 0;
    
    // Textures and Framebuffers
    private int oesTextureId; // Texture ID for the hardware decoder output
    private int fboTextureId; // Standard 2D texture ID attached to the FBO
    private int fboId;        // Framebuffer Object ID for the first pass
    
    // Surface management
    private SurfaceTexture surfaceTexture;
    private Surface decoderSurface;
    private final float[] transformMatrix = new float[16];

    // Handles for Pass 1 (Blit)
    private int blit_uSTMatrixHandle;
    private int blit_aPositionHandle;
    private int blit_aTexCoordHandle;
    private int blit_sTextureHandle;

    // Handles for Pass 2 (Custom Shader)
    private int post_aPositionHandle;
    private int post_aTexCoordHandle;
    private int post_sTextureHandle;

    // Full-screen quad vertices (Position X, Y, Texture X, Y)
    private static final float[] VERTICES = {
            -1.0f, -1.0f, 0.0f, 0.0f, // Bottom Left
             1.0f, -1.0f, 1.0f, 0.0f, // Bottom Right
            -1.0f,  1.0f, 0.0f, 1.0f, // Top Left
             1.0f,  1.0f, 1.0f, 1.0f, // Top Right
    };
    private FloatBuffer vertexBuffer;
    
    // Concurrency and synchronization
    private final Object frameAvailableLock = new Object();
    private boolean frameAvailable = false;
    
    // Resolution information
    private int streamWidth, streamHeight;
    private int displayWidth, displayHeight;

    public GlesPassthroughBridge(Context context) {
        this.context = context;
        
        // Allocate direct memory for the vertex buffer
        vertexBuffer = ByteBuffer.allocateDirect(VERTICES.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        vertexBuffer.put(VERTICES).position(0);
    }

    /**
     * Utility method to load a shader from the assets folder.
     * @param fileName The name of the shader file in assets.
     * @return The string content of the shader, or null if loading failed.
     */
    private String loadAsset(String fileName) {
        StringBuilder sb = new StringBuilder();
        try (InputStream is = context.getAssets().open(fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            LimeLog.severe(TAG + ": Failed to load asset: " + fileName + " - " + e.getMessage());
            return null;
        }
        return sb.toString();
    }

    /**
     * Helper method to check for OpenGL errors and log them.
     * @param op The operation that was just performed.
     */
    private void checkGlError(String op) {
        int error;
        while ((error = GLES31.glGetError()) != GLES31.GL_NO_ERROR) {
            LimeLog.severe(TAG + ": " + op + ": glError " + error);
        }
    }

    /**
     * Initializes the EGL context, the shader programs, and the rendering surfaces.
     * Must be called before any rendering takes place.
     * 
     * @param targetSurface The final display surface (e.g., from a SurfaceView).
     * @param streamWidth The width of the incoming video stream.
     * @param streamHeight The height of the incoming video stream.
     */
    public void initialize(Surface targetSurface, int streamWidth, int streamHeight) {
        this.streamWidth = streamWidth;
        this.streamHeight = streamHeight;

        // Initialize EGL Display
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version = new int[2];
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1);

        // Configure EGL for OpenGL ES 3.0+
        int[] configAttribs = {
                EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfigs = new int[1];
        EGL14.eglChooseConfig(eglDisplay, configAttribs, 0, configs, 0, 1, numConfigs, 0);

        // Create EGL Context
        int[] contextAttribs = { EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE };
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0);
        
        // Create EGL Surface connected to the target display
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], targetSurface, new int[]{EGL14.EGL_NONE}, 0);
        
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            int error = EGL14.eglGetError();
            LimeLog.severe(TAG + ": eglCreateWindowSurface failed: " + error);
            throw new IllegalStateException("eglCreateWindowSurface failed with error " + error);
        }
        
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            int error = EGL14.eglGetError();
            LimeLog.severe(TAG + ": eglMakeCurrent failed: " + error);
            throw new IllegalStateException("eglMakeCurrent failed with error " + error);
        }

        // Disable VSync synchronization inside EGL to avoid adding latency
        EGL14.eglSwapInterval(eglDisplay, 0);

        // Query the actual dimensions of the display surface
        int[] queryW = new int[1], queryH = new int[1];
        EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL14.EGL_WIDTH, queryW, 0);
        EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL14.EGL_HEIGHT, queryH, 0);
        this.displayWidth = queryW[0];
        this.displayHeight = queryH[0];

        // 1. Setup Blit Program (OES -> 2D Texture)
        blitProgram = createProgram(BLIT_VERTEX_SHADER, BLIT_FRAGMENT_SHADER);
        blit_uSTMatrixHandle = GLES31.glGetUniformLocation(blitProgram, "uSTMatrix");
        blit_aPositionHandle = GLES31.glGetAttribLocation(blitProgram, "aPosition");
        blit_aTexCoordHandle = GLES31.glGetAttribLocation(blitProgram, "aTexCoord");
        blit_sTextureHandle = GLES31.glGetUniformLocation(blitProgram, "sTexture");

        // 2. Setup Post-Process Program (Custom Shader)
        setupPostProgram();

        // 3. Generate Textures
        int[] textures = new int[2];
        GLES31.glGenTextures(2, textures, 0);
        oesTextureId = textures[0];
        fboTextureId = textures[1];

        // Configure OES Texture (used by MediaCodec)
        GLES31.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId);
        GLES31.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_NEAREST);
        GLES31.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_NEAREST);
        GLES31.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_CLAMP_TO_EDGE);
        GLES31.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_CLAMP_TO_EDGE);

        // Configure Standard 2D Texture (used as FBO color attachment)
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, fboTextureId);
        GLES31.glTexImage2D(GLES31.GL_TEXTURE_2D, 0, GLES31.GL_RGBA, streamWidth, streamHeight, 0, GLES31.GL_RGBA, GLES31.GL_UNSIGNED_BYTE, null);
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MIN_FILTER, GLES31.GL_LINEAR);
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_MAG_FILTER, GLES31.GL_LINEAR);
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_S, GLES31.GL_CLAMP_TO_EDGE);
        GLES31.glTexParameteri(GLES31.GL_TEXTURE_2D, GLES31.GL_TEXTURE_WRAP_T, GLES31.GL_CLAMP_TO_EDGE);

        // 4. Setup Framebuffer Object (FBO) for Pass 1
        int[] fbos = new int[1];
        GLES31.glGenFramebuffers(1, fbos, 0);
        fboId = fbos[0];
        GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, fboId);
        GLES31.glFramebufferTexture2D(GLES31.GL_FRAMEBUFFER, GLES31.GL_COLOR_ATTACHMENT0, GLES31.GL_TEXTURE_2D, fboTextureId, 0);
        
        // Ensure the Framebuffer is complete
        int status = GLES31.glCheckFramebufferStatus(GLES31.GL_FRAMEBUFFER);
        if (status != GLES31.GL_FRAMEBUFFER_COMPLETE) {
            LimeLog.severe(TAG + ": Framebuffer incomplete: " + status);
        }
        // Unbind FBO
        GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, 0);

        // Create SurfaceTexture to receive hardware decoded frames
        surfaceTexture = new SurfaceTexture(oesTextureId);
        surfaceTexture.setOnFrameAvailableListener(this);
        decoderSurface = new Surface(surfaceTexture);
        
        // IMPORTANT: Unbind context so it can be picked up by the rendering thread!
        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);

        LimeLog.info(TAG + ": GLES 3.1 2-Pass Bridge initialized: Stream=" + streamWidth + "x" + streamHeight + " Display=" + displayWidth + "x" + displayHeight);
    }

    private void setupPostProgram() {
        if (postProgram != 0) {
            GLES31.glDeleteProgram(postProgram);
            postProgram = 0;
        }

        String postSource = loadAsset("sgsr1_shader_mobile_edge_direction.frag");

        // Define stream size resolutions constants directly into the GLSL Code at compile time
        // This leverages "Constant Folding", an optimization where the GPU calculates all
        // resolutions and math functions explicitly at compile time instead of per-frame.
        String compileTimeDefines = "\n#define SRC_W " + streamWidth + ".0\n" +
                                    "#define SRC_H " + streamHeight + ".0\n" +
                                    "#define INV_SRC_W " + (1.0f / streamWidth) + "\n" +
                                    "#define INV_SRC_H " + (1.0f / streamHeight) + "\n";

        if (postSource == null || postSource.trim().isEmpty()) {
            LimeLog.warning(TAG + ": sgsr1_shader_mobile_edge_direction.frag is missing or empty. Using fallback shader.");
            postSource = FALLBACK_POST_FRAGMENT_SHADER;
        } else {
            // Inject the dynamic resolutions into the source code of the Fragment Shader.
            postSource = postSource.replaceFirst("(#version 310 es)", "$1" + compileTimeDefines);
        }

        // Vertex shader for the final post-processing pass
        // It has been highly optimized to pre-calculate image coordinates across the 4 corners
        // of the vertex, allowing the hardware interpolator to naturally pass the coordinate
        // per-fragment down into the pipeline for completely free calculations.
        String postVertexShader =
                "#version 310 es\n" +
                compileTimeDefines +
                "in vec4 aPosition;\n" +
                "in vec4 aTexCoord;\n" +
                "out vec2 v_texCoord;\n" +
                "out vec2 v_imgCoord;\n" +
                "void main() {\n" +
                "  gl_Position = aPosition;\n" +
                "  v_texCoord = aTexCoord.xy;\n" +
                "  v_imgCoord = (aTexCoord.xy * vec2(SRC_W, SRC_H)) + vec2(-0.5, 0.5);\n" +
                "}\n";

        postProgram = createProgram(postVertexShader, postSource);
        post_aPositionHandle = GLES31.glGetAttribLocation(postProgram, "aPosition");
        post_aTexCoordHandle = GLES31.glGetAttribLocation(postProgram, "aTexCoord");
        
        // Find sampler uniform (supports different naming conventions for flexibility)
        post_sTextureHandle = GLES31.glGetUniformLocation(postProgram, "sTexture");
        if (post_sTextureHandle == -1) post_sTextureHandle = GLES31.glGetUniformLocation(postProgram, "uTexture");
        if (post_sTextureHandle == -1) post_sTextureHandle = GLES31.glGetUniformLocation(postProgram, "Source");
        if (post_sTextureHandle == -1) post_sTextureHandle = GLES31.glGetUniformLocation(postProgram, "ps0"); // Used by SGSR1
    }

    /**
     * @return The surface to pass to the MediaCodec decoder.
     */
    public Surface getDecoderSurface() {
        return decoderSurface;
    }

    /**
     * Callback fired by Android when a new frame is decoded and ready to be processed.
     */
    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        synchronized (frameAvailableLock) {
            frameAvailable = true;
            frameAvailableLock.notifyAll(); // Wake up the rendering thread
        }
    }

    /**
     * Renders a single frame. Should be called repeatedly from a dedicated rendering thread.
     * 
     * @param presentationTimeNanos The presentation time for the current frame.
     */
    public void renderFrame(long presentationTimeNanos) {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) return;

        // Wait for a new frame to be available from the decoder
        synchronized (frameAvailableLock) {
            long startTime = System.currentTimeMillis();
            while (!frameAvailable) {
                try {
                    frameAvailableLock.wait(50);
                    // Timeout after 50ms to prevent deadlocks
                    if (System.currentTimeMillis() - startTime > 50) break;
                } catch (InterruptedException e) { return; }
            }
            frameAvailable = false;
        }

        // Ensure the EGL context is current on the rendering thread
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            LimeLog.severe(TAG + ": eglMakeCurrent failed in renderFrame");
            return;
        }
        
        // Consume the new frame from the decoder
        surfaceTexture.updateTexImage();
        surfaceTexture.getTransformMatrix(transformMatrix);

        // ==========================================================
        // PASS 1: Decode to 2D Texture (FBO)
        // ==========================================================
        
        // Bind the FBO to render off-screen
        GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, fboId);
        GLES31.glViewport(0, 0, streamWidth, streamHeight);
        GLES31.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT);

        GLES31.glUseProgram(blitProgram);
        
        // Pass the SurfaceTexture transform matrix to correct orientation
        if (blit_uSTMatrixHandle >= 0) GLES31.glUniformMatrix4fv(blit_uSTMatrixHandle, 1, false, transformMatrix, 0);
        if (blit_sTextureHandle >= 0) GLES31.glUniform1i(blit_sTextureHandle, 0);

        // Bind the hardware OES texture
        GLES31.glActiveTexture(GLES31.GL_TEXTURE0);
        GLES31.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId);
        
        // Draw a full-screen quad to execute the blit
        drawQuad(blit_aPositionHandle, blit_aTexCoordHandle);

        // ==========================================================
        // PASS 2: Post-Process to Screen
        // ==========================================================
        
        // Bind the default framebuffer to render to the display
        GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER, 0);
        GLES31.glViewport(0, 0, displayWidth, displayHeight);
        GLES31.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT);

        GLES31.glUseProgram(postProgram);

        if (post_sTextureHandle >= 0) GLES31.glUniform1i(post_sTextureHandle, 0);

        // Bind the 2D texture populated during Pass 1
        GLES31.glActiveTexture(GLES31.GL_TEXTURE0);
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D, fboTextureId);

        // Draw the final post-processed image
        drawQuad(post_aPositionHandle, post_aTexCoordHandle);
        
        // Present the frame to the display
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, presentationTimeNanos);
        EGL14.eglSwapBuffers(eglDisplay, eglSurface);
    }

    /**
     * Draws a standard full-screen quad using the allocated vertex buffer.
     * 
     * @param posHandle The GL attribute handle for vertex positions.
     * @param texHandle The GL attribute handle for texture coordinates.
     */
    private void drawQuad(int posHandle, int texHandle) {
        if (posHandle >= 0) {
            GLES31.glEnableVertexAttribArray(posHandle);
            vertexBuffer.position(0);
            // Each vertex is 4 floats: [PosX, PosY, TexU, TexV]. Stride = 4 * 4 bytes = 16.
            GLES31.glVertexAttribPointer(posHandle, 2, GLES31.GL_FLOAT, false, 16, vertexBuffer);
        }
        if (texHandle >= 0) {
            GLES31.glEnableVertexAttribArray(texHandle);
            vertexBuffer.position(2); // Offset to start at TexU
            GLES31.glVertexAttribPointer(texHandle, 2, GLES31.GL_FLOAT, false, 16, vertexBuffer);
        }

        // Execute the draw call
        GLES31.glDrawArrays(GLES31.GL_TRIANGLE_STRIP, 0, 4);

        // Cleanup attribute bindings
        if (posHandle >= 0) GLES31.glDisableVertexAttribArray(posHandle);
        if (texHandle >= 0) GLES31.glDisableVertexAttribArray(texHandle);
    }

    /**
     * Compiles and links a complete GLSL program from vertex and fragment shader source code.
     */
    private int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES31.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) return 0;
        int fragmentShader = loadShader(GLES31.GL_FRAGMENT_SHADER, fragmentSource);
        if (fragmentShader == 0) return 0;
        
        int program = GLES31.glCreateProgram();
        if (program != 0) {
            GLES31.glAttachShader(program, vertexShader);
            GLES31.glAttachShader(program, fragmentShader);
            GLES31.glLinkProgram(program);
            
            int[] linkStatus = new int[1];
            GLES31.glGetProgramiv(program, GLES31.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES31.GL_TRUE) {
                LimeLog.severe(TAG + ": Could not link program: " + GLES31.glGetProgramInfoLog(program));
                GLES31.glDeleteProgram(program);
                program = 0;
            }
        }
        return program;
    }

    /**
     * Compiles a single shader object.
     */
    private int loadShader(int type, String shaderCode) {
        int shader = GLES31.glCreateShader(type);
        GLES31.glShaderSource(shader, shaderCode);
        GLES31.glCompileShader(shader);
        
        int[] compiled = new int[1];
        GLES31.glGetShaderiv(shader, GLES31.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            LimeLog.severe(TAG + ": Could not compile shader " + type + ": " + GLES31.glGetShaderInfoLog(shader));
            GLES31.glDeleteShader(shader);
            shader = 0;
        }
        return shader;
    }

    /**
     * Frees all allocated OpenGL and EGL resources.
     */
    public void release() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface);
            }
            EGL14.eglDestroyContext(eglDisplay, eglContext);
            EGL14.eglReleaseThread();
            EGL14.eglTerminate(eglDisplay);
        }
        if (surfaceTexture != null) {
            surfaceTexture.release();
        }
        if (decoderSurface != null) {
            decoderSurface.release();
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY;
        eglContext = EGL14.EGL_NO_CONTEXT;
        eglSurface = EGL14.EGL_NO_SURFACE;
    }
}