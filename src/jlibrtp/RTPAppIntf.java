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
 * This is the callback interface for RTP packets.
 * 
 * It is mandatory, but you can inore the data if you like.
 * 
 * @author Arne Kepp
 */
public interface RTPAppIntf {
	
	/**
	 * The callback method through which the application will receive
	 * data from jlibrtp. These calls are synchronous, so you will not
	 * receive any new packets until this call returns.
	 * 
	 * @param frame the frame containing the data
	 * @param participant the participant from which the data came
	 */
	public void receiveData(DataFrame frame, Participant participant);
	
	
	/**
	 * The callback method through which the application will receive
	 * notifications about user updates, additions and byes.
	 *  Types:
	 *  	1 - Bye
	 *  	2 - New through RTP, check .getRtpSendSock()
	 *  	3 - New through RTCP, check .getRtcpSendSock()
	 * 		4 - SDES packet received, check the getCname() etc methods
	 *      5 - Matched SSRC to ip-address provided by application
	 * 
	 * @param type the type of event
	 * @param participant the participants in question
	 */
	public void userEvent(int type, Participant[] participant);
	
	/**
	 * The callback method through which the application can specify
	 * the number of packets that make up a frame for a given payload type.
	 * 
	 * A negative value denotes frames of variable length, so jlibrtp
	 * will return whatever it has at the time.
	 * 
	 * In most applications, this function can simply return 1.
	 * 
	 * This should be implemented as something fast, such as an
	 * integer array with the indeces being the payload type.
	 * 
	 * @param payloadType the payload type specified in the RTP packet
	 * @return the number of packets that make up a frame
	 */
	public int frameSize(int payloadType);
}
