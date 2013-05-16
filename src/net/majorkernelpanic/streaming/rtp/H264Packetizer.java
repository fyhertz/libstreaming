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

import android.util.Log;

/**
 * 
 *   RFC 3984.
 *   
 *   H.264 streaming over RTP.
 *   
 *   Must be fed with an InputStream containing H.264 NAL units preceded by their length (4 bytes).
 *   The stream must start with mpeg4 or 3gpp header, it will be skipped.
 *   
 */
public class H264Packetizer extends AbstractPacketizer implements Runnable{

	public final static String TAG = "H264Packetizer";

	private final static int MAXPACKETSIZE = 1400;

	private Thread t = null;
	private int naluLength = 0;
	private long delay = 0;
	private Statistics stats = new Statistics();

	public H264Packetizer() throws IOException {
		super();
	}

	public void start() throws IOException {
		if (t == null) {
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

		long duration = 0, oldtime = 0, sum = 0, start = 0, drift = 0, now, count = 0;
		boolean initoffset = false;

		// This will skip the MPEG4 header if this step fails we can't stream anything :(
		try {
			byte buffer[] = new byte[4];
			// Skip all atoms preceding mdat atom
			while (true) {
				while (is.read() != 'm');
				is.read(buffer,0,3);
				if (buffer[0] == 'd' && buffer[1] == 'a' && buffer[2] == 't') break;
			}
		} catch (IOException e) {
			Log.e(TAG,"Couldn't skip mp4 header :/");
			return;
		}

		start = System.nanoTime();
		
		// We read a NAL units from the input stream and we send them
		try {
			while (!Thread.interrupted()) {

				// We measure how long it takes to receive the NAL unit from the phone
				oldtime = System.nanoTime();
				send();
				duration = System.nanoTime() - oldtime;

				delta += duration/1000000;
				if (intervalBetweenReports>0) {
					if (delta>=intervalBetweenReports) {
						// We send a Sender Report
						report.send(oldtime+duration,ts);
					}
				}
				
				count += duration/1000000;
				if (count>=7000) {
					// We estimate the drift of the steam
					now = System.nanoTime();
					if (!initoffset || (now - start < 0)) {
						sum = (now - start);
						initoffset = true;
					}
					drift = (now - start) - sum;
					count = 0;
					//Log.d(TAG, "sum1: "+sum/1000000+" sum2: "+(now-start)/1000000+" drift: "+drift/1000000);
				}
								
				
				if (drift!=0) {
					// Compensates if the stream is drifting
					stats.push(duration+drift);
					drift = 0;
				} else stats.push(duration);
				// Computes the average duration of a NAL unit
				delay = stats.average();
				sum += delay;

			}
		} catch (IOException e) {
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		Log.d(TAG,"H264 packetizer stopped !");

	}

	// Reads a NAL unit in the FIFO and sends it
	// If it is too big, we split it in FU-A units (RFC 3984)
	private void send() throws IOException, InterruptedException {
		int sum = 1, len = 0, type;

		// Read NAL unit length (4 bytes)
		fill(rtphl,4);
		naluLength = buffer[rtphl+3]&0xFF | (buffer[rtphl+2]&0xFF)<<8 | (buffer[rtphl+1]&0xFF)<<16 | (buffer[rtphl]&0xFF)<<24;

		// Read NAL unit header (1 byte)
		fill(rtphl, 1);
		// NAL unit type
		type = buffer[rtphl]&0x1F;

		ts += delay*9/100000;
		socket.updateTimestamp(ts);

		//Log.d(TAG,"- Nal unit length: " + naluLength + " delay: "+delay+" type: "+type);

		// Small NAL unit => Single NAL unit 
		if (naluLength<=MAXPACKETSIZE-rtphl-2) {
			len = fill(rtphl+1,  naluLength-1  );
			socket.markNextPacket();
			super.send(naluLength+rtphl);
			//Log.d(TAG,"----- Single NAL unit - len:"+len+" header:"+printBuffer(rtphl,rtphl+3)+" delay: "+delay+" newDelay: "+newDelay);
		}
		// Large NAL unit => Split nal unit 
		else {

			// Set FU-A header
			buffer[rtphl+1] = (byte) (buffer[rtphl] & 0x1F);  // FU header type
			buffer[rtphl+1] += 0x80; // Start bit
			// Set FU-A indicator
			buffer[rtphl] = (byte) ((buffer[rtphl] & 0x60) & 0xFF); // FU indicator NRI
			buffer[rtphl] += 28;

			while (sum < naluLength) {
				if ((len = fill(rtphl+2,  naluLength-sum > MAXPACKETSIZE-rtphl-2 ? MAXPACKETSIZE-rtphl-2 : naluLength-sum  ))<0) return; sum += len;
				// Last packet before next NAL
				if (sum >= naluLength) {
					// End bit on
					buffer[rtphl+1] += 0x40;
					socket.markNextPacket();
				}
				super.send(len+rtphl+2);
				// Switch start bit
				buffer[rtphl+1] = (byte) (buffer[rtphl+1] & 0x7F); 
				//Log.d(TAG,"----- FU-A unit, sum:"+sum);
			}
		}
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