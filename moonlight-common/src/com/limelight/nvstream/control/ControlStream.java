package com.limelight.nvstream.control;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.LinkedBlockingQueue;

import com.limelight.LimeLog;
import com.limelight.nvstream.NvConnectionListener;
import com.limelight.nvstream.av.ConnectionStatusListener;

public class ControlStream implements ConnectionStatusListener {
	
	public static final int PORT = 47995;
	
	public static final int CONTROL_TIMEOUT = 5000;
	
	public static final short PTYPE_START_STREAM_A = 0x0606;
	public static final short PPAYLEN_START_STREAM_A = 2;
	public static final byte[] PPAYLOAD_START_STREAM_A = new byte[]{0, 0};
	
	public static final short PTYPE_START_STREAM_B = 0x0609;
	public static final short PPAYLEN_START_STREAM_B = 1;
	public static final byte[] PPAYLOAD_START_STREAM_B = new byte[]{0};
	
	public static final short PTYPE_RESYNC = 0x0604;
	public static final short PPAYLEN_RESYNC = 24;
	
	public static final short PTYPE_LOSS_STATS = 0x060a;
	public static final short PPAYLEN_LOSS_STATS = 32;
	
	// Currently unused
	public static final short PTYPE_FRAME_STATS = 0x0611;
	public static final short PPAYLEN_FRAME_STATS = 64;
	
	public static final int LOSS_REPORT_INTERVAL_MS = 50;
	
	private int currentFrame;
	private int lossCountSinceLastReport;
	
	private NvConnectionListener listener;
	private InetAddress host;
	
	public static final int LOSS_PERIOD_MS = 15000;
	public static final int MAX_LOSS_COUNT_IN_PERIOD = 5;
	public static final int MAX_SLOW_SINK_COUNT = 2;
	public static final int MESSAGE_DELAY_FACTOR = 3;
	
	private long lossTimestamp;
	private int lossCount;
	private int slowSinkCount;
	
	private Socket s;
	private InputStream in;
	private OutputStream out;
	
	private Thread lossStatsThread;
	private Thread resyncThread;
	private LinkedBlockingQueue<int[]> invalidReferenceFrameTuples = new LinkedBlockingQueue<int[]>();
	private boolean aborting = false;
	
	public ControlStream(InetAddress host, NvConnectionListener listener)
	{
		this.listener = listener;
		this.host = host;
	}
	
	public void initialize() throws IOException
	{
		s = new Socket();
		s.setTcpNoDelay(true);
		s.connect(new InetSocketAddress(host, PORT), CONTROL_TIMEOUT);
		in = s.getInputStream();
		out = s.getOutputStream();
	}
	
	private void sendPacket(NvCtlPacket packet) throws IOException
	{
		// Prevent multiple clients from writing to the stream at the same time
		synchronized (this) {
			packet.write(out);
			out.flush();
		}
	}
	
	private ControlStream.NvCtlResponse sendAndGetReply(NvCtlPacket packet) throws IOException
	{
		sendPacket(packet);
		return new NvCtlResponse(in);
	}
	
	private void sendLossStats(ByteBuffer bb) throws IOException
	{
		bb.rewind();
		bb.putInt(lossCountSinceLastReport); // Packet loss count
		bb.putInt(LOSS_REPORT_INTERVAL_MS); // Time since last report in milliseconds
		bb.putInt(1000);
		bb.putLong(currentFrame); // Last successfully received frame
		bb.putInt(0);
		bb.putInt(0);
		bb.putInt(0x14);

		sendPacket(new NvCtlPacket(PTYPE_LOSS_STATS, PPAYLEN_LOSS_STATS, bb.array()));
	}
	
	public void abort()
	{
		if (aborting) {
			return;
		}
		
		aborting = true;
		
		try {
			s.close();
		} catch (IOException e) {}
		
		if (lossStatsThread != null) {
			lossStatsThread.interrupt();
			
			try {
				lossStatsThread.join();
			} catch (InterruptedException e) {}
		}
		
		if (resyncThread != null) {
			resyncThread.interrupt();
			
			try {
				resyncThread.join();
			} catch (InterruptedException e) {}
		}
	}
	
	public void start() throws IOException
	{
		// Use a finite timeout during the handshake process
		s.setSoTimeout(CONTROL_TIMEOUT);
		
		doStartA();
		doStartB();
		
		// Return to an infinte read timeout after the initial control handshake
		s.setSoTimeout(0);
		
		lossStatsThread = new Thread() {
			@Override
			public void run() {
				ByteBuffer bb = ByteBuffer.allocate(PPAYLEN_LOSS_STATS).order(ByteOrder.LITTLE_ENDIAN);
				
				while (!isInterrupted())
				{	
					try {
						sendLossStats(bb);
						lossCountSinceLastReport = 0;
					} catch (IOException e) {
						listener.connectionTerminated(e);
						return;
					}
					
					try {
						Thread.sleep(LOSS_REPORT_INTERVAL_MS);
					} catch (InterruptedException e) {
						listener.connectionTerminated(e);
						return;
					}
				}
			}
		};
		lossStatsThread.setPriority(Thread.MIN_PRIORITY + 1);
		lossStatsThread.setName("Control - Loss Stats Thread");
		lossStatsThread.start();
		
		resyncThread = new Thread() {
			@Override
			public void run() {
				while (!isInterrupted())
				{
					int[] tuple;
					
					// Wait for a tuple
					try {
						tuple = invalidReferenceFrameTuples.take();
					} catch (InterruptedException e) {
						listener.connectionTerminated(e);
						return;
					}
					
					// Aggregate all lost frames into one range
					int[] lastTuple = null;
					for (;;) {
						int[] nextTuple = lastTuple = invalidReferenceFrameTuples.poll();
						if (nextTuple == null) {
							break;
						}
						
						lastTuple = nextTuple;
					}
					
					// The server expects this to be the firstLostFrame + 1
					tuple[0]++;
					
					// Update the end of the range to the latest tuple
					if (lastTuple != null) {
						tuple[1] = lastTuple[1];
					}
					
					try {
						LimeLog.warning("Invalidating reference frames from "+tuple[0]+" to "+tuple[1]);
						ControlStream.this.sendResync(tuple[0], tuple[1]);
						LimeLog.warning("Frames invalidated");
					} catch (IOException e) {
						listener.connectionTerminated(e);
						return;
					}
				}
			}
		};
		resyncThread.setName("Control - Resync Thread");
		resyncThread.setPriority(Thread.MAX_PRIORITY - 1);
		resyncThread.start();
	}
	
