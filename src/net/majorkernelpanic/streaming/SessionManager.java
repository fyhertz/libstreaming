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

import java.net.InetAddress;

import net.majorkernelpanic.streaming.video.VideoQuality;
import android.hardware.Camera.CameraInfo;
import android.util.Log;
import android.view.SurfaceHolder;

/**
 * Call {@link net.majorkernelpanic.streaming.SessionManager#getManager()} to get access to the SessionManager.
 * The SessionManager has a number of utility methods to create and manage Sessions.
 */
public class SessionManager {

	public final static String TAG = "SessionManager";
	
	// Removes the default public constructor
	private SessionManager() {}
	
	// The SessionManager implements the singleton pattern
	private static volatile SessionManager sInstance = null; 
	
	// The number of stream currently started on the phone
	private int mStartedStreamCount = 0;

	// Default configuration
	VideoQuality mDefaultVideoQuality = VideoQuality.defaultVideoQualiy.clone();
	int mDefaultVideoEncoder = Session.VIDEO_H263; 
	int mDefaultAudioEncoder = Session.AUDIO_AMRNB;
	int mDefaultCamera = CameraInfo.CAMERA_FACING_BACK;
	
	private static SurfaceHolder sSurfaceHolder = null;
	private static SurfaceHolder.Callback sCallback  = null;
	
	/**
	 * Returns a reference to the {@link SessionManager}.
	 * @return The reference to the {@link SessionManager}
	 */
	public final static SessionManager getManager() {
		if (sInstance == null) {
			synchronized (SessionManager.class) {
				if (sInstance == null) {
					SessionManager.sInstance = new SessionManager();
				}
			}
		}
		return sInstance;
	}
	
	/**
	 * Creates a new {@link Session}.
	 * @param destination The destination address of the streams
	 * @param origin The origin address of the streams
	 * @return The new Session
	 */
	public Session createSession(InetAddress origin, InetAddress destination) {
		return new Session(origin, destination);
	}
	
	/** Those callbacks won't necessarily be called from the ui thread ! */
	public interface CallbackListener {
		
		/** Called when a stream starts. */
		void onStreamingStarted(SessionManager manager);
		
		/** Called when a stream stops. */
		void onStreamingStopped(SessionManager manager);
		
	}
	
	private CallbackListener mListener = null;
	
	/**
	 * See {@link SessionManager.CallbackListener} to check out what events will be fired once you set up a listener.
	 * @param listener The listener
	 */
	public void setCallbackListener(CallbackListener listener) { 
		mListener = listener;
	}
	
	/** Sets default video stream quality, it will be used by {@link Session#addVideoTrack()}. */
	public void setDefaultVideoQuality(VideoQuality quality) {
		mDefaultVideoQuality = quality;
	}
	
	/** Sets the default audio encoder, it will be used by {@link Session#addVideoTrack()}. */
	public void setDefaultAudioEncoder(int encoder) {
		mDefaultAudioEncoder = encoder;
	}
	
	/** Sets the default video encoder, it will be used by {@link Session#addVideoTrack()}. */
	public void setDefaultVideoEncoder(int encoder) {
		mDefaultVideoEncoder = encoder;
	}
	
	/** 
	 * Sets the Surface required by MediaRecorder to record video 
	 * and adds a callback to stop video streaming whenever Android
	 * wishes to destroy the surface.
	 * @param surfaceHolder A SurfaceHolder wrapping a valid surface
	 **/
	public synchronized void setSurfaceHolder(SurfaceHolder surfaceHolder) {
		if (sCallback != null) sSurfaceHolder.removeCallback(sCallback);
		sSurfaceHolder = surfaceHolder;
		
		sCallback = new SurfaceHolder.Callback() {
			public void surfaceChanged(SurfaceHolder holder, int format,
					int width, int height) {
				Log.d(TAG,"Surface changed !!");
			}
			public void surfaceCreated(SurfaceHolder holder) {
				Log.d(TAG,"Surface created !!");
			}
			public void surfaceDestroyed(SurfaceHolder holder) {
				Log.d(TAG,"Surface destroyed !!");
				if (Session.mSessionUsingTheCamera != null) {
					Session.mSessionUsingTheCamera.stopAll();
				}
			}
		};
		sSurfaceHolder.addCallback(sCallback);
		
	}

	/**
	 * Sets the Surface required by MediaRecorder to record video.
	 * @param surfaceHolder A SurfaceHolder wrapping a valid surface
	 * @param setACallback Whether or not you want {@link Session} to add a callback that will stop video streaming when the surface is destroyed
	 */
	public synchronized void setSurfaceHolder(SurfaceHolder surfaceHolder, boolean setACallback) {
		if (sCallback != null) sSurfaceHolder.removeCallback(sCallback);
		if (setACallback) setSurfaceHolder(surfaceHolder);
		else sSurfaceHolder = surfaceHolder;
	}
	
	/** Indicates whether or not a camera is being used in a {@link Session}. */
	public synchronized boolean isCameraInUse() {
		return Session.mSessionUsingTheCamera!=null;
	}
	
	/** Indicates whether or not the microphone is being used in a {@link Session}. */
	public synchronized boolean isMicrophoneInUse() {
		return Session.mSessionUsingTheMic!=null;
	}
	
	/** Indicates if a {@link Session} is currently streaming audio or video */
	public synchronized boolean isStreaming() {
		return isCameraInUse() | isMicrophoneInUse();
	}
	
	/**
	 * Returns the surface holder previously set with {@link #setSurfaceHolder(SurfaceHolder)}.
	 * @return The surface holder
	 */
	public SurfaceHolder getSurfaceHolder() {
		return sSurfaceHolder;
	}
	
	/** Called by sessions to signal when streaming starts */
	synchronized void incStreamCount() {
		mStartedStreamCount++;
		if (mStartedStreamCount == 1) {
			if (mListener != null) {
				mListener.onStreamingStarted(getManager());
			}
		}
	}
	
	/** Called by sessions to signal when streaming stops */
	synchronized void decStreamCount() {
		mStartedStreamCount--;
		if (mStartedStreamCount == 0) {
			if (mListener != null) {
				mListener.onStreamingStopped(getManager());
			}
		}
	}
	
}
