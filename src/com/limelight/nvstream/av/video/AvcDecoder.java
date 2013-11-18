package com.limelight.nvstream.av.video;

public class AvcDecoder {
	static {
		System.loadLibrary("nv_av_dec");
	}
	
	public static native int init(int width, int height);
	public static native void destroy();
	public static native int getCurrentFrame(byte[] yuvframe, int size);
	public static native int getFrameSize();
	public static native int decode(byte[] indata, int inoff, int inlen);
}
