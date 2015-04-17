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

package net.majorkernelpanic.streaming.rtp;

import java.io.IOException;

import android.annotation.SuppressLint;
import android.media.MediaCodec.BufferInfo;
import android.os.SystemClock;
import android.util.Log;

/**
 * RFC 3640.  
 * 
 * Encapsulates AAC Access Units in RTP packets as specified in the RFC 3640.
 * This packetizer is used by the AACStream class in conjunction with the 
 * MediaCodec API introduced in Android 4.1 (API Level 16).       
 * 
 */
@SuppressLint("NewApi")
public class AACLATMPacketizer extends AbstractPacketizer implements Runnable {

	private final static String TAG = "AACLATMPacketizer";

	private Thread t;

	public AACLATMPacketizer() {
		super();
		socket.setCacheSize(0);
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

	public void setSamplingRate(int samplingRate) {
		socket.setClockFrequency(samplingRate);
	}

	@SuppressLint("NewApi")
	public void run() {

		Log.d(TAG,"AAC LATM packetizer started !");

		int length = 0;
		long oldts;
		BufferInfo bufferInfo;

		try {
			while (!Thread.interrupted()) {
				buffer = socket.requestBuffer();
				length = is.read(buffer, rtphl+4, MAXPACKETSIZE-(rtphl+4));
				
				if (length>0) {
					
					bufferInfo = ((MediaCodecInputStream)is).getLastBufferInfo();
					//Log.d(TAG,"length: "+length+" ts: "+bufferInfo.presentationTimeUs);
					oldts = ts;
					ts = bufferInfo.presentationTimeUs*1000;
					
					// Seems to happen sometimes
					if (oldts>ts) {
						socket.commitBuffer();
						continue;
					}
					
					socket.markNextPacket();
					socket.updateTimestamp(ts);
					
					// AU-headers-length field: contains the size in bits of a AU-header
					// 13+3 = 16 bits -> 13bits for AU-size and 3bits for AU-Index / AU-Index-delta 
					// 13 bits will be enough because ADTS uses 13 bits for frame length
					buffer[rtphl] = 0;
					buffer[rtphl+1] = 0x10; 

					// AU-size
					buffer[rtphl+2] = (byte) (length>>5);
					buffer[rtphl+3] = (byte) (length<<3);

					// AU-Index
					buffer[rtphl+3] &= 0xF8;
					buffer[rtphl+3] |= 0x00;
					
					send(rtphl+length+4);
					
				} else {
					socket.commitBuffer();
				}		
				
			}
		} catch (IOException e) {
		} catch (ArrayIndexOutOfBoundsException e) {
			Log.e(TAG,"ArrayIndexOutOfBoundsException: "+(e.getMessage()!=null?e.getMessage():"unknown error"));
			e.printStackTrace();
		} catch (InterruptedException ignore) {}

		Log.d(TAG,"AAC LATM packetizer stopped !");

	}

}
