package com.limelight.nvstream.av.audio;

public class OpusDecoder {
	static {
		System.loadLibrary("nv_opus_dec");
	}
	
	public static native int init();
	public static native void destroy();
	public static native int getChannelCount();
	public static native int getMaxOutputShorts();
	public static native int getSampleRate();
	public static native int decode(byte[] indata, int inoff, int inlen, short[] outpcmdata);
}
