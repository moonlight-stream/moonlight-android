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
import java.util.concurrent.*;

/**
 * The participant database maintains three hashtables with participants.
 * 
 * The key issue is to be fast for operations that happen every time an
 * RTP packet is sent or received. We allow linear searching in cases 
 * where we need to update participants with information.
 * 
 * The keying is therefore usually the SSRC. In cases where we have the
 * cname, but no SSRC is known (no SDES packet has been received), a
 * simple hash i calculated based on the CNAME. The RTCP code should,
 * when receiving SDES packets, check whether the participant is known
 * and update the copy in this database with SSRC if needed.
 * 
 * @author Arne Kepp
 */
public class ParticipantDatabase {
	/** The parent RTP Session */
	RTPSession rtpSession = null;
	/** 
	 * A linked list to hold participants explicitly added by the application
	 * In unicast mode this is the list used for RTP and RTCP transmission, 
	 * in multicast it should not be in use. 
	 */
	LinkedList<Participant> receivers = new LinkedList<Participant>();	
	/** 
	 * The hashtable holds participants added through received RTP and RTCP packets,
	 * as well as participants that have been linked to an SSRC by ip address (in unicast mode).
	 */
	ConcurrentHashMap<Long,Participant> ssrcTable = new ConcurrentHashMap<Long,Participant>();
	
	/**
	 * Simple constructor
	 * 
	 * @param parent parent RTPSession
	 */
	protected ParticipantDatabase(RTPSession parent) {
		rtpSession = parent;
	}
	
	/**
	 * 
	 * @param cameFrom 0: Application, 1: RTP packet, 2: RTCP
	 * @param p the participant
	 * @return 0 if okay, -1 if not 
	 */
	protected int addParticipant(int cameFrom, Participant p) {
		//Multicast or not?
		if(this.rtpSession.mcSession) {
			return this.addParticipantMulticast(cameFrom, p);
		} else {
			return this.addParticipantUnicast(cameFrom, p);
		}
		
	}
	
	/**
	 * Add a multicast participant to the database
	 * 
	 * @param cameFrom 0: Application, 1,2: discovered through RTP or RTCP
	 * @param p the participant to add
	 * @return 0 if okay, -2 if redundant, -1 if adding participant to multicast
	 */
	private int addParticipantMulticast(int cameFrom, Participant p) {
		if( cameFrom == 0) {
			System.out.println("ParticipantDatabase.addParticipant() doesnt expect" 
					+ " application to add participants to multicast session.");
			return -1;
		} else {
			// Check this one is not redundant
			if(this.ssrcTable.contains(p.ssrc)) {
				System.out.println("ParticipantDatabase.addParticipant() SSRC "
						+"already known " + Long.toString(p.ssrc));
				return -2;
			} else {
				this.ssrcTable.put(p.ssrc, p);
				return 0;
			}
		}
	}
	
