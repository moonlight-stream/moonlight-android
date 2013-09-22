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
 * This is a four-directional data structures used for
 * the frame buffer, i.e. buffer for pkts that need
 * to be assimilated into complete frames.
 * 
 * All the actual work is done by PktBuffer.
 * 
 * @author Arne Kepp
 *
 */
public class PktBufNode {
	/** The next node (RTP Timestamp), looking from the back -> next means older */
	protected PktBufNode nextFrameQueueNode = null;
	/** The previous node (RTP Timestmap), looking from the back -> prev means newer */
	protected PktBufNode prevFrameQueueNode = null;
	/** The next node within the frame, i.e. higher sequence number, same RTP timestamp */
	protected PktBufNode nextFrameNode = null;
	/** Number of packets with the same RTP timestamp */
	protected int pktCount;
	/** The RTP timeStamp associated with this node */
	protected long timeStamp;
	/** The sequence number associated with this node */
	protected int seqNum;
	/** The payload, a parsed RTP Packet */
	protected RtpPkt pkt = null;
	
	/**
	 * Create a new packet buffer node based on a packet
	 * @param aPkt the packet
	 */
	protected PktBufNode(RtpPkt aPkt) {
		pkt = aPkt;
		timeStamp = aPkt.getTimeStamp();
		seqNum = aPkt.getSeqNumber();
		pktCount = 1;
	}
}
