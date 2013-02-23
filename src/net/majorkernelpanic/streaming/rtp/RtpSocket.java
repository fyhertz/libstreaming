/*
 * Copyright (C) 2011-2013 GUIGUI Simon, fyhertz@gmail.com
 * 
 * This file is part of Spydroid (http://code.google.com/p/spydroid-ipcamera/)
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

package net.majorkernelpanic.streaming.rtp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Random;

/**
 * A basic implementation of an RTP socket.
 */
public class RtpSocket {

	private MulticastSocket usock;
	private DatagramPacket upack;

	private byte[] buffer = new byte[MTU];
	private int seq = 0;
	private boolean upts = false;
	private int ssrc;
	private int port = -1;

	public static final int RTP_HEADER_LENGTH = 12;
	public static final int MTU = 1500;


	public RtpSocket() throws IOException {

		/*							     Version(2)  Padding(0)					 					*/
		/*									 ^		  ^			Extension(0)						*/
		/*									 |		  |				^								*/
		/*									 | --------				|								*/
		/*									 | |---------------------								*/
		/*									 | ||  -----------------------> Source Identifier(0)	*/
		/*									 | ||  |												*/
		buffer[0] = (byte) Integer.parseInt("10000000",2);

		/* Payload Type */
		buffer[1] = (byte) 96;

		/* Byte 2,3        ->  Sequence Number                   */
		/* Byte 4,5,6,7    ->  Timestamp                         */
		/* Byte 8,9,10,11  ->  Sync Source Identifier            */
		setLong((ssrc=(new Random()).nextInt()),8,12);

		usock = new MulticastSocket();
		upack = new DatagramPacket(buffer, 1);

	}

	public void close() {
		usock.close();
	}

	public void setSSRC(int ssrc) {
		this.ssrc = ssrc; 
		setLong(ssrc,8,12);
	}

	public int getSSRC() {
		return ssrc;
	}

	public void setTimeToLive(int ttl) throws IOException {
		usock.setTimeToLive(ttl);
	}

	public void setDestination(InetAddress dest, int dport) {
		port = dport;
		upack.setPort(dport);
		upack.setAddress(dest);
	}

	/** Returns the buffer that you can directly modify before calling send. */
	public byte[] getBuffer() {
		return buffer;
	}

	public int getPort() {
		return port;
	}

	public int getLocalPort() {
		return usock.getLocalPort();
	}

	/** Sends the RTP packet over the network. */
	public void send(int length) throws IOException {

		updateSequence();
		upack.setLength(length);
		usock.send(upack);

		if (upts) {
			upts = false;
			buffer[1] -= 0x80;
		}

	}

	/** Increments the sequence number. */
	private void updateSequence() {
		setLong(++seq, 2, 4);
	}

	/** 
	 * Overwrites the timestamp in the packet.
	 * @param timestamp The new timestamp
	 **/
	public void updateTimestamp(long timestamp) {
		setLong(timestamp, 4, 8);
	}

	public void markNextPacket() {
		upts = true;
		buffer[1] += 0x80; // Mark next packet
	}

	private void setLong(long n, int begin, int end) {
		for (end--; end >= begin; end--) {
			buffer[end] = (byte) (n % 256);
			n >>= 8;
		}
	}	

}