	/**
	 * Add a unicast participant to the database
	 * 
	 * Result will be reported back through tpSession.appIntf.userEvent
	 * 
	 * @param cameFrom 0: Application, 1,2: discovered through RTP or RTCP
	 * @param p the participant to add
	 * @return 0 if new, 1 if 
	 */
	private int addParticipantUnicast(int cameFrom, Participant p) {
		if(cameFrom == 0) {
			//Check whether there is a match in the ssrcTable
			boolean notDone = true;
			
			Enumeration<Participant> enu = this.ssrcTable.elements();
			while(notDone && enu.hasMoreElements()) {
				Participant part = enu.nextElement();
				if(part.unexpected && 
						(part.rtcpReceivedFromAddress.equals(part.rtcpAddress.getAddress()) 
						|| part.rtpReceivedFromAddress.equals(part.rtpAddress.getAddress()))) {
					
					part.rtpAddress = p.rtpAddress;
					part.rtcpAddress = p.rtcpAddress;
					part.unexpected = false;

					//Report the match back to the application
					Participant[] partArray = {part};
					this.rtpSession.appIntf.userEvent(5, partArray);
					
					notDone = false;
					p = part;
				}
			}

			//Add to the table of people that we send packets to
			this.receivers.add(p);
			return 0;
			
		} else {
			//Check whether there's a match in the receivers table
			boolean notDone = true;
			//System.out.println("GOT " + p.cname);
			Iterator<Participant> iter = this.receivers.iterator();
			
			while(notDone && iter.hasNext()) {
				Participant part = iter.next();
				
				//System.out.println(part.rtpAddress.getAddress().toString()
				//		+ " " + part.rtcpAddress.getAddress().toString() 
				//		+ " " + p.rtpReceivedFromAddress.getAddress().toString()
				//		+ " " + p.rtcpReceivedFromAddress.getAddress().toString());
				
				//System.out.println(" HUUHHHH?  " + p.rtcpReceivedFromAddress.getAddress().equals(part.rtcpAddress.getAddress()));
				if((cameFrom == 1 && p.rtpReceivedFromAddress.getAddress().equals(part.rtpAddress.getAddress()))
					|| (cameFrom == 2 && p.rtcpReceivedFromAddress.getAddress().equals(part.rtcpAddress.getAddress()))) {
					
					part.rtpReceivedFromAddress = p.rtpReceivedFromAddress;
					part.rtcpReceivedFromAddress = p.rtcpReceivedFromAddress;
					
					// Move information
					part.ssrc = p.ssrc;
					part.cname = p.cname;
					part.name = p.name;
					part.loc = p.loc;
					part.phone = p.phone;
					part.email = p.email;
					part.note = p.note;
					part.tool = p.tool;
					part.priv = p.priv;
					
					this.ssrcTable.put(part.ssrc, part);
					
					//Report the match back to the application
					Participant[] partArray = {part};
					this.rtpSession.appIntf.userEvent(5, partArray);
					return 0;
				}
			}
			
			// No match? ok
			this.ssrcTable.put(p.ssrc, p);				
			return 0;
		}
	}
	
	/**
	 * Remove a participant from all tables
	 * 
	 * @param p the participant to be removed
	 */
	protected void removeParticipant(Participant p) {
		if(! this.rtpSession.mcSession)
			this.receivers.remove(p);
		
		this.ssrcTable.remove(p.ssrc, p);
	}
		
	/**
	 * Find a participant based on the ssrc
	 * 
	 * @param ssrc of the participant to be found
	 * @return the participant, null if unknonw
	 */
	protected Participant getParticipant(long ssrc) {
		Participant p = null;
		p = ssrcTable.get(ssrc);
		return p; 
	}
	
	/**
	 * Iterator for all the unicast receivers.
	 * 
	 * This one is used by both RTP for sending packets, as well as RTCP.
	 * 
	 * @return iterator for unicast participants
	 */
	protected Iterator<Participant> getUnicastReceivers() {
		if(! this.rtpSession.mcSession) {
			return this.receivers.iterator();
		} else {
			System.out.println("Request for ParticipantDatabase.getUnicastReceivers in multicast session");
			return null;
		}
	}
	
	/**
	 * Enumeration of all the participants with known ssrcs.
	 * 
	 * This is primarily used for sending packets in multicast sessions.
	 * 
	 * @return enumerator with all the participants with known SSRCs
	 */
	protected Enumeration<Participant> getParticipants() {
		return this.ssrcTable.elements();
	}
	
	protected void debugPrint() {
		System.out.println("   ParticipantDatabase.debugPrint()");
		Participant p;
		Enumeration enu = ssrcTable.elements();
		while(enu.hasMoreElements()) {
			p = (Participant) enu.nextElement();
			System.out.println("           ssrcTable ssrc:"+p.ssrc+" cname:"+p.cname
					+" loc:"+p.loc+" rtpAddress:"+p.rtpAddress+" rtcpAddress:"+p.rtcpAddress);
		}
		
		Iterator<Participant> iter = receivers.iterator();
		while(iter.hasNext()) {
			p = iter.next();
			System.out.println("           receivers: "+p.rtpAddress.toString());
		}
	}
}
