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
import java.net.InetSocketAddress;

/**
 * DebugAppIntf can be registered on RTPSession to provide simple
 * debugging functionality. This is particularly useful to determine
 * whether the client is receing any data at all.
 * 
 * @author Arne Kepp
 *
 */

public interface DebugAppIntf {
	/**
	 * This function wil notify you of any packets received, valid or not.
	 * Useful for network debugging, and finding bugs in jlibrtp.
	 * 
	 * Type is an integer describing the type of event
	 * -2 - Invalid RTCP packet received
	 * -1 - Invalid RTP packet received
	 * 0 - RTP packet received
	 * 1 - RTCP packet received
	 * 
	 * Description is a string that should be meaningful to advanced users, such as
	 * "RTP packet received from 127.0.0.1:12312, SSRC: 1380912 , payload type 1, packet size 16 octets"
	 * or
	 * "Invalid RTP packet received from 127.0.0.1:12312" 
	 *
	 * This function is synchonous and should return quickly.
	 *
	 * @param type , the type of event, see above.
	 * @param socket , taken directly from the UDP packet
	 * @param description , see above. 
	 */
	public void packetReceived(int type, InetSocketAddress socket, String description);
	
	/**
	 * This function will notify you of any packets sent from this instance of RTPSession.
	 * Useful for network debugging, and finding bugs in jlibrtp.
	 * 
	 * Type is an integer describing the type of event
	 * 0 - RTP unicast packet sent
	 * 1 - RTP multicast packet sent
	 * 2 - RTCP unicast packet sent
	 * 3 - RTCP multicast packet sent 
	 * 
	 * Description is a string that should be meaningful to advanced users, such as
	 * 
	 * This function is synchonous and should return quickly.
	 * 
	 * @param type , the type of event, see above
	 * @param socket , taken directly from the UDP packet
	 * @param description , see above
	 */
	public void packetSent(int type, InetSocketAddress socket, String description);
	
	/**
	 * Other important events that can occur in session
	 * -1 SSRC conflict
	 *  0 Session is terminating
	 * @param type see above
	 * @param description , see above
	 */
	public void importantEvent(int type, String description);
}