	private ControlStream.NvCtlResponse doStartA() throws IOException
	{
		return sendAndGetReply(new NvCtlPacket(PTYPE_START_STREAM_A,
				PPAYLEN_START_STREAM_A, PPAYLOAD_START_STREAM_A));
	}
	
	private ControlStream.NvCtlResponse doStartB() throws IOException
	{
		return sendAndGetReply(new NvCtlPacket(PTYPE_START_STREAM_B,
				PPAYLEN_START_STREAM_B, PPAYLOAD_START_STREAM_B));
	}
	
	private void sendResync(int firstLostFrame, int nextSuccessfulFrame) throws IOException
	{
		ByteBuffer conf = ByteBuffer.wrap(new byte[PPAYLEN_RESYNC]).order(ByteOrder.LITTLE_ENDIAN);
		
		//conf.putLong(firstLostFrame);
		//conf.putLong(nextSuccessfulFrame);
		conf.putLong(0);
		conf.putLong(0xFFFFF);
		conf.putLong(0);
		
		sendAndGetReply(new NvCtlPacket(PTYPE_RESYNC, PPAYLEN_RESYNC, conf.array()));
	}
	
	static class NvCtlPacket {
		public short type;
		public short paylen;
		public byte[] payload;
		
		private static final ByteBuffer headerBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
		private static final ByteBuffer serializationBuffer = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN);
		
		public NvCtlPacket(InputStream in) throws IOException
		{
			// Use the class's static header buffer for parsing the header
			synchronized (headerBuffer) {
				int offset = 0;
				byte[] header = headerBuffer.array();
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

				headerBuffer.rewind();
				type = headerBuffer.getShort();
				paylen = headerBuffer.getShort();
			}

			if (paylen != 0)
			{
				payload = new byte[paylen];
				
				int offset = 0;
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
		
		public NvCtlPacket(byte[] packet)
		{
			synchronized (headerBuffer) {
				headerBuffer.rewind();

				headerBuffer.put(packet, 0, 4);
				headerBuffer.rewind();
				
				type = headerBuffer.getShort();
				paylen = headerBuffer.getShort();
			}
			
			if (paylen != 0)
			{
				payload = new byte[paylen];
				System.arraycopy(packet, 4, payload, 0, paylen);
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
		
		public void write(OutputStream out) throws IOException
		{
			// Use the class's serialization buffer to construct the wireform to send
			synchronized (serializationBuffer) {
				serializationBuffer.rewind();
				serializationBuffer.putShort(type);
				serializationBuffer.putShort(paylen);
				serializationBuffer.put(payload);

				out.write(serializationBuffer.array(), 0, serializationBuffer.position());
			}
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

	public void connectionTerminated() {
		abort();
	}

	private void resyncConnection(int firstLostFrame, int nextSuccessfulFrame) {
		invalidReferenceFrameTuples.add(new int[]{firstLostFrame, nextSuccessfulFrame});
	}

	public void connectionDetectedFrameLoss(int firstLostFrame, int nextSuccessfulFrame) {
		resyncConnection(firstLostFrame, nextSuccessfulFrame);
		
		// Suppress connection warnings for the first 150 frames to allow the connection
		// to stabilize
		if (currentFrame < 150) {
			return;
		}
		
		if (System.currentTimeMillis() > LOSS_PERIOD_MS + lossTimestamp) {
			lossCount++;
			lossTimestamp = System.currentTimeMillis();
		}
		else {
			if (++lossCount == MAX_LOSS_COUNT_IN_PERIOD) {
				listener.displayTransientMessage("Detected high amounts of network packet loss");
				lossCount = -MAX_LOSS_COUNT_IN_PERIOD * MESSAGE_DELAY_FACTOR;
				lossTimestamp = 0;
			}
		}
	}

	public void connectionSinkTooSlow(int firstLostFrame, int nextSuccessfulFrame) {
		resyncConnection(firstLostFrame, nextSuccessfulFrame);
		
		// Suppress connection warnings for the first 150 frames to allow the connection
		// to stabilize
		if (currentFrame < 150) {
			return;
		}
		
		if (++slowSinkCount == MAX_SLOW_SINK_COUNT) {
			listener.displayTransientMessage("Your device is processing the A/V data too slowly. Try lowering stream resolution and/or frame rate.");
			slowSinkCount = -MAX_SLOW_SINK_COUNT * MESSAGE_DELAY_FACTOR;
		}
	}

	public void connectionReceivedFrame(int frameIndex) {
		currentFrame = frameIndex;
	}

	public void connectionLostPackets(int lastReceivedPacket, int nextReceivedPacket) {
		// Update the loss count for the next loss report
		lossCountSinceLastReport += (nextReceivedPacket - lastReceivedPacket) - 1;
	}
}
