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

import java.util.Enumeration;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.LinkedList;
import java.util.Hashtable;
import java.util.ListIterator;
import java.util.Arrays;


/**
 * This class acts as an organizer for most of the information
 * and functions pertaining to RTCP packet generation and reception 
 * 
 * @author Arne Kepp
 *
 */
public class RTCPSession {
	/** Parent session */
	protected RTPSession rtpSession = null;
	
	/** Unicast socket */
	protected DatagramSocket rtcpSock = null;
	/** Multicast socket */
	protected MulticastSocket rtcpMCSock = null;
	/** Multicast group */
	protected InetAddress mcGroup = null;

	/** RTCP Receiver thread */
	protected RTCPReceiverThread recvThrd = null;
	/** RTCP Sender thread */
	protected RTCPSenderThread senderThrd = null;
	
	/** Previous time a delay was calculated */
	protected long prevTime = System.currentTimeMillis();
	/** Delay between RTCP transmissions, in ms. Initialized in start() */
	protected int nextDelay = -1; //
	/** The average compound RTCP packet size, in octets, including UDP and IP headers */
	protected int avgPktSize = 200; //
	/** Pessimistic case estimate of the current number of senders */
	protected int senderCount = 1;
	/** Whether next RTCP packet can be sent early */
	protected boolean fbAllowEarly = false;
	/** Feedback queue , index is SSRC of target */
	protected Hashtable<Long, LinkedList<RtcpPkt>> fbQueue = null;
	/** APP queue , index is SSRC of target */
	protected Hashtable<Long, LinkedList<RtcpPktAPP>> appQueue = null;
	/** Are we just starting up? */
	protected boolean initial = true;
	/** Is there a feedback packet waiting? SSRC of destination */
	protected long fbWaiting = -1;

	/**
	 * Constructor for unicast sessions
	 * 
	 * @param parent RTPSession that started this
	 * @param rtcpSocket the socket to use for listening and sending
	 */
	protected RTCPSession(RTPSession parent, DatagramSocket rtcpSocket) {
		this.rtcpSock = rtcpSocket;
		rtpSession = parent;
	}

	/**
	 * Constructor for multicast sessions
	 * 
	 * @param parent parent RTPSession
	 * @param rtcpSocket parent RTPSession that started this
	 * @param multicastGroup multicast group to bind the socket to
	 */
	protected RTCPSession(RTPSession parent, MulticastSocket rtcpSocket, InetAddress multicastGroup) {
		mcGroup = multicastGroup;
		this.rtcpSock = rtcpSocket;
		rtpSession = parent;
	}

	/**
	 * Starts the session, calculates delays and fires up the threads.
	 *
	 */
	protected void start() {
		//nextDelay = 2500 + rtpSession.random.nextInt(1000) - 500;
		this.calculateDelay();
		recvThrd = new RTCPReceiverThread(this, this.rtpSession);
		senderThrd = new RTCPSenderThread(this, this.rtpSession);
		recvThrd.start();
		senderThrd.start();
	}

	/**
	 * Send bye packets, handled by RTCP Sender thread
	 *
	 */
	protected void sendByes() {
		senderThrd.sendByes();
	}
	
	/**
	 * Calculate the delay before the next RTCP packet can be sent
	 *
	 */
	protected void calculateDelay() {
		switch(rtpSession.rtcpMode) {
		case 0: calculateRegularDelay(); break;
		default:
			System.out.println("RTCPSession.calculateDelay() unknown .mode");
		}
	}

