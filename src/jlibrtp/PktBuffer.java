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
 * A PktBuffer stores packets either for buffering purposes,
 * or because they need to be assimilated to create a complete frame.
 * 
 * This behavior can be controlled through rtpSession.pktBufBehavior()
 * 
 * It optionally drops duplicate packets.
 * 
 * Note that newest is the most recently received, i.e. highest timeStamp
 * Next means new to old (from recently received to previously received) 
 * 
 * @author Arne Kepp
 */
public class PktBuffer {
	/** The RTPSession holds information common to all packetBuffers, such as max size */
	RTPSession rtpSession;
	/** SSRC of the the participant that this buffer is for */
	long SSRC;
	/** The parent participant */
	Participant p;
	/** The length of the buffer */
	int length = 0;
	/** The oldest, least recently received, packet */
	PktBufNode oldest = null;
	/** The newest, most recently received, packet */
	PktBufNode newest = null;
	
	/** The last sequence number received */
	int lastSeqNumber = -1;
	/** The last timestamp */
	long lastTimestamp = -1;

	/** 
	 * Creates a new PktBuffer, a linked list of PktBufNode
	 * 
	 * @param rtpSession the parent RTPSession
	 * @param p the participant to which this packetbuffer belongs.
	 * @param aPkt The first RTP packet, to be added to the buffer 
	 */
	protected PktBuffer(RTPSession rtpSession, Participant p, RtpPkt aPkt) {
		this.rtpSession = rtpSession;
		this.p = p;
		SSRC = aPkt.getSsrc();
		PktBufNode newNode = new PktBufNode(aPkt);
		oldest = newNode;
		newest = newNode;
		//lastSeqNumber = (aPkt.getSeqNumber() - 1);
		//lastTimestamp = aPkt.getTimeStamp();
		length = 1;
	}

	/**
	 * Adds a packet, this happens in constant time if they arrive in order.
	 * Optimized for the case where each pkt is a complete frame.
	 * 
	 * @param aPkt the packet to be added to the buffer.
	 * @return integer, negative if operation failed (see code)
	 */
	protected synchronized int addPkt(RtpPkt aPkt) {
		if(aPkt == null) {
			System.out.println("! PktBuffer.addPkt(aPkt) aPkt was null");
			return -5;
		}

		long timeStamp = aPkt.getTimeStamp();
		if(RTPSession.rtpDebugLevel > 7) {
			System.out.println("-> PktBuffer.addPkt() , length:" + length + " , timeStamp of Pkt: " + Long.toString(timeStamp));
		}

		
		PktBufNode newNode = new PktBufNode(aPkt);
		if(aPkt.getSsrc() != SSRC) {
			System.out.println("PktBuffer.addPkt() SSRCs don't match!");
		}
		
		int retVal = 0;
		if(this.rtpSession.pktBufBehavior > 0) {
			retVal = bufferedAddPkt(newNode);
		} else if(this.rtpSession.pktBufBehavior == 0) {
			retVal = filteredAddPkt(newNode);
		} else if(this.rtpSession.pktBufBehavior == -1) {
			retVal = unfilteredAddPkt(newNode);
		}
		

		if(RTPSession.rtpDebugLevel > 7) {
			if(RTPSession.rtpDebugLevel > 10) {
				this.debugPrint();
			}
			System.out.println("<- PktBuffer.addPkt() , length:" + length + " returning " + retVal);
		}
		return retVal;
	}
	
	/**
	 * Adds packets in the same order that they arrive,
	 * doesn't do any filering or processing.
	 * 
	 * @param newNode the node to add to the packet buffer
	 * @return 0 if everything is okay, -1 otherwise
	 */
	private int unfilteredAddPkt(PktBufNode newNode) {
		if(RTPSession.rtpDebugLevel > 8) {
			System.out.println("<->    PktBuffer.unfilteredAddPkt()");
		}
		//No magic, just add to the end
		if(oldest != null) {
			oldest.nextFrameQueueNode = newNode;
			newNode.prevFrameQueueNode = oldest; 
			oldest = newNode;
		} else {
			oldest = newNode;
			newest = newNode;
		}
		return 0;
	}
	
