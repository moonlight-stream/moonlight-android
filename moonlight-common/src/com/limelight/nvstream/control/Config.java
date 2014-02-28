package com.limelight.nvstream.control;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import com.limelight.nvstream.StreamConfiguration;

public class Config {
	
	public static final ConfigTuple[] CONFIG_720_60 =
		{
		new ByteConfigTuple((short)0x1207, (byte)1), //iFrameOnDemand
		new IntConfigTuple((short)0x120b, 7), //averageBitrate
		new IntConfigTuple((short)0x120c, 7), //peakBitrate
		new IntConfigTuple((short)0x120d, 60), //gopLength
		new IntConfigTuple((short)0x120e, 100), //vbvMultiplier
		new IntConfigTuple((short)0x120f, 5), //rateControlMode
		new IntConfigTuple((short)0x1210, 4), //slicesPerFrame
		new IntConfigTuple((short)0x1202, 1024), //packetSize
		new ByteConfigTuple((short)0x1203, (byte)0), //recordServerStats
		new ByteConfigTuple((short)0x1201, (byte)0), //serverCapture
		new ByteConfigTuple((short)0x1234, (byte)0), //serverNetworkCapture
		new ByteConfigTuple((short)0x1248, (byte)0),
		new ByteConfigTuple((short)0x1208, (byte)1), //refPicInvalidation
		new ByteConfigTuple((short)0x1209, (byte)0), //enableFrameRateCtrl
		new IntConfigTuple((short)0x1212, 3000), //pingBackIntervalMs
		new IntConfigTuple((short)0x1238, 10000), //pingBackTimeoutMs
		new ByteConfigTuple((short)0x1211, (byte)0), //enableSubframeEncoding
		new ByteConfigTuple((short)0x1213, (byte)1), //videoQoSFecEnable
		new IntConfigTuple((short)0x1214, 50), //videoQoSFecNumSrcPackets
		new IntConfigTuple((short)0x1215, 60), //videoQoSFecNumOutPackets
		new IntConfigTuple((short)0x1216, 20), //videoQoSFecRepairPercent
		new IntConfigTuple((short)0x1217, 0), //videoQoSTsEnable
		new IntConfigTuple((short)0x1218, 8), //videoQoSTsAverageBitrate
		new IntConfigTuple((short)0x1219, 10), //videoQoSTsMaximumBitrate
		new IntConfigTuple((short)0x121a, 311), //videoQoSBwFlags
		new IntConfigTuple((short)0x121b, 10000), //videoQoSBwMaximumBitrate
		new IntConfigTuple((short)0x121c, 2000), //videoQoSBwMinimumBitrate
		new IntConfigTuple((short)0x121d, 50), //videoQoSBwStatsTime
		new IntConfigTuple((short)0x121e, 3000), //videoQoSBwZeroLossCount
		new IntConfigTuple((short)0x121f, 2),  //videoQoSBwLossThreshold
		new IntConfigTuple((short)0x122a, 5000), //videoQoSBwOwdThreshold
		new IntConfigTuple((short)0x122b, 500), //videoQoSBwOwdReference
		new IntConfigTuple((short)0x1220, 75), //videoQoSBwLossWaitTime
		new IntConfigTuple((short)0x1221, 25), //videoQoSBwRateDropMultiplier
		new IntConfigTuple((short)0x1222, 10), //videoQoSBwRateGainMultiplier
		new IntConfigTuple((short)0x1223, 60), //videoQoSBwMaxFps
		new IntConfigTuple((short)0x1224, 30), //videoQoSBwMinFps
		new IntConfigTuple((short)0x1225, 3), //videoQoSBwFpsThreshold
		new IntConfigTuple((short)0x1226, 1000), //videoQoSBwJitterThreshold
		new IntConfigTuple((short)0x1227, 5000), //videoQoSBwJitterWaitTime
		new IntConfigTuple((short)0x1228, 5000), //videoQoSBwNoJitterWaitTime
		new IntConfigTuple((short)0x124e, 110), 
		new IntConfigTuple((short)0x1237, 10), //videoQoSBwEarlyDetectionEnableL1Threshold
		new IntConfigTuple((short)0x1236, 6), //videoQoSBwEarlyDetectionEnableL0Threshold
		new IntConfigTuple((short)0x1235, 4), //videoQoSBwEarlyDetectionDisableThreshold
		new IntConfigTuple((short)0x1242, 20000), //videoQoSBwEarlyDetectionWaitTime
		new IntConfigTuple((short)0x1244, 100),
		new IntConfigTuple((short)0x1245, 1000),
		new IntConfigTuple((short)0x1246, 720),
		new IntConfigTuple((short)0x1247, 480),
		new IntConfigTuple((short)0x1229, 5000),  //videoQosVideoQualityScoreUpdateTime
		new ByteConfigTuple((short)0x122e, (byte)7), //videoQosTrafficType
		new IntConfigTuple((short)0x1231, 40), //videoQosBnNotifyUpBoundThreshold
		new IntConfigTuple((short)0x1232, 25), //videoQosBnNotifyLowBoundThreshold
		new IntConfigTuple((short)0x1233, 3000), //videoQosBnNotifyWaitTime
		new IntConfigTuple((short)0x122c, 3), //videoQosInvalidateThreshold
		new IntConfigTuple((short)0x122d, 10), //videoQosInvalidateSkipPercentage
		/*new IntConfigTuple((short)0x123b, 12),
		new IntConfigTuple((short)0x123c, 3),
		new IntConfigTuple((short)0x1249, 0),
		new IntConfigTuple((short)0x124a, 4000),
		new IntConfigTuple((short)0x124b, 5000),
		new IntConfigTuple((short)0x124c, 6000),
		new IntConfigTuple((short)0x124d, 1000),*/
		new IntConfigTuple((short)0x122f, 0), //riSecurityProtocol
		new ShortConfigTuple((short)0x1230, (short)0), //riSecInfoUsePredefinedCert
		new IntConfigTuple((short)0x1239, 0), //videoFrameDropIntervalNumber
		new IntConfigTuple((short)0x123a, 0), //videoFrameDropContinualNumber
		new IntConfigTuple((short)0x123d, 96000), //audioQosBitRate
		new IntConfigTuple((short)0x123e, 5), //audioQosPacketDuration
		new IntConfigTuple((short)0x123f, 1), //audioQosEnablePacketLossPercentage
		new IntConfigTuple((short)0x1243, 100) //audioQosPacketLossPercentageUpdateInterval
		};
	
