package com.limelight.nvstream.wol;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Scanner;

import com.limelight.LimeLog;
import com.limelight.nvstream.http.ComputerDetails;

public class WakeOnLanSender {
	private static final int[] PORTS_TO_TRY = new int[] {
		7, 9, // Standard WOL ports
		47998, 47999, 48000, 48002, 48010 // Ports opened by GFE
	};
	
	public static void sendWolPacket(ComputerDetails computer) throws IOException {
		DatagramSocket sock = new DatagramSocket(0);
		byte[] payload = createWolPayload(computer);
		IOException lastException = null;
		boolean sentWolPacket = false;

		try {
			// Try all resolved remote and local addresses and IPv4 broadcast address.
			// The broadcast address is required to avoid stale ARP cache entries
			// making the sleeping machine unreachable.
			for (String unresolvedAddress : new String[] {
					computer.localAddress, computer.remoteAddress, computer.manualAddress, computer.ipv6Address, "255.255.255.255"
			}) {
                if (unresolvedAddress == null) {
                    continue;
                }

				try {
					for (InetAddress resolvedAddress : InetAddress.getAllByName(unresolvedAddress)) {
						// Try all the ports for each resolved address
						for (int port : PORTS_TO_TRY) {
							DatagramPacket dp = new DatagramPacket(payload, payload.length);
							dp.setAddress(resolvedAddress);
							dp.setPort(port);
							sock.send(dp);
							sentWolPacket = true;
						}
					}
				} catch (IOException e) {
					// We may have addresses that don't resolve on this subnet,
					// but don't throw and exit the whole function if that happens.
					// We'll throw it at the end if we didn't send a single packet.
					e.printStackTrace();
					lastException = e;
				}
			}
		} finally {
			sock.close();
		}

		// Propagate the DNS resolution exception if we didn't
		// manage to get a single packet out to the host.
		if (!sentWolPacket && lastException != null) {
			throw lastException;
		}
	}
	
	private static byte[] macStringToBytes(String macAddress) {
		byte[] macBytes = new byte[6];
		@SuppressWarnings("resource")
		Scanner scan = new Scanner(macAddress).useDelimiter(":");
		for (int i = 0; i < macBytes.length && scan.hasNext(); i++) {
			try {
				macBytes[i] = (byte) Integer.parseInt(scan.next(), 16);
			} catch (NumberFormatException e) {
				LimeLog.warning("Malformed MAC address: "+macAddress+" (index: "+i+")");
				break;
			}
		}
		scan.close();
		return macBytes;
	}
	
	private static byte[] createWolPayload(ComputerDetails computer) {
		byte[] payload = new byte[102];
		byte[] macAddress = macStringToBytes(computer.macAddress);
		int i;
		
		// 6 bytes of FF
		for (i = 0; i < 6; i++) {
			payload[i] = (byte)0xFF;
		}
		
		// 16 repetitions of the MAC address
		for (int j = 0; j < 16; j++) {
			System.arraycopy(macAddress, 0, payload, i, macAddress.length);
			i += macAddress.length;
		}
		
		return payload;
	}
}
