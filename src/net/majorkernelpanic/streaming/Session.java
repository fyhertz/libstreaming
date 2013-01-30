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

package net.majorkernelpanic.streaming;

import java.io.IOException;
import java.net.InetAddress;

import net.majorkernelpanic.streaming.audio.AACStream;
import net.majorkernelpanic.streaming.audio.AMRNBStream;
import net.majorkernelpanic.streaming.audio.GenericAudioStream;
import net.majorkernelpanic.streaming.video.H263Stream;
import net.majorkernelpanic.streaming.video.H264Stream;
import net.majorkernelpanic.streaming.video.VideoQuality;
import net.majorkernelpanic.streaming.video.VideoStream;
import android.hardware.Camera.CameraInfo;
import android.os.Handler;
import android.util.Log;
import android.view.SurfaceHolder;

/**
 * This class makes use of all the streaming package.
 * It represents a streaming session between a client and the phone.
 * A stream is designated by the word "track" in this class.
 * To add tracks to the session you need to call addVideoTrack() or addAudioTrack().
 */
public class Session {

	public final static String TAG = "Session";

	/** This message will be sent to the handler when streaming starts */ 
	public static final int MESSAGE_START = 0x03;
	
	/** This message will be sent to the handler when streaming stops */
	public static final int MESSAGE_STOP = 0x04;
	
	/** Can be used with addVideoTrack to add an H.264 encoded stream */
	public final static int VIDEO_H264 = 0x01;
	
	/** Can be used with addVideoTrack to add an H.263 encoded stream */
	public final static int VIDEO_H263 = 0x02;
	
	/** Can be used with addAudioTrack to add an AMR encoded stream */
	public final static int AUDIO_AMRNB = 0x03;
	
	/** Can be used with addAudioTrack to add an AAC encoded stream, only works with ICS */
	public final static int AUDIO_AAC = 0x05;
	
	/** Do not currently work, feel free to fix it :) */
	public final static int AUDIO_ANDROID_AMR = 0x04;

	/** Use this with setRoutingScheme for unicasting */
	public final static int UNICAST = 0x01;
	
	/** Use this with setRoutingScheme for multicasting */
	public final static int MULTICAST = 0x02;
	
	// Default configuration
	private static VideoQuality defaultVideoQuality = VideoQuality.defaultVideoQualiy.clone();
	private static int defaultVideoEncoder = VIDEO_H263, defaultAudioEncoder = AUDIO_AMRNB;
	private static int defaultCamera = CameraInfo.CAMERA_FACING_BACK;
	
	// Indicates if a session is already streaming audio or video
	private static Session sessionUsingTheCamera = null;
	private static Session sessionUsingTheMic = null;
	
	// The number of stream currently started on the phone
	private static int startedStreamCount = 0;
	
	// The number of tracks added to this session
	private int sessionTrackCount = 0;
	
	private static Handler handler;
	private static SurfaceHolder surfaceHolder;
	private static SurfaceHolder.Callback callback;
	private InetAddress origin, destination;
	private int routingScheme = Session.UNICAST;
	private int defaultTimeToLive = 64;
	private Stream[] streamList = new Stream[2];
	private long timestamp;
	
	/** 
	 * Creates a streaming session that can be customized by adding tracks.
	 * @param destination The destination address of the streams
	 * @param origin The origin address of the streams
	 */
	public Session(InetAddress origin, InetAddress destination) {
		this.destination = destination;
		this.origin = origin;
		// This timestamp is used in the session descriptor for the Origin parameter "o="
		this.timestamp = System.currentTimeMillis();
	}
	
	/** Sets a Handler that will receive messages like Session.MESSAGE_START or Session.MESSAGE_STOP. */
	public static void setHandler(Handler h) {
		handler = h;
	}	
	
	/** Sets default video stream quality, it will be used by addVideoTrack. */
	public static void setDefaultVideoQuality(VideoQuality quality) {
		defaultVideoQuality = quality;
	}
	
	/** Sets the default audio encoder, it will be used by addAudioTrack. */
	public static void setDefaultAudioEncoder(int encoder) {
		defaultAudioEncoder = encoder;
	}
	
	/** Sets the default video encoder, it will be used by addVideoTrack. */
	public static void setDefaultVideoEncoder(int encoder) {
		defaultVideoEncoder = encoder;
	}
	
	/** 
	 * Sets the Surface required by MediaRecorder to record video 
	 * and adds a callback to stop video streaming whenever Android
	 * wishes to destroy the surface.
	 * @param sh A SurfaceHolder wrapping a valid surface
	 **/
	public static void setSurfaceHolder(SurfaceHolder sh) {
		if (callback != null) surfaceHolder.removeCallback(callback);
		surfaceHolder = sh;
		callback = new SurfaceHolder.Callback() {
			public void surfaceChanged(SurfaceHolder holder, int format,
					int width, int height) {
			}
			public void surfaceCreated(SurfaceHolder holder) {
			}
			public void surfaceDestroyed(SurfaceHolder holder) {
				Log.d(TAG,"Surface destroyed !!");
				if (sessionUsingTheCamera != null) {
					sessionUsingTheCamera.stopAll();
				}
			}

		};

		surfaceHolder.addCallback(callback);
	}

