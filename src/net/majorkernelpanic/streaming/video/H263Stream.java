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

package net.majorkernelpanic.streaming.video;

import java.io.IOException;

import net.majorkernelpanic.streaming.MediaStream;
import net.majorkernelpanic.streaming.rtp.H263Packetizer;
import net.majorkernelpanic.streaming.rtp.H264Packetizer;
import android.content.Context;
import android.media.MediaRecorder;
import android.util.Log;

/**
 * A class for streaming H.263 from the camera of an android device using RTP.
 * Call setDestination() & setVideoSize() & setVideoFrameRate() & setVideoEncodingBitRate() and you're good to go.
 * You can then call prepare() & start().
 * Call stop() to stop the stream.
 * You don't need to call reset().
 */
public class H263Stream extends VideoStream {

	public H263Stream(int cameraId) throws IOException {
		super(cameraId);
		setVideoEncoder(MediaRecorder.VideoEncoder.H263);
		this.mPacketizer = new H263Packetizer();
	}

	/**
	 * Returns a description of the stream using SDP. It can then be included in an SDP file.
	 */
	public String generateSessionDescription() throws IllegalStateException,
	IOException {

		return "m=video "+String.valueOf(getDestinationPort())+" RTP/AVP 96\r\n" +
				"b=RR:0\r\n" +
				"a=rtpmap:96 H263-1998/90000\r\n";

	}

}
