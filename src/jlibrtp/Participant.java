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

import java.net.InetSocketAddress;

/**
 * A participant represents a peer in an RTPSession. Based on the information stored on 
 * these objects, packets are processed and statistics generated for RTCP.
 */
public class Participant {
	/** Whether the participant is unexpected, e.g. arrived through unicast with SDES */
	protected boolean unexpected = false;
	/** Where to send RTP packets (unicast)*/
	protected InetSocketAddress rtpAddress = null; 
	/** Where to send RTCP packets (unicast) */
	protected InetSocketAddress rtcpAddress = null;
	/** Where the first RTP packet was received from */
	protected InetSocketAddress rtpReceivedFromAddress = null;
	/** Where the first RTCP packet was received from */
	protected InetSocketAddress rtcpReceivedFromAddress = null;
	
	/** SSRC of participant */
	protected long ssrc = -1;
	/** SDES CNAME */
	protected String cname = null;
	/** SDES The participant's real name */
	protected String name = null;
	/** SDES The participant's email */
	protected String email = null;
	/** SDES The participant's phone number */
	protected String phone = null;
	/** SDES The participant's location*/
	protected String loc = null;
	/** SDES The tool the participants is using */
	protected String tool = null;
	/** SDES A note */
	protected String note = null;
	/** SDES A priv string, loosely defined */
	protected String priv = null;

	// Receiver Report Items
	/** RR First sequence number */
	protected int firstSeqNumber = -1;
	/** RR Last sequence number */
	protected int lastSeqNumber = 0;
	/** RR Number of times sequence number has rolled over */
	protected long seqRollOverCount = 0;
	/** RR Number of packets received */
	protected long receivedPkts = 0;
	/** RR Number of octets received */
	protected long receivedOctets = 0;
	/** RR Number of packets received since last SR */
	protected int receivedSinceLastSR = 0;
	/** RR Sequence number associated with last SR */
	protected int lastSRRseqNumber = 0;
	/** RR Interarrival jitter */
	protected double interArrivalJitter = -1.0;
	/** RR Last received RTP Timestamp */
	protected long lastRtpTimestamp = 0;
	
	/** RR Middle 32 bits of the NTP timestamp in the last SR */
	protected long timeStampLSR = 0;
	/** RR The time when we actually got the last SR */
	protected long timeReceivedLSR = 0;
	
	/** Gradient where UNIX timestamp = ntpGradient*RTPTimestamp * ntpOffset */
	protected double ntpGradient = -1;
	/** Offset where UNIX timestamp = ntpGradient*RTPTimestamp * ntpOffset */
	protected long ntpOffset = -1;
	/** Last NTP received in SR packet, MSB */
	protected long lastNtpTs1 = 0; //32 bits
	/** Last NTP received in SR packet, LSB */
	protected long lastNtpTs2 = 0; //32 bits
	/** RTP Timestamp in last SR packet */
	protected long lastSRRtpTs = 0; //32 bits
	
	/** UNIX time when a BYE was received from this participant, for pruning */
	protected long timestampBYE = -1;	// The user said BYE at this time
	
	/** Store the packets received from this participant */
	protected PktBuffer pktBuffer = null;

	/** UNIX time of last RTP packet, to check whether this participant has sent anything recently */
	protected long lastRtpPkt = -1; //Time of last RTP packet
	/** UNIX time of last RTCP packet, to check whether this participant has sent anything recently */
	protected long lastRtcpPkt = -1; //Time of last RTCP packet
	/** UNIX time this participant was added by application, to check whether we ever heard back */
	protected long addedByApp = -1; //Time the participant was added by application
	/** UNIX time of last time we sent an RR to this user */
	protected long lastRtcpRRPkt = -1; //Timestamp of last time we sent this person an RR packet
	/** Unix time of second to last time we sent and RR to this user */
	protected long secondLastRtcpRRPkt = -1; //Timestamp of 2nd to last time we sent this person an RR Packet
		
