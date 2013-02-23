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
import android.util.Log;

/**
 * This class makes use of all the streaming package.
 * It represents a streaming session between a client and the phone.
 * A stream is designated by the word "track" in this class.
 * To add tracks to the session you need to call addVideoTrack() or addAudioTrack().
 */
public class Session {

	public final static String TAG = "Session";

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

	// Indicates if a session is already streaming audio or video
	static Session mSessionUsingTheCamera = null;
	static Session mSessionUsingTheMic = null;

	// Prevents threads from modifying two sessions simultaneously
	private static Object sLock = new Object();

	// The number of tracks added to this session
	private int mSessionTrackCount = 0;

	private InetAddress mOrigin, mDestination;
	private int mRoutingScheme = Session.UNICAST;
	private int mDefaultTimeToLive = 64;
	private Stream[] mStreamList = new Stream[2];
	private long mTimestamp;
	private SessionManager mManager;

	/** 
	 * Creates a streaming session that can be customized by adding tracks.
	 * @param destination The destination address of the streams
	 * @param origin The origin address of the streams
	 */
	public Session(InetAddress origin, InetAddress destination) {
		this.mDestination = destination;
		this.mOrigin = origin;
		// This timestamp is used in the session descriptor for the Origin parameter "o="
		this.mTimestamp = System.currentTimeMillis();
		this.mManager = SessionManager.getManager();
	}	

	/** 
	 * The destination address for all the streams of the session.
	 * This method will have no effect on already existing tracks
	 * @param destination The destination address
	 */
	public void setDestination(InetAddress destination) {
		this.mDestination =  destination;
	}

	/** 
	 * Defines the routing scheme that will be used for this session.
	 * You must call this method before adding tracks to the session.
	 * @param routingScheme Can be either {@link #UNICAST} or {@link #MULTICAST}
	 */
	public void setRoutingScheme(int routingScheme) {
		this.mRoutingScheme = routingScheme;
	}

	/** 
	 * Set the TTL of all packets sent during the session.
	 * You must call this method before adding tracks to the session.
	 * @param ttl The Time To Live
	 */
	public void setTimeToLive(int ttl) {
		mDefaultTimeToLive = ttl;
	}

	/** 
	 * Add the default video track with default configuration.
	 * @throws IllegalStateException
	 * @throws IOException
	 */
	public Session addVideoTrack() throws IllegalStateException, IOException {
		return addVideoTrack(mManager.mDefaultVideoEncoder, mManager.mDefaultCamera, mManager.mDefaultVideoQuality,false);
	}

