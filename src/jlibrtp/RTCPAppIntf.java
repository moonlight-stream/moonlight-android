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


/**
 * This is the callback interface for RTCP packets.
 * 
 * It is optional, you do not have to register it.
 * 
 * If there are specific events you wish to ignore,
 * you can simply implement empty functions.
 * 
 * These are all syncrhonous, make sure to return quickly
 * or do the handling in a new thread.
 * 
 * @author Arne Kepp
 */
public interface RTCPAppIntf {
	
	/**
	 * This function is called whenever a Sender Report (SR) packet is received
	 * and returns unmodified values.
	 *  
	 * A sender report may optionally include Receiver Reports (RR), 
	 * which are returned as arrays. Index i corresponds to the same report
	 * throughout all of the arrays.
	 * 
	 * @param ssrc the (SR) SSRC of the sender
	 * @param ntpHighOrder (SR) NTP high order
	 * @param ntpLowOrder (SR) NTP low order
	 * @param rtpTimestamp (SR) RTP timestamp corresponding to the NTP timestamp
	 * @param packetCount (SR) Packets sent since start of session
	 * @param octetCount (SR) Octets sent since start of session
	 * @param reporteeSsrc (RR) SSRC of sender the receiver is reporting in
	 * @param lossFraction (RR) Loss fraction, see RFC 3550
	 * @param cumulPacketsLost (RR) Cumulative number of packets lost
	 * @param extHighSeq (RR) Extended highest sequence RTP packet received
	 * @param interArrivalJitter (RR) Interarrival jitter, see RFC 3550
	 * @param lastSRTimeStamp (RR) RTP timestamp when last SR was received 
	 * @param delayLastSR (RR) Delay, in RTP, since last SR was received
	 */
	public void SRPktReceived(long ssrc, long ntpHighOrder, long ntpLowOrder, 
			long rtpTimestamp, long packetCount, long octetCount,
			// Get the receiver reports, if any
			long[] reporteeSsrc, int[] lossFraction, int[] cumulPacketsLost, long[] extHighSeq, 
			long[] interArrivalJitter, long[] lastSRTimeStamp, long[] delayLastSR);
	
	/**
	 * This function is called whenever a Receiver Report (SR) packet is received
	 * and returns unmodified values.
	 * 
	 * A receiver report may optionally include report blocks, 
	 * which are returned as arrays. Index i corresponds to the same report
	 * throughout all of the arrays.
	 * 
	 * @param reporterSsrc SSRC of the receiver reporting
	 * @param reporteeSsrc (RR) SSRC of sender the receiver is reporting in
	 * @param lossFraction (RR) Loss fraction, see RFC 3550
	 * @param cumulPacketsLost (RR) Cumulative number of packets lost
	 * @param extHighSeq (RR) Extended highest sequence RTP packet received
	 * @param interArrivalJitter (RR) Interarrival jitter, see RFC 3550
	 * @param lastSRTimeStamp (RR) RTP timestamp when last SR was received 
	 * @param delayLastSR (RR) Delay, in RTP, since last SR was received
	 */
	public void RRPktReceived(long reporterSsrc, long[] reporteeSsrc, 
			int[] lossFraction, int[] cumulPacketsLost, long[] extHighSeq, 
			long[] interArrivalJitter, long[] lastSRTimeStamp, long[] delayLastSR);
	
	/**
	 * This function is called whenever a Source Description (SDES) packet is received.
	 * 
	 * It currently returns the updated participants AFTER they have been updated.
	 * 
	 * @param relevantParticipants participants mentioned in the SDES packet
	 */
	public void SDESPktReceived(Participant[] relevantParticipants);
	
	/**
	 * This function is called whenever a Bye (BYE) packet is received.
	 * 
	 * The participants will automatically be deleted from the participant
	 * database after some time, but in the mean time the application may 
	 * still receive RTP  packets from this source.
	 * 
	 * @param relevantParticipants participants whose SSRC was in the packet
	 * @param reason the reason provided in the packet
	 */
	public void BYEPktReceived(Participant[] relevantParticipants, String reason);
	
	
	/**
	 * This function is called whenever an Application (APP) packet is received.
	 * 
	 * @param part the participant associated with the SSRC
	 * @param subtype specified in the packet
	 * @param name ASCII description of packet
	 * @param data in the packet
	 */
	public void APPPktReceived(Participant part, int subtype, byte[] name, byte[] data);
}