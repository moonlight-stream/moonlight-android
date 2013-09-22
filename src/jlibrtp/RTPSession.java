/**
 * Java RTP Library (jlibrtp)
 * Copyright (C) 2006 Arne Kepp
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package jlibrtp;

import java.net.DatagramSocket;
import java.net.MulticastSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.concurrent.locks.*;
import java.util.Random;
import java.util.Enumeration;
/**
 * The RTPSession object is the core of jlibrtp. 
 * 
 * One should be instantiated for every communication channel, i.e. if you send voice and video, you should create one for each.
 * 
 * The instance holds a participant database, as well as other information about the session. When the application registers with the session, the necessary threads for receiving and processing RTP packets are spawned.
 * 
 * RTP Packets are sent synchronously, all other operations are asynchronous.
 * 
 * @author Arne Kepp
 */
public class RTPSession {
	 /**
	  * The debug level is final to avoid compilation of if-statements.</br>
	  * 0 provides no debugging information, 20 provides everything </br>
	  * Debug output is written to System.out</br>
	  * Debug level for RTP related things.
	  */
	 final static public int rtpDebugLevel = 0;
	 /**
	  * The debug level is final to avoid compilation of if-statements.</br>
	  * 0 provides no debugging information, 20 provides everything </br>
	  * Debug output is written to System.out</br>
	  * Debug level for RTCP related things.
	  */
	 final static public int rtcpDebugLevel = 0;
	 
	 /** RTP unicast socket */
	 protected DatagramSocket rtpSock = null;
	 /** RTP multicast socket */
	 protected MulticastSocket rtpMCSock = null;
	 /** RTP multicast group */
	 protected InetAddress mcGroup = null;
	 
	 // Internal state
	 /** Whether this session is a multicast session or not */
	 protected boolean mcSession = false;
	 /** Current payload type, can be changed by application */
	 protected int payloadType = 0;
	 /** SSRC of this session */
	 protected long ssrc;
	 /** The last timestamp when we sent something */
	 protected long lastTimestamp = 0;
	 /** Current sequence number */
	 protected int seqNum = 0;
	 /** Number of packets sent by this session */
	 protected int sentPktCount = 0;
	 /** Number of octets sent by this session */
	 protected int sentOctetCount = 0;
	 
	 /** The random seed */
	 protected Random random = null;
	 
	 /** Session bandwidth in BYTES per second */
	 protected int bandwidth = 8000;
	 
	 /** By default we do not return packets from strangers in unicast mode */
	 protected boolean naiveReception = false;
	 
	 /** Should the library attempt frame reconstruction? */
	 protected boolean frameReconstruction = true;
	 
	 /** Maximum number of packets used for reordering */
	 protected int pktBufBehavior = 3;
	 
	 /** Participant database */
	 protected ParticipantDatabase partDb = new ParticipantDatabase(this); 
	 /** Handle to application interface for RTP */
	 protected RTPAppIntf appIntf = null;
	 /** Handle to application interface for RTCP (optional) */
	 protected RTCPAppIntf rtcpAppIntf = null;
	 /** Handle to application interface for AVPF, RFC 4585 (optional) */
	 protected RTCPAVPFIntf rtcpAVPFIntf = null;
	 /** Handle to application interface for debugging */
	 protected DebugAppIntf debugAppIntf = null;
	 
	 /** The RTCP session associated with this RTP Session */
	 protected RTCPSession rtcpSession = null;
	 /** The thread for receiving RTP packets */
	 protected RTPReceiverThread recvThrd = null;
	 /** The thread for invoking callbacks for RTP packets */
	 protected AppCallerThread appCallerThrd = null;
	 
	 /** Lock to protect the packet buffers */
	 final protected Lock pktBufLock = new ReentrantLock();
	 /** Condition variable, to tell the  */
	 final protected Condition pktBufDataReady = pktBufLock.newCondition();
	 
	 /** Enough is enough, set to true when you want to quit. */
	 protected boolean endSession = false;
	 /** Only one registered application, please */
	 protected boolean registered = false;
	 /** We're busy resolving a SSRC conflict, please try again later */
	 protected boolean conflict = false;
	 /** Number of conflicts observed, exessive number suggests loop in network */
	 protected int conflictCount = 0;
	 
