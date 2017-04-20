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
package com.limelight.vr;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Draws the video scene with a transparent quad where the video frame will be rendered. */
public class VideoScene {
    private static final String TAG = "VideoScreen";

    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n"
                    + "attribute vec4 aPosition;\n"
                    + "attribute vec4 aTextureCoord;\n"
                    + "varying vec2 vTextureCoord;\n"
                    + "void main() {\n"
                    + "  gl_Position = uMVPMatrix * aPosition;\n"
                    + "  vTextureCoord = aTextureCoord.st;\n"
                    + "}\n";

    private static final String SPRITE_FRAGMENT_SHADER =
            "precision mediump float;\n"
                    + "varying vec2 vTextureCoord;\n"
                    + "uniform sampler2D imageTexture;\n"
                    + "void main() {\n"
                    + "  gl_FragColor = texture2D(imageTexture, vTextureCoord);\n"
                    + "}\n";

    private static final String VIDEO_HOLE_FRAGMENT_SHADER =
            "precision mediump float;\n"
                    + "varying vec2 vTextureCoord;\n"
                    + "void main() {\n"
                    + "  gl_FragColor = vec4(0., 0., 0., 0.);\n"
                    + "}\n";

    private static final float[] SPRITE_VERTICES_DATA = {
            // X,   Y,    Z,    U, V
            -1.0f,  1.0f, 0.0f, 1, 1,
            1.0f,  1.0f, 0.0f, 0, 1,
            -1.0f, -1.0f, 0.0f, 1, 0,
            1.0f, -1.0f, 0.0f, 0, 0,
    };

    private static final int FLOAT_SIZE_BYTES = 4;
    private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
    private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
    private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;

    private final int videoHoleProgram;
    private final int spriteProgram;
    private final TextureHandle backgroundTexture;
    private final FloatBuffer vertices;
    private final int numVertices;

    private volatile boolean isVideoPlaying;

    /**
     * Sets up the drawing object data for use in an OpenGL ES context.
     *
     * @param backgroundTexHandle The TextureHandle that holds a placeholder image, shown before
     *     playback starts.
     */
    public VideoScene(TextureHandle backgroundTexHandle) {
        this.backgroundTexture = backgroundTexHandle;
        isVideoPlaying = false;

        this.vertices =
                ByteBuffer.allocateDirect(SPRITE_VERTICES_DATA.length * FLOAT_SIZE_BYTES)
                        .order(ByteOrder.nativeOrder())
                        .asFloatBuffer();
        this.vertices.put(SPRITE_VERTICES_DATA).position(0);
        this.numVertices = SPRITE_VERTICES_DATA.length / 5;

        this.videoHoleProgram = GLUtil.createProgram(VERTEX_SHADER, VIDEO_HOLE_FRAGMENT_SHADER);
        if (this.videoHoleProgram == 0) {
            throw new RuntimeException("Could not create video program");
        }
        this.spriteProgram = GLUtil.createProgram(VERTEX_SHADER, SPRITE_FRAGMENT_SHADER);
        if (this.spriteProgram == 0) {
            throw new RuntimeException("Could not create sprite program");
        }
    }

    /**
     * Sets whether video playback has started. If video playback has not started, the loading splash
     * screen is drawn.
     *
     * @param hasPlaybackStarted True if video is playing.
     */
    public void setHasVideoPlaybackStarted(boolean hasPlaybackStarted) {
        isVideoPlaying = hasPlaybackStarted;
    }

    /**
     * Draws the video screen.
     *
     * @param mvpMatrix The transformation matrix to use for the video screen.
     */
    public void draw(float[] mvpMatrix) {
        int program;
        TextureHandle texture = null;
        if (isVideoPlaying) {
            program = videoHoleProgram;
        } else {
            program = spriteProgram;
            texture = backgroundTexture;
        }

        GLES20.glUseProgram(program);
        GLUtil.checkGlError(TAG, "glUseProgram");

        if (texture != null && program == spriteProgram) {
            texture.bind();
            GLUtil.checkGlError(TAG, "texture.bind");
        }

        final int positionAtribute = GLES20.glGetAttribLocation(program, "aPosition");
        GLUtil.checkGlError(TAG, "glGetAttribLocation aPosition");

        vertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
        GLES20.glVertexAttribPointer(
                positionAtribute, 3, GLES20.GL_FLOAT, false, TRIANGLE_VERTICES_DATA_STRIDE_BYTES, vertices);
        GLUtil.checkGlError(TAG, "glVertexAttribPointer position");
        GLES20.glEnableVertexAttribArray(positionAtribute);
        GLUtil.checkGlError(TAG, "glEnableVertexAttribArray position handle");

        final int uvAttribute = GLES20.glGetAttribLocation(program, "aTextureCoord");
        GLUtil.checkGlError(TAG, "glGetAttribLocation aTextureCoord");
        if (uvAttribute >= 0) {
            vertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
            GLES20.glVertexAttribPointer(
                    uvAttribute, 2, GLES20.GL_FLOAT, false, TRIANGLE_VERTICES_DATA_STRIDE_BYTES, vertices);
            GLUtil.checkGlError(TAG, "glVertexAttribPointer uv handle");
            GLES20.glEnableVertexAttribArray(uvAttribute);
            GLUtil.checkGlError(TAG, "glEnableVertexAttribArray uv handle");
        }

        final int uMVPMatrix = GLES20.glGetUniformLocation(program, "uMVPMatrix");
        GLUtil.checkGlError(TAG, "glGetUniformLocation uMVPMatrix");
        if (uMVPMatrix == -1) {
            throw new RuntimeException("Could not get uniform location for uMVPMatrix");
        }
        GLES20.glUniformMatrix4fv(uMVPMatrix, 1, false, mvpMatrix, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, numVertices);

        GLES20.glDisableVertexAttribArray(positionAtribute);
        if (uvAttribute >= 0) {
            GLES20.glDisableVertexAttribArray(uvAttribute);
        }

        GLUtil.checkGlError(TAG, "glDrawArrays");
    }
}