	/**
	 * Calculates a delay value in accordance with RFC 3550
	 *
	 */
	protected void calculateRegularDelay() {
		long curTime = System.currentTimeMillis();
		
		if(rtpSession.bandwidth != 0 && ! this.initial && rtpSession.partDb.ssrcTable.size() > 4) {
			// RTPs mechanisms for RTCP scalability
			int rand = rtpSession.random.nextInt(10000) - 5000; //between -500 and +500
			double randDouble =  ((double) 1000 + rand)/1000.0;
			
			
			Enumeration<Participant> enu = rtpSession.partDb.getParticipants();
			while(enu.hasMoreElements()) {
				Participant part = enu.nextElement();
				if(part.lastRtpPkt > this.prevTime)
					senderCount++;
			}
			
			double bw;
			if(rtpSession.rtcpBandwidth > -1) {
				bw = rtpSession.rtcpBandwidth;
			}else {
				bw = rtpSession.bandwidth*0.05;
			}
			if(senderCount*2 > rtpSession.partDb.ssrcTable.size()) {
				if(rtpSession.lastTimestamp > this.prevTime) {
					//We're a sender
					double numerator = ((double) this.avgPktSize)*((double) senderCount);
					double denominator = 0.25*bw;
					this.nextDelay = (int) Math.round((numerator/denominator)*randDouble);
				} else {
					//We're a receiver
					double numerator = ((double) this.avgPktSize)*((double) rtpSession.partDb.ssrcTable.size());
					double denominator = 0.75*bw;
					this.nextDelay = (int) Math.round((numerator/denominator)*randDouble);
				}
			} else {
				double numerator = ((double) this.avgPktSize)*((double) rtpSession.partDb.ssrcTable.size());;
				double denominator = bw;
				this.nextDelay = (int) Math.round(1000.0*(numerator/denominator)) * (1000 + rand);
			}
		} else {
			// Not enough data to scale, use random values
			int rand = rtpSession.random.nextInt(1000) - 500; //between -500 and +500
			if(this.initial) {
				// 2.5 to 3.5 seconds, randomly
				this.nextDelay = 3000 + rand;
				this.initial = false;
			} else {
				// 4.5 to 5.5 seconds, randomly
				this.nextDelay = 5500 + rand;
			}

		}
		
		// preflight check
		if(this.nextDelay < 1000) {
			int rand = rtpSession.random.nextInt(1000) - 500; //between -500 and +500
			System.out.println("RTCPSession.calculateDelay() nextDelay was too short (" 
					+this.nextDelay+"ms), setting to "+(this.nextDelay = 2000 + rand));
		}
		this.prevTime = curTime;
	}

	/**
	 * Update the average packet size
	 * @param length of latest packet
	 */
	synchronized protected void updateAvgPacket(int length) {
		double tempAvg = (double) this.avgPktSize;
		tempAvg = (15*tempAvg + ((double) length))/16;
		this.avgPktSize = (int) tempAvg;
	}
	
	
	/**
	 * Adds an RTCP APP (application) packet to the queue
	 *
	 * @param targetSsrc the SSRC of the recipient
	 * @param aPkt 
	 */
	synchronized protected void addToAppQueue(long targetSsrc, RtcpPktAPP aPkt) {
		aPkt.time = System.currentTimeMillis();
		
		if(this.appQueue == null)
			this.appQueue = new Hashtable<Long, LinkedList<RtcpPktAPP>>();
		
		LinkedList<RtcpPktAPP> ll = this.appQueue.get(targetSsrc);
		if(ll == null) {
			// No list, create and add
			ll = new LinkedList<RtcpPktAPP>();
			this.appQueue.put(targetSsrc, ll);
		}
		
		ll.add(aPkt);
	}
	
	
	/**
	 * Adds an RTCP APP (application) packet to the queue
	 *
	 * @param targetSsrc the SSRC of the recipient
	 * @return array of RTCP Application packets 
	 */
	synchronized protected RtcpPktAPP[] getFromAppQueue(long targetSsrc) {		
		if(this.appQueue == null)
			return null;
		
		LinkedList<RtcpPktAPP> ll = this.appQueue.get(targetSsrc);
		if(ll == null || ll.isEmpty()) {
			return null;
		} else {
			RtcpPktAPP[] ret = new RtcpPktAPP[ll.size()];
			ListIterator<RtcpPktAPP> li = ll.listIterator();
			int i = 0;
			while(li.hasNext()) {
				ret[i] = li.next();
				i++;
			}
			return ret;
		}
	}
	
	
	/**
	 * Cleans the TCP APP (application) packet queues of any packets that are
	 * too old, defined as 60 seconds since insertion.
	 * 
	 * @param ssrc The SSRC of the user who has left, negative value -> general cleanup
	 */
	synchronized protected void cleanAppQueue(long ssrc) {
		if(this.appQueue == null)
			return;
		
		if(ssrc > 0) {
			this.appQueue.remove(ssrc);
		} else {
			Enumeration<LinkedList<RtcpPktAPP>> enu = this.appQueue.elements();
			long curTime = System.currentTimeMillis();


			while(enu.hasMoreElements()) {
				ListIterator<RtcpPktAPP> li = enu.nextElement().listIterator();
				while(li.hasNext()) {
					RtcpPkt aPkt = li.next();
					//Remove after 60 seconds
					if(curTime - aPkt.time > 60000) {
						li.remove();
					}
				}
			}	
		}
	}
	
	
	
