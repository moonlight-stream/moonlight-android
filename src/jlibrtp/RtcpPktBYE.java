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
 * RTCP packets for sending Bye messages
 * 
 * @author Arne Kepp
 */
public class RtcpPktBYE extends RtcpPkt {
	/** SSRCs saying bye, 32xn bits, n<16 */
	protected long[] ssrcArray = null;
	/** Optional reason */
	protected byte[] reason = null;
	
	protected RtcpPktBYE(long[] ssrcs,byte[] aReason) {
		super.packetType = 203;
		// Fetch all the right stuff from the database
		reason = aReason;
		ssrcArray = ssrcs;
		if(ssrcs.length < 1) {
			System.out.println("RtcpBYE.RtcpPktBYE(long[] ssrcs, byte[] aReason) requires at least one SSRC!");
		}
	}
	
	protected RtcpPktBYE(byte[] aRawPkt, int start) {
		rawPkt = aRawPkt;
		if(!super.parseHeaders(start) || packetType != 203 ) {
			if(RTPSession.rtpDebugLevel > 2) {
				System.out.println(" <-> RtcpPktBYE.parseHeaders() etc. problem");
			}
			super.problem = -203;
		} else {
			ssrcArray = new long[super.itemCount];
			
			for(int i=0; i<super.itemCount; i++) {
				ssrcArray[i] = StaticProcs.bytesToUIntLong(aRawPkt, start + (i+1)*4);
			}
			if(super.length > (super.itemCount + 1)) {
				int reasonLength = (int) aRawPkt[start + (super.itemCount+1)*4];
				//System.out.println("super.itemCount:"+super.itemCount+" reasonLength:"+reasonLength+" start:"+(super.itemCount*4 + 4 + 1));
				reason = new byte[reasonLength];
				System.arraycopy(aRawPkt, start + (super.itemCount + 1)*4 + 1, reason, 0, reasonLength);
				//System.out.println("test:" + new String(reason));
			}
		}
	}
	
	protected void encode() {			
		itemCount = ssrcArray.length;
		length = 4*ssrcArray.length;
		
		if(reason != null) {
			length += (reason.length + 1)/4;
			if((reason.length + 1) % 4 != 0) {
				length +=1;
			}
		}
		rawPkt = new byte[length*4 + 4];
		
		int i;
		byte[] someBytes;
		
		// SSRCs
		for(i=0; i<ssrcArray.length; i++ ) {
			someBytes = StaticProcs.uIntLongToByteWord(ssrcArray[i]);
			System.arraycopy(someBytes, 0, rawPkt, 4 + 4*i, 4);			
		}
		
		// Reason for leaving
		if(reason != null) {
			//System.out.println("Writing to:"+(4+4*ssrcArray.length)+ " reason.length:"+reason.length );
			rawPkt[(4 + 4*ssrcArray.length)] = (byte) reason.length;
			System.arraycopy(reason, 0, rawPkt, 4+4*i +1, reason.length);		
		}
		super.writeHeaders();
	}
	
	public void debugPrint() {
		System.out.println("RtcpPktBYE.debugPrint() ");
		if(ssrcArray != null) {
			for(int i= 0; i<ssrcArray.length; i++) {
				long anSsrc = ssrcArray[i];
				System.out.println("     ssrc: " + anSsrc);
			}
		}
		if(reason != null) {
			System.out.println("     Reason: " + new String(reason));
		}
	}
}