	public static final ConfigTuple[] CONFIG_1080_30_DIFF =
		{
		new IntConfigTuple((short)0x120b, 10), //averageBitrate
		new IntConfigTuple((short)0x120c, 10), //peakBitrate
		
		// HACK: Streaming 1080p30 without these options causes the encoder
		// to step down to 720p which breaks the CPU decoder
		new IntConfigTuple((short)0x121b, 25000), //videoQoSBwMaximumBitrate
		new IntConfigTuple((short)0x121c, 25000), //videoQoSBwMinimumBitrate
		
		new IntConfigTuple((short)0x1246, 1280),
		new IntConfigTuple((short)0x1247, 720),
		/*new IntConfigTuple((short)0x124a, 5000),
		new IntConfigTuple((short)0x124c, 7000),*/
		};
	
	public static final ConfigTuple[] CONFIG_1080_60_DIFF =
		{
		new IntConfigTuple((short)0x120b, 30), //averageBitrate
		new IntConfigTuple((short)0x120c, 30), //peakBitrate
		new IntConfigTuple((short)0x120f, 4), //rateControlMode
		new IntConfigTuple((short)0x121b, 30000), //videoQoSBwMaximumBitrate
		new IntConfigTuple((short)0x121c, 25000), //videoQoSBwMinimumBitrate
		new IntConfigTuple((short)0x1245, 3000),
		new IntConfigTuple((short)0x1246, 1280),
		new IntConfigTuple((short)0x1247, 720),
		/*new IntConfigTuple((short)0x124a, 5000),
		new IntConfigTuple((short)0x124c, 7000),*/
		};
		
	private StreamConfiguration streamConfig;
	
	public Config(StreamConfiguration streamConfig) {
		this.streamConfig = streamConfig;
	}
	
	private void updateSetWithConfig(ArrayList<ConfigTuple> set, ConfigTuple[] config)
	{
		for (ConfigTuple tuple : config)
		{
			int i;
			
			for (i = 0; i < set.size(); i++) {
				ConfigTuple existingTuple = set.get(i);
				if (existingTuple.packetType == tuple.packetType) {
					set.remove(i);
					set.add(i, tuple);
					break;
				}
			}
			
			if (i == set.size()) {
				set.add(tuple);
			}
		}
	}
	
	private int getConfigOnWireSize(ArrayList<ConfigTuple> tupleSet)
	{
		int size = 0;
		
		for (ConfigTuple t : tupleSet)
		{
			size += ConfigTuple.HEADER_LENGTH + t.payloadLength;
		}
		
		return size;
	}
	
	private ArrayList<ConfigTuple> generateTupleSet() {
		ArrayList<ConfigTuple> tupleSet = new ArrayList<ConfigTuple>();
		
		tupleSet.add(new IntConfigTuple((short)0x1204, streamConfig.getWidth()));
		tupleSet.add(new IntConfigTuple((short)0x1205, streamConfig.getHeight()));
		tupleSet.add(new IntConfigTuple((short)0x1206, 1)); //videoTransferProtocol
		tupleSet.add(new IntConfigTuple((short)0x120A, streamConfig.getRefreshRate()));
		
		// Start with the initial config for 720p60
		updateSetWithConfig(tupleSet, CONFIG_720_60);
		
		if (streamConfig.getWidth() >= 1920 &&
			streamConfig.getHeight() >= 1080)
		{
			if (streamConfig.getRefreshRate() >= 60)
			{
				// Update the initial set with the changed 1080p60 options
				updateSetWithConfig(tupleSet, CONFIG_1080_60_DIFF);
			}
			else
			{
				// Update the initial set with the changed 1080p30 options
				updateSetWithConfig(tupleSet, CONFIG_1080_30_DIFF);
			}
		}
		
		return tupleSet;
	}
	
	public byte[] toWire() {
		ArrayList<ConfigTuple> tupleSet = generateTupleSet();
		ByteBuffer bb = ByteBuffer.allocate(getConfigOnWireSize(tupleSet) + 4).order(ByteOrder.LITTLE_ENDIAN);
		
		for (ConfigTuple t : tupleSet)
		{
			bb.put(t.toWire());
		}
		
		// Config tail
		bb.putShort((short) 0x13fe);
		bb.putShort((short) 0x00);
		
		return bb.array();
	}
}