	 /** SDES CNAME */
	 protected String cname = null;
	 /** SDES The participant's real name */
	 public String name = null;
	 /** SDES The participant's email */
	 public String email = null;
	 /** SDES The participant's phone number */
	 public String phone = null;
	 /** SDES The participant's location*/
	 public String loc = null;
	 /** SDES The tool the participants is using */
	 public String tool = null;
	 /** SDES A note */
	 public String note = null;
	 /** SDES A priv string, loosely defined */
	 public String priv = null;
		
	 // RFC 4585 stuff. This should live on RTCPSession, but we need to have this
	 // infromation ready by the time the RTCP Session starts
	 // 0 = RFC 3550 , -1 = ACK , 1 = Immediate feedback, 2 = Early RTCP,  
	 protected int rtcpMode = 0;
	 protected int fbEarlyThreshold = -1;		// group size, immediate -> early transition point
	 protected int fbRegularThreshold = -1;	// group size, early -> regular transition point
	 protected int minInterval = 5000;		// minimum interval
	 protected int fbMaxDelay = 1000;			// how long the information is useful
	 // RTCP bandwidth
	 protected int rtcpBandwidth = -1;
	 
	 
	 /**
	  * Returns an instance of a <b>unicast</b> RTP session. 
	  * Following this you should adjust any settings and then register your application.
	  * 
	  * The sockets should have external ip addresses, else your CNAME automatically
	  * generated CNAMe will be bad.
	  * 
	  * @param	rtpSocket UDP socket to receive RTP communication on
	  * @param	rtcpSocket UDP socket to receive RTCP communication on, null if none.
	  */
	 public RTPSession(DatagramSocket rtpSocket, DatagramSocket rtcpSocket) {
		 mcSession = false;
		 rtpSock = rtpSocket;
		 this.generateCNAME();
		 this.generateSsrc();
		 this.rtcpSession = new RTCPSession(this,rtcpSocket);
		 
		 // The sockets are not always imediately available?
		 try { Thread.sleep(1); } catch (InterruptedException e) { System.out.println("RTPSession sleep failed"); }
	 }
	 
	 /**
	  * Returns an instance of a <b>multicast</b> RTP session. 
	  * Following this you should register your application.
	  * 
	  * The sockets should have external ip addresses, else your CNAME automatically
	  * generated CNAMe will be bad.
	  * 
	  * @param	rtpSock a multicast socket to receive RTP communication on
	  * @param	rtcpSock a multicast socket to receive RTP communication on
	  * @param	multicastGroup the multicast group that we want to communicate with.
	  */
	 public RTPSession(MulticastSocket rtpSock, MulticastSocket rtcpSock, InetAddress multicastGroup) throws Exception {
		 mcSession = true;
		 rtpMCSock =rtpSock;
		 mcGroup = multicastGroup;
		 rtpMCSock.joinGroup(mcGroup);
		 rtcpSock.joinGroup(mcGroup);
		 this.generateCNAME();
		 this.generateSsrc();
		 this.rtcpSession = new RTCPSession(this,rtcpSock,mcGroup);
		 
		 // The sockets are not always imediately available?
		 try { Thread.sleep(1); } catch (InterruptedException e) { System.out.println("RTPSession sleep failed"); }
	 }
	 
	 /**
	  * Registers an application (RTPAppIntf) with the RTP session.
	  * The session will call receiveData() on the supplied instance whenever data has been received.
	  * 
	  * Following this you should set the payload type and add participants to the session.
	  * 
	  * @param	rtpApp an object that implements the RTPAppIntf-interface
	  * @param	rtcpApp an object that implements the RTCPAppIntf-interface (optional)
	  * @return	-1 if this RTPSession-instance already has an application registered.
	  */
	 public int RTPSessionRegister(RTPAppIntf rtpApp, RTCPAppIntf rtcpApp, DebugAppIntf debugApp) {
		if(registered) {
			System.out.println("RTPSessionRegister(): Can\'t register another application!");
			return -1;
		} else {
			registered = true;
			generateSeqNum();
			if(RTPSession.rtpDebugLevel > 0) {
				System.out.println("-> RTPSessionRegister");
			}  
			this.appIntf = rtpApp;
			this.rtcpAppIntf = rtcpApp;
			this.debugAppIntf = debugApp;
			
			recvThrd = new RTPReceiverThread(this);
			appCallerThrd = new AppCallerThread(this, rtpApp);
			recvThrd.start();
		 	appCallerThrd.start();
		 	rtcpSession.start();
		 	return 0;
		}
	}
	
