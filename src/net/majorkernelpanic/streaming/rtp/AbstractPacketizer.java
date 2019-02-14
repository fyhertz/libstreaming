/*
 * Copyright (C) 2011-2015 GUIGUI Simon, fyhertz@gmail.com
 *
 * This file is part of libstreaming (https://github.com/fyhertz/libstreaming)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.majorkernelpanic.streaming.rtp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
	
	// Maximum size of RTP packets
	protected final static int MAXPACKETSIZE = RtpSocket.MTU-28;

	protected RtpSocket socket = null;
	protected InputStream is = null;
	protected byte[] buffer;
	
	protected long ts = 0;

	public AbstractPacketizer() {
		int ssrc = new Random().nextInt();
		ts = new Random().nextInt();
		socket = new RtpSocket();
		socket.setSSRC(ssrc);
	}

	public RtpSocket getRtpSocket() {
		return socket;
	}

	public void setSSRC(int ssrc) {
		socket.setSSRC(ssrc);
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
		socket.setDestination(dest, rtpPort, rtcpPort);		
	}

	/** Starts the packetizer. */
	public abstract void start();

	/** Stops the packetizer. */
	public abstract void stop();

	/** Updates data for RTCP SR and sends the packet. */
	protected void send(int length) throws IOException {
		socket.commitBuffer(length);
	}

	/** For debugging purposes. */
	protected static String printBuffer(byte[] buffer, int start,int end) {
		String str = "";
		for (int i=start;i<end;i++) str+=","+Integer.toHexString(buffer[i]&0xFF);
		return str;
	}

	/** Used in packetizers to estimate timestamps in RTP packets. */
	protected static class Statistics {

		public final static String TAG = "Statistics";
		
		private int count=700, c = 0;
		private float m = 0, q = 0;
		private long elapsed = 0;
		private long start = 0;
		private long duration = 0;
		private long period = 10000000000L;
		private boolean initoffset = false;
		
		public Statistics() {}
		
		public Statistics(int count, int period) {
			this.count = count;
			this.period = period;
		}
		
		public void reset() {
			initoffset = false;
			q = 0; m = 0; c = 0;
			elapsed = 0;
			start = 0;
			duration = 0;
		}
		
		public void push(long value) {
			elapsed += value;
			if (elapsed>period) {
				elapsed = 0;
				long now = System.nanoTime();
				if (!initoffset || (now - start < 0)) {
					start = now;
					duration = 0;
					initoffset = true;
				}
				// Prevents drifting issues by comparing the real duration of the 
				// stream with the sum of all temporal lengths of RTP packets. 
				value += (now - start) - duration;
				//Log.d(TAG, "sum1: "+duration/1000000+" sum2: "+(now-start)/1000000+" drift: "+((now-start)-duration)/1000000+" v: "+value/1000000);
			}
			if (c<5) {
				// We ignore the first 20 measured values because they may not be accurate
				c++;
				m = value;
			} else {
				m = (m*q+value)/(q+1);
				if (q<count) q++;
			}
		}
		
		public long average() {
			long l = (long)m;
			duration += l;
			return l;
		}

	}
	
}
