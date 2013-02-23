/*
 * Copyright (C) 2011-2013 GUIGUI Simon, fyhertz@gmail.com
 * With the help of Havlena Petr, havlenapetr@gmail.com
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

import net.majorkernelpanic.streaming.rtp.AbstractPacketizer.Statistics;

import android.os.SystemClock;
import android.util.Log;

/**
 * 
 *   RFC 3267.
 *   
 *   AMR Streaming over RTP.
 *   
 *   Must be fed with an InputStream containing raw amr nb
 *   Stream must begin with a 6 bytes long header: "#!AMR\n", it will be skipped
 *   
 */
public class AMRNBPacketizer extends AbstractPacketizer implements Runnable {

	public final static String TAG = "AMRNBPacketizer";

	private final int AMR_HEADER_LENGTH = 6; // "#!AMR\n"
	private static final int AMR_FRAME_HEADER_LENGTH = 1; // Each frame has a short header

	private static final int[] sBitrates = {
		4750, 5150, 5900, 6700, 7400, 7950, 1020, 1220
	};
	private static final int[] sFrameBits = {95, 103, 118, 134, 148, 159, 204, 244};

	private Thread t;
	private Statistics stats = new Statistics();

	public AMRNBPacketizer() throws IOException {
		super();
	}

	public void start() {
		if (!running) {
			running = true;
			t = new Thread(this);
			t.start();
		}
	}

	public void stop() {
		try {
			is.close();
		} catch (IOException ignore) {}
		running = false;
		// We wait until the packetizer thread returns
		try {
			t.join();
		} catch (InterruptedException e) {}
	}

	public void run() {

		int frameLength, frameType;
		long ts=0, oldtime = SystemClock.elapsedRealtime(), now = oldtime;

		try {

			// Skip raw amr header
			fill(rtphl,AMR_HEADER_LENGTH);

			buffer[rtphl] = (byte) 0xF0;

			while (running) {

				// First we read the frame header
				fill(rtphl+1,AMR_FRAME_HEADER_LENGTH);

				// Then we calculate the frame payload length
				frameType = (Math.abs(buffer[rtphl + 1]) >> 3) & 0x0f;
				frameLength = (sFrameBits[frameType]+7)/8;

				// And we read the payload
				fill(rtphl+2,frameLength);

				//Log.d(TAG,"Frame length: "+frameLength+" frameType: "+frameType);

				// RFC 3267 Page 14: 
				// "For AMR, the sampling frequency is 8 kHz"
				now = SystemClock.elapsedRealtime();
				stats.push(now-oldtime);
				oldtime = now;
				ts += stats.average()*8;
				oldtime = now;
				socket.updateTimestamp(ts);
				socket.markNextPacket();

				socket.send(rtphl+1+AMR_FRAME_HEADER_LENGTH+frameLength);
			}

		} catch (IOException e) {
			running = false;
			Log.d(TAG,"IOException: "+(e.getMessage()!=null?e.getMessage():"unknown error"));
		}

		Log.d(TAG,"Packetizer stopped !");

	}


	private int fill(int offset,int length) throws IOException {

		int sum = 0, len;

		while (sum<length) {
			len = is.read(buffer, offset+sum, length-sum);
			if (len<0) {
				throw new IOException("End of stream");
			}
			else sum+=len;
		}

		return sum;

	}


}