	 /**
	  * Send data to all participants registered as receivers, using the current timeStamp,
	  * dynamic sequence number and the current payload type specified for the session.
	  * 
	  * @param buf A buffer of bytes, less than 1496 bytes
	  * @return	null if there was a problem, {RTP Timestamp, Sequence number} otherwise
	  */
	 public long[] sendData(byte[] buf) {
		 byte[][] tmp = {buf}; 
		 long[][] ret = this.sendData(tmp, null, null, -1, null);
		 
		 if(ret != null)
			 return ret[0];
		 
		 return null;
	 }
	 
	 /**
	  * Send data to all participants registered as receivers, using the specified timeStamp,
	  * sequence number and the current payload type specified for the session.
	  * 
	  * @param buf A buffer of bytes, less than 1496 bytes
	  * @param rtpTimestamp the RTP timestamp to be used in the packet
	  * @param seqNum the sequence number to be used in the packet
	  * @return null if there was a problem, {RTP Timestamp, Sequence number} otherwise
	  */
	 public long[] sendData(byte[] buf, long rtpTimestamp, long seqNum) {
		 byte[][] tmp = {buf};
		 long[][] ret = this.sendData(tmp, null, null, -1, null);
		 
		 if(ret != null)
			 return ret[0];
		 
		 return null;
	 }
	 
	 /**
	  * Send data to all participants registered as receivers, using the current timeStamp and
	  * payload type. The RTP timestamp will be the same for all the packets.
	  * 
	  * @param buffers A buffer of bytes, should not bed padded and less than 1500 bytes on most networks.
	  * @param csrcArray an array with the SSRCs of contributing sources
	  * @param markers An array indicating what packets should be marked. Rarely anything but the first one
	  * @param rtpTimestamp The RTP timestamp to be applied to all packets
	  * @param seqNumbers An array with the sequence number associated with each byte[]
	  * @return	null if there was a problem sending the packets, 2-dim array with {RTP Timestamp, Sequence number}
	  */
	 public long[][] sendData(byte[][] buffers, long[] csrcArray, boolean[] markers, long rtpTimestamp, long[] seqNumbers) {
		 if(RTPSession.rtpDebugLevel > 5) {
			 System.out.println("-> RTPSession.sendData(byte[])");
		 }

		 // Same RTP timestamp for all
		 if(rtpTimestamp < 0)
			 rtpTimestamp = System.currentTimeMillis();
		 
		 // Return values
		 long[][] ret = new long[buffers.length][2];

		 for(int i=0; i<buffers.length; i++) {
			 byte[] buf = buffers[i];
			 
			 boolean marker = false;
			 if(markers != null)
				  marker = markers[i];
			 
			 if(buf.length > 1500) {
				 System.out.println("RTPSession.sendData() called with buffer exceeding 1500 bytes ("+buf.length+")");
			 }

			 // Get the return values
			 ret[i][0] = rtpTimestamp;
			 if(seqNumbers == null) {
				 ret[i][1] = getNextSeqNum();
			 } else {
				 ret[i][1] = seqNumbers[i];
			 }
			 // Create a new RTP Packet
			 RtpPkt pkt = new RtpPkt(rtpTimestamp,this.ssrc,(int) ret[i][1],this.payloadType,buf);

			 if(csrcArray != null)
				 pkt.setCsrcs(csrcArray);

			 pkt.setMarked(marker);

			 // Creates a raw packet
			 byte[] pktBytes = pkt.encode();
			 
			 //System.out.println(Integer.toString(StaticProcs.bytesToUIntInt(pktBytes, 2)));

			 // Pre-flight check, are resolving an SSRC conflict?
			 if(this.conflict) {
				 System.out.println("RTPSession.sendData() called while trying to resolve conflict.");
				 return null;
			 }


			 if(this.mcSession) {
				 DatagramPacket packet = null;


				 try {
					 packet = new DatagramPacket(pktBytes,pktBytes.length,this.mcGroup,this.rtpMCSock.getPort());
				 } catch (Exception e) {
					 System.out.println("RTPSession.sendData() packet creation failed.");
					 e.printStackTrace();
					 return null;
				 }

				 try {
					 rtpMCSock.send(packet);
					 //Debug
					 if(this.debugAppIntf != null) {
						 this.debugAppIntf.packetSent(1, (InetSocketAddress) packet.getSocketAddress(), 
								 new String("Sent multicast RTP packet of size " + packet.getLength() + 
										 " to " + packet.getSocketAddress().toString() + " via " 
										 + rtpMCSock.getLocalSocketAddress().toString()));
					 }
				 } catch (Exception e) {
					 System.out.println("RTPSession.sendData() multicast failed.");
					 e.printStackTrace();
					 return null;
				 }		

			 } else {
				 // Loop over recipients
				 Iterator<Participant> iter = partDb.getUnicastReceivers();
				 while(iter.hasNext()) {			
					 InetSocketAddress receiver = iter.next().rtpAddress;
					 DatagramPacket packet = null;

					 if(RTPSession.rtpDebugLevel > 15) {
						 System.out.println("   Sending to " + receiver.toString());
					 }

					 try {
						 packet = new DatagramPacket(pktBytes,pktBytes.length,receiver);
					 } catch (Exception e) {
						 System.out.println("RTPSession.sendData() packet creation failed.");
						 e.printStackTrace();
						 return null;
					 }

					 //Actually send the packet
					 try {
						 rtpSock.send(packet);
						 //Debug
						 if(this.debugAppIntf != null) {
							 this.debugAppIntf.packetSent(0, (InetSocketAddress) packet.getSocketAddress(), 
									 new String("Sent unicast RTP packet of size " + packet.getLength() + 
											 " to " + packet.getSocketAddress().toString() + " via " 
											 + rtpSock.getLocalSocketAddress().toString()));
						 }
					 } catch (Exception e) {
						 System.out.println("RTPSession.sendData() unicast failed.");
						 e.printStackTrace();
						 return null;
					 }
				 }
			 }

			 //Update our stats
			 this.sentPktCount++;
			 this.sentOctetCount++;

			 if(RTPSession.rtpDebugLevel > 5) {
				 System.out.println("<- RTPSession.sendData(byte[]) " + pkt.getSeqNumber());
			 }  
		 }

		 return ret;
	 }
	 
