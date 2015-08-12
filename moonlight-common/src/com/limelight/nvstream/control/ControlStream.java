package com.limelight.nvstream.control;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.LinkedBlockingQueue;

import com.limelight.LimeLog;
import com.limelight.nvstream.ConnectionContext;
import com.limelight.nvstream.av.ConnectionStatusListener;
import com.limelight.nvstream.av.video.VideoDecoderRenderer;

public class ControlStream implements ConnectionStatusListener {
	
	private static final int PORT = 47995;
	
	private static final int CONTROL_TIMEOUT = 5000;
	
	private static final int IDX_START_A = 0;
	private static final int IDX_REQUEST_IDR_FRAME = 0;
	private static final int IDX_START_B = 1;
	private static final int IDX_INVALIDATE_REF_FRAMES = 2;
	private static final int IDX_LOSS_STATS = 3;
	
	private static final short packetTypesGen3[] = {
		0x140b, // Start A
		0x1410, // Start B
		0x1404, // Invalidate reference frames
		0x140c, // Loss Stats
		0x1417, // Frame Stats (unused)
	};
	private static final short packetTypesGen4[] = {
		0x0606, // Request IDR frame
		0x0609, // Start B
		0x0604, // Invalidate reference frames
		0x060a, // Loss Stats
		0x0611, // Frame Stats (unused)
	};
	
	private static final short payloadLengthsGen3[] = {
		-1, // Start A
		16, // Start B
		24, // Invalidate reference frames
		32, // Loss Stats
		64, // Frame Stats
	};
	private static final short payloadLengthsGen4[] = {
		-1, // Request IDR frame
		-1, // Start B
		24, // Invalidate reference frames
		32, // Loss Stats
		64, // Frame Stats
	};
	
	private static final byte[] precontructedPayloadsGen3[] = {
		new byte[]{0}, // Start A
		null, // Start B
		null, // Invalidate reference frames
		null, // Loss Stats
		null, // Frame Stats
	};
	private static final byte[] precontructedPayloadsGen4[] = {
		new byte[]{0, 0}, // Request IDR frame
		new byte[]{0},  // Start B
		null, // Invalidate reference frames
		null, // Loss Stats
		null, // Frame Stats
	};
	
	public static final int LOSS_REPORT_INTERVAL_MS = 50;
	
	private int currentFrame;
	private int lossCountSinceLastReport;
	
	private ConnectionContext context;
	
	// If we drop at least 10 frames in 15 second (or less) window
	// more than 5 times in 60 seconds, we'll display a warning
	public static final int LOSS_PERIOD_MS = 15000;
	public static final int LOSS_EVENT_TIME_THRESHOLD_MS = 60000;
	public static final int MAX_LOSS_COUNT_IN_PERIOD = 10;
	public static final int LOSS_EVENTS_TO_WARN = 5;
	public static final int MAX_SLOW_SINK_COUNT = 2;
	public static final int MESSAGE_DELAY_FACTOR = 3;
	
	private long lossTimestamp;
	private long lossEventTimestamp;
	private int lossCount;
	private int lossEventCount;

	private int slowSinkCount;
	
	private Socket s;
	private InputStream in;
	private OutputStream out;
	
	private Thread lossStatsThread;
	private Thread resyncThread;
	private LinkedBlockingQueue<int[]> invalidReferenceFrameTuples = new LinkedBlockingQueue<int[]>();
	private boolean aborting = false;
	private boolean forceIdrRequest;
	
	private final short[] packetTypes;
	private final short[] payloadLengths;
	private final byte[][] preconstructedPayloads;
	
	public ControlStream(ConnectionContext context)
	{
		this.context = context;
		
		switch (context.serverGeneration)
		{
		case ConnectionContext.SERVER_GENERATION_3:
			packetTypes = packetTypesGen3;
			payloadLengths = payloadLengthsGen3;
			preconstructedPayloads = precontructedPayloadsGen3;
			break;
		case ConnectionContext.SERVER_GENERATION_4:
		default:
			packetTypes = packetTypesGen4;
			payloadLengths = payloadLengthsGen4;
			preconstructedPayloads = precontructedPayloadsGen4;
			break;
		}
		
		if (context.videoDecoderRenderer != null) {
			forceIdrRequest = (context.videoDecoderRenderer.getCapabilities() &
					VideoDecoderRenderer.CAPABILITY_REFERENCE_FRAME_INVALIDATION) == 0;
		}
	}
	
