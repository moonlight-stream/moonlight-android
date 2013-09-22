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
 * Validates the StaticProcs.
 * 
 * @author Arne Kepp
 *
 */
public class ValidateStaticProcs {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		long one = 100;
		long two = 1;
		long three = 9999000;
		
		byte aByte = (byte) 7;
		System.out.println("aByte.hex: " + StaticProcs.hexOfByte(aByte));
			
		//byte[] oneb = StaticProcs.longToByteWord(one);
		byte[] twob = StaticProcs.uIntLongToByteWord(two);
		//byte[] threeb = StaticProcs.longToByteWord(three);
		
		for(int i = 0; i< 4; i++) {
			StaticProcs.printBits(twob[i]);
		}
		//one = StaticProcs.combineBytes(oneb[0], oneb[1], oneb[2], oneb[3]);
		two = StaticProcs.bytesToUIntLong(twob,0);
		//three = StaticProcs.combineBytes(threeb[0], threeb[1], threeb[2], threeb[3]);
		
		System.out.println("  one " + one + "  two " + two + "  three " + three);
		
		twob = StaticProcs.uIntLongToByteWord(two);
		
		for(int i = 0; i< 4; i++) {
			StaticProcs.printBits(twob[i]);
		}
		
		byte[] bytes = new byte[2];
		int check = 0;
		for(int i=0; i< 65536; i++) {
			bytes = StaticProcs.uIntIntToByteWord(i);
			check = StaticProcs.bytesToUIntInt(bytes, 0);
			if(check != i) {
				System.out.println(" oops:" + check +" != "+ i);
				StaticProcs.printBits(bytes[0]);
				StaticProcs.printBits(bytes[1]);
			}
		}
		int a = 65534;
		bytes = StaticProcs.uIntIntToByteWord(a);
		StaticProcs.printBits(bytes[0]);
		StaticProcs.printBits(bytes[1]);
		check = StaticProcs.bytesToUIntInt(bytes, 0);
		System.out.println(check);
		
		byte[] arbytes = new byte[22];
		arbytes[13] = -127;
		arbytes[14] = 127;
		arbytes[15] = -1;
		arbytes[16] = 127;
		arbytes[17] = -127;
		System.out.println("arbitrary length:");
		StaticProcs.printBits(arbytes[14]);
		StaticProcs.printBits(arbytes[15]);
		StaticProcs.printBits(arbytes[16]);
		//long arbTest = StaticProcs.bytesToUintLong(arbytes, 14, 16);
		//byte[] reArBytes = StaticProcs.uIntLongToByteWord(arbTest);
		//System.out.println("arbitrary length recode: " + Long.toString(arbTest));
		//StaticProcs.printBits(reArBytes[0]);
		//StaticProcs.printBits(reArBytes[1]);
		//StaticProcs.printBits(reArBytes[2]);
		//StaticProcs.printBits(reArBytes[3]);
		
		byte[] tmp = new byte[4];
		tmp[0] = -127;
		tmp[1] = 127;
		tmp[2] = -49;
		tmp[3] = -1; 
		
		String str2 = "";
		for(int i=0; i<tmp.length; i++) {
			str2 += StaticProcs.hexOfByte(tmp[i]);
		}
		System.out.println(str2);
		
		byte temp2[] = str2.getBytes();
		byte temp4[] = new byte[temp2.length / 2];
		byte[] temp3 = new byte[2]; 
		
		for(int i=0; i<temp4.length; i++) {
			temp3[0] = temp2[i*2];
			temp3[1] = temp2[i*2+1];
			temp4[i] = StaticProcs.byteOfHex(temp3);
		}
		
		for(int i=0; i<tmp.length; i++) {
			if(tmp[i] == temp4[i]) {
				System.out.println("ok");
			} else {
				System.out.println("nope");
			}
		}
	}
}