	 /**
	  * Send RTCP App packet to receiver specified by ssrc
	  * 
	  * 
	  * 
	  * Return values:
	  *  0 okay
	  * -1 no RTCP session established
	  * -2 name is not byte[4];
	  * -3 data is not byte[x], where x = 4*y for syme y
	  * -4 type is not a 5 bit unsigned integer
	  * 
	  * Note that a return value of 0 does not guarantee delivery.
	  * The participant must also exist in the participant database,
	  * otherwise the message will eventually be deleted.
	  * 
	  * @param ssrc of the participant you want to reach
	  * @param type the RTCP App packet subtype, default 0
	  * @param name the ASCII (in byte[4]) representation
	  * @param data the data itself 
	  * @return 0 if okay, negative value otherwise (see above)
	  */
	
	 public int sendRTCPAppPacket(long ssrc, int type, byte[] name, byte[] data) {
		 if(this.rtcpSession == null)
			 return -1;
		 
		 if(name.length != 4)
			 return -2;
		 
		 if(data.length % 4 != 0)
			 return -3;
		 
		 if(type > 63 || type < 0 )
			 return -4;
		
		RtcpPktAPP pkt = new RtcpPktAPP(ssrc, type, name, data);
		this.rtcpSession.addToAppQueue(ssrc, pkt);
		
		return 0;
	 }
	 /**
	  * Add a participant object to the participant database.
	  * 
	  * If packets have already been received from this user, we will try to update the automatically inserted participant with the information provided here.
	  *
	  * @param p A participant.
	  */
	public int addParticipant(Participant p) {
		//For now we make all participants added this way persistent
		p.unexpected = false;
		return this.partDb.addParticipant(0, p);
	}
	
	 /**
	  * Remove a participant from the database. All buffered packets will be destroyed.
	  *
	  * @param p A participant.
	  */
	 public void removeParticipant(Participant p) {
		partDb.removeParticipant(p);
	 }
	 
	 public Iterator<Participant> getUnicastReceivers() {
		 return partDb.getUnicastReceivers();
	 }
	 
	 public Enumeration<Participant> getParticipants() {
		 return partDb.getParticipants();
	 }
	 