	/**
	 * Check the feedback queue for similar packets and adds
	 * the new packet if it is not redundant
	 * 
	 * @param aPkt
	 * @return 0 if the packet was added, 1 if it was dropped
	 */
	synchronized protected int addToFbQueue(long targetSsrc, RtcpPkt aPkt) {
		if(this.fbQueue == null)
			this.fbQueue = new Hashtable<Long, LinkedList<RtcpPkt>>();
		
		LinkedList<RtcpPkt> ll = this.fbQueue.get(targetSsrc);
		if(ll == null) {
			// No list, create and add
			ll = new LinkedList<RtcpPkt>();
			ll.add(aPkt);
			this.fbQueue.put(targetSsrc, ll);
		} else {
			// Check for matching packets, else add to end
			ListIterator<RtcpPkt> li = ll.listIterator();
			while(li.hasNext()) {
				RtcpPkt tmp = li.next();
				if(equivalent(tmp, aPkt))
					return -1;
			}
			ll.addLast(aPkt);
		}
		return 0;
	}
		
	/**
	 * Checks whether there are ny feedback packets waiting
	 * to be sent.
	 * 
	 * @param ssrc of the participant we are notifying
	 * @return all relevant feedback packets, or null
	 */
	synchronized protected RtcpPkt[] getFromFbQueue(long ssrc) {
		if(this.fbQueue == null)
			return null;
		
		LinkedList<RtcpPkt> ll = this.fbQueue.get(ssrc);
		
		if(ll == null)
			return null;
		
		ListIterator<RtcpPkt> li = ll.listIterator();
		if(li.hasNext()) {
			long curTime = System.currentTimeMillis();
			long maxDelay = curTime - rtpSession.fbMaxDelay;
			long keepDelay =  curTime - 2000;
			int count = 0;
			
			//TODO below the indeces should be collected instead of looping twice
			
			// Clean out what we dont want and count what we want
			while(li.hasNext()) {
				RtcpPkt aPkt = li.next();
				if(aPkt.received) {
					//This is a packet received, we keep these for
					// 2000ms to avoid redundant feedback
					if(aPkt.time < keepDelay)
						li.remove();
				} else {
					//This is a packet we havent sent yet
					if(aPkt.time < maxDelay) {
						li.remove();
					} else {
						count++;
					}
				}
			}
			
			// Gather what we want to return
			if(count != 0) {
				li = ll.listIterator();
				RtcpPkt[] ret = new RtcpPkt[count];
		
				while(count > 0) {
					RtcpPkt aPkt = li.next();
					if(! aPkt.received) {
						ret[ret.length - count] = aPkt; 
						count--;
					}
				}
				return ret;
			}
		}
		
		return null;
	}
	
	/**
	 * Cleans the feeback queue of any packets that have expired,
	 * ie feedback packet that are no longer relevant.
	 * 
	 * @param ssrc The SSRC of the user who has left, negative value -> general cleanup
	 */
	synchronized protected void cleanFbQueue(long ssrc) {
		if(this.fbQueue == null)
			return;
		
		if(ssrc > 0) {
			this.fbQueue.remove(ssrc);
		} else { 
			Enumeration<LinkedList<RtcpPkt>> enu = this.fbQueue.elements();
			long curTime = System.currentTimeMillis();
			long maxDelay = curTime - rtpSession.fbMaxDelay;
			long keepDelay =  curTime - 2000;

			while(enu.hasMoreElements()) {
				ListIterator<RtcpPkt> li = enu.nextElement().listIterator();
				while(li.hasNext()) {
					RtcpPkt aPkt = li.next();
					if(aPkt.received) {
						//This is a packet received, we keep these for
						// 2000ms to avoid redundant feedback
						if(aPkt.time < keepDelay)
							li.remove();
					} else {
						//This is a packet we havent sent yet
						if(aPkt.time < maxDelay)
							li.remove();
					}
				}
			}
		}
	}
	
