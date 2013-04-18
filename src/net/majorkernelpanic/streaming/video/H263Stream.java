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

import net.majorkernelpanic.streaming.rtp.H263Packetizer;
import android.hardware.Camera.CameraInfo;
import android.media.MediaRecorder;

/**
 * A class for streaming H.263 from the camera of an android device using RTP.
 * Call {@link #setDestinationAddress(java.net.InetAddress)}, {@link #setDestinationPorts(int)}, 
 * {@link #setVideoSize(int, int)}, {@link #setVideoFramerate(int)} and {@link #setVideoEncodingBitrate(int)} and you're good to go.
 * You can then call {@link #prepare()} & {@link #start()}.
 * Call {@link #stop()} to stop the stream.
 * Finally, do not forget to call {@link #release()} when you're done.
 */
public class H263Stream extends VideoStream {

	/**
	 * Constructs the H.263 stream.
	 * Uses CAMERA_FACING_BACK by default.
	 * @throws IOException
	 */
	public H263Stream() throws IOException {
		this(CameraInfo.CAMERA_FACING_BACK);
	}	
		
	/**
	 * Constructs the H.263 stream.
	 * @param cameraId Can be either CameraInfo.CAMERA_FACING_BACK or CameraInfo.CAMERA_FACING_FRONT 
	 * @throws IOException
	 */	
	public H263Stream(int cameraId) throws IOException {
		super(cameraId);
		setVideoEncoder(MediaRecorder.VideoEncoder.H263);
		mPacketizer = new H263Packetizer();
	}

	/**
	 * Returns a description of the stream using SDP. It can then be included in an SDP file.
	 */
	public String generateSessionDescription() throws IllegalStateException,
	IOException {

		return "m=video "+String.valueOf(getDestinationPorts()[0])+" RTP/AVP 96\r\n" +
				"a=rtpmap:96 H263-1998/90000\r\n";

	}

}
