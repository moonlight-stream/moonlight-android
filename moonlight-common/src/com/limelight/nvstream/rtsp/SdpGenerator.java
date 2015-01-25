package com.limelight.nvstream.rtsp;

import java.net.Inet6Address;

import com.limelight.nvstream.ConnectionContext;

public class SdpGenerator {
	private static void addSessionAttribute(StringBuilder config, String attribute, String value) {
		config.append("a="+attribute+":"+value+" \r\n");
	}
	
	public static String generateSdpFromContext(ConnectionContext context) {
		StringBuilder config = new StringBuilder();
		config.append("v=0").append("\r\n"); // SDP Version 0
		config.append("o=android 0 "+RtspConnection.getRtspVersionFromContext(context)+" IN ");
		if (context.serverAddress instanceof Inet6Address) {
			config.append("IPv6 ");
		}
		else {
			config.append("IPv4 ");
		}
		config.append(context.serverAddress.getHostAddress());
		config.append("\r\n");
		config.append("s=NVIDIA Streaming Client").append("\r\n");
		
		addSessionAttribute(config, "x-nv-general.serverAddress", "rtsp://"+context.serverAddress.getHostAddress()+":48010");
		
		addSessionAttribute(config, "x-nv-video[0].clientViewportWd", ""+context.streamConfig.getWidth());
		addSessionAttribute(config, "x-nv-video[0].clientViewportHt", ""+context.streamConfig.getHeight());
		addSessionAttribute(config, "x-nv-video[0].maxFPS", ""+context.streamConfig.getRefreshRate());
		
		addSessionAttribute(config, "x-nv-video[0].packetSize", ""+context.streamConfig.getMaxPacketSize());

		addSessionAttribute(config, "x-nv-video[0].rateControlMode", "4");
		
		if (context.streamConfig.getRemote()) {
			addSessionAttribute(config, "x-nv-video[0].averageBitrate", "4");
			addSessionAttribute(config, "x-nv-video[0].peakBitrate", "4");
		}
		else if (context.streamConfig.getBitrate() <= 13000) {
			addSessionAttribute(config, "x-nv-video[0].averageBitrate", "9");
			addSessionAttribute(config, "x-nv-video[0].peakBitrate", "9");
		}
		
		addSessionAttribute(config, "x-nv-video[0].timeoutLengthMs", "7000");
		addSessionAttribute(config, "x-nv-video[0].framesWithInvalidRefThreshold", "0");
		
		addSessionAttribute(config, "x-nv-vqos[0].bw.flags", "51");
		
		// Lock the bitrate if we're not scaling resolution so the picture doesn't get too bad
		if (context.streamConfig.getHeight() >= 1080 && context.streamConfig.getRefreshRate() >= 60) {
			if (context.streamConfig.getBitrate() < 10000) {
				addSessionAttribute(config, "x-nv-vqos[0].bw.minimumBitrate", ""+context.streamConfig.getBitrate());
			}
			else {
				addSessionAttribute(config, "x-nv-vqos[0].bw.minimumBitrate", "10000");
			}
		}
		else if (context.streamConfig.getHeight() >= 1080 || context.streamConfig.getRefreshRate() >= 60) {
			if (context.streamConfig.getBitrate() < 7000) {
				addSessionAttribute(config, "x-nv-vqos[0].bw.minimumBitrate", ""+context.streamConfig.getBitrate());
			}
			else {
				addSessionAttribute(config, "x-nv-vqos[0].bw.minimumBitrate", "7000");
			}
		}
		else {
			if (context.streamConfig.getBitrate() < 3000) {
				addSessionAttribute(config, "x-nv-vqos[0].bw.minimumBitrate", ""+context.streamConfig.getBitrate());
			}
			else {
				addSessionAttribute(config, "x-nv-vqos[0].bw.minimumBitrate", "3000");
			}
		}
		
		addSessionAttribute(config, "x-nv-vqos[0].bw.maximumBitrate", ""+context.streamConfig.getBitrate());
		
		// Using FEC turns padding on which makes us have to take the slow path
		// in the depacketizer, not to mention exposing some ambiguous cases with
		// distinguishing padding from valid sequences. Since we can only perform
		// execute an FEC recovery on a 1 packet frame, we'll just turn it off completely.
		addSessionAttribute(config, "x-nv-vqos[0].fec.enable", "0");
		
		addSessionAttribute(config, "x-nv-vqos[0].videoQualityScoreUpdateTime", "5000");
		
		if (context.streamConfig.getRemote()) {
			addSessionAttribute(config, "x-nv-vqos[0].qosTrafficType", "0");
		}
		else {
			addSessionAttribute(config, "x-nv-vqos[0].qosTrafficType", "5");
		}
		
		if (context.streamConfig.getRemote()) {
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
