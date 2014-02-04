package com.limelight.nvstream.control;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.limelight.nvstream.StreamConfiguration;

public class Config {
	
	public static final int[] UNKNOWN_CONFIG =
		{
		70151,
		68291329,
		1280,
		68291584,
		1280,
		68291840,
		15360,
		68292096,
		25600,
		68292352,
		2048,
		68292608,
		1024,
		68289024,
		262144,
		17957632,
		302055424,
		134217729,
		16777490,
		70153,
		68293120,
		768000,
		17961216,
		303235072,
		335609857,
		838861842,
		352321536,
		1006634002,
		369098752,
		335545362,
		385875968,
		1042,
		402653184,
		134218770,
		419430400,
		167773202,
		436207616,
		855638290,
		266779,
		10000,
		266780,
		2000,
		266781,
		50,
		266782,
		3000,
		266783,
		2,
		266794,
		5000,
		266795,
		500,
		266784,
		75,
		266785,
		25,
		266786,
		10,
		266787,
		60,
		266788,
		30,
		266789,
		3,
		266790,
		1000,
		266791,
		5000,
		266792,
		5000,
		266793,
		5000,
		70190,
		68301063,
		10240,
		68301312,
		6400,
		68301568,
		768000,
		68299776,
		768,
		68300032,
		2560,
		68300544,
		0,
		34746368,
		(int)0xFE000000
		};
	
	public static final int CONFIG_SIZE = ((8 + UNKNOWN_CONFIG.length) * 4) + 3;
	
	private StreamConfiguration streamConfig;
	
	public Config(StreamConfiguration streamConfig) {
		this.streamConfig = streamConfig;
	}
	
	public byte[] toWire() {
		ByteBuffer bb = ByteBuffer.allocate(CONFIG_SIZE).order(ByteOrder.LITTLE_ENDIAN);
		
		// Width
		bb.putShort((short) 0x1204);
		bb.putShort((short) 0x0004);
		bb.putInt(streamConfig.getWidth());
		
		// Height
		bb.putShort((short) 0x1205);
		bb.putShort((short) 0x0004);
		bb.putInt(streamConfig.getHeight());
		
		// Unknown
		bb.putShort((short) 0x1206);
		bb.putShort((short) 0x0004);
		bb.putInt(1);
		
		// Refresh rate
		bb.putShort((short) 0x120A);
		bb.putShort((short) 0x0004);
		bb.putInt(streamConfig.getRefreshRate());
		
		// The rest are hardcoded
		for (int i : UNKNOWN_CONFIG) {
			bb.putInt(i);
		}
		
		// Config tail
		bb.putShort((short) 0x0013);
		bb.put((byte) 0x00);
		
		return bb.array();
	}
}
