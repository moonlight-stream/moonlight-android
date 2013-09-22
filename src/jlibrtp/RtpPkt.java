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
 * RtpPkt is the basic class for creating and parsing RTP packets.
 * 
 * There are two ways of instantiating an RtpPkt. One is for packets that you wish to send,
 * which requires that you provide basic information about the packet and a payload. Upon calling
 * encode() the fields of the structure are written into a bytebuffer, in the form that it would
 * sent across the network, excluding the UDP headers.
 * 
 * The other way is by passing a bytebuffer. The assumption is that this is a packet
 * that has been received from the network, excluding UDP headers, and the bytebuffer will
 * be parsed into the correct fields. 
 * 
 * The class keeps track of changes. Therefore, modifications are possible after calling encode(),
 * if necessary, the raw version of the packet will be regenerated on subsequent requests.
 * 
 * @author Arne Kepp
 */
public class RtpPkt {
	/** Whether the packet has been changed since encode() */
	private boolean rawPktCurrent = false;
	/** The version, always 2, 2 bits */
	private int version = 2; 		//2 bits
	/** Whether the packet is padded, 1 bit */
	private int padding; 			//1 bit
	/** Whether and extension is used, 1 bit */
	private int extension = 0; 		//1 bit
	/** Whether the packet is marked, 1 bit */
	private int marker = 0;			//1 bit
	/** What payload type is used, 7 bits */
	private int payloadType;		//
	/** The sequence number, taken from RTP Session, 16 bits */
	private int seqNumber;			//16 bits
	/** The RTP timestamp, 32bits */
	private long timeStamp;			//32 bits
	/** The SSRC of the packet sender, 32 bits*/
	private long ssrc;				//32 bits
	/** SSRCs of contributing sources, 32xn bits, n<16 */ 
	private long[] csrcArray = null;//
	
	/** Contains the actual data (eventually) */
	private byte[] rawPkt = null;
	/** The actual data, without any RTP stuff */
	private byte[] payload = null;

	/**
	 * Construct a packet-instance. The ByteBuffer required for UDP transmission can afterwards be obtained from getRawPkt(). If you need to set additional parameters, such as the marker bit or contributing sources, you should do so before calling getRawPkt;
	 *
	 * @param aTimeStamp RTP timestamp for data
	 * @param syncSource the SSRC, usually taken from RTPSession
	 * @param seqNum Sequency number
	 * @param plt Type of payload
	 * @param pl Payload, the actual data
	 */
	protected RtpPkt(long aTimeStamp, long syncSource, int seqNum, int plt, byte[] pl){
		int test = 0;
		test += setTimeStamp(aTimeStamp);
		test += setSsrc(syncSource);
		test += setSeqNumber(seqNum);
		test += setPayloadType(plt);
		test += setPayload(pl);
		if(test != 0) {
			System.out.println("RtpPkt() failed, check with checkPkt()");
		}
		rawPktCurrent = true;
		if( RTPSession.rtpDebugLevel > 5) {
			System.out.println("<--> RtpPkt(aTimeStamp, syncSource, seqNum, plt, pl)"); 
		}
	}
	/**
	 * Construct a packet-instance from an raw packet (believed to be RTP). The UDP-headers must be removed before invoking this method. Call checkPkt on the instance to verify that it was successfully parsed.
	 *
	 * @param aRawPkt The data-part of a UDP-packet believed to be RTP 
	 * @param packetSize the number of valid octets in the packet, should be aRawPkt.length
	 */
	protected RtpPkt(byte[] aRawPkt, int packetSize){
		if( RTPSession.rtpDebugLevel > 5) {
			System.out.println("-> RtpPkt(aRawPkt)"); 
		}
		//Check size, need to have at least a complete header
		if(aRawPkt == null) {
			System.out.println("RtpPkt(byte[]) Packet null");
		}
		
		int remOct = packetSize - 12;
		if(remOct >= 0) {
			rawPkt = aRawPkt;	//Store it
			//Interrogate the packet
			sliceFirstLine();
			if(version == 2) {
				sliceTimeStamp();
				sliceSSRC();
				if(remOct > 4 && getCsrcCount() > 0) {
					sliceCSRCs();
					remOct -= csrcArray.length * 4; //4 octets per CSRC
				}
				// TODO Extension
				if(remOct > 0) {
					slicePayload(remOct);
				}
			
				//Sanity checks
				checkPkt();
		
				//Mark the buffer as current
				rawPktCurrent = true;
			} else {
				System.out.println("RtpPkt(byte[]) Packet is not version 2, giving up.");
			}
		} else {
			System.out.println("RtpPkt(byte[]) Packet too small to be sliced");
		}
		rawPktCurrent = true;
		if( RTPSession.rtpDebugLevel > 5) {
			System.out.println("<- RtpPkt(aRawPkt)");
		}
	}
	
