package com.limelight.nvstream.rtsp;

import java.net.InetAddress;
import java.net.Inet6Address;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


import com.limelight.nvstream.StreamConfiguration;

public class SdpGenerator {
	private static void addSessionAttributeBytes(StringBuilder config, String attribute, byte[] value) {
		char str[] = new char[value.length];
		
		for (int i = 0; i < value.length; i++) {
			str[i] = (char)value[i];
		}
		
		addSessionAttribute(config, attribute, new String(str));
	}
	
	private static void addSessionAttributeInts(StringBuilder config, String attribute, int[] value) {
		ByteBuffer b = ByteBuffer.allocate(value.length * 4).order(ByteOrder.LITTLE_ENDIAN);
		
		for (int val : value) {
			b.putInt(val);
		}
		
		addSessionAttributeBytes(config, attribute, b.array());
	}
	
	private static void addSessionAttributeInt(StringBuilder config, String attribute, int value) {
		ByteBuffer b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
		b.putInt(value);
		addSessionAttributeBytes(config, attribute, b.array());
	}
	
	private static void addSessionAttribute(StringBuilder config, String attribute, String value) {
		config.append("a="+attribute+":"+value+" \r\n");
	}
	
	public static String generateSdpFromConfig(InetAddress host, StreamConfiguration sc) {
		StringBuilder config = new StringBuilder();
		config.append("v=0").append("\r\n"); // SDP Version 0
		config.append("o=android 0 9 IN ");
		if (host instanceof Inet6Address) {
			config.append("IPv6 ");
		}
		else {
			config.append("IPv4 ");
		}
		config.append(host.getHostAddress());
		config.append("\r\n");
		config.append("s=NVIDIA Streaming Client").append("\r\n");
		
		addSessionAttributeBytes(config, "x-nv-callbacks", new byte[] {
				0x50, 0x51, 0x49, 0x4a, 0x0d,
				(byte)0xad, 0x30, 0x4a, (byte)0xf1, (byte)0xbd, 0x30, 0x4a, (byte)0xd5,
				(byte)0xac, 0x30, 0x4a, 0x21, (byte)0xbc, 0x30, 0x4a, (byte)0xc1,
				(byte)0xbb, 0x30, 0x4a, 0x7d, (byte)0xbb, 0x30, 0x4a, 0x19,
				(byte)0xbb, 0x30, 0x4a, 0x00, 0x00, 0x00, 0x00
		});
		addSessionAttributeBytes(config, "x-nv-videoDecoder", new byte[] {
				0x50, 0x51, 0x49, 0x4a, 0x65, (byte)0xad, 0x30, 0x4a, 0x01,
				0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
				0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte)0xd1, (byte)0xac, 0x30,
				0x4a, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
				0x4d, (byte)0xad, 0x30, 0x4a
		});
		addSessionAttributeBytes(config, "x-nv-audioRenderer", new byte[] {
				0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
		});
		
		addSessionAttribute(config, "x-nv-general.serverAddress", host.getHostAddress());
		
		addSessionAttributeInts(config, "x-nv-general.serverPorts", new int[] {
				0x00000000, 0xffffffff, 0xffffffff, 0x00000000,
				0xffffffff, 0xffffffff, 0x00000000, 0xffffffff,
				0xffffffff, 0x00000000, 0xffffffff, 0xffffffff,
				
				0x00000000, 0xffffffff, 0xffffffff, 0x00000000,
				0xffffffff, 0xffffffff, 0x00000000, 0xffffffff,
				0xffffffff, 0x00000000, 0xffffffff, 0xffffffff,
				
				0x00000000, 0xffffffff, 0xffffffff, 0x00000000,
				0xffffffff, 0xffffffff, 0x00000000, 0xffffffff,
				0xffffffff, 0x00000000, 0xffffffff, 0xffffffff,
				
				0x00000000, 0xffffffff, 0xffffffff, 0x00000000,
				0xffffffff, 0xffffffff, 0x00000000, 0xffffffff,
				0xffffffff, 0x00000000, 0xffffffff, 0xffffffff,
				
				0x00000000, 0xffffffff, 0xffffffff, 0x00000000,
				0xffffffff, 0xffffffff, 0x00000000, 0xffffffff,
				0xffffffff, 0x00000000, 0xffffffff, 0xffffffff,
				
				0x00000000, 0xffffffff, 0xffffffff, 0x00000000,
				0xffffffff, 0xffffffff, 0x00000000, 0xffffffff,
				0xffffffff, 0x00000000, 0xffffffff, 0xffffffff,
				
				0x00000000, 0xffffffff, 0xffffffff, 0x00000000,
				0xffffffff, 0xffffffff, 0x00000000, 0xffffffff,
				0xffffffff, 0x00000000, 0xffffffff, 0xffffffff,
				
				0x00000000, 0xffffffff, 0xffffffff, 0x00000000,
				0xffffffff, 0xffffffff, 0x00000000, 0xffffffff,
				0xffffffff, 0x00000000, 0xffffffff, 0xffffffff
		});
		
