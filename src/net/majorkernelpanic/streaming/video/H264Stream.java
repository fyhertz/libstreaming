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

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import net.majorkernelpanic.mp4.MP4Config;
import net.majorkernelpanic.streaming.rtp.H264Packetizer;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

/**
 * A class for streaming H.264 from the camera of an android device using RTP. 
 * Call setDestination() & setVideoSize() & setVideoFrameRate() & setVideoEncodingBitRate() and you're good to go.
 * You can then call prepare() & start().
 * Call stop() to stop the stream.
 * You don't need to call reset().
 */
public class H264Stream extends VideoStream {

	static private SharedPreferences settings = null;
	
	private Semaphore mLock = new Semaphore(0);
	private MP4Config mMp4Config;
	
	/**
	 * Constructs the H.264 stream.
	 * @param cameraId Can be either CameraInfo.CAMERA_FACING_BACK or CameraInfo.CAMERA_FACING_FRONT
	 * @throws IOException
	 */
	public H264Stream(int cameraId) throws IOException {
		super(cameraId);
		setVideoEncoder(MediaRecorder.VideoEncoder.H264);
		this.mPacketizer = new H264Packetizer();
	}
	
	/**
	 * When start() is called, the SPS and PPS parameters used by the phone are determined and
	 * can be stored if this method has been called at some point.
	 * @param prefs The SharedPreferences that will be used to save SPS and PPS parameters
	 */
	static public void setPreferences(SharedPreferences prefs) {
		settings = prefs;
	}
	
	// Should not be called by the UI thread
	private MP4Config testH264() throws IllegalStateException, IOException {
		if (!mQualityHasChanged && mMp4Config!=null) return mMp4Config;
		
		if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			throw new IllegalStateException("No external storage or external storage not ready !");
		}
		
		final String TESTFILE = Environment.getExternalStorageDirectory().getPath()+"/spydroid-test.mp4";
		
		Log.i(TAG,"Testing H264 support... Test file saved at: "+TESTFILE);
		
		// Save flash state & set it to false so that led remains off while testing h264
		boolean savedFlashState = mFlashState;
		mFlashState = false;
		
		// That means the H264Stream will behave as a regular MediaRecorder object
		// it will not start the packetizer thread and can be used to save video in a file
		setMode(MODE_DEFAULT);
		
		setOutputFile(TESTFILE);
		
		// We wait a little and stop recording
		this.setOnInfoListener(new MediaRecorder.OnInfoListener() {
			public void onInfo(MediaRecorder mr, int what, int extra) {
				Log.d(TAG,"MediaRecorder callback called !");
				if (what==MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
					Log.d(TAG,"MediaRecorder: MAX_DURATION_REACHED");
				} else if (what==MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
					Log.d(TAG,"MediaRecorder: MAX_FILESIZE_REACHED");
				} else if (what==MEDIA_RECORDER_INFO_UNKNOWN) {
					Log.d(TAG,"MediaRecorder: INFO_UNKNOWN");
				} else {
					Log.d(TAG,"WTF ?");
				}
				mLock.release();
			}
		});
		
		// Start recording
		prepare();
		start();
		
		try {
			if (mLock.tryAcquire(6,TimeUnit.SECONDS)) {
				Log.d(TAG,"MediaRecorder callback was called :)");
				Thread.sleep(400);
			} else {
				Log.d(TAG,"MediaRecorder callback was not called after 6 seconds... :(");
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			stop();
			setMode(MODE_STREAMING);
		}
		
		// Disable the callback
		try {
			this.setOnInfoListener(null);
		} catch (Exception ignore) {}
		
		// Retrieve SPS & PPS & ProfileId with MP4Config
		mMp4Config = new MP4Config(TESTFILE);

		// Delete dummy video
		File file = new File(TESTFILE);
		if (!file.delete()) Log.e(TAG,"Temp file could not be erased");
		
		// Restore flash state
		mFlashState = savedFlashState;
		
		Log.i(TAG,"H264 Test succeded...");
		
		// Save test result
		if (settings != null) {
			Editor editor = settings.edit();
			editor.putString(mQuality.framerate+","+mQuality.resX+","+mQuality.resY, mMp4Config.getProfileLevel()+","+mMp4Config.getB64SPS()+","+mMp4Config.getB64PPS());
			editor.commit();
		}
		return mMp4Config;
		
	}
	
	/**
	 * Returns a description of the stream using SDP. It can then be included in an SDP file.
	 * This method will fail if called when streaming.
	 */
	public String generateSessionDescription() throws IllegalStateException, IOException {
		String profile,sps,pps;
		
		if (settings != null) {
			if (!settings.contains(mQuality.framerate+","+mQuality.resX+","+mQuality.resY)) {
				testH264();
				profile = mMp4Config.getProfileLevel();
				pps = mMp4Config.getB64PPS();
				sps = mMp4Config.getB64SPS();
			} else {
				String[] s = settings.getString(mQuality.framerate+","+mQuality.resX+","+mQuality.resY, "").split(",");
				profile = s[0];
				sps = s[1];
				pps = s[2];
			}
		} else {
			testH264();
			profile = mMp4Config.getProfileLevel();
			pps = mMp4Config.getB64PPS();
			sps = mMp4Config.getB64SPS();
		}

		return "m=video "+String.valueOf(getDestinationPort())+" RTP/AVP 96\r\n" +
				   "b=RR:0\r\n" +
				   "a=rtpmap:96 H264/90000\r\n" +
				   "a=fmtp:96 packetization-mode=1;profile-level-id="+profile+";sprop-parameter-sets="+sps+","+pps+";\r\n";
	}
	
}