	public void initialize() throws IOException
	{
		s = new Socket();
		s.setTcpNoDelay(true);
		s.connect(new InetSocketAddress(context.serverAddress, PORT), CONTROL_TIMEOUT);
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

		sendPacket(new NvCtlPacket(packetTypes[IDX_LOSS_STATS],
				payloadLengths[IDX_LOSS_STATS], bb.array()));
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
				ByteBuffer bb = ByteBuffer.allocate(payloadLengths[IDX_LOSS_STATS]).order(ByteOrder.LITTLE_ENDIAN);
				
				while (!isInterrupted())
				{	
					try {
						sendLossStats(bb);
						lossCountSinceLastReport = 0;
					} catch (IOException e) {
						context.connListener.connectionTerminated(e);
						return;
					}
					
					try {
						Thread.sleep(LOSS_REPORT_INTERVAL_MS);
					} catch (InterruptedException e) {
						context.connListener.connectionTerminated(e);
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
					boolean idrFrameRequired = false;
					
					// Wait for a tuple
					try {
						tuple = invalidReferenceFrameTuples.take();
					} catch (InterruptedException e) {
						context.connListener.connectionTerminated(e);
						return;
					}
					
					// Check for the magic IDR frame tuple
					int[] lastTuple = null;
					if (tuple[0] != 0 || tuple[1] != 0) {
						// Aggregate all lost frames into one range
						for (;;) {
							int[] nextTuple = lastTuple = invalidReferenceFrameTuples.poll();
							if (nextTuple == null) {
								break;
							}
							
							// Check if this tuple has IDR frame magic values
							if (nextTuple[0] == 0 && nextTuple[1] == 0) {
								// We will need an IDR frame now, but we won't break out
								// of the loop because we want to dequeue all pending requests
								idrFrameRequired = true;
							}
							
							lastTuple = nextTuple;
						}
					}
					else {
						// We must require an IDR frame
						idrFrameRequired = true;
					}
					
					try {
						if (forceIdrRequest || idrFrameRequired) {
							requestIdrFrame();
						}
						else {
							// The server expects this to be the firstLostFrame + 1
							tuple[0]++;
							
							// Update the end of the range to the latest tuple
							if (lastTuple != null) {
								tuple[1] = lastTuple[1];
							}
							
							invalidateReferenceFrames(tuple[0], tuple[1]);
						}
					} catch (IOException e) {
						context.connListener.connectionTerminated(e);
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
		return sendAndGetReply(new NvCtlPacket(packetTypes[IDX_START_A],
				(short) preconstructedPayloads[IDX_START_A].length,
				preconstructedPayloads[IDX_START_A]));
	}
	
	private ControlStream.NvCtlResponse doStartB() throws IOException
	{
		if (context.serverGeneration == ConnectionContext.SERVER_GENERATION_3) {
	        ByteBuffer payload = ByteBuffer.wrap(new byte[payloadLengths[IDX_START_B]]).order(ByteOrder.LITTLE_ENDIAN);
	        
	        payload.putInt(0);
	        payload.putInt(0);
	        payload.putInt(0);
	        payload.putInt(0xa);
	        
	        return sendAndGetReply(new NvCtlPacket(packetTypes[IDX_START_B],
	        		payloadLengths[IDX_START_B], payload.array()));
		}
		else {
			return sendAndGetReply(new NvCtlPacket(packetTypes[IDX_START_B],
					 (short) preconstructedPayloads[IDX_START_B].length,
					 preconstructedPayloads[IDX_START_B]));
		}
	}
	
	private void requestIdrFrame() throws IOException {
		// On Gen 3, we use the invalidate reference frames trick which works for about 5 hours of streaming at 60 FPS
		// On Gen 4+, we use the known IDR frame request packet
		
		if (context.serverGeneration == ConnectionContext.SERVER_GENERATION_3) {
			ByteBuffer conf = ByteBuffer.wrap(new byte[payloadLengths[IDX_INVALIDATE_REF_FRAMES]]).order(ByteOrder.LITTLE_ENDIAN);
			
			//conf.putLong(firstLostFrame);
			//conf.putLong(nextSuccessfulFrame);
			conf.putLong(0);
			conf.putLong(0xFFFFF);
			conf.putLong(0);
			
			sendAndGetReply(new NvCtlPacket(packetTypes[IDX_INVALIDATE_REF_FRAMES],
					payloadLengths[IDX_INVALIDATE_REF_FRAMES], conf.array()));
		}
		else {
			sendAndGetReply(new NvCtlPacket(packetTypes[IDX_REQUEST_IDR_FRAME],
					(short) preconstructedPayloads[IDX_REQUEST_IDR_FRAME].length,
					preconstructedPayloads[IDX_REQUEST_IDR_FRAME]));
		}
		
		LimeLog.warning("IDR frame request sent");
	}
	
	private void invalidateReferenceFrames(int firstLostFrame, int nextSuccessfulFrame) throws IOException {
		LimeLog.warning("Invalidating reference frames from "+firstLostFrame+" to "+nextSuccessfulFrame);

		ByteBuffer conf = ByteBuffer.wrap(new byte[payloadLengths[IDX_INVALIDATE_REF_FRAMES]]).order(ByteOrder.LITTLE_ENDIAN);
		
		conf.putLong(firstLostFrame);
		conf.putLong(nextSuccessfulFrame);
		conf.putLong(0);
		
		sendAndGetReply(new NvCtlPacket(packetTypes[IDX_INVALIDATE_REF_FRAMES],
				payloadLengths[IDX_INVALIDATE_REF_FRAMES], conf.array()));
		
		LimeLog.warning("Reference frame invalidation sent");
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
		
		// Reset the loss count if it's been too long
		if (System.currentTimeMillis() > LOSS_PERIOD_MS + lossTimestamp) {
			lossCount = 0;
			lossTimestamp = System.currentTimeMillis();
		}
		
		// Count this loss event
		if (++lossCount == MAX_LOSS_COUNT_IN_PERIOD) {
			// Reset the loss event count if it's been too long
			if (System.currentTimeMillis() > LOSS_EVENT_TIME_THRESHOLD_MS + lossEventTimestamp) {
				lossEventCount = 0;
				lossEventTimestamp = System.currentTimeMillis();
			}
			
			if (++lossEventCount == LOSS_EVENTS_TO_WARN) {
				context.connListener.displayTransientMessage("Poor network connection");
				
				lossEventCount = 0;
				lossEventTimestamp = 0;
			}
			
			lossCount = 0;
			lossTimestamp = 0;
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
			context.connListener.displayTransientMessage("Your device is processing the A/V data too slowly. Try lowering stream resolution and/or frame rate.");
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
