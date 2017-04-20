package com.limelight.vr;

/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.content.Context;
import android.graphics.Point;
import android.graphics.RectF;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView.Renderer;
import android.opengl.Matrix;

import com.google.vr.ndk.base.BufferSpec;
import com.google.vr.ndk.base.BufferViewport;
import com.google.vr.ndk.base.BufferViewportList;
import com.google.vr.ndk.base.Frame;
import com.google.vr.ndk.base.GvrApi;
import com.google.vr.ndk.base.SwapChain;
import com.limelight.R;

import java.util.concurrent.TimeUnit;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * The main app renderer. Draws the scene the video is displayed in a color buffer, and signals to
 * the the GvrApi to render video from the ExternalSurface at the specified ID.
 */
public class VideoSceneRenderer implements Renderer {

    private static final String TAG = VideoSceneRenderer.class.getSimpleName();

    // The buffer index of the color buffer, The app's frame color data should be drawn when this
    // buffer is bound.
    public static final int INDEX_COLOR_BUFFER = 0;
    // The number of textured quads to draw in the scene. Used for the loading texture.
    private static final int NUM_SPRITE_TEXTURES = 1;
    // The scene's clipping planes.
    private static final float NEAR_PLANE = 50.0f;
    private static final float FAR_PLANE = 1000.0f;

    private final GvrApi api;
    private final Context context;
    private final BufferViewportList recommendedList;
    private final BufferViewportList viewportList;
    private final BufferViewport scratchViewport;

    private final long predictionOffsetNanos;

    private SwapChain swapChain;
    private volatile int videoSurfaceID;
    private VideoScene videoScene;
    private boolean shouldShowVideo;

    private final float[] head = new float[16];
    private final float[] eyeFromHead = new float[16];
    private final float[] eyePerspective = new float[16];
    private final float[][] eyeView = new float[2][16];
    private final float[][] eyeFromQuad = new float[2][16];
    private final float[] worldFromQuad = new float[16];
    private final float[] viewFromQuad = new float[16];
    private final RectF eyeFov = new RectF();
    private final RectF eyeUv = new RectF();
    private final RectF videoUv = new RectF(0.f, 1.f, 1.f, 0.f);
    private final Point targetSize = new Point();

    public VideoSceneRenderer(Context context, GvrApi api) {
        this.context = context;
        this.api = api;
        recommendedList = api.createBufferViewportList();
        viewportList = api.createBufferViewportList();
        scratchViewport = api.createBufferViewport();
        predictionOffsetNanos = TimeUnit.MILLISECONDS.toNanos(50);

        videoSurfaceID = BufferViewport.EXTERNAL_SURFACE_ID_NONE;
        shouldShowVideo = false;
        Matrix.setIdentityM(worldFromQuad, 0);
    }

    /** Shuts down the renderer. Can be called from any thread. */
    public void shutdown() {
        recommendedList.shutdown();
        viewportList.shutdown();
        scratchViewport.shutdown();
        if (swapChain != null) {
            swapChain.shutdown();
        }
    }

    /**
     * Sets the transformation that positions a quad with vertices (1, 1, 0), (1, -1, 0), (-1, 1, 0),
     * (-1, -1, 0) in the desired place in world space. The video frame will be rendered at this
     * quad's position.
     *
     * <p>Note: this should be set before the GL thread is started.
     *
     * @param transform The quad's transform in world space.
     */
    public void setVideoTransform(float[] transform) {
        System.arraycopy(transform, 0, worldFromQuad, 0, 16);
    }

    /**
     * Sets the ID of the video Surface. Used to signal to the {@link GvrApi} to sample from the
     * buffer at the specified ID when rendering the video Surface.
     *
     * <p>Note: this can be set at any time. If there are no initialized ExternalSurfaces or the
     * ExternalSurface buffer at the ID is not ready, the GvrApi skips rendering the Surface.
     *
     * @param id The ID of the video Surface. BufferViewport.EXTERNAL_SURFACE_ID_NONE if the
     *     GvrApi should not sample from any Surface.
     */
    public void setVideoSurfaceId(int id) {
        videoSurfaceID = id;
    }

