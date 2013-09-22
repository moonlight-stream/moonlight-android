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
import java.util.*;
import java.net.InetSocketAddress;

/**
 * Compound RTCP packet class.
 * 
 * It basically holds a list of packets. This list can either be constructed
 * by providing a byte[] of a compound packet, or by adding individual packets.
 * 
 * Upon encode(), the packet will call encode on all the added packets.
 * 
 * problem == 0 indicates the parsing succeeded.
 * 
 * 
 * @author Arne Kepp
 */

public class CompRtcpPkt {
	/** Problem indicator, negative values denote packet type that cause problem */
	protected int problem = 0;
	/** Stores the different subclasses of RtcpPkt that make up the compound packet */
	protected LinkedList<RtcpPkt> rtcpPkts = new LinkedList<RtcpPkt>();
	
	/**
	 * Instantiates an empty Compound RTCP packet to which you can add RTCP packets
	 */
	protected CompRtcpPkt() {
		// Will have to add packets directly to rtcpPkts.
		if(RTPSession.rtpDebugLevel > 7) {
			System.out.println("<-> CompRtcpPkt()");
		}
	}
	
	/**
	 * Add a RTCP packet to the compound packet. Pakcets are added in order,
	 * so you have to ensure that a Sender Report or Receiver Report is 
	 * added first.
	 * 
	 * @param aPkt the packet to be added
	 */
	protected void addPacket(RtcpPkt aPkt) {
		if(RTPSession.rtpDebugLevel > 11) {
			System.out.println("  <-> CompRtcpPkt.addPacket( "+ aPkt.getClass() + " )");
		}
		
		if(aPkt.problem == 0) {
			rtcpPkts.add(aPkt);
		} else {
			this.problem = aPkt.problem;
		}
	}
	
	/**
	 * Picks a received Compound RTCP packet apart.
	 * 
	 * Only SDES packets are processed directly, other packets are
	 * parsed and put into aComptRtcpPkt.rtcpPkts, but not 
	 * 
	 * Check the aComptRtcpPkt.problem , if the value is non-zero
	 * the packets should probably be discarded.
	 * 
	 * @param rawPkt the byte array received from the socket
	 * @param packetSize the actual number of used bytes
	 * @param adr the socket address from which the packet was received
	 * @param rtpSession the RTPSession with the participant database
	 */
	protected CompRtcpPkt(byte[] rawPkt, int packetSize, InetSocketAddress adr, RTPSession rtpSession) {
		if(RTPSession.rtcpDebugLevel > 7) {
			System.out.println("-> CompRtcpPkt(" + rawPkt.getClass() + ", size " + packetSize + ", from " + adr.toString() + ", " + rtpSession.getClass() + ")");
		}
		//System.out.println("rawPkt.length:" + rawPkt.length + " packetSize:" + packetSize);
		
		// Chop it up
		int start = 0;

		while(start < packetSize && problem == 0) {
			int length = (StaticProcs.bytesToUIntInt(rawPkt, start + 2)) + 1;
			
			if(length*4 + start > rawPkt.length) {
				System.out.println("!!!! CompRtcpPkt.(rawPkt,..,..) length ("+ (length*4+start)
						+ ") exceeds size of raw packet ("+rawPkt.length+") !");
				this.problem = -3;
			}
			
			int pktType = (int) rawPkt[start + 1];
			
			if(pktType < 0) {
				pktType += 256;
			}
			
			
			if(start == 0) {
				// Compound packets need to start with SR or RR
				if(pktType != 200 && pktType != 201 ) {
					if(RTPSession.rtcpDebugLevel > 3) {
						System.out.println("!!!! CompRtcpPkt(rawPkt...) packet did not start with SR or RR");
					}
					this.problem = -1;
				}
				
				// Padding bit should be zero for the first packet
				if(((rawPkt[start] & 0x20) >>> 5) == 1) {
					if(RTPSession.rtcpDebugLevel > 3) {
						System.out.println("!!!! CompRtcpPkt(rawPkt...) first packet was padded");
					}
					this.problem = -2;
				}
			}
			
			//System.out.println("start: " + start + "   pktType: " + pktType + "  length:" + length );			
			if(pktType == 200) {
				addPacket(new RtcpPktSR(rawPkt,start,length*4));
			} else if(pktType == 201 ) {
				addPacket(new RtcpPktRR(rawPkt,start, -1));
			} else if(pktType == 202) {
				addPacket(new RtcpPktSDES(rawPkt,start, adr, rtpSession.partDb));
			} else if(pktType == 203 ) {
				addPacket(new RtcpPktBYE(rawPkt,start));
			} else if(pktType == 204) {
				addPacket(new RtcpPktAPP(rawPkt,start));
			} else if(pktType == 205) {
				addPacket(new RtcpPktRTPFB(rawPkt,start, rtpSession));
			} else if(pktType == 206) {
				addPacket(new RtcpPktPSFB(rawPkt,start,rtpSession));
			} else {
				System.out.println("!!!! CompRtcpPkt(byte[] rawPkt, int packetSize...) "
						+"UNKNOWN RTCP PACKET TYPE:" + pktType);
			}
			
			//System.out.println(" start:" + start + "  pktType:" + pktType + " length:" + length);
			
			start += length*4;
			
			if(RTPSession.rtcpDebugLevel > 12) {
				System.out.println(" start:"+start+"  parsing pktType "+pktType+" length: "+length);
			}
		}
		if(RTPSession.rtcpDebugLevel > 7) {
			System.out.println("<- CompRtcpPkt(rawPkt....)");
		}
	}
	