	 /**
	  * End the RTP Session. This will halt all threads and send bye-messages to other participants.
	  * 
	  * RTCP related threads may require several seconds to wake up and terminate.
	  */
	public void endSession() {
		this.endSession = true;
		
		// No more RTP packets, please
		if(this.mcSession) {
			this.rtpMCSock.close();
		} else {
			this.rtpSock.close();
		}
		
		// Signal the thread that pushes data to application
		this.pktBufLock.lock();
		try { this.pktBufDataReady.signalAll(); } finally {
			this.pktBufLock.unlock();
		}
		// Interrupt what may be sleeping
		this.rtcpSession.senderThrd.interrupt();
		
		// Give things a chance to cool down.
		try { Thread.sleep(50); } catch (Exception e){ };
		
		this.appCallerThrd.interrupt();

		// Give things a chance to cool down.
		try { Thread.sleep(50); } catch (Exception e){ };
		
		if(this.rtcpSession != null) {		
			// No more RTP packets, please
			if(this.mcSession) {
				this.rtcpSession.rtcpMCSock.close();
			} else {
				this.rtcpSession.rtcpSock.close();
			}
		}
	}

	
	 /**
	  * Check whether this session is ending.
	  * 
	  * @return true if session and associated threads are terminating.
	  */
	boolean isEnding() {
		return this.endSession;
	}

	/**
	 * Overrides CNAME, used for outgoing RTCP packets.
	 * 
	 * @param cname a string, e.g. username@hostname. Must be unique for session.
	 */
	public void CNAME(String cname) {
		this.cname = cname;
	}
	
	/**
	 * Get the current CNAME, used for outgoing SDES packets
	 */
	public String CNAME() {
		return this.cname;
	}
	
	public long getSsrc() {
		return this.ssrc;
	}
	
	private void generateCNAME() {
		String hostname;
		
		if(this.mcSession) {
			hostname = this.rtpMCSock.getLocalAddress().getCanonicalHostName();
		} else {
			hostname = this.rtpSock.getLocalAddress().getCanonicalHostName();
		}
		
		//if(hostname.equals("0.0.0.0") && System.getenv("HOSTNAME") != null) {
		//	hostname = System.getenv("HOSTNAME");
		//}
		
		cname = System.getProperty("user.name") + "@" + hostname;
	}
	
	/**
	 * Change the RTP socket of the session. 
	 * Peers must be notified through SIP or other signalling protocol.
	 * Only valid if this is a unicast session to begin with.
	 * 
	 * @param newSock integer for new port number, check it is free first.
	 */
	public int updateRTPSock(DatagramSocket newSock) {
		if(!mcSession) {
			 rtpSock = newSock;
			 return 0;
		} else {
			System.out.println("Can't switch from multicast to unicast.");
			return -1;
		}
	}
	
	/**
	 * Change the RTCP socket of the session. 
	 * Peers must be notified through SIP or other signalling protocol.
	 * Only valid if this is a unicast session to begin with.
	 * 
	 * @param newSock the new unicast socket for RTP communication.
	 */
	public int updateRTCPSock(DatagramSocket newSock) {
		if(!mcSession) {
			this.rtcpSession.rtcpSock = newSock;
			return 0;
		} else {
			System.out.println("Can't switch from multicast to unicast.");
			return -1;
		}
	}
	
	/**
	 * Change the RTP multicast socket of the session. 
	 * Peers must be notified through SIP or other signalling protocol.
	 * Only valid if this is a multicast session to begin with.
	 * 
	 * @param newSock the new multicast socket for RTP communication.
	 */
	public int updateRTPSock(MulticastSocket newSock) {
		if(mcSession) {
			 this.rtpMCSock = newSock;
			 return 0;
		} else {
			System.out.println("Can't switch from unicast to multicast.");
			return -1;
		}
	}
	
	/**
	 * Change the RTCP multicast socket of the session. 
	 * Peers must be notified through SIP or other signalling protocol.
	 * Only valid if this is a multicast session to begin with.
	 * 
	 * @param newSock the new multicast socket for RTCP communication.
	 */
	public int updateRTCPSock(MulticastSocket newSock) {
		if(mcSession) {
			this.rtcpSession.rtcpMCSock = newSock;
			return 0;
		} else {
			System.out.println("Can't switch from unicast to multicast.");
			return -1;
		}
	}
	
	/**
	 * Update the payload type used for the session. It is represented as a 7 bit integer, whose meaning must be negotiated elsewhere (see IETF RFCs <a href="http://www.ietf.org/rfc/rfc3550.txt">3550</a> and <a href="http://www.ietf.org/rfc/rfc3550.txt">3551</a>)
	 * 
	 * @param payloadT an integer representing the payload type of any subsequent packets that are sent.
	 */
	public int payloadType(int payloadT) {
		if(payloadT > 128 || payloadT < 0) {
			return -1;
		} else {
			this.payloadType = payloadT;
			return this.payloadType;
		}
	}
	