	/**
	 * Check whether the conditions are satisfied to send a feedbkac packet immediately.
	 * 
	 * @return true if they are, false otherwise
	 */
	protected boolean fbSendImmediately() {
		if(rtpSession.partDb.ssrcTable.size() > this.rtpSession.fbEarlyThreshold 
				&& rtpSession.partDb.receivers.size() > this.rtpSession.fbEarlyThreshold)
			return false;
		
		return true;
	}
	
	
	/**
	 * Check whether the conditions are satisfied to send a feedbkac packet immediately.
	 * 
	 * @return true if they are, false otherwise
	 */
	protected boolean fbSendEarly() {
		if(rtpSession.partDb.ssrcTable.size() > this.rtpSession.fbRegularThreshold 
				&& rtpSession.partDb.receivers.size() > this.rtpSession.fbRegularThreshold)
			return false;
		
		return true;
	}
	
	/**
	 * Wake the sender thread because of this ssrc
	 * 
	 * @param ssrc that has feedback waiting.
	 */
	protected void wakeSenderThread(long ssrc) {
		this.fbWaiting = ssrc;
		this.senderThrd.interrupt();
		
		// Give it a chance to catch up
		try { Thread.sleep(0,1); } catch (Exception e){ };
	}
	
	/**
	 * Compares two packets to check whether they are equivalent feedback messages,
	 * to avoid sending the same feedback to a host twice.
	 * 
	 * Expect false negatives, but not false positives.
	 * 
	 * @param one packet
	 * @param two packet
	 * @return true if they are equivalent, false otherwise 
	 */
	private boolean equivalent(RtcpPkt one, RtcpPkt two) {
		// Cheap checks
		if(one.packetType != two.packetType)
			return false;
		
		if(one.itemCount != two.itemCount)
			return false;
		
		if(one.packetType == 205) {
			// RTP Feedback, i.e. a NACK
			RtcpPktRTPFB pktone = (RtcpPktRTPFB) one;
			RtcpPktRTPFB pkttwo = (RtcpPktRTPFB) two;

			if(pktone.ssrcMediaSource != pkttwo.ssrcMediaSource)
				return false;
			
			if(Arrays.equals(pktone.BLP,pkttwo.BLP) 
					&& Arrays.equals(pktone.BLP,pkttwo.BLP))
				return true;
			
			return true;
		} else if(one.packetType == 206) {
			RtcpPktPSFB pktone = (RtcpPktPSFB) one;
			RtcpPktPSFB pkttwo = (RtcpPktPSFB) two;

			if(pktone.ssrcMediaSource != pkttwo.ssrcMediaSource)
				return false;
			
			switch(one.itemCount) {
			case 1: // Picture Loss Indication 
				return true;
				
			case 2: // Slice Loss Indication
				// This will not work if the slice loss indicators are in different order
				if(pktone.sliFirst.length == pkttwo.sliFirst.length
						&& Arrays.equals(pktone.sliFirst, pkttwo.sliFirst) 
						&& Arrays.equals(pktone.sliNumber, pkttwo.sliNumber)
						&& Arrays.equals(pktone.sliPictureId, pkttwo.sliPictureId))
					return true;
				break;
			case 3: // Reference Picture Selection Indication 
				if(Arrays.equals(pktone.rpsiBitString, pkttwo.rpsiBitString))
					return true;
				break;
			case 15: // Application Layer Feedback Messages
				// This will not work if the padding scheme is different
				if(pktone.sliFirst.length == pkttwo.sliFirst.length
						&& Arrays.equals(pktone.alfBitString, pkttwo.alfBitString))
					return true;
				break;
			default:
				
			}
			return true;
		} else {
			System.out.println("!!!! RTCPSession.equivalentPackets() encountered unexpected packet type!");
		}
		return false;
	}
}

