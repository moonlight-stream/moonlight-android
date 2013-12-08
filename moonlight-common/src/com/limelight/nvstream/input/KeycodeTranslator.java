package com.limelight.nvstream.input;

import com.limelight.nvstream.NvConnection;

public abstract class KeycodeTranslator {
	public abstract short translate(int keycode);
	protected NvConnection conn;
	
	public KeycodeTranslator(NvConnection conn) {
		this.conn = conn;
	}
	
	public void sendKeyDown(short keyMap) {
		conn.sendKeyboardInput(keyMap, KeyboardPacket.KEY_DOWN);
	}
	
	public void sendKeyUp(short keyMap) {
		conn.sendKeyboardInput(keyMap, KeyboardPacket.KEY_UP);
	}
}