		addSessionAttribute(config, "x-nv-general.videoSyncAudioDelayAdjust", "10000");
		addSessionAttribute(config, "x-nv-general.startTime", "0");
		addSessionAttributeInt(config, "x-nv-general.featureFlags", 0xffffffff);
		addSessionAttribute(config, "x-nv-general.userIdleWarningTimeout", "0");
		addSessionAttribute(config, "x-nv-general.userIdleSessionTimeout", "0");
		addSessionAttribute(config, "x-nv-general.serverCapture", "0");
		addSessionAttribute(config, "x-nv-general.clientCapture", "0");
		addSessionAttribute(config, "x-nv-general.rtpQueueMaxPackets", "16");
		addSessionAttribute(config, "x-nv-general.rtpQueueMaxDurationMs", "40");
		addSessionAttribute(config, "x-nv-general.useRtspClient", "257");
		
		addSessionAttribute(config, "x-nv-video[0].clientViewportWd", ""+sc.getWidth());
		addSessionAttribute(config, "x-nv-video[0].clientViewportHt", ""+sc.getHeight());
		addSessionAttribute(config, "x-nv-video[0].adapterNumber", "0");
		addSessionAttribute(config, "x-nv-video[0].maxFPS", ""+sc.getRefreshRate());
		addSessionAttribute(config, "x-nv-video[0].iFrameOnDemand", "1");
		addSessionAttributeInt(config, "x-nv-video[0].transferProtocol", 1);
		if (sc.getHeight() >= 1080 && sc.getRefreshRate() >= 60) {
			addSessionAttributeInt(config, "x-nv-video[0].rateControlMode", 4);
			addSessionAttribute(config, "x-nv-video[0].averageBitrate", "30");
			addSessionAttribute(config, "x-nv-video[0].peakBitrate", "30");
		}
		else {
			addSessionAttributeInt(config, "x-nv-video[0].rateControlMode", 5);
			addSessionAttribute(config, "x-nv-video[0].averageBitrate", "7");
			addSessionAttribute(config, "x-nv-video[0].peakBitrate", "7");
		}

		addSessionAttribute(config, "x-nv-video[0].gopLength", "60");
		addSessionAttribute(config, "x-nv-video[0].vbvMultiplier", "100");
		addSessionAttribute(config, "x-nv-video[0].slicesPerFrame", "4");
		addSessionAttribute(config, "x-nv-video[0].numTemporalLayers", "0");
		addSessionAttribute(config, "x-nv-video[0].packetSize", "1024");
		addSessionAttribute(config, "x-nv-video[0].enableSubframeEncoding", "0");
		addSessionAttribute(config, "x-nv-video[0].refPicInvalidation", "1");
		addSessionAttribute(config, "x-nv-video[0].pingBackIntervalMs", "3000");
		addSessionAttribute(config, "x-nv-video[0].pingBackTimeoutMs", "10000");
		addSessionAttribute(config, "x-nv-video[0].timeoutLengthMs", "7000");
		addSessionAttribute(config, "x-nv-video[0].fullFrameAssembly", "1");
		addSessionAttribute(config, "x-nv-video[0].decodeIncompleteFrames", "0");
		addSessionAttribute(config, "x-nv-video[0].enableIntraRefresh", "0");
		addSessionAttribute(config, "x-nv-video[0].enableLongTermReferences", "0");
		addSessionAttribute(config, "x-nv-video[0].enableFrameRateCtrl", "0");
		addSessionAttribute(config, "x-nv-video[0].rtpDynamicPort", "0");
		addSessionAttribute(config, "x-nv-video[0].framesWithInvalidRefThreshold", "0");
		addSessionAttribute(config, "x-nv-video[0].consecutiveFrameLostThreshold", "0");
		
