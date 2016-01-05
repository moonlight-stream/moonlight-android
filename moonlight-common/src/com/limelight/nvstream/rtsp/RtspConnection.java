package com.limelight.nvstream.rtsp;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;

import com.limelight.nvstream.ConnectionContext;
import com.limelight.nvstream.av.video.VideoDecoderRenderer.VideoFormat;
import com.tinyrtsp.rtsp.message.RtspMessage;
import com.tinyrtsp.rtsp.message.RtspRequest;
import com.tinyrtsp.rtsp.message.RtspResponse;
import com.tinyrtsp.rtsp.parser.RtspStream;

public class RtspConnection {
	public static final int PORT = 48010;
	public static final int RTSP_TIMEOUT = 10000;
	
	private int sequenceNumber = 1;
	private int sessionId = 0;
	
	private ConnectionContext context;
	private String hostStr;
	
	public RtspConnection(ConnectionContext context) {
		this.context = context;
		if (context.serverAddress instanceof Inet6Address) {
			// RFC2732-formatted IPv6 address for use in URL
			this.hostStr = "["+context.serverAddress.getHostAddress()+"]";
		}
		else {
			this.hostStr = context.serverAddress.getHostAddress();
		}
	}
	
	public static int getRtspVersionFromContext(ConnectionContext context) {
		switch (context.serverGeneration)
		{
		case ConnectionContext.SERVER_GENERATION_3:
			return 10;
		case ConnectionContext.SERVER_GENERATION_4:
		default:
			return 11;
		}
	}

	private RtspRequest createRtspRequest(String command, String target) {
		RtspRequest m = new RtspRequest(command, target, "RTSP/1.0",
				sequenceNumber++, new HashMap<String, String>(), null);
		m.setOption("X-GS-ClientVersion", ""+getRtspVersionFromContext(context));
		return m;
	}
	
	private RtspResponse transactRtspMessage(RtspMessage m) throws IOException {
		Socket s = new Socket();
		try {
			s.setTcpNoDelay(true);
			s.connect(new InetSocketAddress(context.serverAddress, PORT), RTSP_TIMEOUT);
			
			RtspStream rtspStream = new RtspStream(s.getInputStream(), s.getOutputStream());
			try {
				rtspStream.write(m);
				return (RtspResponse) rtspStream.read();
			} finally {
				rtspStream.close();
			}
		} finally {
			s.close();
		}
	}
	
	private RtspResponse requestOptions() throws IOException {
		RtspRequest m = createRtspRequest("OPTIONS", "rtsp://"+hostStr);
		return transactRtspMessage(m);
	}
	
	private RtspResponse requestDescribe() throws IOException {
		RtspRequest m = createRtspRequest("DESCRIBE", "rtsp://"+hostStr);
		m.setOption("Accept", "application/sdp");
		m.setOption("If-Modified-Since", "Thu, 01 Jan 1970 00:00:00 GMT");
		return transactRtspMessage(m);
	}
	
	private RtspResponse setupStream(String streamName) throws IOException {
		RtspRequest m = createRtspRequest("SETUP", "streamid="+streamName);
		if (sessionId != 0) {
			m.setOption("Session", ""+sessionId);
		}
		m.setOption("Transport", " ");
		m.setOption("If-Modified-Since", "Thu, 01 Jan 1970 00:00:00 GMT");
		return transactRtspMessage(m);
	}
	
	private RtspResponse playStream(String streamName) throws IOException {
		RtspRequest m = createRtspRequest("PLAY", "streamid="+streamName);
		m.setOption("Session", ""+sessionId);
		return transactRtspMessage(m);
	}
	
	private RtspResponse sendVideoAnnounce() throws IOException {
		RtspRequest m = createRtspRequest("ANNOUNCE", "streamid=video");
		m.setOption("Session", ""+sessionId);
		m.setOption("Content-type", "application/sdp");
		m.setPayload(SdpGenerator.generateSdpFromContext(context));
		m.setOption("Content-length", ""+m.getPayload().length());
		return transactRtspMessage(m);
	}
	
	private void processDescribeResponse(RtspResponse r) {
		// The RTSP DESCRIBE reply will contain a collection of SDP media attributes that
		// describe the various supported video stream formats and include the SPS, PPS,
		// and VPS (if applicable). We will use this information to determine whether the
		// server can support HEVC. For some reason, they still set the MIME type of the HEVC
		// format to H264, so we can't just look for the HEVC MIME type. What we'll do instead is
		// look for the base 64 encoded VPS NALU prefix that is unique to the HEVC bitstream.
		String describeSdpContent = r.getPayload();
		if (context.streamConfig.getHevcSupported() &&
				describeSdpContent.contains("sprop-parameter-sets=AAAAAU")) {
			context.negotiatedVideoFormat = VideoFormat.H265;
		}
		else {
			context.negotiatedVideoFormat = VideoFormat.H264;
		}
	}
	
	private void processRtspSetupAudio(RtspResponse r) throws IOException {
		try {
			sessionId = Integer.parseInt(r.getOption("Session"));
		} catch (NumberFormatException e) {
			throw new IOException("RTSP SETUP response was malformed");
		}
	}
	
	public void doRtspHandshake() throws IOException {
		RtspResponse r;
		
		r = requestOptions();
		if (r.getStatusCode() != 200) {
			throw new IOException("RTSP OPTIONS request failed: "+r.getStatusCode());
		}
		
		r = requestDescribe();
		if (r.getStatusCode() != 200) {
			throw new IOException("RTSP DESCRIBE request failed: "+r.getStatusCode());
		}
		
		// Process the RTSP DESCRIBE response
		processDescribeResponse(r);
		
		r = setupStream("audio");
		if (r.getStatusCode() != 200) {
			throw new IOException("RTSP SETUP request failed: "+r.getStatusCode());
		}
		
		// Process the RTSP SETUP streamid=audio response
		processRtspSetupAudio(r);
		
		r = setupStream("video");
		if (r.getStatusCode() != 200) {
			throw new IOException("RTSP SETUP request failed: "+r.getStatusCode());
		}
		
		r = sendVideoAnnounce();
		if (r.getStatusCode() != 200) {
			throw new IOException("RTSP ANNOUNCE request failed: "+r.getStatusCode());
		}
		
		r = playStream("video");
		if (r.getStatusCode() != 200) {
			throw new IOException("RTSP PLAY request failed: "+r.getStatusCode());
		}
		r = playStream("audio");
		if (r.getStatusCode() != 200) {
			throw new IOException("RTSP PLAY request failed: "+r.getStatusCode());
		}
	}
}
