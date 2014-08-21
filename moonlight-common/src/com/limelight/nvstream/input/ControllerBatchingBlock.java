package com.limelight.nvstream.input;

public class ControllerBatchingBlock {
	
	private byte[] axisDirs = new byte[6];
	
	private short buttonFlags;
	private byte leftTrigger;
	private byte rightTrigger;
	private short leftStickX;
	private short leftStickY;
	private short rightStickX;
	private short rightStickY;
	
	public ControllerBatchingBlock(ControllerPacket initialPacket) {
		this.buttonFlags = initialPacket.buttonFlags;
		this.leftTrigger = initialPacket.leftTrigger;
		this.rightTrigger = initialPacket.rightTrigger;
		this.leftStickX = initialPacket.leftStickX;
		this.leftStickY = initialPacket.leftStickY;
		this.rightStickX = initialPacket.rightStickX;
		this.rightStickY = initialPacket.rightStickY;
	}
	
	private boolean checkDirs(short currentVal, short newVal, int dirIndex) {
		if (currentVal == newVal) {
			return true;
		}
		
		// We want to send a packet if we've now zeroed an axis
		if (newVal == 0) {
			return false;
		}
		
		if (axisDirs[dirIndex] == 0) {
			if (newVal < currentVal) {
				axisDirs[dirIndex] = -1;
			}
			else {
				axisDirs[dirIndex] = 1;
			}
		}
		else if (axisDirs[dirIndex] == -1) {
			return newVal < currentVal;
		}
		else if (newVal < currentVal) {
			return false;
		}

		return true;
	}
	
	// Controller packet batching is far more restricted than mouse move batching.
	// We have several restrictions that will cause batching to break up the controller packets.
	// 1) Button flags must be the same for all packets in the batch
	// 2) The movement direction of all axes must remain the same or be neutral
	public boolean submitNewPacket(ControllerPacket packet) {
		if (buttonFlags != packet.buttonFlags ||
			!checkDirs(leftTrigger, packet.leftTrigger, 0) ||
			!checkDirs(rightTrigger, packet.rightTrigger, 1) ||
			!checkDirs(leftStickX, packet.leftStickX, 2) ||
			!checkDirs(leftStickY, packet.leftStickY, 3) ||
			!checkDirs(rightStickX, packet.rightStickX, 4) ||
			!checkDirs(rightStickY, packet.rightStickY, 5))
		{
			return false;
		}
		
		this.leftTrigger = packet.leftTrigger;
		this.rightTrigger = packet.rightTrigger;
		this.leftStickX = packet.leftStickX;
		this.leftStickY = packet.leftStickY;
		this.rightStickX = packet.rightStickX;
		this.rightStickY = packet.rightStickY;
		return true;
	}
	
	public void reinitializePacket(ControllerPacket packet) {
		packet.buttonFlags = buttonFlags;
		packet.leftTrigger = leftTrigger;
		packet.rightTrigger = rightTrigger;
		packet.leftStickX = leftStickX;
		packet.leftStickY = leftStickY;
		packet.rightStickX = rightStickX;
		packet.rightStickY = rightStickY;
	}
}