		addSessionAttribute(config, "x-nv-vqos[0].ts.enable", "0");
		addSessionAttribute(config, "x-nv-vqos[0].ts.averageBitrate", "8");
		addSessionAttribute(config, "x-nv-vqos[0].ts.maximumBitrate", "10");
		addSessionAttribute(config, "x-nv-vqos[0].bw.flags", "819"); // Bit 2 being set causes picture problems (should be 823)
		
		// We clamp to min = max so manual bitrate settings take effect without time to scale up
		addSessionAttribute(config, "x-nv-vqos[0].bw.maximumBitrate", ""+sc.getBitrate());
		addSessionAttribute(config, "x-nv-vqos[0].bw.minimumBitrate", ""+sc.getBitrate());

		addSessionAttribute(config, "x-nv-vqos[0].bw.statsTime", "50");
		addSessionAttribute(config, "x-nv-vqos[0].bw.zeroLossCount", "3000");
		addSessionAttribute(config, "x-nv-vqos[0].bw.lossThreshold", "2");
		addSessionAttribute(config, "x-nv-vqos[0].bw.owdThreshold", "5000");
		addSessionAttribute(config, "x-nv-vqos[0].bw.owdReference", "500");
		addSessionAttribute(config, "x-nv-vqos[0].bw.lossWaitTime", "75");
		addSessionAttribute(config, "x-nv-vqos[0].bw.rateDropMultiplier", "25");
		addSessionAttribute(config, "x-nv-vqos[0].bw.rateGainMultiplier", "10");
		addSessionAttribute(config, "x-nv-vqos[0].bw.maxFps", "60");
		addSessionAttribute(config, "x-nv-vqos[0].bw.minFps", "30");
		addSessionAttribute(config, "x-nv-vqos[0].bw.fpsThreshold", "3");
		addSessionAttribute(config, "x-nv-vqos[0].bw.jitterThreshold", "1000");
		addSessionAttribute(config, "x-nv-vqos[0].bw.jitterWaitTime", "5000");
		addSessionAttribute(config, "x-nv-vqos[0].bw.noJitterWaitTime", "5000");
		addSessionAttribute(config, "x-nv-vqos[0].bw.earlyDetectionEnableBitRatePercentThreshold", "110");
		addSessionAttribute(config, "x-nv-vqos[0].bw.earlyDetectionEnableL1Threshold", "10");
		addSessionAttribute(config, "x-nv-vqos[0].bw.earlyDetectionEnableL0Threshold", "6");
		addSessionAttribute(config, "x-nv-vqos[0].bw.earlyDetectionDisableThreshold", "4");
		addSessionAttribute(config, "x-nv-vqos[0].bw.earlyDetectionDisableWaitTime", "20000");
		addSessionAttribute(config, "x-nv-vqos[0].bw.earlyDetectionDisableWaitPercent", "100");
		addSessionAttribute(config, "x-nv-vqos[0].bw.earlyDetectionLowerBoundRate", "1000");
		
		if (sc.getHeight() >= 1080) {
			addSessionAttribute(config, "x-nv-vqos[0].bw.earlyDetectionLowerBoundWidth", "1280");
			addSessionAttribute(config, "x-nv-vqos[0].bw.earlyDetectionLowerBoundHeight", "720");
		}
		else {
			addSessionAttribute(config, "x-nv-vqos[0].bw.earlyDetectionLowerBoundWidth", "720");
			addSessionAttribute(config, "x-nv-vqos[0].bw.earlyDetectionLowerBoundHeight", "480");
		}
		
		addSessionAttribute(config, "x-nv-vqos[0].bw.pf.enableFlags", "3");
		
