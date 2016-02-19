package com.limelight.nvstream;

import java.net.InetAddress;

import javax.crypto.SecretKey;

import com.limelight.nvstream.av.video.VideoDecoderRenderer;
import com.limelight.nvstream.av.video.VideoDecoderRenderer.VideoFormat;

public class ConnectionContext {
	// Gen 3 servers are 2.1.1 - 2.2.1
	public static final int SERVER_GENERATION_3 = 3;
	
	// Gen 4 servers are 2.2.2+
	public static final int SERVER_GENERATION_4 = 4;	
	
	// Gen 5 servers are 2.10.2+
	public static final int SERVER_GENERATION_5 = 5;
	
	public InetAddress serverAddress;
	public StreamConfiguration streamConfig;
	public VideoDecoderRenderer videoDecoderRenderer;
	public NvConnectionListener connListener;
	public SecretKey riKey;
	public int riKeyId;
	
	public int serverGeneration;
	
	public VideoFormat negotiatedVideoFormat;
	public int negotiatedWidth, negotiatedHeight;
	public int negotiatedFps;
}
