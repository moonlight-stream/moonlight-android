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
 * Data structure to hold a complete frame if frame reconstruction
 * is enabled, or the data from an individual packet if it is not
 * 
 * It also contains most of the data from the individual packets 
 * that it is based on.
 * 
 * @author Arne Kepp
 */
public class DataFrame {
	/** The share RTP timestamp */
	private long rtpTimestamp;
	/** The calculated UNIX timestamp, guessed after 2 Sender Reports */
	private long timestamp = -1;
	/** the SSRC from which this frame originated */
	private long SSRC;
	/** contributing CSRCs, only read from the first packet */
	private long[] CSRCs;
	/** RTP payload type */
	private int payloadType;
	/** The marks on individual packets, ordered */
	private boolean[] marks;
	/** Whether any packets were marked or not */
	private boolean anyMarked = false;
	/** Whether the frame contains the expected number of packets */
	private int isComplete = 0;
	//private int dataLength;
	/** The data from the individual packets, ordered */
	private byte[][] data;
	/** The sequence numbers of the individual packets, ordered */
	private int[] seqNum;
	/** The total amount of data bytes in this frame */
	private int totalLength = 0;
	/** The last sequence number in this frame */
	protected int lastSeqNum;
	/** The first sequence number in this frame */
	protected int firstSeqNum;
	/** The number of packets expected for a complete frame */
	protected int noPkts;
	
	/**
	 * The usual way to construct a frame is by giving it a PktBufNode,
	 * which contains links to all the other pkts that make it up.
	 */
	protected DataFrame(PktBufNode aBufNode, Participant p, int noPkts) {
		if(RTPSession.rtpDebugLevel > 6) {
			System.out.println("-> DataFrame(PktBufNode, noPkts = " + noPkts +")");
		}
		this.noPkts = noPkts;
		RtpPkt aPkt = aBufNode.pkt;
		int pktCount = aBufNode.pktCount;
		firstSeqNum = aBufNode.pktCount;
		
		// All this data should be shared, so we just get it from the first one
		this.rtpTimestamp = aBufNode.timeStamp;
		SSRC = aPkt.getSsrc();
		CSRCs = aPkt.getCsrcArray();
		
		// Check whether we can compute an NTPish timestamp? Requires two SR reports 
		if(p.ntpGradient > 0) {
			//System.out.print(Long.toString(p.ntpOffset)+" " 
			timestamp =  p.ntpOffset + (long) (p.ntpGradient*(double)(this.rtpTimestamp-p.lastSRRtpTs));
		}
		
		// Make data the right length
		int payloadLength = aPkt.getPayloadLength();
		//System.out.println("aBufNode.pktCount " + aBufNode.pktCount);
		data = new byte[aBufNode.pktCount][payloadLength];
		seqNum = new int[aBufNode.pktCount];
		marks = new boolean[aBufNode.pktCount];
		
		// Concatenate the data of the packets
		int i;
		for(i=0; i< pktCount; i++) {
			aPkt = aBufNode.pkt;
			byte[] temp = aPkt.getPayload();
			totalLength += temp.length;
			if(temp.length == payloadLength) {
				data[i] = temp;
			} else if(temp.length < payloadLength){
				System.arraycopy(temp, 0, data[i], 0, temp.length);
			} else {
				System.out.println("DataFrame() received node structure with increasing packet payload size.");
			}
			//System.out.println("i " + i + " seqNum[i] " + seqNum[i] + " aBufNode"  + aBufNode);
			seqNum[i] = aBufNode.seqNum;
			marks[i] = aBufNode.pkt.isMarked();
			if(marks[i])
				anyMarked = true;
			
			// Get next node
			aBufNode = aBufNode.nextFrameNode;
		}
		
		lastSeqNum = seqNum[i - 1];
		
		if(noPkts > 0) {
			int seqDiff = firstSeqNum - lastSeqNum;
			if(seqDiff < 0)
				seqDiff = (Integer.MAX_VALUE - firstSeqNum)  + lastSeqNum;
			if(seqDiff == pktCount && pktCount == noPkts)
				isComplete = 1;
		} else {
			isComplete = -1;
		}
		
		if(RTPSession.rtpDebugLevel > 6) {
			System.out.println("<- DataFrame(PktBufNode, noPkt), data length: " + data.length);
		}
	}
	