	/**
	 * Encode combines the RTCP packets in this.rtcpPkts into a byte[]
	 * by calling the encode() function on each of them individually.
	 * 
	 * The order of rtcpPkts is preserved, so a RR or SR packet must be first.
	 * 
	 * @return the trimmed byte[] representation of the packet, ready to go into a UDP packet.
	 */
	protected byte[] encode() {
		if(RTPSession.rtpDebugLevel > 9) {
			System.out.println(" <- CompRtcpPkt.encode()");
		}
		
		ListIterator<RtcpPkt>  iter = rtcpPkts.listIterator();

		byte[] rawPkt = new byte[1500];
		int index = 0;
		
		while(iter.hasNext()) {
			RtcpPkt aPkt = (RtcpPkt) iter.next();
			
			if(aPkt.packetType == 200) {
				RtcpPktSR pkt = (RtcpPktSR) aPkt;
				pkt.encode();
				System.arraycopy(pkt.rawPkt, 0, rawPkt, index, pkt.rawPkt.length);
				index += pkt.rawPkt.length;
			} else if(aPkt.packetType == 201 ) {
				RtcpPktRR pkt = (RtcpPktRR) aPkt;
				pkt.encode();
				System.arraycopy(pkt.rawPkt, 0, rawPkt, index, pkt.rawPkt.length);
				index += pkt.rawPkt.length;
			} else if(aPkt.packetType == 202) {
				RtcpPktSDES pkt = (RtcpPktSDES) aPkt;
				pkt.encode();
				//System.out.println(" ENCODE SIZE: " + pkt.rawPkt.length);
				System.arraycopy(pkt.rawPkt, 0, rawPkt, index, pkt.rawPkt.length);
				index += pkt.rawPkt.length;
			} else if(aPkt.packetType == 203) {
				RtcpPktBYE pkt = (RtcpPktBYE) aPkt;
				pkt.encode();
				System.arraycopy(pkt.rawPkt, 0, rawPkt, index, pkt.rawPkt.length);
				index += pkt.rawPkt.length;
			} else if(aPkt.packetType == 204) {
				RtcpPktAPP pkt = (RtcpPktAPP) aPkt;
				pkt.encode();
				System.arraycopy(pkt.rawPkt, 0, rawPkt, index, pkt.rawPkt.length);
				index += pkt.rawPkt.length;
			} else if(aPkt.packetType == 205) {
				RtcpPktRTPFB pkt = (RtcpPktRTPFB) aPkt;
				pkt.encode();
				System.arraycopy(pkt.rawPkt, 0, rawPkt, index, pkt.rawPkt.length);
				index += pkt.rawPkt.length;
			} else if(aPkt.packetType == 206) {
				RtcpPktPSFB pkt = (RtcpPktPSFB) aPkt;
				pkt.encode();
				System.arraycopy(pkt.rawPkt, 0, rawPkt, index, pkt.rawPkt.length);
				index += pkt.rawPkt.length;
			} else {
				System.out.println("CompRtcpPkt aPkt.packetType:" + aPkt.packetType);
			}
			//System.out.println(" packetType:" + aPkt.packetType + " length:" + aPkt.rawPkt.length + " index:" + index);
		} 
		
		byte[] output = new byte[index];
		
		System.arraycopy(rawPkt, 0, output, 0, index);

		if(RTPSession.rtpDebugLevel > 9) {
			System.out.println(" -> CompRtcpPkt.encode()");
		}
		return output;
	}
}