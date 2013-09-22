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
 * This is the callback interface for the AVPF profile (RFC 4585)
 * 
 * It is optional, you do not have to register it.
 * 
 * If there are specific events you wish to ignore,
 * you can simply implement empty functions.
 * 
 * These are all syncrhonous, make sure to return quickly
 * or do the handling in a new thread.
 * 
 * @author Arne Kepp
 */
public interface RTCPAVPFIntf {	
	
	/**
	 * This function is called when a 
	 * Picture Loss Indication (PLI, FMT = 1) is received
	 * 
	 * @param ssrcPacketSender the SSRC of the participant reporting loss of picture
	 */
	public void PSFBPktPictureLossReceived(
			long ssrcPacketSender);
	
	/**
	 * This function is called when a
	 * Slice Loss Indication (SLI, FMT=2) is received
	 * 
	 * @param ssrcPacketSender the SSRC of the participant reporting loss of slice(s)
	 * @param sliceFirst macroblock address of first macroblock
	 * @param sliceNumber number of lost macroblocks, in scan order
	 * @param slicePictureId the six least significant bits of the picture identifier 
	 */
	public void PSFBPktSliceLossIndic(
			long ssrcPacketSender,
			int[] sliceFirst, int[] sliceNumber, int[] slicePictureId);
	
	/**
	 * This function is called when a
	 * Reference Picture Selection Indication (RPSI, FMT=3) is received
	 * 
	 * @param ssrcPacketSender the SSRC of the participant reporting the selection
	 * @param rpsiPayloadType the RTP payload type related to the RPSI bit string
	 * @param rpsiBitString the RPSI information as natively defined by the video codec
	 * @param rpsiPaddingBits the number of padding bits at the end of the string
	 */
	public void PSFBPktRefPictureSelIndic(
			long ssrcPacketSender,
			int rpsiPayloadType, byte[] rpsiBitString, int rpsiPaddingBits);
	
	/**
	 * This function is called when a 
	 * Transport Layer Feedback Messages is received
	 * 
	 * @param ssrcPacketSender
	 * @param alfBitString
	 */
	public void PSFBPktAppLayerFBReceived(
			long ssrcPacketSender,
			byte[] alfBitString);

	/**
	 * This function is called when a 
	 * Transport Layer Feedback Messages is received
	 * 
	 * @param ssrcPacketSender 
	 * @param FMT 1: NACK, 0,2-30: unassigned, 31: reserved
	 * @param packetID the RTP sequence number of the lost packet
	 * @param bitmaskLostPackets the bitmask of following lost packets
	 */
	public void RTPFBPktReceived(
			long ssrcPacketSender,
			int FMT, int[] packetID, int[] bitmaskLostPackets);
}