	/**
	 * Takes care of duplicate packets
	 * 
	 * @param newNode the node to add to the packet buffer
	 * @return 0 if everything is okay, -1 otherwise
	 */
	private int filteredAddPkt(PktBufNode newNode) {
		if(RTPSession.rtpDebugLevel > 8) {
			System.out.println("<->    PktBuffer.filteredAddPkt()");
		}
		
		if(length == 0) {
			// The buffer was empty, this packet is the one and only.
			newest = newNode;
			oldest = newNode;
			length = 1;
		} else {
			// The packetbuffer is not empty.
			if(newNode.timeStamp > newest.timeStamp || newNode.seqNum > newest.seqNum && (newNode.seqNum - newest.seqNum) < 10) {
				// Packet came in order
				newNode.nextFrameQueueNode = newest;
				newest.prevFrameQueueNode = newNode;
				newest = newNode;
				length++;
			} else {
					if(RTPSession.rtpDebugLevel > 2) {
						System.out.println("PktBuffer.filteredAddPkt Dropped a packet due to lag! " +  newNode.timeStamp + " " 
								+ newNode.seqNum + " vs "+ oldest.timeStamp + " " + oldest.seqNum);
					}
					return -1;
			}
		}
		
		return 0;
	}
	
	/**
	 * Does most of the packet organization for the application.
	 * Packets are put in order, duplicate packets or late arrivals are discarded
	 * 
	 * If multiple packets make up a frame, these will also be organized
	 * by RTP timestamp and sequence number, and returned as a complete frame.
	 * 
	 * @param newNode the node to add to the packet buffer
	 * @return 0 if everything is okay, -1 otherwise
	 */
	private int bufferedAddPkt(PktBufNode newNode) {
		if(RTPSession.rtpDebugLevel > 8) {
			System.out.println("<->    PktBuffer.bufferedAddPkt()");
		}
		if(length == 0) {
			// The buffer was empty, this packet is the one and only.
			newest = newNode;
			oldest = newNode;
		} else {
			// The packetbuffer is not empty.
			if(newNode.timeStamp > newest.timeStamp || newNode.seqNum > newest.seqNum) {
				// Packet came in order
				newNode.nextFrameQueueNode = newest;
				newest.prevFrameQueueNode = newNode;
				newest = newNode;
			} else {
				//There are packets, we need to order this one right.
				if(! pktOnTime(newNode.timeStamp, newNode.seqNum) && rtpSession.pktBufBehavior > -1) {
					// We got this too late, can't put it in order anymore.
					if(RTPSession.rtpDebugLevel > 2) {
						System.out.println("PktBuffer.addPkt Dropped a packet due to lag! " +  newNode.timeStamp + " " 
								+ newNode.seqNum + " vs "+ oldest.timeStamp + " " + oldest.seqNum);
					}
					return -1;
				}

				//Need to do some real work, find out where it belongs (linear search from the back).
				PktBufNode tmpNode = newest;
				while(tmpNode.timeStamp > newNode.timeStamp) {
					tmpNode = tmpNode.nextFrameQueueNode;
				}
				
				if( tmpNode.timeStamp == newNode.timeStamp
						&& rtpSession.frameReconstruction
						&& newNode.seqNum != tmpNode.seqNum) {
					//Packet has same timestamp, presumably belongs to frame. Need to order within frame.
					if(RTPSession.rtpDebugLevel > 8) {
						System.out.println("Found pkt with existing timeStamp: " + newNode.timeStamp);
					}
					int ret = addToFrame(tmpNode, newNode);
					if(ret != 0) {
						return ret;
					}
				} else {
								
					// Check that it's not a duplicate
					if(tmpNode.timeStamp == newNode.timeStamp && newNode.seqNum == tmpNode.seqNum) {
						if(RTPSession.rtpDebugLevel > 2) {
							System.out.println("PktBuffer.addPkt Dropped a duplicate packet! " 
									+  newNode.timeStamp + " " + newNode.seqNum );
						}
						return -1;
					}
				
					// Insert into buffer
					newNode.nextFrameQueueNode = tmpNode;
					newNode.prevFrameQueueNode = tmpNode.prevFrameQueueNode;

					// Update the node behind
					if(newNode.prevFrameQueueNode != null) {
						newNode.prevFrameQueueNode.nextFrameQueueNode = newNode;
					}
					tmpNode.prevFrameQueueNode = newNode;

					if(newNode.timeStamp > newest.timeStamp) {
						newest = newNode; 
					}
				}
			}
		}
		// Update the length of this buffer
		length++;
		return 0;
	}
	
