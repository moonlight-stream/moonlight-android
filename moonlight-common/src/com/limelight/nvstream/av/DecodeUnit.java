package com.limelight.nvstream.av;

import java.util.List;

public class DecodeUnit {
	public static final int TYPE_UNKNOWN = 0;
	public static final int TYPE_H264 = 1;
	public static final int TYPE_OPUS = 2;
	
	private int type;
	private List<ByteBufferDescriptor> bufferList;
	private int dataLength;
	private int flags;
	
	public DecodeUnit(int type, List<ByteBufferDescriptor> bufferList, int dataLength, int flags)
	{
		this.type = type;
		this.bufferList = bufferList;
		this.dataLength = dataLength;
		this.flags = flags;
	}
	
	public int getType()
	{
		return type;
	}
	
	public int getFlags()
	{
		return flags;
	}
	
	public List<ByteBufferDescriptor> getBufferList()
	{
		return bufferList;
	}
	
	public int getDataLength()
	{
		return dataLength;
	}
}
