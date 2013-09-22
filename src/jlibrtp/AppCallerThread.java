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

import java.util.Enumeration;

/**
 * The purpose of this thread is to check whether there are packets ready from 
 * any participants.
 * 
 * It should sleep when not in use, and be woken up by a condition variable.
 * 
 * Optionally, if we do jitter-control, the condition variable should have a max waiting period 
 * equal to how often we need to push data.
 * 
 * @author Arne Kepp
 */
public class AppCallerThread extends Thread {
	/**  The parent RTP Session */
	RTPSession rtpSession;
	/**  The applications interface, where the callback methods are called */
	RTPAppIntf appl;
	
	/**
	 * Instatiates the AppCallerThread
	 * 
	 * @param session the RTPSession with participants etc
	 * @param rtpApp the interface to which data is given
	 */
	protected AppCallerThread(RTPSession session, RTPAppIntf rtpApp) {
		rtpSession = session;
		appl = rtpApp;
		if(RTPSession.rtpDebugLevel > 1) {
			System.out.println("<-> AppCallerThread created");
		}  
	}
	
	/**
	 * The AppCallerThread will run in this loop until the RTPSession
	 * is terminated.
	 * 
	 * Whenever an RTP packet is received it will loop over the
	 * participants to check for packet buffers that have available
	 * frame.
	 */
	public void run() {
		if(RTPSession.rtpDebugLevel > 3) {
			System.out.println("-> AppCallerThread.run()");
		}
		
		while(rtpSession.endSession == false) {
			
			rtpSession.pktBufLock.lock();
		    try {
				if(RTPSession.rtpDebugLevel > 4) {
					System.out.println("<-> AppCallerThread going to Sleep");
				}
				
				try { rtpSession.pktBufDataReady.await(); } 
					catch (Exception e) { System.out.println("AppCallerThread:" + e.getMessage());}
					
		    	// Next loop over all participants and check whether they have anything for us.
				Enumeration<Participant> enu = rtpSession.partDb.getParticipants();
				
				while(enu.hasMoreElements()) {
					Participant p = enu.nextElement(); 
					
					boolean done = false;
					//System.out.println(p.ssrc + " " + !done +" " + p.rtpAddress 
					//		+ " " + rtpSession.naiveReception + " " + p.pktBuffer);
					//System.out.println("done: " + done + "  p.unexpected: " + p.unexpected);
					while(!done && (!p.unexpected || rtpSession.naiveReception) 
							&& p.pktBuffer != null && p.pktBuffer.length > 0) {

						DataFrame aFrame = p.pktBuffer.popOldestFrame();
						if(aFrame == null) {
							done = true;
						} else {
							appl.receiveData(aFrame, p);
						}
					}
				}
		    
		     } finally {
		       rtpSession.pktBufLock.unlock();
		     }
			
		}
		if(RTPSession.rtpDebugLevel > 3) {
			System.out.println("<- AppCallerThread.run() terminating");
		}  
	}

}