	/**
	 * Get the payload type that is currently used for outgoing RTP packets.
	 * 
	 * @return payload type as integer
	 */
	public int payloadType() {
		return this.payloadType;
	}
	
	/**
	 * Should packets from unknown participants be returned to the application? This can be dangerous.
	 * 
	 * @param doAccept packets from participants not added by the application.
	 */
	public void naivePktReception(boolean doAccept) {
		naiveReception = doAccept;
	}
	
	/**
	 * Are packets from unknown participants returned to the application?
	 * 
	 * @return whether we accept packets from participants not added by the application.
	 */
	public boolean naivePktReception() {
		return naiveReception;
	}
	
	/**
	 * Set the number of RTP packets that should be buffered when a packet is
	 * missing or received out of order. Setting this number high increases
	 * the chance of correctly reordering packets, but increases latency when
	 * a packet is dropped by the network.
	 * 
	 * Packets that arrive in order are not affected, they are passed straight
	 * to the application.
	 * 
	 * The maximum delay is numberofPackets * packet rate , where the packet rate
	 * depends on the codec and profile used by the sender.
	 * 
	 * Valid values:
	 *  >0 - The maximum number of packets (based on RTP Timestamp) that may accumulate
	 *  0 - All valid packets received in order will be given to the application
	 * -1 - All valid packets will be given to the application
	 * 
	 * @param behavior the be
	 * @return the behavior set, unchanged in the case of a erroneous value
	 */
	public int packetBufferBehavior(int behavior) {
		if(behavior > -2) {
			this.pktBufBehavior = behavior; 
			// Signal the thread that pushes data to application
			this.pktBufLock.lock();
			try { this.pktBufDataReady.signalAll(); } finally {
				this.pktBufLock.unlock();
			}
			return this.pktBufBehavior;
		} else {
			return this.pktBufBehavior;
		}
	}
	
	/**
	 * The number of RTP packets that should be buffered when a packet is
	 * missing or received out of order. A high number  increases the chance 
	 * of correctly reordering packets, but increases latency when a packet is 
	 * dropped by the network.
	 * 
	 * A negative value disables the buffering, out of order packets will simply be dropped.
	 * 
	 * @return the maximum number of packets that can accumulate before the first is returned
	 */
	public int packetBufferBehavior() {
		return this.pktBufBehavior;
	}
	
	/**
	 * Set whether the stack should operate in RFC 4585 mode.
	 * 
	 * This will automatically call adjustPacketBufferBehavior(-1),
	 * i.e. disable all RTP packet buffering in jlibrtp,
	 * and disable frame reconstruction 
	 * 
	 * @param rtcpAVPFIntf the in
	 */
	public int registerAVPFIntf(RTCPAVPFIntf rtcpAVPFIntf, int maxDelay, int earlyThreshold, int regularThreshold ) {
		if(this.rtcpSession != null) {
			this.packetBufferBehavior(-1);
			this.frameReconstruction = false;
			this.rtcpAVPFIntf = rtcpAVPFIntf;
			this.fbEarlyThreshold = earlyThreshold;
			this.fbRegularThreshold = regularThreshold;	
			return 0;
		} else {
			return -1;
		}
	}
	
	/**
	 * Unregisters the RTCP AVPF interface, thereby going from
	 * RFC 4585 mode to RFC 3550
	 * 
	 * You still have to adjust packetBufferBehavior() and
	 * frameReconstruction.
	 * 	
	 */
	public void unregisterAVPFIntf() {
		this.fbEarlyThreshold = -1;
		this.fbRegularThreshold = -1;	
		this.rtcpAVPFIntf = null;
	}
	
	/**
	 * Enable / disable frame reconstruction in the packet buffers.
	 * This is only relevant if getPacketBufferBehavior > 0;
	 * 
	 * Default is true.
	 */
	public void frameReconstruction(boolean toggle) {
		this.frameReconstruction = toggle;
	}
	
	/**
	 * Whether the packet buffer will attempt to reconstruct
	 * packet automatically.  
	 * 
	 * @return the status
	 */
	public boolean frameReconstruction() {
		return this.frameReconstruction;
	}
	
