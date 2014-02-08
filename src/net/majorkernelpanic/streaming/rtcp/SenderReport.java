/*
 * Copyright (C) 2011-2014 GUIGUI Simon, fyhertz@gmail.com
 * 
 * This file is part of libstreaming (https://github.com/fyhertz/libstreaming)
 * 
 * Spydroid is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package net.majorkernelpanic.streaming.rtcp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.channels.IllegalSelectorException;

import android.os.SystemClock;
import android.util.Log;

/**
 * Implementation of Sender Report RTCP packets.
 */
public class SenderReport {

	public static final int MTU = 1500;

	private MulticastSocket usock;
	private DatagramPacket upack;

	private byte[] buffer = new byte[MTU];
	private int ssrc, port = -1;
	private int octetCount = 0, packetCount = 0;
	private long interval, delta, now, oldnow;

	public SenderReport(int ssrc) throws IOException {
		super();
		this.ssrc = ssrc;
	}
	
	public SenderReport() {

		/*							     Version(2)  Padding(0)					 					*/
		/*									 ^		  ^			PT = 0	    						*/
		/*									 |		  |				^								*/
		/*									 | --------			 	|								*/
		/*									 | |---------------------								*/
		/*									 | ||													*/
		/*									 | ||													*/
		buffer[0] = (byte) Integer.parseInt("10000000",2);

		/* Packet Type PT */
		buffer[1] = (byte) 200;

		/* Byte 2,3          ->  Length		                     */
		setLong(28/4-1, 2, 4);

		/* Byte 4,5,6,7      ->  SSRC                            */
		/* Byte 8,9,10,11    ->  NTP timestamp hb				 */
		/* Byte 12,13,14,15  ->  NTP timestamp lb				 */
		/* Byte 16,17,18,19  ->  RTP timestamp		             */
		/* Byte 20,21,22,23  ->  packet count				 	 */
		/* Byte 24,25,26,27  ->  octet count			         */

		try {
			usock = new MulticastSocket();
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage());
		}
		upack = new DatagramPacket(buffer, 1);

		// By default we sent one report every 5 secconde
		interval = 3000;
		
	}

	public void close() {
		usock.close();
	}

	/**
	 * Sets the temporal interval between two RTCP Sender Reports.
	 * Default interval is set to 5 secondes.
	 * Set 0 to disable RTCP.
	 * @param interval The interval in milliseconds
	 */
	public void setInterval(long interval) {
		this.interval = interval;
	}	

	/** 
	 * Updates the number of packets sent, and the total amount of data sent.
	 * @param length The length of the packet 
	 * @throws IOException 
	 **/
	public void update(int length, long ntpts, long rtpts) throws IOException {
		packetCount += 1;
		octetCount += length;
		setLong(packetCount, 20, 24);
		setLong(octetCount, 24, 28);

		now = SystemClock.elapsedRealtime();
		delta += oldnow != 0 ? now-oldnow : 0;
		oldnow = now;
		if (interval>0) {
			if (delta>=interval) {
				// We send a Sender Report
				send(ntpts,rtpts);
				delta = 0;
			}
		}
		
	}

	public void setSSRC(int ssrc) {
		this.ssrc = ssrc; 
		setLong(ssrc,4,8);
		packetCount = 0;
		octetCount = 0;
		setLong(packetCount, 20, 24);
		setLong(octetCount, 24, 28);
	}

	public void setDestination(InetAddress dest, int dport) {
		port = dport;
		upack.setPort(dport);
		upack.setAddress(dest);
	}

	public int getPort() {
		return port;
	}

	public int getLocalPort() {
		return usock.getLocalPort();
	}

	public int getSSRC() {
		return ssrc;
	}

	/**
	 * Resets the reports (total number of bytes sent, number of packets sent, etc.)
	 */
	public void reset() {
		packetCount = 0;
		octetCount = 0;
		setLong(packetCount, 20, 24);
		setLong(octetCount, 24, 28);
		delta = now = oldnow = 0;
	}
	
	private void setLong(long n, int begin, int end) {
		for (end--; end >= begin; end--) {
			buffer[end] = (byte) (n % 256);
			n >>= 8;
		}
	}	

	/** Sends the RTCP packet over the network. */
	private void send(long ntpts, long rtpts) throws IOException {
		long hb = ntpts/1000000000;
		long lb = ( ( ntpts - hb*1000000000 ) * 4294967296L )/1000000000;
		setLong(hb, 8, 12);
		setLong(lb, 12, 16);
		setLong(rtpts, 16, 20);
		upack.setLength(28);
		usock.send(upack);		
	}
		
	
}
