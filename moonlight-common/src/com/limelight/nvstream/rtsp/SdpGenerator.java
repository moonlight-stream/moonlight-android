package com.limelight.nvstream.rtsp;

import java.net.InetAddress;
import java.net.Inet6Address;


import com.limelight.nvstream.StreamConfiguration;

public class SdpGenerator {
	private static void addSessionAttribute(StringBuilder config, String attribute, String value) {
		config.append("a="+attribute+":"+value+" \r\n");
	}
	
	public static String generateSdpFromConfig(InetAddress host, StreamConfiguration sc) {
		StringBuilder config = new StringBuilder();
		config.append("v=0").append("\r\n"); // SDP Version 0
		config.append("o=android 0 "+RtspConnection.CLIENT_VERSION+" IN ");
		if (host instanceof Inet6Address) {
			config.append("IPv6 ");
		}
		else {
			config.append("IPv4 ");
		}
		config.append(host.getHostAddress());
		config.append("\r\n");
		config.append("s=NVIDIA Streaming Client").append("\r\n");
		
		addSessionAttribute(config, "x-nv-general.serverAddress", "rtsp://"+host.getHostAddress()+":48010");
		
		addSessionAttribute(config, "x-nv-video[0].clientViewportWd", ""+sc.getWidth());
		addSessionAttribute(config, "x-nv-video[0].clientViewportHt", ""+sc.getHeight());
		addSessionAttribute(config, "x-nv-video[0].maxFPS", ""+sc.getRefreshRate());
		
		addSessionAttribute(config, "x-nv-video[0].packetSize", ""+sc.getMaxPacketSize());

		addSessionAttribute(config, "x-nv-video[0].rateControlMode", "4");
		
		if (sc.getRemote()) {
			addSessionAttribute(config, "x-nv-video[0].averageBitrate", "4");
			addSessionAttribute(config, "x-nv-video[0].peakBitrate", "4");
		}
		else if (sc.getBitrate() <= 13000) {
			addSessionAttribute(config, "x-nv-video[0].averageBitrate", "9");
			addSessionAttribute(config, "x-nv-video[0].peakBitrate", "9");
		}
		
		addSessionAttribute(config, "x-nv-video[0].timeoutLengthMs", "7000");
		addSessionAttribute(config, "x-nv-video[0].framesWithInvalidRefThreshold", "0");
		
		addSessionAttribute(config, "x-nv-vqos[0].bw.flags", "51");
		
		// Lock the bitrate if we're not scaling resolution so the picture doesn't get too bad
		if (sc.getHeight() >= 1080 && sc.getRefreshRate() >= 60) {
			if (sc.getBitrate() < 10000) {
				addSessionAttribute(config, "x-nv-vqos[0].bw.minimumBitrate", ""+sc.getBitrate());
			}
			else {
				addSessionAttribute(config, "x-nv-vqos[0].bw.minimumBitrate", "10000");
			}
		}
		else if (sc.getHeight() >= 1080 || sc.getRefreshRate() >= 60) {
			if (sc.getBitrate() < 7000) {
				addSessionAttribute(config, "x-nv-vqos[0].bw.minimumBitrate", ""+sc.getBitrate());
			}
			else {
				addSessionAttribute(config, "x-nv-vqos[0].bw.minimumBitrate", "7000");
			}
		}
		else {
			if (sc.getBitrate() < 3000) {
				addSessionAttribute(config, "x-nv-vqos[0].bw.minimumBitrate", ""+sc.getBitrate());
			}
			else {
				addSessionAttribute(config, "x-nv-vqos[0].bw.minimumBitrate", "3000");
			}
		}
		
		addSessionAttribute(config, "x-nv-vqos[0].bw.maximumBitrate", ""+sc.getBitrate());
		
		// Using FEC turns padding on which makes us have to take the slow path
		// in the depacketizer, not to mention exposing some ambiguous cases with
		// distinguishing padding from valid sequences. Since we can only perform
		// execute an FEC recovery on a 1 packet frame, we'll just turn it off completely.
		addSessionAttribute(config, "x-nv-vqos[0].fec.enable", "0");
		
		addSessionAttribute(config, "x-nv-vqos[0].videoQualityScoreUpdateTime", "5000");
		
		if (sc.getRemote()) {
			addSessionAttribute(config, "x-nv-vqos[0].qosTrafficType", "0");
		}
		else {
			addSessionAttribute(config, "x-nv-vqos[0].qosTrafficType", "5");
		}
		
		if (sc.getRemote()) {
			addSessionAttribute(config, "x-nv-aqos.qosTrafficType", "0");
		}
		else {
			addSessionAttribute(config, "x-nv-aqos.qosTrafficType", "4");
		}

		config.append("t=0 0").append("\r\n");
		
		config.append("m=video 47998  ").append("\r\n");
		
		return config.toString();
	}
}
