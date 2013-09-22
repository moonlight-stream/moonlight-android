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

import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.*;

/**
 * This thread sends scheduled RTCP packets 
 * 
 * It also performs maintenance of various queues and the participant
 * database.
 * 
 * @author Arne Kepp
 *
 */
public class RTCPSenderThread extends Thread {
	/** Parent RTP Session */
	private RTPSession rtpSession = null;
	/** Parent RTCP Session */
	private RTCPSession rtcpSession = null;
	
	/** Whether we have sent byes for the last conflict */
	private boolean byesSent = false;
	
	/**
	 * Constructor for new thread
	 * @param rtcpSession parent RTCP session
	 * @param rtpSession parent RTP session
	 */
	protected RTCPSenderThread(RTCPSession rtcpSession, RTPSession rtpSession) {
		this.rtpSession = rtpSession;
		this.rtcpSession = rtcpSession;
		if(RTPSession.rtpDebugLevel > 1) {
			System.out.println("<-> RTCPSenderThread created");
		} 
	}
	
	/**
	 * Send BYE messages to all the relevant participants
	 *
	 */
	protected void sendByes() {
		// Create the packet
		CompRtcpPkt compPkt = new CompRtcpPkt();
		
		//Need a SR for validation
		RtcpPktSR srPkt = new RtcpPktSR(this.rtpSession.ssrc, 
				this.rtpSession.sentPktCount, this.rtpSession.sentOctetCount, null);
		compPkt.addPacket(srPkt);
		
		byte[] reasonBytes;
		
		//Add the actualy BYE Pkt
		long[] ssrcArray = {this.rtpSession.ssrc};
		if(rtpSession.conflict) {
			reasonBytes = "SSRC collision".getBytes();
		} else {
			reasonBytes = "jlibrtp says bye bye!".getBytes();
		}
		RtcpPktBYE byePkt = new RtcpPktBYE( ssrcArray, reasonBytes);
		
		compPkt.addPacket(byePkt);
		
		// Send it off
		if(rtpSession.mcSession) {
			mcSendCompRtcpPkt(compPkt);
		} else {
			Iterator<Participant> iter = rtpSession.partDb.getUnicastReceivers();
		
			while(iter.hasNext()) {
				Participant part = (Participant) iter.next();
				if(part.rtcpAddress != null)
					sendCompRtcpPkt(compPkt, part.rtcpAddress);
			}
			//System.out.println("SENT BYE PACKETS!!!!!");
		}
	}
	
	/**
	 * Multicast version of sending a Compound RTCP packet
	 * 
	 * @param pkt the packet to best
	 * @return 0 is successful, -1 otherwise
	 */
	protected int mcSendCompRtcpPkt(CompRtcpPkt pkt) {
		byte[] pktBytes = pkt.encode();
		DatagramPacket packet;
		
		// Create datagram
		try {
			packet = new DatagramPacket(pktBytes,pktBytes.length,rtpSession.mcGroup,rtcpSession.rtcpMCSock.getPort());
		} catch (Exception e) {
			System.out.println("RCTPSenderThread.MCSendCompRtcpPkt() packet creation failed.");
			e.printStackTrace();
			return -1;
		}
		
		// Send packet
		if(RTPSession.rtcpDebugLevel > 5) {
			System.out.println("<-> RTCPSenderThread.SendCompRtcpPkt() multicast");
		}
		try {
			rtcpSession.rtcpMCSock.send(packet);
			//Debug
			if(this.rtpSession.debugAppIntf != null) {
				this.rtpSession.debugAppIntf.packetSent(3, (InetSocketAddress) packet.getSocketAddress(), 
						new String("Sent multicast RTCP packet of size " + packet.getLength() + 
								" to " + packet.getSocketAddress().toString() + " via " 
								+ this.rtcpSession.rtcpMCSock.getLocalSocketAddress().toString()));
			}
		} catch (Exception e) {
			System.out.println("RCTPSenderThread.MCSendCompRtcpPkt() multicast failed.");
			e.printStackTrace();
			return -1;
		}
		return packet.getLength();
	}
	