	/**
	 * Returns a two dimensial array where the first dimension represents individual
	 * packets, from which the frame is made up, in order of increasing sequence number. 
	 * These indeces can be matched to the sequence numbers returned by sequenceNumbers().
	 * 
	 * @return 2-dim array with raw data from packets
	 */
	public byte[][] getData() {
		return this.data;
	}
	
	/**
	 * Returns a concatenated version of the data from getData()
	 * It ignores missing sequence numbers, but then isComplete()
	 * will return false provided that RTPAppIntf.frameSize()
	 * provides a non-negative number for this payload type.
	 * 
	 * @return byte[] with all the data concatenated
	 */
	public byte[] getConcatenatedData() {
		if(this.noPkts < 2) {
			byte[] ret = new byte[this.totalLength];
			int pos = 0;
		
			for(int i=0; i<data.length; i++) {
				int length = data[i].length;
				
				// Last packet may be shorter
				if(pos + length > totalLength) 
					length = totalLength - pos;
				
				System.arraycopy(data[i], 0, ret, pos, length);
				pos += data[i].length;
			}
			return ret;
		} else {
			return data[0];
		}
	}
	
	/**
	 * If two SR packet have been received jlibrtp will attempt to calculate 
	 * the local UNIX timestamp (in milliseconds) of all packets received.
	 * 
	 * This value should ideally correspond to the local time when the 
	 * SSRC sent the packet. Note that the source may not be reliable.
	 * 
	 * Returns -1 if less than two SRs have been received
	 * 
	 * @return the UNIX timestamp, similar to System.currentTimeMillis() or -1;
	 */
	public long timestamp() {
		return this.timestamp;
		
	}
	
	/**
	 * Returns the RTP timestamp of all the packets in the frame.
	 * 
	 * @return unmodified RTP timestamp
	 */
	public long rtpTimestamp() {
		return this.rtpTimestamp;
	}
	
	/**
	 * Returns the payload type of the packets
	 * 
	 * @return the payload type of the packets
	 */
	public int payloadType() {
		return this.payloadType;
	}

	/**
	 * Returns an array whose values, for the same index, correpond to the 
	 * sequence number of the packet from which the data came.
	 * 
	 * This information can be valuable in conjunction with getData(), 
	 * to identify what parts of a frame are missing.
	 * 
	 * @return array with sequence numbers
	 */
	public int[] sequenceNumbers() {
		return seqNum;
	}
	
	/**
	 * Returns an array whose values, for the same index, correpond to 
	 * whether the data was marked or not. 
	 * 
	 * This information can be valuable in conjunction with getData().
	 * 
	 * @return array of booleans
	 */
	public boolean[] marks() {
		return this.marks;
	}
	
	/**
	 * Returns true if any packet in the frame was marked.
	 * 
	 * This function should be used if all your frames fit
	 * into single packets.
	 * 
	 * @return true if any packet was marked, false otherwise
	 */
	public boolean marked() {
		return this.anyMarked;
	}
	
	/**
	 * The SSRC associated with this frame.
	 * 
	 * @return the ssrc that created this frame
	 */
	public long ssrc() {
		return this.SSRC;
	}
	
	/**
	 * The SSRCs that contributed to this frame
	 * 
	 * @return an array of contributing SSRCs, or null
	 */
	public long[] csrcs() {
		return this.CSRCs;
	}
	
	/**
	 * Checks whether the difference in sequence numbers corresponds
	 * to the number of packets received for the current timestamp,
	 * and whether this value corresponds to the expected number of
	 * packets.
	 * 
	 * @return true if the right number of packets make up the frame
	 */
	public int complete() {
		return this.isComplete;
	}
}