    /**
     * Signals whether video playback has started. The video scene renders a loading texture when
     * hasPlaybackStarted is false, and renders alpha zero where video should appear when true.
     *
     * <p>Note: this must be called from the GL thread.
     *
     * @param hasPlaybackStarted True if video playback has started and frames are being produced.
     */
    public void setHasVideoPlaybackStarted(boolean hasPlaybackStarted) {
        shouldShowVideo = hasPlaybackStarted;
        if (videoScene != null) {
            videoScene.setHasVideoPlaybackStarted(shouldShowVideo);
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // Initialize the SwapChain.
        api.initializeGl();
        GLUtil.checkGlError(TAG, "initializeGl");

        api.getMaximumEffectiveRenderTargetSize(targetSize);

        BufferSpec[] specList = new BufferSpec[1];
        BufferSpec bufferSpec = api.createBufferSpec();
        bufferSpec.setSize(targetSize);
        specList[INDEX_COLOR_BUFFER] = bufferSpec;

        swapChain = api.createSwapChain(specList);
        for (BufferSpec spec : specList) {
            spec.shutdown();
        }

        initVideoScene();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {}

    @Override
    public void onDrawFrame(GL10 gl) {
        Frame frame = swapChain.acquireFrame();

        api.getHeadSpaceFromStartSpaceRotation(head, System.nanoTime() + predictionOffsetNanos);
        for (int eye = 0; eye < 2; ++eye) {
            api.getEyeFromHeadMatrix(eye, eyeFromHead);
            Matrix.multiplyMM(eyeView[eye], 0, eyeFromHead, 0, head, 0);
            Matrix.multiplyMM(eyeFromQuad[eye], 0, eyeView[eye], 0, worldFromQuad, 0);
        }
        // Populate the BufferViewportList to describe to the GvrApi how the color buffer
        // and video frame ExternalSurface buffer should be rendered. The eyeFromQuad matrix
        // describes how the video Surface frame should be transformed and rendered in eye space.
        populateBufferViewportList();

        drawVideoScene(gl, frame);

        // Submit the color buffer the and video Surface frame info to the GvrApi.
        frame.submit(viewportList, head);
        GLUtil.checkGlError(TAG, "submit frame");
    }

    private void initVideoScene() {
        int[] textureIds = new int[NUM_SPRITE_TEXTURES];
        GLES20.glGenTextures(NUM_SPRITE_TEXTURES, textureIds, 0);

        // Create the texture used by the video background logo.
        GLUtil.createResourceTexture(context, textureIds[0], R.raw.loading_bg);
        final TextureHandle videoBackgroundTexture = new TextureHandle(textureIds[0]);

        // Initialize the video scene. Draws the app's color buffer.
        videoScene = new VideoScene(videoBackgroundTexture);
        videoScene.setHasVideoPlaybackStarted(shouldShowVideo);

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
    }

    private void populateBufferViewportList() {
        // Get the recommended BufferViewports. The recommended list should be populated with two
        // BufferViewports, one per eye.
        api.getRecommendedBufferViewports(recommendedList);
        // To render video, for each eye, add a BufferViewport for the video surface. The video
        // viewports have to be at the start of the list, so that they are under the color
        // viewports.
        // Note: if the buffer is not yet valid (i.e. no video frame has been produced yet), the
        // GvrApi instance will skip rendering the video layer until it is ready. This renderer
        // could also wait for the first ExternalSurface.onFrameAvaliable callback before including
        // the BufferViewports that reference the video Surface.
        for (int eye = 0; eye < 2; eye++) {
            recommendedList.get(eye, scratchViewport);
            scratchViewport.setSourceUv(videoUv);
            scratchViewport.setSourceBufferIndex(BufferViewport.BUFFER_INDEX_EXTERNAL_SURFACE);
            scratchViewport.setExternalSurfaceId(videoSurfaceID);
            scratchViewport.setTransform(eyeFromQuad[eye]);
            viewportList.set(eye, scratchViewport);
        }
        // Add the color viewport for each eye.
        for (int eye = 0; eye < 2; eye++) {
            // Copy the color viewport from the recommended list without changes.
            recommendedList.get(eye, scratchViewport);
            viewportList.set(2 + eye, scratchViewport);
        }
    }

    private void drawVideoScene(GL10 gl, Frame frame) {
        // Draw the color buffer.
        frame.bindBuffer(INDEX_COLOR_BUFFER);
        // Draw background color
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        GLUtil.checkGlError(TAG, "new frame");

        for (int eye = 0; eye < 2; ++eye) {
            // The viewport list contents are:
            // 0: left eye video, 1: right eye video, 2: left eye color, 3: right eye color.
            // The color viewport will be drawn on top of the video layer and will contain a transparent
            // hole where the video will be. This can be used to render controls on top of the video.
            int colorViewportIndex = 2 + eye;
            viewportList.get(colorViewportIndex, scratchViewport);
            scratchViewport.getSourceUv(eyeUv);
            scratchViewport.getSourceFov(eyeFov);

            int x = (int) (eyeUv.left * targetSize.x);
            int y = (int) (eyeUv.bottom * targetSize.y);
            int width = (int) (eyeUv.width() * targetSize.x);
            int height = (int) (-eyeUv.height() * targetSize.y);
            gl.glViewport(x, y, width, height);

            float l = (float) -Math.tan(Math.toRadians(eyeFov.left)) * NEAR_PLANE;
            float r = (float) Math.tan(Math.toRadians(eyeFov.right)) * NEAR_PLANE;
            float b = (float) -Math.tan(Math.toRadians(eyeFov.bottom)) * NEAR_PLANE;
            float t = (float) Math.tan(Math.toRadians(eyeFov.top)) * NEAR_PLANE;
            Matrix.frustumM(eyePerspective, 0, l, r, b, t, NEAR_PLANE, FAR_PLANE);
            Matrix.multiplyMM(viewFromQuad, 0, eyePerspective, 0, eyeFromQuad[eye], 0);

            // Draw the video scene.
            videoScene.draw(viewFromQuad);

            GLUtil.checkGlError(TAG, "draw eye");
        }
        frame.unbind();
    }
}