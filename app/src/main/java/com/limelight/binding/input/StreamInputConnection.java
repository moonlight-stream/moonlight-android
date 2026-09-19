package com.limelight.binding.input;

import android.view.View;
import android.view.inputmethod.BaseInputConnection;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;

/**
 * Custom InputConnection that intercepts commitText() from the Android IME
 * (voice dictation, autocomplete, swipe typing) and routes the committed text
 * to the remote host via the Moonlight streaming protocol.
 *
 * This is an additive fix — individual KeyEvent handling via onKeyDown/onKeyUp
 * continues to work unchanged through StreamView.onKeyPreIme().
 *
 * Copyright (C) 2024 Jon Gutierrez / MN Compute
 * License: GPL-3.0-or-later (same as upstream moonlight-android)
 */
public class StreamInputConnection extends BaseInputConnection {
    private final NvConnection conn;

    // VK key codes in Moonlight's wire format: (KEY_PREFIX << 8) | windowsVkCode
    // KEY_PREFIX = 0x80 (see KeyboardTranslator.KEY_PREFIX)
    // VK_BACK = 0x08 (backspace), VK_DELETE = 0x2E (forward delete)
    private static final short VK_BACKSPACE = (short) ((0x80 << 8) | 0x08);
    private static final short VK_DELETE    = (short) ((0x80 << 8) | 0x2E);

    public StreamInputConnection(View targetView, boolean fullEditor, NvConnection conn) {
        super(targetView, fullEditor);
        this.conn = conn;
    }

    /**
     * Called when the IME finalizes text input — voice dictation result,
     * autocomplete selection, swipe-typed word, etc.
     * Route the full text string to the remote host.
     */
    @Override
    public boolean commitText(CharSequence text, int newCursorPosition) {
        if (text != null && text.length() > 0 && conn != null) {
            // sendUtf8Text accepts full strings — no need for char-by-char loop
            conn.sendUtf8Text(text.toString());
        }
        return true;
    }

    /**
     * Called with in-progress (composing) text while the user is still
     * dictating or typing. Don't send this — only send on commitText()
     * when the text is finalized.
     */
    @Override
    public boolean setComposingText(CharSequence text, int newCursorPosition) {
        // Intentionally do nothing with composing text
        return true;
    }

    /**
     * Called when the IME finishes composition. Nothing to do here since
     * we handle everything in commitText().
     */
    @Override
    public boolean finishComposingText() {
        return true;
    }

    /**
     * Called by the IME to delete surrounding text (e.g., voice correction
     * "delete that", or backspace from swipe keyboard).
     * Send the appropriate number of backspace or delete key events.
     */
    @Override
    public boolean deleteSurroundingText(int beforeLength, int afterLength) {
        if (conn != null) {
            for (int i = 0; i < beforeLength; i++) {
                conn.sendKeyboardInput(VK_BACKSPACE, KeyboardPacket.KEY_DOWN, (byte) 0, (byte) 0);
                conn.sendKeyboardInput(VK_BACKSPACE, KeyboardPacket.KEY_UP, (byte) 0, (byte) 0);
            }
            for (int i = 0; i < afterLength; i++) {
                conn.sendKeyboardInput(VK_DELETE, KeyboardPacket.KEY_DOWN, (byte) 0, (byte) 0);
                conn.sendKeyboardInput(VK_DELETE, KeyboardPacket.KEY_UP, (byte) 0, (byte) 0);
            }
        }
        return true;
    }
}
