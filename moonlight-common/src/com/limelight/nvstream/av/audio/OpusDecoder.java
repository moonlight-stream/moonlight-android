package com.limelight.nvstream.av.audio;

public class OpusDecoder {
	static {
		System.loadLibrary("nv_opus_dec");
	}
	
	public static native int init(int sampleRate, int channelCount, int streams, int coupledStreams, byte[] mapping);
	public static native void destroy();
	public static native int decode(byte[] indata, int inoff, int inlen, byte[] outpcmdata);
}
