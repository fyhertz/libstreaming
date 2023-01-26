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
import android.util.Log;

/**
 * 
 *   RFC 3267.
 *   
 *   AMR Streaming over RTP.
 *   
 *   Must be fed with an InputStream containing raw AMR NB
 *   Stream must begin with a 6 bytes long header: "#!AMR\n", it will be skipped
 *   
 */
public class AMRNBPacketizer extends AbstractPacketizer implements Runnable {

	public final static String TAG = "AMRNBPacketizer";

	private final int AMR_HEADER_LENGTH = 6; // "#!AMR\n"
	private static final int AMR_FRAME_HEADER_LENGTH = 1; // Each frame has a short header
	private static final int[] sFrameBits = {95, 103, 118, 134, 148, 159, 204, 244};
	private int samplingRate = 8000;

	private Thread t;

	public AMRNBPacketizer() {
		super();
		socket.setClockFrequency(samplingRate);
	}

	public void start() {
		if (t==null) {
			t = new Thread(this);
			t.start();
		}
	}

	public void stop() {
		if (t != null) {
			try {
				is.close();
			} catch (IOException ignore) {}
			t.interrupt();
			try {
				t.join();
			} catch (InterruptedException e) {}
			t = null;
		}
	}

	public void run() {

		int frameLength, frameType;
		long now = System.nanoTime(), oldtime = now;
		byte[] header = new byte[AMR_HEADER_LENGTH];

		try {

			// Skip raw AMR header
			fill(header,0,AMR_HEADER_LENGTH);
			
			if (header[5] != '\n') {
				Log.e(TAG,"Bad header ! AMR not correcty supported by the phone !");
				return;
			}

			while (!Thread.interrupted()) {

				buffer = socket.requestBuffer();
				buffer[rtphl] = (byte) 0xF0;
				
				// First we read the frame header
				fill(buffer, rtphl+1,AMR_FRAME_HEADER_LENGTH);

				// Then we calculate the frame payload length
				frameType = (Math.abs(buffer[rtphl + 1]) >> 3) & 0x0f;
				frameLength = (sFrameBits[frameType]+7)/8;

				// And we read the payload
				fill(buffer, rtphl+2,frameLength);

				//Log.d(TAG,"Frame length: "+frameLength+" frameType: "+frameType);

				// RFC 3267 Page 14: "For AMR, the sampling frequency is 8 kHz"
				// FIXME: Is this really always the case ??
				ts += 160L*1000000000L/samplingRate; //stats.average();
				socket.updateTimestamp(ts);
				socket.markNextPacket();

				//Log.d(TAG,"expected: "+ expected + " measured: "+measured);
				
				send(rtphl+1+AMR_FRAME_HEADER_LENGTH+frameLength);
				
			}

		} catch (IOException e) { 
		} catch (InterruptedException e) {}

		Log.d(TAG,"AMR packetizer stopped !");

	}

	private int fill(byte[] buffer, int offset,int length) throws IOException {
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