	/**
	 * The bandwidth currently allocated to the session,
	 * in bytes per second. The default is 8000.
	 * 
	 * This value is not enforced and currently only
	 * used to calculate the RTCP interval to ensure the
	 * control messages do not exceed 5% of the total bandwidth
	 * described here.
	 * 
	 * Since the actual value may change a conservative
	 * estimate should be used to avoid RTCP flooding.
	 * 
	 * see rtcpBandwidth(void)
	 * 
	 * @return current bandwidth setting
	 */
	public int sessionBandwidth() {
		return this.bandwidth;
	}
	
	/**
	 * Set the bandwidth of the session.
	 * 
	 * See sessionBandwidth(void) for details. 
	 * 
	 * @param bandwidth the new value requested, in bytes per second
	 * @return the actual value set
 	 */
	public int sessionBandwidth(int bandwidth) {
		if(bandwidth < 1) {
			this.bandwidth = 8000;
		} else {
			this.bandwidth = bandwidth;
		}
		return this.bandwidth;
	}
	
	
	/**
	 * RFC 3550 dictates that 5% of the total bandwidth,
	 * as set by sessionBandwidth, should be dedicated
	 * to RTCP traffic. This 
	 * 
	 * This should normally not be done, but is permissible in 
	 * conjunction with feedback (RFC 4585) and possibly
	 * other profiles. 
	 * 
	 * Also see sessionBandwidth(void)
	 * 
	 * @return current RTCP bandwidth setting, -1 means not in use
	 */
	public int rtcpBandwidth() {
		return this.rtcpBandwidth;
	}
	
	/**
	 * Set the RTCP bandwidth, see rtcpBandwidth(void) for details. 
	 * 
	 * This function must be
	 * 
	 * @param bandwidth the new value requested, in bytes per second or -1 to disable
	 * @return the actual value set
 	 */
	public int rtcpBandwidth(int bandwidth) {
		if(bandwidth < -1) {
			this.rtcpBandwidth = -1;
		} else {
			this.rtcpBandwidth = bandwidth;
		}
		return this.rtcpBandwidth;
	}
	
	/********************************************* Feedback message stuff ***************************************/
	
	/**
	 * Adds a Picture Loss Indication to the feedback queue
	 * 
	 * @param ssrcMediaSource
	 * @return 0 if packet was queued, -1 if no feedback support, 1 if redundant
	 */
	public int fbPictureLossIndication(long ssrcMediaSource) {
		int ret = 0;
		
		if(this.rtcpAVPFIntf == null)
			return -1;
		
		RtcpPktPSFB pkt = new RtcpPktPSFB(this.ssrc, ssrcMediaSource);
		pkt.makePictureLossIndication();
		ret = this.rtcpSession.addToFbQueue(ssrcMediaSource, pkt);
		if(ret == 0)
			this.rtcpSession.wakeSenderThread(ssrcMediaSource);
		return ret; 
	}
	
	/**
	 * Adds a Slice Loss Indication to the feedback queue
	 * 
	 * @param ssrcMediaSource
	 * @param sliFirst macroblock (MB) address of the first lost macroblock
	 * @param sliNumber number of lost macroblocks
	 * @param sliPictureId six least significant bits of the codec-specific identif
	 * @return 0 if packet was queued, -1 if no feedback support, 1 if redundant
	 */
	public int fbSlicLossIndication(long ssrcMediaSource, int[] sliFirst, int[] sliNumber, int[] sliPictureId) {
		int ret = 0;
		if(this.rtcpAVPFIntf == null)
			return -1;
		
		RtcpPktPSFB pkt = new RtcpPktPSFB(this.ssrc, ssrcMediaSource);
		pkt.makeSliceLossIndication(sliFirst, sliNumber, sliPictureId);
		
		ret = this.rtcpSession.addToFbQueue(ssrcMediaSource, pkt);
		if(ret == 0)
			this.rtcpSession.wakeSenderThread(ssrcMediaSource);
		return ret; 
	}
	
	/**
	 * Adds a Reference Picture Selection Indication to the feedback queue
	 * 
	 * @param ssrcMediaSource
	 * @param bitPadding number of padded bits at end of bitString
	 * @param payloadType RTP payload type for codec
	 * @param bitString RPSI information as natively defined by the video codec
	 * @return 0 if packet was queued, -1 if no feedback support, 1 if redundant
	 */
	public int fbRefPictureSelIndic(long ssrcMediaSource, int bitPadding, int payloadType, byte[] bitString) {
		int ret = 0;
		
		if(this.rtcpAVPFIntf == null)
			return -1;
		
		RtcpPktPSFB pkt = new RtcpPktPSFB(this.ssrc, ssrcMediaSource);
		pkt.makeRefPictureSelIndic(bitPadding, payloadType, bitString);
		ret = this.rtcpSession.addToFbQueue(ssrcMediaSource, pkt);
		if(ret == 0)
			this.rtcpSession.wakeSenderThread(ssrcMediaSource);
		return ret; 
	}
	
