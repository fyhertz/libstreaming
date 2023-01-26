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
 *   RFC 4629.
 * 
 *   H.263 Streaming over RTP.
 *   
 *   Must be fed with an InputStream containing H.263 frames.
 *   The stream must start with mpeg4 or 3gpp header, it will be skipped.
 *   
 */
public class H263Packetizer extends AbstractPacketizer implements Runnable {

	public final static String TAG = "H263Packetizer";
	private Statistics stats = new Statistics();

	private Thread t;

	public H263Packetizer() {
		super();
		socket.setClockFrequency(90000);
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
		long time, duration = 0;
		int i = 0, j = 0, tr;
		boolean firstFragment = true;
		byte[] nextBuffer;
		stats.reset();

		try { 
			while (!Thread.interrupted()) {
				
				if (j==0) buffer = socket.requestBuffer();
				socket.updateTimestamp(ts);
				
				// Each packet we send has a two byte long header (See section 5.1 of RFC 4629)
				buffer[rtphl] = 0;
				buffer[rtphl+1] = 0;
				
				time = System.nanoTime();
				if (fill(rtphl+j+2,MAXPACKETSIZE-rtphl-j-2)<0) return;
				duration += System.nanoTime() - time;
				j = 0;
				// Each h263 frame starts with: 0000 0000 0000 0000 1000 00??
				// Here we search where the next frame begins in the bit stream
				for (i=rtphl+2;i<MAXPACKETSIZE-1;i++) {
					if (buffer[i]==0 && buffer[i+1]==0 && (buffer[i+2]&0xFC)==0x80) {
						j=i;
						break;
					}
				}
				// Parse temporal reference
				tr = (buffer[i+2]&0x03)<<6 | (buffer[i+3]&0xFF)>>2;
				//Log.d(TAG,"j: "+j+" buffer: "+printBuffer(rtphl, rtphl+5)+" tr: "+tr);
				if (firstFragment) {
					// This is the first fragment of the frame -> header is set to 0x0400
					buffer[rtphl] = 4;
					firstFragment = false;
				} else {
					buffer[rtphl] = 0;
				}
				if (j>0) {
					// We have found the end of the frame
					stats.push(duration);
					ts+= stats.average(); duration = 0;
					//Log.d(TAG,"End of frame ! duration: "+stats.average());
					// The last fragment of a frame has to be marked
					socket.markNextPacket();
					send(j);
					nextBuffer = socket.requestBuffer();
					System.arraycopy(buffer,j+2,nextBuffer,rtphl+2,MAXPACKETSIZE-j-2);
					buffer = nextBuffer;
					j = MAXPACKETSIZE-j-2;
					firstFragment = true;
				} else {
					// We have not found the beginning of another frame
					// The whole packet is a fragment of a frame
					send(MAXPACKETSIZE);
				}
			}
		} catch (IOException e) { 
		} catch (InterruptedException e) {}

		Log.d(TAG,"H263 Packetizer stopped !");

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
