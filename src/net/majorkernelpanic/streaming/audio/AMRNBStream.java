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

package net.majorkernelpanic.streaming.audio;

import java.io.IOException;
import java.lang.reflect.Field;

import net.majorkernelpanic.streaming.rtp.AMRNBPacketizer;
import android.media.MediaRecorder;

/**
 * A class for streaming AMR-NB from the microphone of an android device using RTP.
 * Call {@link #setDestinationAddress(java.net.InetAddress)}, {@link #prepare()} & {@link #start()} and that's it !
 * Call {@link #stop()} to stop the stream.
 * Do not forget to call {@link #release()} when you're done.
 */
public class AMRNBStream extends AudioStream {

	public AMRNBStream() throws IOException {
		super();

		mPacketizer = new AMRNBPacketizer();

		setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
		
		try {
			// RAW_AMR was deprecated in API level 16.
			Field deprecatedName = MediaRecorder.OutputFormat.class.getField("RAW_AMR");
			setOutputFormat(deprecatedName.getInt(null));
		} catch (Exception e) {
			setOutputFormat(MediaRecorder.OutputFormat.AMR_NB);
		}
		
		setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
		setAudioSamplingRate(8000);
		
	}

	/**
	 * Returns a description of the stream using SDP. It can then be included in an SDP file.
	 */	
	public String generateSessionDescription() {
		return "m=audio "+String.valueOf(getDestinationPorts()[0])+" RTP/AVP 96\r\n" +
				"a=rtpmap:96 AMR/8000\r\n" +
				"a=fmtp:96 octet-align=1;\r\n";
	}

}