	/**
	 * 
	 * @param frameNode the node currently representing the frame in the packet buffer
	 * @param newNode the new node to be added to the frame
	 * @return 0 if no error, -2 if this is a duplicate packet
	 */
	private int addToFrame(PktBufNode frameNode, PktBufNode newNode) {
		// Node has same timeStamp, assume pkt belongs to frame
		
		if(frameNode.seqNum < newNode.seqNum) {
			// this is not the first packet in the frame
			frameNode.pktCount++;
			
			// Find the right spot
			while( frameNode.nextFrameNode != null 
					&& frameNode.nextFrameNode.seqNum < newNode.seqNum) {
				frameNode = frameNode.nextFrameNode;
			}
			
			// Check whether packet is duplicate
			if(frameNode.nextFrameNode != null 
					&& frameNode.nextFrameNode.seqNum == newNode.seqNum) {
				if(RTPSession.rtpDebugLevel > 2) {
					System.out.println("PktBuffer.addPkt Dropped a duplicate packet!");
				}
				return -2;
			}
			
			newNode.nextFrameNode = frameNode.nextFrameNode;
			frameNode.nextFrameNode = newNode;
		
		} else {
			// newNode has the lowest sequence number
			newNode.nextFrameNode = frameNode;
			newNode.pktCount = frameNode.pktCount + 1;
			
			//Update the queue
			if(frameNode.nextFrameQueueNode != null) {
				frameNode.nextFrameQueueNode.prevFrameQueueNode = newNode;
				newNode.nextFrameQueueNode = frameNode.nextFrameQueueNode;
				frameNode.nextFrameQueueNode = null;
			}
			if(frameNode.prevFrameQueueNode != null) {
				frameNode.prevFrameQueueNode.nextFrameQueueNode = newNode;
				newNode.prevFrameQueueNode = frameNode.prevFrameQueueNode;
				frameNode.prevFrameQueueNode = null;
			}
			if(newest.timeStamp == newNode.timeStamp) {
				newest = newNode;
			}
		}

		return 0;
	}

	/** 
	 * Checks the oldest frame, if there is one, sees whether it is complete.
	 * @return Returns null if there are no complete frames available.
	 */
	protected synchronized DataFrame popOldestFrame() {
		if(RTPSession.rtpDebugLevel > 7) {
			System.out.println("-> PktBuffer.popOldestFrame()");
		}
		if(RTPSession.rtpDebugLevel > 10) {
			this.debugPrint();
		}
		
		if(this.rtpSession.pktBufBehavior > 0) {
			return this.bufferedPopFrame();
		} else {
			return this.unbufferedPopFrame();
		}
	}
	
	/**
	 * Will return the oldest frame without checking whether it is in
	 * the right order, or whether we should wate for late arrivals.
	 * 
	 * @return the first frame on the queue, null otherwise
	 */
	private DataFrame unbufferedPopFrame() {
		if(oldest != null) {
			PktBufNode retNode = oldest;
			
			popFrameQueueCleanup(retNode, retNode.seqNum);
			
			return new DataFrame(retNode, this.p, 
					rtpSession.appIntf.frameSize(retNode.pkt.getPayloadType()));
		} else {
			return null;
		}
	}
	