	/*********************************************************************************************************
	 *                                                Reading stuff 
	 *********************************************************************************************************/
	protected int checkPkt() {
		//TODO, check for version 2 etc
		return 0;
	}
	protected int getHeaderLength() {
		//TODO include extension
		return 12 + 4*getCsrcCount();
	}
	protected int getPayloadLength() {
		return payload.length;
	}
	//public int getPaddingLength() {
	//	return lenPadding;
	//}
	protected int getVersion() {
		return version;
	}
	//public boolean isPadded() {
	//	if(lenPadding > 0) {
	//		return true;
	//	}else {
	//		return false;
	//	}
	//}
	//public int getHeaderExtension() {
	//TODO
	//}
	protected boolean isMarked() {
		return (marker != 0);
	}
	protected int getPayloadType() {
		return payloadType;
	}
	
	protected int getSeqNumber() {
		return seqNumber;
	}
	protected long getTimeStamp() {
		return timeStamp;
	}
	protected long getSsrc() {
		return ssrc;
	}
	
	protected int getCsrcCount() {
		if(csrcArray != null) {
			return csrcArray.length;
		}else{
			return 0;
		}
	}
	protected long[] getCsrcArray() {
		return csrcArray;
	}

	/** 
	 *  Encodes the a
	 */
	protected byte[] encode() {
		if(! rawPktCurrent || rawPkt == null) {
			writePkt();
		} 
		return rawPkt;
	}
	
	/* For debugging purposes */
	protected void printPkt() {
		System.out.print("V:" + version + " P:" + padding + " EXT:" + extension);
		System.out.println(" CC:" + getCsrcCount() + " M:"+ marker +" PT:" + payloadType + " SN: "+ seqNumber);
		System.out.println("Timestamp:" + timeStamp + "(long output as int, may be 2s complement)");
		System.out.println("SSRC:" + ssrc + "(long output as int, may be 2s complement)");
		for(int i=0;i<getCsrcCount();i++) {
			System.out.println("CSRC:" + csrcArray[i] + "(long output as int, may be 2s complement)");
		}
		//TODO Extension
		System.out.println("Payload, first four bytes: " + payload[0] + " " + payload[1] + " " + payload[2] + " " + payload[3]);
	}
	/*********************************************************************************************************
	 *                                                Setting stuff 
	 *********************************************************************************************************/
	protected void setMarked(boolean mark) {
		rawPktCurrent = false;
		if(mark) {
			marker = 1;
		} else {
			marker = 0;
		}
	}
	//public int setHeaderExtension() {
	//TODO
	//}	
	protected int setPayloadType(int plType) {
		int temp = (plType & 0x0000007F); // 7 bits, checks in RTPSession as well.
		if(temp == plType) {
			rawPktCurrent = false;
			payloadType = temp;
			return 0;
		} else {
			return -1;
		}
	}
	
	protected int setSeqNumber(int number) {
		if(number <= 65536 && number >= 0) {
			rawPktCurrent = false;
			seqNumber = number;
			return 0;
		} else {
			System.out.println("RtpPkt.setSeqNumber: invalid number");
			return -1;
		}
	}
	
	protected int setTimeStamp(long time) {
		rawPktCurrent = false;
		timeStamp = time;
		return 0;	//Naive for now
	}
	
