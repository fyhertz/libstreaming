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
	private static final int[] sFrameBits = {95, 103, 118, 134, 148, 159, 204, 244};

	private Thread t;

	public AMRNBPacketizer() throws IOException {
		super();
	}

	public void start() {
		if (t==null) {
			t = new Thread(this);
			t.start();
		}
	}

	public void stop() {
		try {
			is.close();
		} catch (IOException ignore) {}
		t.interrupt();
		t = null;
	}

	public void run() {

		int frameLength, frameType, delta = 10000;
		long now = SystemClock.elapsedRealtime(), oldtime = now, measured;
		long expected = 20, lastmeasured = 10000;

		try {

			// Skip raw amr header
			fill(rtphl,AMR_HEADER_LENGTH);
			
			if (buffer[rtphl+5] != '\n') {
				Log.e(TAG,"Bad header ! AMR not correcty supported by the phone !");
				return;
			}

			buffer[rtphl] = (byte) 0xF0;

			while (!Thread.interrupted()) {

				// First we read the frame header
				fill(rtphl+1,AMR_FRAME_HEADER_LENGTH);

				// Then we calculate the frame payload length
				frameType = (Math.abs(buffer[rtphl + 1]) >> 3) & 0x0f;
				frameLength = (sFrameBits[frameType]+7)/8;

				// And we read the payload
				fill(rtphl+2,frameLength);

				//Log.d(TAG,"Frame length: "+frameLength+" frameType: "+frameType);

				// RFC 3267 Page 14: "For AMR, the sampling frequency is 8 kHz"
				// FIXME: Is this really always the case ??
				ts += 160; 
				socket.updateTimestamp(ts);
				socket.markNextPacket();

				// We wait a little to avoid sending to many packets too quickly
				now = SystemClock.elapsedRealtime();
				measured = now-oldtime;
				delta += measured;
				oldtime = now;
				//Log.d(TAG,"expected: "+ expected + " measured: "+measured);
				measured -= lastmeasured<2*expected/3 ? 2*expected/3-lastmeasured : 0;
				lastmeasured = measured;
				if (measured<2*expected/3) {
					Thread.sleep( 2*expected/3-measured );
				}
				
				if (delta>5000) {
					delta = 0;
					report.setNtpTimestamp(now);
					report.setRtpTimestamp(ts);
					report.send();
				}
				
				send(rtphl+1+AMR_FRAME_HEADER_LENGTH+frameLength);
				
			}

		} catch (IOException e) { 
		} catch (InterruptedException e) {}

		Log.d(TAG,"AMR packetizer stopped !");

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