	/**
	 * Sets the Surface required by MediaRecorder to record video.
	 * @param sh A SurfaceHolder wrapping a valid surface
	 * @param setACallback Whether or not you want Session to add a callback that will stop video streaming when the surface is destroyed
	 */
	public static void setSurfaceHolder(SurfaceHolder sh, boolean setACallback) {
		if (callback != null) surfaceHolder.removeCallback(callback);
		if (setACallback) setSurfaceHolder(sh);
		else surfaceHolder = sh;
	}
	
	/** 
	 * The destination address for all the streams of the session.
	 * This method will have no effect on already existing tracks
	 * @param destination The destination address
	 */
	public void setDestination(InetAddress destination) {
		this.destination =  destination;
	}
	
	/** 
	 * Defines the routing scheme that will be used for this session.
	 * You must call this method before adding tracks to the session.
	 * @param routingScheme Can be either Session.UNICAST or Session.MULTICAST
	 */
	public void setRoutingScheme(int routingScheme) {
		this.routingScheme = routingScheme;
	}
	
	/** 
	 * Set the TTL of all packets sent during the session.
	 * You must call this method before adding tracks to the session.
	 * @param ttl The Time To Live
	 */
	public void setTimeToLive(int ttl) {
		defaultTimeToLive = ttl;
	}
	
	/** 
	 * Add the default video track with default configuration.
	 * @throws IllegalStateException
	 * @throws IOException
	 */
	public void addVideoTrack() throws IllegalStateException, IOException {
		addVideoTrack(defaultVideoEncoder,defaultCamera,defaultVideoQuality,false);
	}
	
	/** 
	 * Add video track with specified quality and encoder. 
	 * @param encoder Can be either Session.VIDEO_H264 or Session.VIDEO_H263
	 * @param camera Can be either CameraInfo.CAMERA_FACING_BACK or CameraInfo.CAMERA_FACING_FRONT
	 * @param videoQuality Will determine the bitrate,framerate and resolution of the stream
	 * @param flash Set it to true to turn the flash on, if the phone has no flash, an exception IllegalStateException will be thrown
	 * @throws IllegalStateException
	 * @throws IOException
	 */
	public synchronized void addVideoTrack(int encoder, int camera, VideoQuality videoQuality, boolean flash) throws IllegalStateException, IOException {
		if (sessionUsingTheCamera != null) {
			if (sessionUsingTheCamera.routingScheme==UNICAST) throw new IllegalStateException("Camera already in use by another client");
			else {
				streamList[0] = sessionUsingTheCamera.streamList[0];
				sessionTrackCount++;
				return;
			}
		}
		Stream stream = null;
		VideoQuality.merge(videoQuality,defaultVideoQuality);

		switch (encoder) {
		case VIDEO_H264:
			Log.d(TAG,"Video streaming: H.264");
			stream = new H264Stream(camera);
			break;
		case VIDEO_H263:
			Log.d(TAG,"Video streaming: H.263");
			stream = new H263Stream(camera);
			break;
		}

		if (stream != null) {
			Log.d(TAG,"Quality is: "+videoQuality.resX+"x"+videoQuality.resY+"px "+videoQuality.framerate+"fps, "+videoQuality.bitrate+"bps");
			((VideoStream) stream).setVideoQuality(videoQuality);
			((VideoStream) stream).setPreviewDisplay(surfaceHolder.getSurface());
			((VideoStream) stream).setFlashState(flash);
			stream.setTimeToLive(defaultTimeToLive);
			stream.setDestination(destination, 5006);
			streamList[0] = stream;
			sessionUsingTheCamera = this;
			sessionTrackCount++;
		}
	}
	
	/** 
	 * Adds default audio track with default configuration. 
	 * @throws IOException 
	 */
	public void addAudioTrack() throws IOException {
		addAudioTrack(defaultAudioEncoder);
	}
	
