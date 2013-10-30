package com.limelight.nvstream.av;

import java.util.List;

public class AvDecodeUnit {
	public static final int TYPE_UNKNOWN = 0;
	public static final int TYPE_H264 = 1;
	
	private int type;
	private List<AvBufferDescriptor> bufferList;
	private int dataLength;
	
	public AvDecodeUnit(int type, List<AvBufferDescriptor> bufferList, int dataLength)
	{
		this.type = type;
		this.bufferList = bufferList;
		this.dataLength = dataLength;
	}
	
	public int getType()
	{
		return type;
	}
	
	public List<AvBufferDescriptor> getBufferList()
	{
		return bufferList;
	}
	
	public int getDataLength()
	{
		return dataLength;
	}
}
