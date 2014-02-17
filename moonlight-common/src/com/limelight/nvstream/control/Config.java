package com.limelight.nvstream.control;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;

import com.limelight.nvstream.StreamConfiguration;

public class Config {
	
	public static final ConfigTuple[] CONFIG_720_60 =
		{
		new IntConfigTuple((short)0x1206, 1),
		new ByteConfigTuple((short)0x1207, (byte)1),
		new IntConfigTuple((short)0x120b, 7),
		new IntConfigTuple((short)0x120c, 7),
		new IntConfigTuple((short)0x120d, 60),
		new IntConfigTuple((short)0x120e, 100),
		new IntConfigTuple((short)0x120f, 5),
		new IntConfigTuple((short)0x1210, 4),
		new IntConfigTuple((short)0x1202, 1024),
		new ByteConfigTuple((short)0x1203, (byte)0),
		new ByteConfigTuple((short)0x1201, (byte)0),
		new ByteConfigTuple((short)0x1234, (byte)0),
		new ByteConfigTuple((short)0x1248, (byte)0),
		new ByteConfigTuple((short)0x1208, (byte)1),
		new ByteConfigTuple((short)0x1209, (byte)0),
		new IntConfigTuple((short)0x1212, 3000),
		new IntConfigTuple((short)0x1238, 10000),
		new ByteConfigTuple((short)0x1211, (byte)0),
		new ByteConfigTuple((short)0x1213, (byte)1),
		new IntConfigTuple((short)0x1214, 50),
		new IntConfigTuple((short)0x1215, 60),
		new IntConfigTuple((short)0x1216, 20),
		new IntConfigTuple((short)0x1217, 0),
		new IntConfigTuple((short)0x1218, 8),
		new IntConfigTuple((short)0x1219, 10),
		new IntConfigTuple((short)0x121a, 311),
		new IntConfigTuple((short)0x121b, 10000),
		new IntConfigTuple((short)0x121c, 2000),
		new IntConfigTuple((short)0x121d, 50),
		new IntConfigTuple((short)0x121e, 3000),
		new IntConfigTuple((short)0x121f, 2),
		new IntConfigTuple((short)0x122a, 5000),
		new IntConfigTuple((short)0x122b, 500),
		new IntConfigTuple((short)0x1220, 75),
		new IntConfigTuple((short)0x1221, 25),
		new IntConfigTuple((short)0x1222, 10),
		new IntConfigTuple((short)0x1223, 60),
		new IntConfigTuple((short)0x1224, 30),
		new IntConfigTuple((short)0x1225, 3),
		new IntConfigTuple((short)0x1226, 1000),
		new IntConfigTuple((short)0x1227, 5000),
		new IntConfigTuple((short)0x1228, 5000),
		new IntConfigTuple((short)0x124e, 110),
		new IntConfigTuple((short)0x1237, 10),
		new IntConfigTuple((short)0x1236, 6),
		new IntConfigTuple((short)0x1235, 4),
		new IntConfigTuple((short)0x1242, 20000),
		new IntConfigTuple((short)0x1244, 100),
		new IntConfigTuple((short)0x1245, 1000),
		new IntConfigTuple((short)0x1246, 720),
		new IntConfigTuple((short)0x1247, 480),
		new IntConfigTuple((short)0x1229, 5000),
		new ByteConfigTuple((short)0x122e, (byte)7),
		new IntConfigTuple((short)0x1231, 40),
		new IntConfigTuple((short)0x1232, 25),
		new IntConfigTuple((short)0x1233, 3000),
		new IntConfigTuple((short)0x122c, 3),
		new IntConfigTuple((short)0x122d, 10),
		new IntConfigTuple((short)0x123b, 12),
		new IntConfigTuple((short)0x123c, 3),
		new IntConfigTuple((short)0x1249, 0),
		new IntConfigTuple((short)0x124a, 4000),
		new IntConfigTuple((short)0x124b, 5000),
		new IntConfigTuple((short)0x124c, 6000),
		new IntConfigTuple((short)0x124d, 1000),
		new IntConfigTuple((short)0x122f, 0),
		new ShortConfigTuple((short)0x1230, (short)0),
		new IntConfigTuple((short)0x1239, 0),
		new IntConfigTuple((short)0x123a, 0),
		new IntConfigTuple((short)0x123d, 96000),
		new IntConfigTuple((short)0x123e, 5),
		new IntConfigTuple((short)0x123f, 1),
		new IntConfigTuple((short)0x1243, 100)
		};
	
	public static final ConfigTuple[] CONFIG_1080_30_DIFF =
		{
		new IntConfigTuple((short)0x120b, 10),
		new IntConfigTuple((short)0x120c, 10),
		new IntConfigTuple((short)0x121c, 4000),
		new IntConfigTuple((short)0x1245, 3000),
		new IntConfigTuple((short)0x1246, 1280),
		new IntConfigTuple((short)0x1247, 720),
		new IntConfigTuple((short)0x124a, 5000),
		new IntConfigTuple((short)0x124c, 7000),
		};
	
	public static final ConfigTuple[] CONFIG_1080_60_DIFF =
		{
		new IntConfigTuple((short)0x120b, 30),
		new IntConfigTuple((short)0x120c, 30),
		new IntConfigTuple((short)0x120f, 4),
		new IntConfigTuple((short)0x121b, 30000),
		new IntConfigTuple((short)0x121c, 25000),
		new IntConfigTuple((short)0x1245, 3000),
		new IntConfigTuple((short)0x1246, 1280),
		new IntConfigTuple((short)0x1247, 720),
		new IntConfigTuple((short)0x124a, 5000),
		new IntConfigTuple((short)0x124c, 7000),
		};
		
	private StreamConfiguration streamConfig;
	
	public Config(StreamConfiguration streamConfig) {
		this.streamConfig = streamConfig;
	}
	
	private void updateSetWithConfig(HashSet<ConfigTuple> set, ConfigTuple[] config)
	{
		for (ConfigTuple tuple : config)
		{
			// Remove any existing tuple of this type
			set.remove(tuple);
			
			set.add(tuple);
		}
	}
	
	private int getConfigOnWireSize(HashSet<ConfigTuple> tupleSet)
	{
		int size = 0;
		
		for (ConfigTuple t : tupleSet)
		{
			size += ConfigTuple.HEADER_LENGTH + t.payloadLength;
		}
		
		return size;
	}
	
	private HashSet<ConfigTuple> generateTupleSet() {
		HashSet<ConfigTuple> tupleSet = new HashSet<ConfigTuple>();
		
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
		
		tupleSet.add(new IntConfigTuple((short)0x1204, streamConfig.getWidth()));
		tupleSet.add(new IntConfigTuple((short)0x1205, streamConfig.getHeight()));
		tupleSet.add(new IntConfigTuple((short)0x120A, streamConfig.getRefreshRate()));
		
		return tupleSet;
	}
	
	public byte[] toWire() {
		HashSet<ConfigTuple> tupleSet = generateTupleSet();
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
