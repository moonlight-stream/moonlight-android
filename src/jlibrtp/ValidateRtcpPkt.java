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
import java.net.*;

public class ValidateRtcpPkt {
	
	public static void main(String[] args) {
		DatagramSocket rtpSock = null;
		DatagramSocket rtcpSock = null;
		
		try {
			rtpSock = new DatagramSocket(1233);
			rtcpSock = new DatagramSocket(1234);
		} catch (Exception e) {
			//do nothing
		}
		RTPSession rtpSession = new RTPSession(rtpSock, rtcpSock);
		
		System.out.println("************************** SSRC: " + rtpSession.ssrc + " **************************");
		ParticipantDatabase partDb = new ParticipantDatabase(rtpSession);
		//InetAddress test = InetAddress.getByName("127.0.0.1");
		Participant part1 = new Participant("127.0.0.1",12, 34);
		Participant part2 = new Participant("127.0.0.2",56, 78);
		
		part1.ssrc = 123;
		part2.ssrc = 345;
		
		InetSocketAddress testadr = null;
		
		try {
			testadr = InetSocketAddress.createUnresolved("localhost", 12371);
		} catch (Exception e) {
			// Do nothing
		}
		
		part1.cname = "test3";
		part2.cname = "test2";
		part1.loc = "1231231231";
		part2.loc = "Asker";
		part1.phone = "+452 1231231";
		part2.phone = "aasdasda.asdasdas";
		part1.lastSeqNumber = 111;
		part2.lastSeqNumber = 222;
		part1.timeStampLSR = 111111;
		part2.timeStampLSR = 222222;
		partDb.addParticipant(0,part1);
		partDb.addParticipant(0,part2);
		
		Participant[] partArray = new Participant[2];
		partArray[0] = part1;
		partArray[1] = part2;

		RtcpPktRR rrpkt = new RtcpPktRR(partArray,123456789);
		RtcpPktSR srpkt = new RtcpPktSR(rtpSession.ssrc,12,21,rrpkt);
		//RtcpPktSR srpkt2 = new RtcpPktSR(rtpSession.ssrc,12,21,null);
		//rrpkt = new RtcpPktRR(partArray,1234512311);
		
		//srpkt.debugPrint();
		//rrpkt.debugPrint();
		
		CompRtcpPkt compkt = new CompRtcpPkt();
		compkt.addPacket(srpkt);
		compkt.addPacket(rrpkt);
		compkt.addPacket(rrpkt);
		
		byte[] test2 = compkt.encode();
		//System.out.print(StaticProcs.bitsOfBytes(test));
		System.out.println("****************************** DONE ENCODING *******************************");
		CompRtcpPkt decomppkt = new CompRtcpPkt(test2,test2.length,testadr,rtpSession);
		System.out.println("****************************** DONE DECODING *******************************");
		System.out.println("Problem code:" + decomppkt.problem);
		
		ListIterator iter = decomppkt.rtcpPkts.listIterator();
		int i = 0;
		
		while(iter.hasNext()) {
			System.out.println(" i:" + i + " ");
			i++;
			
			Object aPkt = iter.next();
			if(	aPkt.getClass() == RtcpPktRR.class) {
				RtcpPktRR pkt = (RtcpPktRR) aPkt;
				pkt.debugPrint();
			} else if(aPkt.getClass() == RtcpPktSR.class) {
				RtcpPktSR pkt = (RtcpPktSR) aPkt;
				pkt.debugPrint();
			}
		} 

		System.out.println("****************************** BYE *******************************");
		long[] tempArray = {rtpSession.ssrc};
		byte[] tempReason = "tas".getBytes();
		RtcpPktBYE byepkt = new RtcpPktBYE(tempArray,tempReason);
		//byepkt.debugPrint();
		byepkt.encode();
		byte[] rawpktbye = byepkt.rawPkt;
		
		RtcpPktBYE byepkt2 = new RtcpPktBYE(rawpktbye,0);
		byepkt2.debugPrint();
		
		System.out.println("****************************** SDES *******************************");
		RtcpPktSDES sdespkt = new RtcpPktSDES(true,rtpSession,null);
		rtpSession.cname = "cname123@localhost";
		//rtpSession.loc = "right here";
		sdespkt.encode();
		//rtpSession.cname = "cname124@localhost";
		//rtpSession.loc = "right hera";
		byte[] rawpktsdes = sdespkt.rawPkt;
		InetSocketAddress tmpAdr = (InetSocketAddress) rtpSock.getLocalSocketAddress();
		RtcpPktSDES decsdespkt = new RtcpPktSDES(rawpktsdes, 0, (InetSocketAddress) rtpSock.getLocalSocketAddress() , partDb);
		decsdespkt.debugPrint();
		//partDb.debugPrint();
		
		CompRtcpPkt compkt2 = new CompRtcpPkt();
		compkt2.addPacket(srpkt);
		compkt2.addPacket(sdespkt);
		byte[] compkt2Raw = compkt.encode();
		
		CompRtcpPkt compkt3 = new CompRtcpPkt(compkt2Raw,compkt2Raw.length,tmpAdr,rtpSession);
	}
}
