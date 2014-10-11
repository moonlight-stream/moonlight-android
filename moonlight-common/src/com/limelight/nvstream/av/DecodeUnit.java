package com.limelight.nvstream.av;

import java.util.HashSet;
import java.util.List;

import com.limelight.nvstream.av.video.VideoPacket;

public class DecodeUnit {
	public static final int TYPE_UNKNOWN = 0;
	public static final int TYPE_H264 = 1;
	public static final int TYPE_OPUS = 2;
	
	public static final int DU_FLAG_CODEC_CONFIG = 0x1;
	public static final int DU_FLAG_SYNC_FRAME = 0x2;
	
	private int type;
	private List<ByteBufferDescriptor> bufferList;
	private int dataLength;
	private int frameNumber;
	private long receiveTimestamp;
	private int flags;
	private HashSet<VideoPacket> backingPackets; 
	
	public DecodeUnit() {
	}
	
	public void initialize(int type, List<ByteBufferDescriptor> bufferList, int dataLength,
			int frameNumber, long receiveTimestamp, int flags, HashSet<VideoPacket> backingPackets)
	{
		this.type = type;
		this.bufferList = bufferList;
		this.dataLength = dataLength;
		this.frameNumber = frameNumber;
		this.receiveTimestamp = receiveTimestamp;
		this.flags = flags;
		this.backingPackets = backingPackets;
	}
	
	public int getType()
	{
		return type;
	}
	
	public long getReceiveTimestamp()
	{
		return receiveTimestamp;
	}
	
	public List<ByteBufferDescriptor> getBufferList()
	{
		return bufferList;
	}
	
	public int getDataLength()
	{
		return dataLength;
	}
	
	public int getFrameNumber()
	{
		return frameNumber;
	}
	
	public int getFlags()
	{
		return flags;
	}
	
	// Internal use only
	public HashSet<VideoPacket> getBackingPackets() {
		return backingPackets;
	}
	
	// Internal use only
	public void clearBackingPackets() {
		backingPackets.clear();
	}
}
