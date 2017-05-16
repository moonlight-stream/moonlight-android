package com.limelight.nvstream;

import java.net.InetAddress;

import javax.crypto.SecretKey;

import com.limelight.nvstream.av.video.VideoDecoderRenderer;

public class ConnectionContext {
	public InetAddress serverAddress;
	public StreamConfiguration streamConfig;
	public NvConnectionListener connListener;
	public SecretKey riKey;
	public int riKeyId;
	
	// This is the version quad from the appversion tag of /serverinfo
	public String serverAppVersion;
	public String serverGfeVersion;
	
	public int negotiatedWidth, negotiatedHeight;
	public int negotiatedFps;

    public int videoCapabilities;
}