	/**
	 * Create a basic participant. If this is a <b>unicast</b> session you must provide network address (ipv4 or ipv6) and ports for RTP and RTCP, 
	 * as well as a cname for this contact. These things should be negotiated through SIP or a similar protocol.
	 * 
	 * jlibrtp will listen for RTCP packets to obtain a matching SSRC for this participant, based on cname.
	 * @param networkAddress string representation of network address (ipv4 or ipv6). Use "127.0.0.1" for multicast session.
	 * @param rtpPort port on which peer expects RTP packets. Use 0 if this is a sender-only, or this is a multicast session.
	 * @param rtcpPort port on which peer expects RTCP packets. Use 0 if this is a sender-only, or this is a multicast session.
	 */
	public Participant(String networkAddress, int rtpPort, int rtcpPort) {
		if(RTPSession.rtpDebugLevel > 6) {
			System.out.println("Creating new participant: " + networkAddress);
		}
		
		// RTP
		if(rtpPort > 0) {
			try {
				rtpAddress = new InetSocketAddress(networkAddress, rtpPort);
			} catch (Exception e) {
				System.out.println("Couldn't resolve " + networkAddress);
			}
			//isReceiver = true;
		}
		
		// RTCP 
		if(rtcpPort > 0) {
			try {
				rtcpAddress = new InetSocketAddress(networkAddress, rtcpPort);
			} catch (Exception e) {
				System.out.println("Couldn't resolve " + networkAddress);
			}
		}
		
		//By default this is a sender
		//isSender = true;
	}
	
	// We got a packet, but we don't know this person yet.
	protected Participant(InetSocketAddress rtpAdr, InetSocketAddress rtcpAdr, long SSRC) {
		rtpReceivedFromAddress = rtpAdr;
		rtcpReceivedFromAddress = rtcpAdr;
		ssrc = SSRC;
		unexpected = true;
	}
	
	// Dummy constructor to ease testing
	protected Participant() {
		System.out.println("Don't use the Participan(void) Constructor!");
	}
	
	/**
	 * RTP Address registered with this participant.
	 * 
	 * @return address of participant
	 */
	InetSocketAddress getRtpSocketAddress() {
		return rtpAddress;
	}
	
	
	/**
	 * RTCP Address registered with this participant.
	 * 
	 * @return address of participant
	 */
	InetSocketAddress getRtcpSocketAddress() {
		return rtcpAddress;
	}

	/**
	 * InetSocketAddress this participant has used to
	 * send us RTP packets.
	 * 
	 * @return address of participant
	 */
	InetSocketAddress getRtpReceivedFromAddress() {
		return rtpAddress;
	}

	
	
	/**
	 * InetSocketAddress this participant has used to
	 * send us RTCP packets.
	 * 
	 * @return address of participant
	 */
	InetSocketAddress getRtcpReceivedFromAddress() {
		return rtcpAddress;
	}
	
	
	/**
	 * CNAME registered for this participant.
	 * 
	 * @return the cname
	 */
	public String getCNAME() {
		return cname;
	}
	
	
	/**
	 * NAME registered for this participant.
	 * 
	 * @return the name
	 */
	public String getNAME() {
		return name;
	}
	
	/**
	 * EMAIL registered for this participant.
	 * 
	 * @return the email address
	 */
	public String getEmail() {
		return email;
	}
	
	/**
	 * PHONE registered for this participant.
	 * 
	 * @return the phone number
	 */
	public String getPhone() {
		return phone;
	}
	
	/**
	 * LOCATION registered for this participant.
	 * 
	 * @return the location
	 */
	public String getLocation() {
		return loc;
	}
	
	/**
	 * NOTE registered for this participant.
	 * 
	 * @return the note
	 */
	public String getNote() {
		return note;
	}
	
	/**
	 * PRIVATE something registered for this participant.
	 * 
	 * @return the private-string
	 */
	public String getPriv() {
		return priv;
	}
	
	/**
	 * TOOL something registered for this participant.
	 * 
	 * @return the tool
	 */
	public String getTool() {
		return tool;
	}
		
	/**
	 * SSRC for participant, determined through RTCP SDES
	 * 
	 * @return SSRC (32 bit unsigned integer as long)
	 */
	public long getSSRC() {
		return this.ssrc;
	}
	