	protected int setSsrc(long source) {
		rawPktCurrent = false;
		ssrc = source;
		return 0;	//Naive for now
	}
	
	protected int setCsrcs(long[] contributors) {
		if(contributors.length <= 16) {
			csrcArray = contributors;
			return 0;
		} else {
			System.out.println("RtpPkt.setCsrcs: Cannot have more than 16 CSRCs");
			return -1;
		}
	}
	
	protected int setPayload(byte[] data) {
		// TODO Padding
		if(data.length < (1500 - 12)) {
			rawPktCurrent = false;
			payload = data;
			return 0;
		} else {
			System.out.println("RtpPkt.setPayload: Cannot carry more than 1480 bytes for now.");
			return -1;
		}
	}
	protected byte[] getPayload() {
		return payload;
	}

	/*********************************************************************************************************
	 *                                           Private functions 
	 *********************************************************************************************************/
	//Generate a bytebyffer representing the packet, store it.
	private void writePkt() {
		int bytes = getPayloadLength();
		int headerLen = getHeaderLength();
		int csrcLen = getCsrcCount();
		rawPkt = new byte[headerLen + bytes];
		
		// The first line contains, version and various bits
		writeFirstLine();
		byte[] someBytes = StaticProcs.uIntLongToByteWord(timeStamp);
		for(int i=0;i<4;i++) {
			rawPkt[i + 4] = someBytes[i];
		}
		//System.out.println("writePkt timeStamp:" + rawPkt[7]);
		
		someBytes = StaticProcs.uIntLongToByteWord(ssrc);
		System.arraycopy(someBytes, 0, rawPkt, 8, 4);
		//System.out.println("writePkt ssrc:" + rawPkt[11]);
		
		for(int i=0; i<csrcLen ; i++) {
			someBytes = StaticProcs.uIntLongToByteWord(csrcArray[i]);
			System.arraycopy(someBytes, 0, rawPkt, 12 + 4*i, 4);
		}
		// TODO Extension

		//Payload
		System.arraycopy(payload, 0, rawPkt, headerLen, bytes);
		rawPktCurrent = true;
	}
	//Writes the first 4 octets of the RTP packet
	private void writeFirstLine() {
		byte aByte = 0;
		aByte |=(version << 6);
		aByte |=(padding << 5);
		aByte |=(extension << 4);
		aByte |=(getCsrcCount());
		rawPkt[0] = aByte;
		aByte = 0;
		aByte |=(marker << 7);
		aByte |= payloadType;
		rawPkt[1] = aByte;
		byte[] someBytes = StaticProcs.uIntIntToByteWord(seqNumber);
		rawPkt[2] = someBytes[0];
		rawPkt[3] = someBytes[1];
	}
	//Picks apart the first 4 octets of an RTP packet
	private void sliceFirstLine() {
		version = ((rawPkt[0] & 0xC0) >>> 6);
		padding = ((rawPkt[0] & 0x20) >>> 5);
		extension = ((rawPkt[0] & 0x10) >>> 4);
		csrcArray = new long[(rawPkt[0] & 0x0F)];
		marker = ((rawPkt[1] & 0x80) >> 7);
		payloadType = (rawPkt[1] & 0x7F);
		seqNumber = StaticProcs.bytesToUIntInt(rawPkt, 2);
	}
	//Takes the 4 octets representing the timestamp
	private void sliceTimeStamp() {
		timeStamp = StaticProcs.bytesToUIntLong(rawPkt, 4);
	}
	//Takes the 4 octets representing the SSRC
	private void sliceSSRC() {
		ssrc = StaticProcs.bytesToUIntLong(rawPkt,8);
	}
	//Check the length of the csrcArray (set during sliceFirstLine) 
	private void  sliceCSRCs() {
		for(int i=0; i< csrcArray.length; i++) {
			ssrc = StaticProcs.bytesToUIntLong(rawPkt, i*4 + 12);
		}
	}
	//Extensions //TODO
	private void slicePayload(int bytes) {
		payload = new byte[bytes];
		int headerLen = getHeaderLength();
		
		System.arraycopy(rawPkt, headerLen, payload, 0, bytes);
	}
}	