	/**
	 * Unicast version of sending a Compound RTCP packet
	 * 
	 * @param pkt the packet to best
	 * @param receiver the socket address of the recipient
	 * @return 0 is successful, -1 otherwise
	 */
	protected int sendCompRtcpPkt(CompRtcpPkt pkt, InetSocketAddress receiver) {
		byte[] pktBytes = pkt.encode();
		DatagramPacket packet;
		
		//Create datagram
		try {
			//System.out.println("receiver: " + receiver);
			packet = new DatagramPacket(pktBytes,pktBytes.length,receiver);
		} catch (Exception e) {
			System.out.println("RCTPSenderThread.SendCompRtcpPkt() packet creation failed.");
			e.printStackTrace();
			return -1;
		}
		
		//Send packet
		if(RTPSession.rtcpDebugLevel > 5) {
			Iterator<RtcpPkt> iter = pkt.rtcpPkts.iterator();
			String str = " ";
			while(iter.hasNext()) {
				RtcpPkt aPkt = iter.next();
				str += (aPkt.getClass().toString() + ":"+aPkt.itemCount+ ", ");
			}
			System.out.println("<-> RTCPSenderThread.SendCompRtcpPkt() unicast to " + receiver + str);
		}
		try {
			rtcpSession.rtcpSock.send(packet);
			//Debug
			if(this.rtpSession.debugAppIntf != null) {
				this.rtpSession.debugAppIntf.packetSent(2, (InetSocketAddress) packet.getSocketAddress(), 
						new String("Sent unicast RTCP packet of size " + packet.getLength() + 
								" to " + packet.getSocketAddress().toString() + " via " 
								+ this.rtcpSession.rtcpSock.getLocalSocketAddress().toString()));
			}
		} catch (Exception e) {
			System.out.println("RTCPSenderThread.SendCompRtcpPkt() unicast failed.");
			e.printStackTrace();
			return -1;
		}
		return packet.getLength();
	}
	
	/**
	 * Check whether we can send an immediate feedback packet to this person
	 * @param ssrc SSRC of participant
	 */
	protected void reconsiderTiming(long ssrc) {
		Participant part =  this.rtpSession.partDb.getParticipant(ssrc);
		
		if( part != null && this.rtcpSession.fbSendImmediately()) {
			CompRtcpPkt compPkt = preparePacket(part, false);
			/*********** Send the packet ***********/
			// Keep track of sent packet length for average;
			int datagramLength;
			if(rtpSession.mcSession) {
				datagramLength = this.mcSendCompRtcpPkt(compPkt);
			} else {
				//part.debugPrint();
				datagramLength = this.sendCompRtcpPkt(compPkt, part.rtcpAddress);
			}
			/*********** Administrative tasks ***********/			
			//Update average packet size
			if(datagramLength > 0) {
				rtcpSession.updateAvgPacket(datagramLength);
			}
		} else if(part != null 
				&& this.rtcpSession.fbAllowEarly 
				&& this.rtcpSession.fbSendEarly()) {
			
			// Make sure we dont do it too often
			this.rtcpSession.fbAllowEarly = false;
			
			CompRtcpPkt compPkt = preparePacket(part, true);
			/*********** Send the packet ***********/
			// Keep track of sent packet length for average;
			int datagramLength;
			if(rtpSession.mcSession) {
				datagramLength = this.mcSendCompRtcpPkt(compPkt);
			} else {
				//part.debugPrint();
				datagramLength = this.sendCompRtcpPkt(compPkt, part.rtcpAddress);
			}
			/*********** Administrative tasks ***********/			
			//Update average packet size
			if(datagramLength > 0) {
				rtcpSession.updateAvgPacket(datagramLength);
			}
			rtcpSession.calculateDelay();
		}
		
		//Out of luck, fb message will have to go with next regular packet
		//Sleep for the remaining time.
		this.rtcpSession.nextDelay -= System.currentTimeMillis() - this.rtcpSession.prevTime;
		if(this.rtcpSession.nextDelay < 0)
			this.rtcpSession.nextDelay = 0;
		
	}
	
	/** 
	 * Prepare a packet. The output depends on the participant and how the
	 * packet is scheduled.
	 * 
	 * @param part the participant to report to
	 * @param regular whether this is a regularly, or early scheduled RTCP packet
	 * @return compound RTCP packet
	 */
	protected CompRtcpPkt preparePacket(Participant part, boolean regular) {
		/*********** Figure out what we are going to send ***********/
		// Check whether this person has sent RTP packets since the last RR.
		boolean incRR = false;
		if(part.secondLastRtcpRRPkt > part.lastRtcpRRPkt) {
			incRR = true;
			part.secondLastRtcpRRPkt = part.lastRtcpRRPkt;
			part.lastRtcpRRPkt = System.currentTimeMillis();
		}
		
		// Are we sending packets? -> add SR
		boolean incSR = false;
		if(rtpSession.sentPktCount > 0 && regular) {
			incSR = true;
		}
		
		
		/*********** Actually create the packet ***********/
		// Create compound packet
		CompRtcpPkt compPkt = new CompRtcpPkt();
		
		//If we're sending packets we'll use a SR for header
		if(incSR) {
			RtcpPktSR srPkt = new RtcpPktSR(this.rtpSession.ssrc, 
					this.rtpSession.sentPktCount, this.rtpSession.sentOctetCount, null);
			compPkt.addPacket(srPkt);
			
			
			if(part.ssrc > 0) {
				RtcpPkt[] ar = this.rtcpSession.getFromFbQueue(part.ssrc);
				if(ar != null) {
					for(int i=0; i<ar.length; i++) {
						compPkt.addPacket(ar[i]);
					}
				}
			}
			
		}
		
		//If we got anything from this participant since we sent the 2nd to last RtcpPkt
		if(incRR || !incSR) {
			Participant[] partArray = {part};
			
			if(part.receivedPkts < 1)
				partArray = null;
			
			RtcpPktRR rrPkt = new RtcpPktRR(partArray, rtpSession.ssrc);
			compPkt.addPacket(rrPkt);
			
			if( !incSR && part.ssrc > 0) {
				RtcpPkt[] ar = this.rtcpSession.getFromFbQueue(part.ssrc);
				if(ar != null) {
					for(int i=0; i<ar.length; i++) {
						compPkt.addPacket(ar[i]);
					}
				}
			}
		}
		
		// APP packets
		if(regular && part.ssrc > 0) {
			RtcpPkt[] ar = this.rtcpSession.getFromAppQueue(part.ssrc);
			if(ar != null) {
				for(int i=0; i<ar.length; i++) {
					compPkt.addPacket(ar[i]);
				}
			} else {
				//Nope
			}
		}
		
		
		// For now we'll stick the SDES on every time, and only for us
		//if(regular) {
			RtcpPktSDES sdesPkt = new RtcpPktSDES(true, this.rtpSession, null);
			compPkt.addPacket(sdesPkt);
		//}
		
		return compPkt;
	}
	