	/** 
	 * Updates the participant with information for receiver reports.
	 * 
	 * @param packetLength to keep track of received octets
	 * @param pkt the most recently received packet
	 */
	protected void updateRRStats(int packetLength, RtpPkt pkt) {
		int curSeqNum = pkt.getSeqNumber();
		
		if(firstSeqNumber < 0) {
			firstSeqNumber = curSeqNum;
		}
		
		receivedOctets += packetLength;
		receivedSinceLastSR++;
		receivedPkts++;
		
		long curTime =  System.currentTimeMillis();
		
		if( this.lastSeqNumber < curSeqNum ) {
			//In-line packet, best thing you could hope for
			this.lastSeqNumber = curSeqNum;
						
		} else if(this.lastSeqNumber - this.lastSeqNumber < -100) {
			//Sequence counter rolled over
			this.lastSeqNumber = curSeqNum;
			seqRollOverCount++;
			
		} else {
			//This was probably a duplicate or a late arrival.
		}
		
		// Calculate jitter
		if(this.lastRtpPkt > 0) {
			
			long D = (pkt.getTimeStamp() - curTime) - (this.lastRtpTimestamp - this.lastRtpPkt);
			if(D < 0)
				D = (-1)*D;
			
			this.interArrivalJitter += ((double)D - this.interArrivalJitter) / 16.0;
		}

		lastRtpPkt = curTime;
		lastRtpTimestamp = pkt.getTimeStamp();
	}
	
	/**
	 * Calculates the extended highest sequence received by adding 
	 * the last sequence number to 65536 times the number of times 
	 * the sequence counter has rolled over.
	 * 
	 * @return extended highest sequence
	 */
	protected long getExtHighSeqRecv() {
		return (65536*seqRollOverCount + lastSeqNumber);
	}
	
	/**
	 * Get the fraction of lost packets, calculated as described
	 * in RFC 3550 as a fraction of 256.
	 * 
	 * @return the fraction of lost packets since last SR received
	 */
	protected int getFractionLost() {
		int expected = (lastSeqNumber - lastSRRseqNumber);
		if(expected < 0)
			expected = 65536 + expected;
                
		int fraction = 256 * (expected - receivedSinceLastSR);
		if(expected > 0) {
			fraction = (fraction / expected);
		} else {
			fraction = 0;
		}
		
		//Clear counters 
		receivedSinceLastSR = 0;
		lastSRRseqNumber = lastSeqNumber;
		
		return fraction;
	}
	
	/**
	 * The total number of packets lost during the session.
	 * 
	 * Returns zero if loss is negative, i.e. duplicates have been received.
	 * 
	 * @return number of lost packets, or zero.
	 */
	protected long getLostPktCount() {
		long lost = (this.getExtHighSeqRecv() - this.firstSeqNumber) - receivedPkts;
		
		if(lost < 0)
			lost = 0;
		return lost;
	}
	
	/** 
	 * 
	 * @return the interArrivalJitter, calculated continuously
	 */
	protected double getInterArrivalJitter() {
		return this.interArrivalJitter;
	}
	
	/**
	 * Set the timestamp for last sender report
	 * 
	 * @param ntp1 high order bits
	 * @param ntp2 low order bits
	 */
	protected void setTimeStampLSR(long ntp1, long ntp2) {
		// Use what we've got
		byte[] high = StaticProcs.uIntLongToByteWord(ntp1);
		byte[] low = StaticProcs.uIntLongToByteWord(ntp2);
		low[3] = low[1];
		low[2] = low[0];
		low[1] = high[3];
		low[0] = high[2];
		
		this.timeStampLSR = StaticProcs.bytesToUIntLong(low, 0);
	}
	
	/**
	 * Calculate the delay between the last received sender report
	 * and now.
	 * 
	 * @return the delay in units of 1/65.536ms
	 */
	protected long delaySinceLastSR() {
		if(this.timeReceivedLSR < 1) 
			return 0;
			
		long delay = System.currentTimeMillis() - this.timeReceivedLSR;
		
		//Convert ms into 1/65536s = 1/65.536ms
		return (long) ((double)delay * 65.536);
	}
	
	/**
	 * Only for debugging purposes
	 */
	public void debugPrint() {
		System.out.print(" Participant.debugPrint() SSRC:"+this.ssrc+" CNAME:"+this.cname);
		if(this.rtpAddress != null)
			System.out.print(" RTP:"+this.rtpAddress.toString());
		if(this.rtcpAddress != null)
			System.out.print(" RTCP:"+this.rtcpAddress.toString());
		System.out.println("");
		
		System.out.println("                          Packets received:"+this.receivedPkts);
	}
}
