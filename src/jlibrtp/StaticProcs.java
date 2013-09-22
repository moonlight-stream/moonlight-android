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
 * Generic functions for converting between unsigned integers and byte[]s.
 *
 * @author Arne Kepp
 */
public class StaticProcs {

	/** 
	 * Converts an integer into an array of bytes. 
	 * Primarily used for 16 bit unsigned integers, ignore the first two octets.
	 * 
	 * @param i a 16 bit unsigned integer in an int
	 * @return byte[2] representing the integer as unsigned, most significant bit first. 
	 */
	public static byte[] uIntIntToByteWord(int i) {		
		byte[] byteWord = new byte[2];
		byteWord[0] = (byte) ((i >> 8) & 0x000000FF);
		byteWord[1] = (byte) (i & 0x00FF);
		return byteWord;
	}
	
	/** 
	 * Converts an unsigned 32 bit integer, stored in a long, into an array of bytes.
	 * 
	 * @param j a long
	 * @return byte[4] representing the unsigned integer, most significant bit first. 
	 */
	public static byte[] uIntLongToByteWord(long j) {
		int i = (int) j;
		byte[] byteWord = new byte[4];
		byteWord[0] = (byte) ((i >>> 24) & 0x000000FF);
		byteWord[1] = (byte) ((i >> 16) & 0x000000FF);
		byteWord[2] = (byte) ((i >> 8) & 0x000000FF);
		byteWord[3] = (byte) (i & 0x00FF);
		return byteWord;
	}
	
	/** 
	 * Combines two bytes (most significant bit first) into a 16 bit unsigned integer.
	 * 
	 * @param index of most significant byte
	 * @return int with the 16 bit unsigned integer
	 */
	public static int bytesToUIntInt(byte[] bytes, int index) {
		int accum = 0;
		int i = 1;
		for (int shiftBy = 0; shiftBy < 16; shiftBy += 8 ) {
			accum |= ( (long)( bytes[index + i] & 0xff ) ) << shiftBy;
			i--;
		}
		return accum;
	}
	
	/** 
	 * Combines four bytes (most significant bit first) into a 32 bit unsigned integer.
	 * 
	 * @param bytes
	 * @param index of most significant byte
	 * @return long with the 32 bit unsigned integer
	 */
	public static long bytesToUIntLong(byte[] bytes, int index) {
		long accum = 0;
		int i = 3;
		for (int shiftBy = 0; shiftBy < 32; shiftBy += 8 ) {
			accum |= ( (long)( bytes[index + i] & 0xff ) ) << shiftBy;
			i--;
		}
		return accum;
	}
	
	/**
	 * Converts an arbitrary number of bytes, assumed to represent an unsigned integer,
	 * to a Java long
	 */
	/*public static long bytesToUintLong(byte[] bytes, int firstByte, int lastByte) {
		long accum = 0;
		int i = lastByte - firstByte;
		if(i > 7) {
			System.out.println("!!!! StaticProcs.bytesToUintLong() Can't convert more than 63 bits!");
			return -1;
		}
		int stop = (i+1)*8;
		
		for (int shiftBy = 0; shiftBy < stop; shiftBy += 8 ) {
			accum |= ( (long)( bytes[firstByte + i] & 0xff ) ) << shiftBy;
			i--;
		}
		return accum;
	}*/
	
	/**
	 * Converts an arbitrary number of bytes, assumed to represent an unsigned integer,
	 * to a Java int
	 */
	/*	public static int bytesToUintInt(byte[] bytes, int firstByte, int lastByte) {
		int accum = 0;
		int i = lastByte - firstByte;
		if(i > 3) {
			System.out.println("!!!! StaticProcs.bytesToUintLong() Can't convert more than 31 bits!");
			return -1;
		}
		int stop = (i+1)*8;
		
		for (int shiftBy = 0; shiftBy < stop; shiftBy += 8 ) {
			accum |= ( (long)( bytes[firstByte + i] & 0xff ) ) << shiftBy;
			i--;
		}
		return accum;
	}*/
	
	/**
	 * Recreates a UNIX timestamp based on the NTP representation used
	 * in RTCP SR packets
	 * 
	 * @param ntpTs1 from RTCP SR packet
	 * @param ntpTs2 from RTCP SR packet
	 * @return the UNIX timestamp
	 */
	public static long undoNtpMess(long ntpTs1, long ntpTs2) {		
		long timeVal = (ntpTs1 - 2208988800L)*1000;
			
		double tmp = (1000.0*(double)ntpTs2)/((double)4294967295L);
		long ms = (long) tmp;
		//System.out.println(" timeVal: " +Long.toString(timeVal)+ " ms " + Long.toString(ms));
		timeVal += ms;
		
		return timeVal;
	}
	

	
	/** 
	 * Get the bits of a byte
	 * 
	 * @param aByte the byte you wish to convert
	 * @return a String of 1's and 0's
	 */
	public static String bitsOfByte(byte aByte) {
		int temp;
		String out = "";
		for(int i=7; i>=0; i--) {
			temp = (aByte >>> i);
			temp &= 0x0001;
			out += (""+temp);
		}
		return out;
	}
	
	/** 
	 * Get the hex representation of a byte
	 * 
	 * @param aByte the byte you wish to convert
	 * @return a String of two chars 0-1,A-F
	 */
	public static String hexOfByte(byte aByte) {
		String out = "";

		for(int i=0; i<2; i++) {
			int temp = (int) aByte;
			if(temp < 0) {
				temp +=256;
			}
			if(i == 0) {
				temp = temp/16;
			} else {
				temp = temp%16;
			}
			
			if( temp > 9) {
				switch(temp) {
				case 10: out += "A"; break;
				case 11: out += "B"; break;
				case 12: out += "C"; break;
				case 13: out += "D"; break;
				case 14: out += "E"; break;
				case 15: out += "F"; break;
				}
			} else {
				out += Integer.toString(temp);
			}
		}
		return out;
	}
	
	/** 
	 * Get the hex representation of a byte
	 * 
	 * @param hex 4 bytes  the byte you wish to convert
	 * @return a String of two chars 0-1,A-F
	 */
	public static byte byteOfHex(byte[] hex) {
		byte retByte = 0;
		Byte tmp;
		int val = 0;
		
		// First 4 bits
		tmp = hex[0];
		val = tmp.intValue();
		if(val > 64) {
			// Letter
			val -= 55;
		} else {
			// Number
			val -= 48;
		}
		retByte = ((byte) (val << 4));
		
		// Last 4 bits
		tmp = hex[1];
		val = tmp.intValue();
		if(val > 64) {
			// Letter
			val -= 55;
		} else {
			// Number
			val -= 48;
		}
		retByte |= ((byte) val);
		
		return retByte;
	}
	
	
	/** 
	 * Print the bits of a byte to standard out. For debugging.
	 * 
	 * @param aByte the byte you wish to print out.
	 */
	public static void printBits(byte aByte) {
		int temp;
		for(int i=7; i>=0; i--) {
			temp = (aByte >>> i);
			temp &= 0x0001;
			System.out.print(""+temp);
		}
		System.out.println();
	}
	
	public static String bitsOfBytes(byte[] bytes) {
		String str = "";
		//Expensive, but who cares
		for(int i=0; i<bytes.length; i++ ) {
			str += bitsOfByte(bytes[i]) + " ";
			if((i + 1) % 4 == 0)
				str += "\n";
		}
		
		return str;
	}
}