	/** 
	 * Add video track with specified quality and encoder. 
	 * @param encoder Can be either {@link #VIDEO_H264} or {@link #VIDEO_H263}
	 * @param camera Can be either CameraInfo.CAMERA_FACING_BACK or CameraInfo.CAMERA_FACING_FRONT
	 * @param videoQuality Will determine the bitrate,framerate and resolution of the stream
	 * @param flash Set it to true to turn the flash on, if the phone has no flash, an exception IllegalStateException will be thrown
	 * @throws IllegalStateException
	 * @throws IOException
	 */
	public Session addVideoTrack(int encoder, int camera, VideoQuality videoQuality, boolean flash) throws IllegalStateException, IOException {
		synchronized (sLock) {
			if (mSessionUsingTheCamera != null) {
				if (mSessionUsingTheCamera.mRoutingScheme==UNICAST) throw new IllegalStateException("Camera already in use by another client");
				else {
					mStreamList[0] = mSessionUsingTheCamera.mStreamList[0];
					mSessionTrackCount++;
					return this;
				}
			}
			Stream stream = null;
			VideoQuality.merge(videoQuality,mManager.mDefaultVideoQuality);

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
				((VideoStream) stream).setPreviewDisplay(mManager.getSurfaceHolder().getSurface());
				((VideoStream) stream).setFlashState(flash);
				stream.setTimeToLive(mDefaultTimeToLive);
				stream.setDestination(mDestination, 5006);
				mStreamList[0] = stream;
				mSessionUsingTheCamera = this;
				mSessionTrackCount++;
			}
		}
		return this;
	}

	/** 
	 * Adds default audio track with default configuration. 
	 * @throws IOException 
	 */
	public Session addAudioTrack() throws IOException {
		return addAudioTrack(mManager.mDefaultAudioEncoder);
	}

	/** 
	 * Adds audio track with specified encoder. 
	 * @param encoder Can be either {@link #AUDIO_AMRNB} or {@link #AUDIO_AAC}
	 * @throws IOException
	 */
	public Session addAudioTrack(int encoder) throws IOException {
		synchronized (sLock) {
			if (mSessionUsingTheMic != null) {
				if (mSessionUsingTheMic.mRoutingScheme==UNICAST) throw new IllegalStateException("Microphone already in use by another client");
				else {
					mStreamList[1] = mSessionUsingTheMic.mStreamList[1];
					mSessionTrackCount++;
					return this;
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
				stream.setTimeToLive(mDefaultTimeToLive);
				stream.setDestination(mDestination, 5004);
				mStreamList[1] = stream;
				mSessionUsingTheMic = this;
				mSessionTrackCount++;
			}
		}
		return this;
	}

	/** 
	 * Returns a Session Description that can be stored in a file or sent to a client with RTSP.
	 * @return The Session Description
	 * @throws IllegalStateException
	 * @throws IOException
	 */
	public String getSessionDescription() throws IllegalStateException, IOException {
		synchronized (sLock) {
			StringBuilder sessionDescription = new StringBuilder();
			sessionDescription.append("v=0\r\n");
			// The RFC 4566 (5.2) suggest to use an NTP timestamp here but we will simply use a UNIX timestamp
			// TODO: Add IPV6 support
			sessionDescription.append("o=-"+mTimestamp+" "+mTimestamp+" IN IP4 "+mOrigin.getHostAddress()+"\r\n");
			sessionDescription.append("s=Unnamed\r\n");
			sessionDescription.append("i=N/A\r\n");
			sessionDescription.append("c=IN IP4 "+mDestination.getHostAddress()+"\r\n");
			// t=0 0 means the session is permanent (we don't know when it will stop)
			sessionDescription.append("t=0 0\r\n");
			sessionDescription.append("a=recvonly\r\n");
			// Prevents two different sessions from using the same peripheral at the same time
			for (int i=0;i<mStreamList.length;i++) {
				if (mStreamList[i] != null) {
					sessionDescription.append(mStreamList[i].generateSessionDescription());
					sessionDescription.append("a=control:trackID="+i+"\r\n");
				}
			}
			return sessionDescription.toString();
		}
	}

	/**
	 * This method returns the selected routing scheme of the session.
	 * The routing scheme can be either {@link #UNICAST} or {@link #MULTICAST}.
	 * @return The routing sheme of the session
	 */
	public String getRoutingScheme() {
		return mRoutingScheme==Session.UNICAST ? "unicast" : "multicast";
	}

	public InetAddress getDestination() {
		return mDestination;
	}

	/** Returns the number of tracks of this session. **/
	public int getTrackCount() {
		return mSessionTrackCount;
	}

	public boolean trackExists(int id) {
		return mStreamList[id]!=null;
	}

	public void setTrackDestinationPort(int id, int port) {
		mStreamList[id].setDestination(mDestination,port);
	}

	public int getTrackDestinationPort(int id) {
		return mStreamList[id].getDestinationPort();
	}

	public int getTrackLocalPort(int id) {
		return mStreamList[id].getLocalPort();
	}

	public int getTrackSSRC(int id) {
		return mStreamList[id].getSSRC();
	}

	/** Starts stream with id trackId. */
	public void start(int trackId) throws IllegalStateException, IOException {
		synchronized (sLock) {
			Stream stream = mStreamList[trackId];
			if (stream!=null && !stream.isStreaming()) {
				stream.prepare();
				stream.start();
				mManager.incStreamCount();
			}
		}
	}

	/** Starts existing streams. */
	public void startAll() throws IllegalStateException, IOException {
		for (int i=0;i<mStreamList.length;i++) {
			start(i);
		}
	}

	/** Stops existing streams. */
	public void stopAll() {
		synchronized (sLock) {
			for (int i=0;i<mStreamList.length;i++) {
				if (mStreamList[i] != null && mStreamList[i].isStreaming()) {
					mStreamList[i].stop();
					mManager.decStreamCount();
				}
			}
		}
	}

	/** Deletes all existing tracks & release associated resources. */
	public void flush() {
		synchronized (sLock) {
			for (int i=0;i<mStreamList.length;i++) {
				if (mStreamList[i] != null) {
					mStreamList[i].release();
					if (i == 0) mSessionUsingTheCamera = null;
					else mSessionUsingTheMic = null;
				}
			}
		}
	}

}
