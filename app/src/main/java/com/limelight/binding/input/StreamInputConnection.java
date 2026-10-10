package com.limelight.binding.input;

import android.view.View;
import android.view.inputmethod.BaseInputConnection;

import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;

/** Routes committed Android IME text to the remote host. */
public class StreamInputConnection extends BaseInputConnection {
    private volatile NvConnection conn;
    private String composingText = "";

    // Moonlight wire format: (KEY_PREFIX << 8) | Windows virtual key code.
    private static final short VK_BACKSPACE = (short) ((0x80 << 8) | 0x08);
    private static final short VK_DELETE = (short) ((0x80 << 8) | 0x2E);

    public void setNvConnection(NvConnection conn) {
        this.conn = conn;
    }

    public StreamInputConnection(View targetView, boolean fullEditor, NvConnection conn) {
        super(targetView, fullEditor);
        this.conn = conn;
    }

    @Override
    public synchronized boolean commitText(CharSequence text, int newCursorPosition) {
        if (!replaceComposingText(text == null ? "" : text.toString())) {
            return false;
        }
        composingText = "";
        return true;
    }

    @Override
    public synchronized boolean setComposingText(CharSequence text, int newCursorPosition) {
        return replaceComposingText(text == null ? "" : text.toString());
    }

    @Override
    public synchronized boolean finishComposingText() {
        composingText = "";
        return true;
    }

    @Override
    public synchronized boolean deleteSurroundingText(int beforeLength, int afterLength) {
        NvConnection activeConn = conn;
        if (activeConn != null) {
            int composingCodePoints = composingText.codePointCount(0, composingText.length());
            int composingBackspaces = Math.min(Math.max(beforeLength, 0), composingCodePoints);
            if (composingBackspaces > 0) {
                int keepLength = composingText.offsetByCodePoints(0, composingCodePoints - composingBackspaces);
                composingText = composingText.substring(0, keepLength);
            }
            for (int i = 0; i < beforeLength; i++) {
                activeConn.sendKeyboardInput(VK_BACKSPACE, KeyboardPacket.KEY_DOWN, (byte) 0, (byte) 0);
                activeConn.sendKeyboardInput(VK_BACKSPACE, KeyboardPacket.KEY_UP, (byte) 0, (byte) 0);
            }
            for (int i = 0; i < afterLength; i++) {
                activeConn.sendKeyboardInput(VK_DELETE, KeyboardPacket.KEY_DOWN, (byte) 0, (byte) 0);
                activeConn.sendKeyboardInput(VK_DELETE, KeyboardPacket.KEY_UP, (byte) 0, (byte) 0);
            }
        }
        return true;
    }

    /**
     * Android IMEs often send letters as composing text and only commit the
     * whole word after a delimiter. Keep the remote text in sync as that
     * composing region grows or is corrected, so letters are visible before
     * the user presses space or enter.
     */
    private boolean replaceComposingText(String newText) {
        NvConnection activeConn = conn;
        if (activeConn == null) {
            return false;
        }

        int commonPrefixLength = 0;
        int oldOffset = 0;
        int newOffset = 0;
        while (oldOffset < composingText.length() && newOffset < newText.length()) {
            int oldCodePoint = composingText.codePointAt(oldOffset);
            int newCodePoint = newText.codePointAt(newOffset);
            if (oldCodePoint != newCodePoint) {
                break;
            }
            oldOffset += Character.charCount(oldCodePoint);
            newOffset += Character.charCount(newCodePoint);
            commonPrefixLength = oldOffset;
        }

        int backspaces = composingText.codePointCount(commonPrefixLength, composingText.length());
        for (int i = 0; i < backspaces; i++) {
            activeConn.sendKeyboardInput(VK_BACKSPACE, KeyboardPacket.KEY_DOWN, (byte) 0, (byte) 0);
            activeConn.sendKeyboardInput(VK_BACKSPACE, KeyboardPacket.KEY_UP, (byte) 0, (byte) 0);
        }

        if (newOffset < newText.length()) {
            activeConn.sendUtf8Text(newText.substring(newOffset));
        }
        composingText = newText;
        return true;
    }
}
