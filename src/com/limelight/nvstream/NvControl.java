package com.limelight.nvstream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.limelight.nvstream.av.ConnectionStatusListener;

public class NvControl implements ConnectionStatusListener {
	
	public static final int PORT = 47995;
	
	public static final int CONTROL_TIMEOUT = 5000;
	
	public static final short PTYPE_HELLO = 0x1204;
	public static final short PPAYLEN_HELLO = 0x0004;
	public static final byte[] PPAYLOAD_HELLO =
		{
		(byte)0x00,
		(byte)0x05,
		(byte)0x00,
		(byte)0x00
		};
	
	public static final short PTYPE_KEEPALIVE = 0x13ff;
	public static final short PPAYLEN_KEEPALIVE = 0x0000;
	
	public static final short PTYPE_HEARTBEAT = 0x1401;
	public static final short PPAYLEN_HEARTBEAT = 0x0000;
	
	public static final short PTYPE_1405 = 0x1405;
	public static final short PPAYLEN_1405 = 0x0000;
	
	public static final short PTYPE_RESYNC = 0x1404;
	public static final short PPAYLEN_RESYNC = 16;
	
	public static final short PTYPE_CONFIG = 0x1205;
	public static final short PPAYLEN_CONFIG = 0x0004;
	public static final int[] PPAYLOAD_CONFIG =
		{
		720,
		266758,
		1,
		266762,
		30,
		70151,
		68291329,
		1280,
		68291584,
		1280,
		68291840,
		15360,
		68292096,
		25600,
		68292352,
		2048,
		68292608,
		1024,
		68289024,
		262144,
		17957632,
		302055424,
		134217729,
		16777490,
		70153,
		68293120,
		768000,
		17961216,
		303235072,
		335609857,
		838861842,
		352321536,
		1006634002,
		369098752,
		335545362,
		385875968,
		1042,
		402653184,
		134218770,
		419430400,
		167773202,
		436207616,
		855638290,
		266779,
		7000,
		266780,
		2000,
		266781,
		50,
		266782,
		3000,
		266783,
		2,
		266794,
		5000,
		266795,
		500,
		266784,
		75,
		266785,
		25,
		266786,
		10,
		266787,
		60,
		266788,
		30,
		266789,
		3,
		266790,
		1000,
		266791,
		5000,
		266792,
		5000,
		266793,
		5000,
		70190,
		68301063,
		10240,
		68301312,
		6400,
		68301568,
		768000,
		68299776,
		768,
		68300032,
		2560,
		68300544,
		0,
		34746368,
		(int)0xFE000000
		};

	
	public static final short PTYPE_JITTER = 0x140c;
	public static final short PPAYLEN_JITTER = 0x10;
	
	private int seqNum;
	
	private NvConnectionListener listener;
	private InetAddress host;
	
	private Socket s;
	private InputStream in;
	private OutputStream out;
	
	private Thread heartbeatThread;
	private Thread jitterThread;
	private boolean aborting = false;
	
	public NvControl(InetAddress host, NvConnectionListener listener)
	{
		this.listener = listener;
		this.host = host;
	}
	
	public void initialize() throws IOException
	{
		s = new Socket();
		s.setSoTimeout(CONTROL_TIMEOUT);
		s.connect(new InetSocketAddress(host, PORT), CONTROL_TIMEOUT);
		in = s.getInputStream();
		out = s.getOutputStream();
	}
	
	private void sendPacket(NvCtlPacket packet) throws IOException
	{
		out.write(packet.toWire());
		out.flush();
	}
	
	private NvControl.NvCtlResponse sendAndGetReply(NvCtlPacket packet) throws IOException
	{
		sendPacket(packet);
		return new NvCtlResponse(in);
	}
	
	private void sendJitter() throws IOException
	{
		ByteBuffer bb = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
		
		bb.putInt(0);
		bb.putInt(77);
		bb.putInt(888);
		bb.putInt(seqNum += 2);

		sendPacket(new NvCtlPacket(PTYPE_JITTER, PPAYLEN_JITTER, bb.array()));
	}
	
	public void abort()
	{
		if (aborting) {
			return;
		}
		
		aborting = true;
		
		if (jitterThread != null) {
			jitterThread.interrupt();
		}
		
		if (heartbeatThread != null) {
			heartbeatThread.interrupt();
		}
		
		try {
			s.close();
		} catch (IOException e) {}
	}
	
	public void requestResync() throws IOException
	{
		System.out.println("CTL: Requesting IDR frame");
		sendResync();
	}
	
	public void start() throws IOException
	{
		sendHello();
		sendConfig();
		pingPong();
		send1405AndGetResponse();
		
		heartbeatThread = new Thread() {
			@Override
			public void run() {
				while (!isInterrupted())
				{
					try {
						sendHeartbeat();
					} catch (IOException e) {
						listener.connectionTerminated(e);
						return;
					}
					
					
					try {
						Thread.sleep(3000);
					} catch (InterruptedException e) {
						listener.connectionTerminated(e);
						return;
					}
				}
			}
		};
		heartbeatThread.start();
	}
	
