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
import android.opengl.Matrix;

/**
 * A wrapper around an OpenGL texture object optionally backed by an EGLImage.
 */
/* package */ class TextureHandle {

    public static final int GL_TEXTURE_EXTERNAL_OES = 0x8D65;

    private static final String TAG = "TextureHandle";

    /**
     * Whether the texture is a normal 2D target or backed by an external EGLImage.
     */
    public enum TextureType {
        TEXTURE_2D,
        TEXTURE_EXTERNAL
    }

    private final int textureId;
    private final TextureType type;
    private final float[] textureMatrix = new float[16];

    /**
     * Creates a TextureHandle.
     *
     * @param id The GL texture ID.
     */
    public TextureHandle(int id) {
        textureId = id;
        type = TextureType.TEXTURE_2D;
        Matrix.setIdentityM(textureMatrix, 0);
    }

    /**
     * Creates a TextureHandle.
     *
     * @param id The GL texture id.
     * @param t The TextureType of the texture.
     */
    public TextureHandle(int id, TextureType t) {
        textureId = id;
        type = t;
        Matrix.setIdentityM(textureMatrix, 0);
    }

    /**
     * Creates a TextureHandle.
     *
     * @param id The GL texture id.
     * @param t The TextureType of the texture.
     * @param matrix The transform to use for the texture.
     */
    public TextureHandle(int id, TextureType t, float[] matrix) {
        textureId = id;
        type = t;
        System.arraycopy(matrix, 0, textureMatrix, 0, textureMatrix.length);
    }

    /**
     * Sets the transform to use for this texture.
     * @param matrix The texture matrix to use for the texture.
     */
    public void setTextureMatrix(float[] matrix) {
        if (textureMatrix.length != matrix.length) {
            throw new IllegalArgumentException("Wrong size for texture matrix.");
        }
        System.arraycopy(matrix, 0, textureMatrix, 0, textureMatrix.length);
    }

    /**
     * Gets the transform to used for this texture.
     * @return The texture matrix used the texture.
     */
    public float[] getTextureMatrix() {
        return textureMatrix;
    }

    /**
     * Binds the texture to the current context, using the 0th bind point (GL_TEXTURE0).
     */
    public void bind() {
        bind(0);
    }

    /**
     * Binds the texture to the current context.
     * @param bindPoint The bind point index to use, 0-based.
     */
    public void bind(int bindPoint) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + bindPoint);
        int glTextureType =
                type == TextureType.TEXTURE_EXTERNAL ? GL_TEXTURE_EXTERNAL_OES : GLES20.GL_TEXTURE_2D;
        GLES20.glBindTexture(glTextureType, textureId);
        GLUtil.checkGlError(TAG, "bind texture, layer " + bindPoint);
    }

    /**
     * Binds the texture transform to the current program.
     * @param matrixUniform The uniform location of the texture matrix.
     */
    public void bindTextureMatrix(int matrixUniform) {
        GLES20.glUniformMatrix4fv(matrixUniform, 1, false, textureMatrix, 0);
    }

    /**
     * Gets the GL ID of the texture.
     * @return The GL ID of the texture.
     */
    public int getId() {
        return textureId;
    }

    /**
     * Gets the TextureType of the texture.
     * @return The TextureType the texture.
     */
    public TextureType getType() {
        return type;
    }
}