	/**
	 * Adds a Picture Loss Indication to the feedback queue
	 * 
	 * @param ssrcMediaSource
	 * @param bitString the original application message
	 * @return 0 if packet was queued, -1 if no feedback support, 1 if redundant
	 */
	public int fbAppLayerFeedback(long ssrcMediaSource, byte[] bitString) {
		int ret = 0;
		
		if(this.rtcpAVPFIntf == null)
			return -1;
		
		RtcpPktPSFB pkt = new RtcpPktPSFB(this.ssrc, ssrcMediaSource);
		pkt.makeAppLayerFeedback(bitString);
		ret = this.rtcpSession.addToFbQueue(ssrcMediaSource, pkt);
		if(ret == 0)
			this.rtcpSession.wakeSenderThread(ssrcMediaSource);
		return ret; 
	}
	
	
	/**
	 * Adds a RTP Feedback packet to the feedback queue.
	 * 
	 * These are mostly used for NACKs.
	 * 
	 * @param ssrcMediaSource
	 * @param FMT the Feedback Message Subtype
	 * @param PID RTP sequence numbers of lost packets
	 * @param BLP bitmask of following lost packets, shared index with PID 
	 * @return 0 if packet was queued, -1 if no feedback support, 1 if redundant
	 */
	public int fbPictureLossIndication(long ssrcMediaSource, int FMT, int[] PID, int[] BLP) {
		int ret = 0;
		
		if(this.rtcpAVPFIntf == null)
			return -1;
		
		RtcpPktRTPFB pkt = new RtcpPktRTPFB(this.ssrc, ssrcMediaSource, FMT, PID, BLP);
		ret = this.rtcpSession.addToFbQueue(ssrcMediaSource, pkt);
		if(ret == 0)
			this.rtcpSession.wakeSenderThread(ssrcMediaSource);
		return ret; 
	}
		
	/**
	 * Fetches the next sequence number for RTP packets.
	 * @return the next sequence number 
	 */
	private int getNextSeqNum() {
		seqNum++;
		// 16 bit number
		if(seqNum > 65536) { 
			seqNum = 0;
		}
		return seqNum;
	}
	
	/** 
	 * Initializes a random variable
	 *
	 */
	private void createRandom() {
		this.random = new Random(System.currentTimeMillis() + Thread.currentThread().getId() 
				- Thread.currentThread().hashCode() + this.cname.hashCode());
	}
	
	
	/** 
	 * Generates a random sequence number
	 */
	private void generateSeqNum() {
		if(this.random == null)
			createRandom();
		
		seqNum = this.random.nextInt();
		if(seqNum < 0)
			seqNum = -seqNum;
		while(seqNum > 65535) {
			seqNum = seqNum / 10;
		}
	}
	
	/**
	 * Generates a random SSRC
	 */
	private void generateSsrc() {
		if(this.random == null)
			createRandom();
		
		// Set an SSRC
		this.ssrc = this.random.nextInt();
		if(this.ssrc < 0) {
			this.ssrc = this.ssrc * -1;
		}	
	}
	
	/**
	 * Resolve an SSRC conflict.
	 * 
	 * Also increments the SSRC conflict counter, after 5 conflicts
	 * it is assumed there is a loop somewhere and the session will
	 * terminate. 
	 *
	 */
	protected void resolveSsrcConflict() {
		System.out.println("!!!!!!! Beginning SSRC conflict resolution !!!!!!!!!");
		this.conflictCount++;
		
		if(this.conflictCount < 5) {
			//Don't send any more regular packets out until we have this sorted out.
			this.conflict = true;
		
			//Send byes
			rtcpSession.sendByes();
		
			//Calculate the next delay
			rtcpSession.calculateDelay();
			
			//Generate a new Ssrc for ourselves
			generateSsrc();
			
			//Get the SDES packets out faster
			rtcpSession.initial = true;
			
			this.conflict = false;
			System.out.println("SSRC conflict resolution complete");
			
		} else {
			System.out.println("Too many conflicts. There is probably a loop in the network.");
			this.endSession();
		}
	}
}