	public void startJitterPackets()
	{
		jitterThread = new Thread() {
			@Override
			public void run() {
				while (!isInterrupted())
				{
					try {
						sendJitter();
					} catch (IOException e) {
						listener.connectionTerminated(e);
						return;
					}
					
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						listener.connectionTerminated(e);
						return;
					}
				}
			}
		};
		jitterThread.start();
	}
	
	private NvControl.NvCtlResponse send1405AndGetResponse() throws IOException
	{
		return sendAndGetReply(new NvCtlPacket(PTYPE_1405, PPAYLEN_1405));
	}
	
	private void sendHello() throws IOException
	{
		sendPacket(new NvCtlPacket(PTYPE_HELLO, PPAYLEN_HELLO, PPAYLOAD_HELLO));
	}
	
	private void sendResync() throws IOException
	{
		ByteBuffer conf = ByteBuffer.wrap(new byte[PPAYLEN_RESYNC]).order(ByteOrder.LITTLE_ENDIAN);
		
		conf.putLong(0);
		conf.putLong(0xFFFF);
		
		sendAndGetReply(new NvCtlPacket(PTYPE_RESYNC, PPAYLEN_RESYNC, conf.array()));
	}
	
	private void sendConfig() throws IOException
	{
		ByteBuffer conf = ByteBuffer.wrap(new byte[PPAYLOAD_CONFIG.length * 4 + 3]).order(ByteOrder.LITTLE_ENDIAN);
		
		for (int i : PPAYLOAD_CONFIG)
			conf.putInt(i);
		
		conf.putShort((short)0x0013);
		conf.put((byte) 0x00);
		
		sendPacket(new NvCtlPacket(PTYPE_CONFIG, PPAYLEN_CONFIG, conf.array()));
	}
	
	private void sendHeartbeat() throws IOException
	{
		sendPacket(new NvCtlPacket(PTYPE_HEARTBEAT, PPAYLEN_HEARTBEAT));
	}
	
	private NvControl.NvCtlResponse pingPong() throws IOException
	{
		sendPacket(new NvCtlPacket(PTYPE_KEEPALIVE, PPAYLEN_KEEPALIVE));
		return new NvControl.NvCtlResponse(in);
	}
	
	class NvCtlPacket {
		public short type;
		public short paylen;
		public byte[] payload;
		
		public NvCtlPacket(InputStream in) throws IOException
		{
			byte[] header = new byte[4];
			
			int offset = 0;
			do
			{
				int bytesRead = in.read(header, offset, header.length - offset);
				if (bytesRead < 0) {
					break;
				}
				offset += bytesRead;
			} while (offset != header.length);
			
			if (offset != header.length) {
				throw new IOException("Socket closed prematurely");
			}
			
			ByteBuffer bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
			
			type = bb.getShort();
			paylen = bb.getShort();
			
			if (paylen != 0)
			{
				payload = new byte[paylen];

				offset = 0;
				do
				{
					int bytesRead = in.read(payload, offset, payload.length - offset);
					if (bytesRead < 0) {
						break;
					}
					offset += bytesRead;
				} while (offset != payload.length);
				
				if (offset != payload.length) {
					throw new IOException("Socket closed prematurely");
				}
			}
		}
		
		public NvCtlPacket(byte[] payload)
		{
			ByteBuffer bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
			
			type = bb.getShort();
			paylen = bb.getShort();
			
			if (bb.hasRemaining())
			{
				payload = new byte[bb.remaining()];
				bb.get(payload);
			}
		}
		
		public NvCtlPacket(short type, short paylen)
		{
			this.type = type;
			this.paylen = paylen;
		}
		
		public NvCtlPacket(short type, short paylen, byte[] payload)
		{
			this.type = type;
			this.paylen = paylen;
			this.payload = payload;
		}
		
		public short getType()
		{
			return type;
		}
		
		public short getPaylen()
		{
			return paylen;
		}
		
		public void setType(short type)
		{
			this.type = type;
		}
		
		public void setPaylen(short paylen)
		{
			this.paylen = paylen;
		}
		
		public byte[] toWire()
		{
			ByteBuffer bb = ByteBuffer.allocate(4 + (payload != null ? payload.length : 0)).order(ByteOrder.LITTLE_ENDIAN);
			
			bb.putShort(type);
			bb.putShort(paylen);
			
			if (payload != null)
				bb.put(payload);
			
			return bb.array();
		}
	}
	
	class NvCtlResponse extends NvCtlPacket {
		public short status;
		
		public NvCtlResponse(InputStream in) throws IOException {
			super(in);
		}
		
		public NvCtlResponse(short type, short paylen) {
			super(type, paylen);
		}
		
		public NvCtlResponse(short type, short paylen, byte[] payload) {
			super(type, paylen, payload);
		}
		
		public NvCtlResponse(byte[] payload) {
			super(payload);
		}
		
		public void setStatusCode(short status)
		{
			this.status = status;
		}
		
		public short getStatusCode()
		{
			return status;
		}
	}

	@Override
	public void connectionTerminated() {
		abort();
	}

	@Override
	public void connectionNeedsResync() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					requestResync();
				} catch (IOException e1) {
					abort();
					return;
				}
			}
		}).start();
	}
}
