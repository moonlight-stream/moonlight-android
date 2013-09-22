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

import java.net.DatagramSocket;


/**
 * Validates the PktBuffer and associated classes.
 * 
 * @author Arne Kepp
 *
 */
public class ValidatePktBuffer {

	/**
	 * Instantiates a buffer, creates some packets, adds them and sorts them.
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		DatagramSocket rtpSocket = null;
		DatagramSocket rtcpSocket = null;
		try {
			rtpSocket = new DatagramSocket(6002);
			rtcpSocket = new DatagramSocket(6003);
		} catch (Exception e) {
			System.out.println("RTPSession failed to obtain port");
		}
		RTPSession rtpSession = new RTPSession(rtpSocket, rtcpSocket);
		
		
		String str1 = "ab";
		String str2 = "cd";
		String str3 = "ef";
		String str4 = "gh";
		String str5 = "ij";
		String str6 = "kl";
		//String str7 = "mn";
		
		long syncSource1 = 1;
		int seqNumber1 = 1;
		//int seqNumber2 = 1;
		RtpPkt pkt1 = new RtpPkt(10, syncSource1, 1, 0, str1.getBytes());
		RtpPkt pkt2 = new RtpPkt(20, syncSource1, 2, 0, str2.getBytes());
		RtpPkt pkt3 = new RtpPkt(30, syncSource1, 3, 0, str3.getBytes());
		RtpPkt pkt4 = new RtpPkt(40, syncSource1, 4, 0, str4.getBytes());
		RtpPkt pkt6 = new RtpPkt(60, syncSource1, 6, 0, str5.getBytes());
		RtpPkt pkt7 = new RtpPkt(70, syncSource1, 7, 0, str6.getBytes());
		
		Participant p = new Participant();
		
		PktBuffer pktBuf = new PktBuffer(rtpSession, p, pkt1);
		pktBuf.addPkt(pkt3); //2
		pktBuf.addPkt(pkt2); //3
		DataFrame aFrame = pktBuf.popOldestFrame();
		String outStr = new String(aFrame.getConcatenatedData());
		System.out.println("** 1 Data from first frame: " + outStr + ", should be ab");
		pktBuf.addPkt(pkt4); //3
		pktBuf.addPkt(pkt7); //4
		System.out.println("** 1.5 sixth");		
		pktBuf.addPkt(pkt6); //5
		System.out.println("** 2 Duplicate, should be dropped");
		pktBuf.addPkt(pkt3); //5
		// Pop second frame
		aFrame = pktBuf.popOldestFrame(); //4
		outStr = new String(aFrame.getConcatenatedData());
		System.out.println("** 3 Data from second frame: " + outStr + ", should be cd");
		
		// Pop third frame
		aFrame = pktBuf.popOldestFrame(); //3
		outStr = new String(aFrame.getConcatenatedData());
		System.out.println("** 4 Data from third frame: " + outStr + ", should be ef");
		System.out.println("** 5 pktBuf.getLength is " + pktBuf.getLength() + ", should be 3");
		
		System.out.println("** 6 Late arrival, should be dropped");
		pktBuf.addPkt(pkt2);
		
		aFrame = pktBuf.popOldestFrame();
		outStr = new String(aFrame.getConcatenatedData());
		System.out.println("** 7 Data from fourth frame: " + outStr + ", should be gh");
		
		aFrame = pktBuf.popOldestFrame();
		outStr = new String(aFrame.getConcatenatedData());
		System.out.println("** 8 Data from fifth frame: " + outStr + ", should be ij");

		aFrame = pktBuf.popOldestFrame();
		outStr = new String(aFrame.getConcatenatedData());
		System.out.println("** 9 Data from fifth frame: " + outStr + ", should be kl");
	}

}
