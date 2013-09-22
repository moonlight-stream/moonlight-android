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
 * RTCP packets for Receiver Reports 
 * 
 * @author Arne Kepp
 */
public class RtcpPktRR extends RtcpPkt {
	/** Array of participants to send Receiver Reports to */
	protected Participant[] reportees = null;
	/** SSRC of participants the reports are for */
	protected long[] reporteeSsrc = null;// -1; //32 bits
	/** Fraction (over 256) of packets lost */
	protected int[] lossFraction = null;//-1; //8 bits
	/** Number of lost packets */
	protected int[] lostPktCount = null;//-1; //24 bits
	/** Extended highest sequence received */
	protected long[] extHighSeqRecv = null;//-1; //32 bits
	/** Interarrival jitter*/
	protected long[] interArvJitter = null;//-1; //32 bits
	/** Middle 32 bits of NTP when last SR was received */
	protected long[] timeStampLSR = null;//-1; //32 bits
	/** Delay on last SRC */
	protected long[] delaySR = null;//-1; //32 bits
	
	/**
	 * Constructor for a packet with receiver reports
	 * 
	 * @param reportees the participants on which to generate reports
	 * @param ssrc the SSRC of the sender, from the RTPSession
	 */
	protected RtcpPktRR(Participant[] reportees, long ssrc) {
		super.packetType = 201;
		// Fetch all the right stuff from the database
		super.ssrc = ssrc;
		this.reportees = reportees;
	}


	/**
	 * 
	 * 
	 * If rcount < 0 we assume we have to parse the entire packet,
	 * otherwise we'll just parse the receiver report blocks
	 * (ie. the data came from a Sender Report packet)
	 * 
	 * @param aRawPkt the byte[] with the report(s)
	 * @param start where in the raw packet to start reading
	 * @param rrCount the number of receiver reports, -1 if this does not come from an SR
	 */
	protected RtcpPktRR(byte[] aRawPkt, int start, int rrCount) {
		//System.out.println("RtcpPktRR: " + rrCount + "  start: " + start);
		super.rawPkt = aRawPkt;
		
		if(rrCount < 0 && (!super.parseHeaders(start) || packetType != 201 || super.length < 1)) {
			if(RTPSession.rtpDebugLevel > 2) {
				System.out.println(" <-> RtcpPktRR.parseHeaders() etc. problem: "+(!super.parseHeaders(start))+" "+packetType+" "+super.length);
			}
			super.problem = -201;
		}
		
		int base;
		if(rrCount > 0) {
			base = start + 28;
		} else {
			base = start + 8;
			rrCount = super.itemCount;
			super.ssrc = StaticProcs.bytesToUIntLong(aRawPkt, start + 4);
		}
		
		if(rrCount > 0) {
			reporteeSsrc = new long[rrCount];
			lossFraction = new int[rrCount];
			lostPktCount = new int[rrCount];
			extHighSeqRecv = new long[rrCount];
			interArvJitter = new long[rrCount];
			timeStampLSR = new long[rrCount];
			delaySR = new long[rrCount];

			for(int i=0; i<rrCount; i++ ) {
				int pos = base + i*24;
				reporteeSsrc[i] = StaticProcs.bytesToUIntLong(aRawPkt, pos);
				lossFraction[i] = (int) aRawPkt[pos + 4];
				aRawPkt[pos + 4] = (byte) 0;
				lostPktCount[i] = (int) StaticProcs.bytesToUIntLong(aRawPkt, pos + 4);
				extHighSeqRecv[i] = StaticProcs.bytesToUIntLong(aRawPkt, pos + 8);
				interArvJitter[i] = StaticProcs.bytesToUIntLong(aRawPkt, pos + 12);
				timeStampLSR[i] = StaticProcs.bytesToUIntLong(aRawPkt, pos + 16);
				delaySR[i] = StaticProcs.bytesToUIntLong(aRawPkt, pos + 20);
			}
		}
	}
	
