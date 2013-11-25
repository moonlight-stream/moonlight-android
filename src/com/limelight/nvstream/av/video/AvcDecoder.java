package com.limelight.nvstream.av.video;

import android.view.Surface;

public class AvcDecoder {
	static {
		// FFMPEG dependencies
		System.loadLibrary("avutil-52");
		System.loadLibrary("swresample-0");
		System.loadLibrary("swscale-2");
		System.loadLibrary("avcodec-55");
		System.loadLibrary("avformat-55");
		System.loadLibrary("avfilter-3");
		
		System.loadLibrary("nv_avc_dec");
	}
	
	/** Disables the deblocking filter at the cost of image quality */
	public static final int DISABLE_LOOP_FILTER = 0x1;
	/** Uses the low latency decode flag (disables multithreading) */
	public static final int LOW_LATENCY_DECODE = 0x2;
	/** Threads process each slice, rather than each frame */
	public static final int SLICE_THREADING = 0x4;
	/** Uses nonstandard speedup tricks */
	public static final int FAST_DECODE = 0x8;
	/** Uses bilinear filtering instead of bicubic */
	public static final int BILINEAR_FILTERING = 0x10;
	/** Uses a faster bilinear filtering with lower image quality */
	public static final int FAST_BILINEAR_FILTERING = 0x20;
	
	public static native int init(int width, int height, int perflvl, int threadcount);
	public static native void destroy();
	public static native void redraw(Surface surface);
	public static native int decode(byte[] indata, int inoff, int inlen);
}