		if (sc.getHeight() >= 1080 && sc.getRefreshRate() >= 60) {
			addSessionAttribute(config, "x-nv-vqos[0].bw.pf.lowBitrate30FpsThreshold", "5000");
			addSessionAttribute(config, "x-nv-vqos[0].bw.pf.lowBitrate60FpsThreshold", "5000");
			addSessionAttribute(config, "x-nv-vqos[0].bw.pf.highBitrateThreshold", "7000");
		}
		else {
			addSessionAttribute(config, "x-nv-vqos[0].bw.pf.lowBitrate30FpsThreshold", "4000");
			addSessionAttribute(config, "x-nv-vqos[0].bw.pf.lowBitrate60FpsThreshold", "5000");
			addSessionAttribute(config, "x-nv-vqos[0].bw.pf.highBitrateThreshold", "6000");
		}
		addSessionAttribute(config, "x-nv-vqos[0].bw.pf.bitrateStepSize", "1000");
		addSessionAttribute(config, "x-nv-vqos[0].bn.notifyUpBoundThreshold", "40");
		addSessionAttribute(config, "x-nv-vqos[0].bn.notifyLowBoundThreshold", "25");
		addSessionAttribute(config, "x-nv-vqos[0].bn.notifyWaitTime", "3000");
		addSessionAttribute(config, "x-nv-vqos[0].fec.enable", "1");
		addSessionAttribute(config, "x-nv-vqos[0].fec.numSrcPackets", "50");
		addSessionAttribute(config, "x-nv-vqos[0].fec.numOutPackets", "60");
		addSessionAttribute(config, "x-nv-vqos[0].fec.repairPercent", "20");
		addSessionAttribute(config, "x-nv-vqos[0].pictureRefreshIntervalMs", "0");
		addSessionAttribute(config, "x-nv-vqos[0].videoQualityScoreUpdateTime", "5000");
		addSessionAttribute(config, "x-nv-vqos[0].invalidateThreshold", "3");
		addSessionAttribute(config, "x-nv-vqos[0].invalidateSkipPercentage", "10");
		addSessionAttribute(config, "x-nv-vqos[0].qosTrafficType", "7");
		addSessionAttribute(config, "x-nv-vqos[0].videoQoSMaxRoundTripLatencyFrames", "12");
		addSessionAttribute(config, "x-nv-vqos[0].videoQoSMaxConsecutiveDrops", "3");
		addSessionAttributeInt(config, "x-nv-vqos[0].profile", 0);
		
		addSessionAttributeInt(config, "x-nv-aqos.mode", 1);
		addSessionAttribute(config, "x-nv-aqos.enableAudioStats", "1");
		addSessionAttribute(config, "x-nv-aqos.audioStatsUpdateIntervalMs", "70");
		addSessionAttribute(config, "x-nv-aqos.enablePacketLossPercentage", "1");
		addSessionAttribute(config, "x-nv-aqos.bitRate", "96000");
		addSessionAttribute(config, "x-nv-aqos.packetDuration", "5");
		addSessionAttribute(config, "x-nv-aqos.packetLossPercentageUpdateIntervalMs", "100");
		addSessionAttribute(config, "x-nv-aqos.qosTrafficType", "4");
		
		addSessionAttribute(config, "x-nv-runtime.recordClientStats", "8");
		addSessionAttribute(config, "x-nv-runtime.recordServerStats", "0");
		addSessionAttribute(config, "x-nv-runtime.clientNetworkCapture", "0");
		addSessionAttribute(config, "x-nv-runtime.clientTraceCapture", "0");
		addSessionAttribute(config, "x-nv-runtime.serverNetworkCapture", "0");
		addSessionAttribute(config, "x-nv-runtime.serverTraceCapture", "0");
		
		addSessionAttributeInt(config, "x-nv-ri.protocol", 0);
		addSessionAttribute(config, "x-nv-ri.sendStatus", "0");
		addSessionAttributeInt(config, "x-nv-ri.securityProtocol", 0);
		addSessionAttributeBytes(config, "x-nv-ri.secInfo", new byte[0x20a]);
		addSessionAttribute(config, "x-nv-videoFrameDropIntervalNumber", "0");
		addSessionAttribute(config, "x-nv-videoFrameDropContinualNumber", "0");
		
		config.append("t=0 0").append("\r\n");
		
		config.append("m=video 47996  ").append("\r\n");
		
		return config.toString();
	}
}