	/**
	 * Only returns if the buffer is full, i.e. length exceeds
	 * rtpSession.pktBufBehavior, or if the next packet directly
	 * follows the previous one returned to the application.
	 * 
	 * @return first frame in order, null otherwise
	 */
	private DataFrame bufferedPopFrame() {
		PktBufNode retNode = oldest;
		/**
		 * Three scenarios:
		 * 1) There are no packets available
		 * 2) The first packet is vailable and in order
		 * 3) The first packet is not the next on in the sequence
		 * 		a) We have exceeded the wait buffer
		 * 		b) We wait
		 */
		//System.out.println(" Debug:" +(retNode != null) + " " + (retNode.seqNum == this.lastSeqNumber + 1)
		//		+ " " + ( retNode.seqNum == 0 ) + " " +  (this.length > this.rtpSession.maxReorderBuffer)
		//		+ " " + (this.lastSeqNumber < 0));

		// Pop it off, null all references.
		if( retNode != null && (retNode.seqNum == this.lastSeqNumber + 1 || retNode.seqNum == 0 
					|| this.length > this.rtpSession.pktBufBehavior || this.lastSeqNumber < 0)) {
			
				
			//if(tmpNode.pktCount == compLen) {
			if(RTPSession.rtpDebugLevel > 7) {
				System.out.println("<- PktBuffer.popOldestFrame() returns frame");
			}
			
			DataFrame df = new DataFrame(retNode, this.p, 
					rtpSession.appIntf.frameSize(oldest.pkt.getPayloadType()));
			
			//DataFrame df = new DataFrame(retNode, this.p, 1);
			popFrameQueueCleanup(retNode, df.lastSeqNum);
			
			return df;
		
		} else {
			// If we get here we have little to show for.
			if(RTPSession.rtpDebugLevel > 2) {
				System.out.println("<- PktBuffer.popOldestFrame() returns null " + retNode.seqNum + " " + this.lastSeqNumber);
				this.debugPrint();
			}
			return null;
		}
	}
	
	/**
	 * Cleans the packet buffer before returning the frame,
	 * i.e. making sure the queue has a head etc.
	 * 
	 * @param retNode the node that is about to be popped
	 * @param highestSeq the highest sequence number returned to the application
	 */
	private void popFrameQueueCleanup(PktBufNode retNode, int highestSeq) {
		if(1 == length) {
			//There's only one frame
			newest = null;
			oldest = null;
		} else {
			//There are more frames
			oldest = oldest.prevFrameQueueNode;
			oldest.nextFrameQueueNode = null;
		}

		// Update counters
		length--;
		
		//Find the highest sequence number associated with this timestamp
		this.lastSeqNumber = highestSeq;
		this.lastTimestamp = retNode.timeStamp;
	}
	
	/** 
	 * Returns the length of the packetbuffer.
	 * @return number of frames (complete or not) in packetbuffer.
	 */
	protected int getLength() {
		return length;
	}
	
	/**
	 * Checks whether a packet is not too late, i.e. the next packet has already been returned.
	 * @param timeStamp the RTP timestamp of the packet under consideration 
	 * @param seqNum the sequence number of the packet under consideration
	 * @return true if newer packets have not been handed to the application
	 */
	protected boolean pktOnTime(long timeStamp, int seqNum) {
		if(this.lastSeqNumber == -1) {
			// First packet
			return true;
		} else {			
			if(seqNum >= this.lastSeqNumber) {
				if(this.lastSeqNumber < 3 && timeStamp < this.lastTimestamp ) {
					return false;
				}
			} else {
				if(seqNum > 3 || timeStamp < this.lastTimestamp) {
					return false;
				}
			}
		}
		return true;
	}

	/** 
	 * Prints out the packet buffer, oldest node first (on top).
	 */
	protected void debugPrint() {
		System.out.println("PktBuffer.debugPrint() : length "+length+" SSRC "+SSRC+" lastSeqNum:"+lastSeqNumber);
		PktBufNode tmpNode = oldest;
		int i = 0;
		while(tmpNode != null) {
			//String str = tmpNode.timeStamp.toString();
			System.out.println("   " + i + " seqNum:"+tmpNode.seqNum+" timeStamp: " + tmpNode.timeStamp + " pktCount:" + tmpNode.pktCount );
			i++;
			tmpNode = tmpNode.prevFrameQueueNode;
		}
	}
}
