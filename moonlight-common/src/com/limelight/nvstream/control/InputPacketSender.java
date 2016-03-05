package com.limelight.nvstream.control;

import java.io.IOException;

public interface InputPacketSender {
	void sendInputPacket(byte[] data, short length) throws IOException;
}
