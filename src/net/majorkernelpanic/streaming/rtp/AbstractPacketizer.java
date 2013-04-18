/*
 * Copyright (C) 2011 GUIGUI Simon, fyhertz@gmail.com
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
import java.io.InputStream;
import java.net.InetAddress;
import java.util.Random;

import net.majorkernelpanic.streaming.rtcp.SenderReport;

/**
 * 
 * Each packetizer inherits from this one and therefore uses RTP and UDP.
 *
 */
abstract public class AbstractPacketizer {

	protected static final int rtphl = RtpSocket.RTP_HEADER_LENGTH;

	protected RtpSocket socket = null;
	protected SenderReport report = null;
	protected InputStream is = null;
	protected byte[] buffer;
	protected long ts = 0;

	public AbstractPacketizer() throws IOException {
		int ssrc = new Random().nextInt();
		ts = new Random().nextInt();
		socket = new RtpSocket();
		report = new SenderReport();
		socket.setSSRC(ssrc);
		report.setSSRC(ssrc);
		buffer = socket.getBuffer();
	}

	public RtpSocket getRtpSocket() {
		return socket;
	}

	public SenderReport getRtcpSocket() {
		return report;
	}


	public void setSSRC(int ssrc) {
		socket.setSSRC(ssrc);
		report.setSSRC(ssrc);
	}

	public int getSSRC() {
		return socket.getSSRC();
	}

	public void setInputStream(InputStream is) {
		this.is = is;
	}

	public void setTimeToLive(int ttl) throws IOException {
		socket.setTimeToLive(ttl);
	}

	/**
	 * Sets the destination of the stream.
	 * @param dest The destination address of the stream
	 * @param rtpPort Destination port that will be used for RTP
	 * @param rtcpPort Destination port that will be used for RTCP
	 */
	public void setDestination(InetAddress dest, int rtpPort, int rtcpPort) {
		socket.setDestination(dest, rtpPort);
		report.setDestination(dest, rtcpPort);		
	}

	public abstract void start() throws IOException;

	public abstract void stop();

	protected void send(int length) throws IOException {
		socket.send(length);
		report.update(length);
	}

	// Useful for debug
	protected static String printBuffer(byte[] buffer, int start,int end) {
		String str = "";
		for (int i=start;i<end;i++) str+=","+Integer.toHexString(buffer[i]&0xFF);
		return str;
	}

	protected static class Statistics {

		public final static int COUNT=50;
		private float m = 0, q = 0;

		public void push(long duration) {
			m = (m*q+duration)/(q+1);
			if (q<COUNT) q++;
		}

		public long average() {
			return (long)m;
		}

	}

}