	/**
	 * Start the RTCP sender thread.
	 * 
	 * RFC 4585 is more complicated, but in general it will
	 * 1) Wait a precalculated amount of time
	 * 2) Determine the next RTCP recipient
	 * 3) Construct a compound packet with all the relevant information
	 * 4) Send the packet
	 * 5) Calculate next delay before going to sleep
	 */
	public void run() {
		if(RTPSession.rtcpDebugLevel > 1) {
			System.out.println("<-> RTCPSenderThread running");
		}
		
		// Give the application a chance to register some participants
		try { Thread.sleep(10); } 
		catch (Exception e) { System.out.println("RTCPSenderThread didn't get any initial rest."); }
		
		// Set up an iterator for the member list
		Enumeration<Participant> enu = null;
		Iterator<Participant> iter = null;
		
		// TODO Change to rtcpReceivers
		if(rtpSession.mcSession) {
			enu = rtpSession.partDb.getParticipants();
		} else {
			iter = rtpSession.partDb.getUnicastReceivers();
		}
		while(! rtpSession.endSession) {
			if(RTPSession.rtcpDebugLevel > 5) {
				System.out.println("<-> RTCPSenderThread sleeping for " +rtcpSession.nextDelay+" ms");
			}
			
			try { Thread.sleep(rtcpSession.nextDelay); } 
			catch (Exception e) { 
				System.out.println("RTCPSenderThread Exception message:" + e.getMessage());
				// Is the party over?
				if(this.rtpSession.endSession) {
					continue;
				}
				
				if(rtcpSession.fbWaiting != -1) {
					reconsiderTiming(rtcpSession.fbWaiting);
					continue;
				}
			}
			
			/** Came here the regular way */
			this.rtcpSession.fbAllowEarly = true;
			
				
			if(RTPSession.rtcpDebugLevel > 5) {
				System.out.println("<-> RTCPSenderThread waking up");
			}
			
			// Regenerate nextDelay, before anything happens.
			rtcpSession.calculateDelay();
			
			// We'll wait here until a conflict (if any) has been resolved,
			// so that the bye packets for our current SSRC can be sent.
			if(rtpSession.conflict) {
				if(! this.byesSent) {
					sendByes();
					this.byesSent = true;
				}
				continue;
			}
			this.byesSent = false;
						
			//Grab the next person
			Participant part = null;

			//Multicast
			if(this.rtpSession.mcSession) {
				if(! enu.hasMoreElements())
					enu = rtpSession.partDb.getParticipants();
				
				if( enu.hasMoreElements() ) {
					part = enu.nextElement();
				} else {
					continue;
				}
				
			//Unicast
			} else {
				if(! iter.hasNext()) {
					iter = rtpSession.partDb.getUnicastReceivers();
				}
				
				if(iter.hasNext() ) {
					while( iter.hasNext() && (part == null || part.rtcpAddress == null)) {
						part = iter.next();
					}
				}
				
				if(part == null || part.rtcpAddress == null)
					continue;
			}
			
			CompRtcpPkt compPkt = preparePacket(part, true);
			
			/*********** Send the packet ***********/
			// Keep track of sent packet length for average;
			int datagramLength;
			if(rtpSession.mcSession) {
				datagramLength = this.mcSendCompRtcpPkt(compPkt);
			} else {
				//part.debugPrint();
				datagramLength = this.sendCompRtcpPkt(compPkt, part.rtcpAddress);
			}
			
			/*********** Administrative tasks ***********/			
			//Update average packet size
			if(datagramLength > 0) {
				rtcpSession.updateAvgPacket(datagramLength);
			}
		}

		// Be polite, say Bye to everone
		sendByes();
		try { Thread.sleep(200);} catch(Exception e) {}
		
		if(RTPSession.rtcpDebugLevel > 0) {
			System.out.println("<-> RTCPSenderThread terminating");
		}
	}
}