	/** 
	 * Adds audio track with specified encoder. 
	 * @param encoder Can be either Session.AUDIO_AMRNB or Session.AUDIO_AAC
	 * @throws IOException
	 */
	public synchronized void addAudioTrack(int encoder) throws IOException {
		if (sessionUsingTheMic != null) {
			if (sessionUsingTheMic.routingScheme==UNICAST) throw new IllegalStateException("Microphone already in use by another client");
			else {
				streamList[1] = sessionUsingTheMic.streamList[1];
				sessionTrackCount++;
				return;
			}
		}
		Stream stream = null;

		switch (encoder) {
		case AUDIO_AMRNB:
			Log.d(TAG,"Audio streaming: AMR");
			stream = new AMRNBStream();
			break;
		case AUDIO_ANDROID_AMR:
			Log.d(TAG,"Audio streaming: GENERIC");
			stream = new GenericAudioStream();
			break;
		case AUDIO_AAC:
			if (Integer.parseInt(android.os.Build.VERSION.SDK)<14) throw new IllegalStateException("This phone does not support AAC :/");
			Log.d(TAG,"Audio streaming: AAC");
			stream = new AACStream();
			break;
		}

		if (stream != null) {
			stream.setTimeToLive(defaultTimeToLive);
			stream.setDestination(destination, 5004);
			streamList[1] = stream;
			sessionUsingTheMic = this;
			sessionTrackCount++;
		}
	}
	
	/** 
	 * Returns a Session Description that can be stored in a file or sent to a client with RTSP.
	 * @return The Session Description
	 * @throws IllegalStateException
	 * @throws IOException
	 */
	public synchronized String getSessionDescription() throws IllegalStateException, IOException {
		StringBuilder sessionDescription = new StringBuilder();
		sessionDescription.append("v=0\r\n");
		// The RFC 4566 (5.2) suggest to use an NTP timestamp here but we will simply use a UNIX timestamp
		// TODO: Add IPV6 support
		sessionDescription.append("o=- "+timestamp+" "+timestamp+" IN IP4 "+origin.getHostAddress()+"\r\n");
		sessionDescription.append("s=Unnamed\r\n");
		sessionDescription.append("i=N/A\r\n");
		sessionDescription.append("c=IN IP4 "+destination.getHostAddress()+"\r\n");
		// t=0 0 means the session is permanent (we don't know when it will stop)
		sessionDescription.append("t=0 0\r\n");
		sessionDescription.append("a=recvonly\r\n");
		// Prevents two different sessions from using the same peripheral at the same time
		for (int i=0;i<streamList.length;i++) {
			if (streamList[i] != null) {
				sessionDescription.append(streamList[i].generateSessionDescription());
				sessionDescription.append("a=control:trackID="+i+"\r\n");
			}
		}
		return sessionDescription.toString();
	}
	
	/**
	 * This method returns the selected routing scheme of the session.
	 * The routing scheme can be either Session.UNICAST or Session.MULTICAST.
	 * @return The routing sheme of the session
	 */
	public String getRoutingScheme() {
		return routingScheme==Session.UNICAST ? "unicast" : "multicast";
	}
	
	public InetAddress getDestination() {
		return destination;
	}

	/** Returns the number of tracks of this session. **/
	public int getTrackCount() {
		return sessionTrackCount;
	}
	
	/** Indicates whether or not a camera is being used in a session. **/
	public static boolean isCameraInUse() {
		return sessionUsingTheCamera!=null;
	}
	
	/** Indicates whether or not the microphone is being used in a session. **/
	public static boolean isMicrophoneInUse() {
		return sessionUsingTheMic!=null;
	}
	
	public boolean trackExists(int id) {
		return streamList[id]!=null;
	}
	
	public void setTrackDestinationPort(int id, int port) {
		streamList[id].setDestination(destination,port);
	}
	
	public int getTrackDestinationPort(int id) {
		return streamList[id].getDestinationPort();
	}

	public int getTrackLocalPort(int id) {
		return streamList[id].getLocalPort();
	}
	
	public int getTrackSSRC(int id) {
		return streamList[id].getSSRC();
	}
	
	/** Starts stream with id trackId. */
	public synchronized void start(int trackId) throws IllegalStateException, IOException {
		Stream stream = streamList[trackId];
		if (stream!=null && !stream.isStreaming()) {
			stream.prepare();
			stream.start();
			if (++startedStreamCount==1) handler.obtainMessage(Session.MESSAGE_START).sendToTarget();
		}
	}

	/** Starts existing streams. */
	public void startAll() throws IllegalStateException, IOException {
		for (int i=0;i<streamList.length;i++) {
			start(i);
		}
	}

	/** Stops existing streams. */
	public synchronized void stopAll() {
		for (int i=0;i<streamList.length;i++) {
			if (streamList[i] != null && streamList[i].isStreaming()) {
				streamList[i].stop();
				if (--startedStreamCount==0) handler.obtainMessage(Session.MESSAGE_STOP).sendToTarget();
			}
		}
	}
	
	/** Deletes all existing tracks & release associated resources. */
	public synchronized void flush() {
		for (int i=0;i<streamList.length;i++) {
			if (streamList[i] != null) {
				streamList[i].release();
				if (i == 0) sessionUsingTheCamera = null;
				else sessionUsingTheMic = null;
			}
		}
	}
	
}