	/**
	 * Encode the packet into a byte[], saved in .rawPkt
	 * 
	 * CompRtcpPkt will call this automatically
	 */
	protected void encode() {
		if(RTPSession.rtpDebugLevel > 9) {
			System.out.println("  -> RtcpPktRR.encode()");
		}
		
		byte[] rRs = null;
		//Gather up the actual receiver reports
		if(this.reportees != null) {
			rRs = this.encodeRR();
			super.rawPkt = new byte[rRs.length + 8];
			System.arraycopy(rRs, 0, super.rawPkt, 8, rRs.length);
			super.itemCount = reportees.length;
		} else {
			super.rawPkt = new byte[8];
			super.itemCount = 0;	
		}
		
		//Write the common header
		super.writeHeaders();
		
		//Add our SSRC (as sender)
		byte[] someBytes;
		someBytes = StaticProcs.uIntLongToByteWord(super.ssrc);
		System.arraycopy(someBytes, 0, super.rawPkt, 4, 4);
		
		if(RTPSession.rtpDebugLevel > 9) {
			System.out.println("  <- RtcpPktRR.encode()");
		}
		
	}
	
	/**
	 * Encodes the individual Receiver Report blocks, 
	 * 
	 * so they can be used either in RR packets or appended to SR
	 * 
	 * @return the encoded packets
	 */
	protected byte[] encodeRR() {
		if(RTPSession.rtpDebugLevel > 10) {
			System.out.println("   -> RtcpPktRR.encodeRR()");
		}
		//assuming we will always create complete reports:
		byte[] ret = new byte[24*reportees.length];
		
		//Write SR stuff
		for(int i = 0; i<reportees.length; i++) {
			int offset = 24*i;
			byte[] someBytes = StaticProcs.uIntLongToByteWord(reportees[i].ssrc);
			System.arraycopy(someBytes, 0, ret, offset, 4);
			
			//Cumulative number of packets lost
			someBytes = StaticProcs.uIntLongToByteWord(reportees[i].getLostPktCount());
		
			someBytes[0] = (byte) reportees[i].getFractionLost();
		
			//Write Cumulative number of packets lost and loss fraction to packet:
			System.arraycopy(someBytes, 0, ret, 4 + offset, 4);
		
			// Extended highest sequence received
			someBytes = StaticProcs.uIntLongToByteWord(reportees[i].getExtHighSeqRecv());
			System.arraycopy(someBytes, 0, ret, 8 + offset, 4);
		
			// Interarrival jitter
			if(reportees[i].interArrivalJitter >= 0) {
				someBytes = StaticProcs.uIntLongToByteWord((long)reportees[i].interArrivalJitter);
			} else { 
				someBytes = StaticProcs.uIntLongToByteWord((long) 0); 
			}
			System.arraycopy(someBytes, 0, ret, 12 + offset, 4);
		
			// Timestamp last sender report received
			someBytes = StaticProcs.uIntLongToByteWord(reportees[i].timeStampLSR);
			System.arraycopy(someBytes, 0, ret, 16 + offset, 4);
		
			// Delay since last sender report received, in terms of 1/655536 s = 0.02 ms
			if(reportees[i].timeReceivedLSR > 0) {
				someBytes = StaticProcs.uIntLongToByteWord(reportees[i].delaySinceLastSR());
			} else {
				someBytes = StaticProcs.uIntLongToByteWord(0);
			}
			System.arraycopy(someBytes, 0, ret, 20 + offset, 4);
		}
		if(RTPSession.rtpDebugLevel > 10) {
			System.out.println("   <- RtcpPktRR.encodeRR()");
		}
		return ret;
	}
	
	/**
	 * Debug purposes only
	 */
	public void debugPrint() {
		System.out.println("RtcpPktRR.debugPrint() ");
		if(reportees != null) {
			for(int i= 0; i<reportees.length; i++) {
				Participant part = reportees[i];
				System.out.println("     part.ssrc: " + part.ssrc + "  part.cname: " + part.cname);
			}
		} else {
			for(int i=0;i<reporteeSsrc.length; i++) {
				System.out.println("     reporteeSSRC: " + reporteeSsrc[i] + "  timeStampLSR: " + timeStampLSR[i]);
			}
		}
	}
}
