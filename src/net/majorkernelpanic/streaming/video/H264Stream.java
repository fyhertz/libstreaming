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

import net.majorkernelpanic.streaming.mp4.MP4Config;
import net.majorkernelpanic.streaming.rtp.H264Packetizer;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.hardware.Camera.CameraInfo;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

/**
 * A class for streaming H.264 from the camera of an android device using RTP. 
 * Call {@link #setDestinationAddress(java.net.InetAddress)}, {@link #setDestinationPorts(int)}, 
 * {@link #setVideoSize(int, int)}, {@link #setVideoFramerate(int)} and {@link #setVideoEncodingBitrate(int)} and you're good to go.
 * You can then call {@link #start()}.
 * Call {@link #stop()} to stop the stream.
 */
public class H264Stream extends VideoStream {

	private SharedPreferences mSettings = null;

	private Semaphore mLock = new Semaphore(0);

	/**
	 * Constructs the H.264 stream.
	 * Uses CAMERA_FACING_BACK by default.
	 * @throws IOException
	 */
	public H264Stream() throws IOException {
		this(CameraInfo.CAMERA_FACING_BACK);
	}	

	/**
	 * Constructs the H.264 stream.
	 * @param cameraId Can be either CameraInfo.CAMERA_FACING_BACK or CameraInfo.CAMERA_FACING_FRONT
	 * @throws IOException
	 */
	public H264Stream(int cameraId) throws IOException {
		super(cameraId);
		setVideoEncoder(MediaRecorder.VideoEncoder.H264);
		mPacketizer = new H264Packetizer();
	}

	/**
	 * Some data (SPS and PPS params) needs to be stored when {@link #generateSessionDescription()} is called 
	 * @param prefs The SharedPreferences that will be used to save SPS and PPS parameters
	 */
	public void setPreferences(SharedPreferences prefs) {
		mSettings = prefs;
	}

	/**
	 * Returns a description of the stream using SDP. It can then be included in an SDP file.
	 * Will fail if called when streaming.
	 */
	public synchronized  String generateSessionDescription() throws IllegalStateException, IOException {
		MP4Config config = testH264();

		return "m=video "+String.valueOf(getDestinationPorts()[0])+" RTP/AVP 96\r\n" +
		"a=rtpmap:96 H264/90000\r\n" +
		"a=fmtp:96 packetization-mode=1;profile-level-id="+config.getProfileLevel()+";sprop-parameter-sets="+config.getB64SPS()+","+config.getB64PPS()+";\r\n";
	}	

	// Should not be called by the UI thread
	private MP4Config testH264() throws IllegalStateException, IOException {

		if (mSettings != null) {
			if (mSettings.contains("h264"+mQuality.framerate+","+mQuality.resX+","+mQuality.resY)) {
				String[] s = mSettings.getString("h264"+mQuality.framerate+","+mQuality.resX+","+mQuality.resY, "").split(",");
				return new MP4Config(s[0],s[1],s[2]);
			}
		}

		if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			throw new IllegalStateException("No external storage or external storage not ready !");
		}

		final String TESTFILE = Environment.getExternalStorageDirectory().getPath()+"/spydroid-test.mp4";

		Log.i(TAG,"Testing H264 support... Test file saved at: "+TESTFILE);

		// Save flash state & set it to false so that led remains off while testing h264
		boolean savedFlashState = mFlashState;
		mFlashState = false;

		// Opens the camera if needed
		if (mCamera == null) {
			mCameraOpenedManually = false;
		}

		// Will start the preview if not already started !
		startPreview();

		try {
			Thread.sleep(5000);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		unlockCamera();

		mMediaRecorder = new MediaRecorder();
		mMediaRecorder.setCamera(mCamera);
		mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
		mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
		mMediaRecorder.setMaxDuration(1000);
		//mMediaRecorder.setMaxFileSize(Integer.MAX_VALUE);
		mMediaRecorder.setVideoEncoder(mVideoEncoder);
		mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
		mMediaRecorder.setVideoSize(mQuality.resX,mQuality.resY);
		mMediaRecorder.setVideoFrameRate(mQuality.framerate);
		mMediaRecorder.setVideoEncodingBitRate(mQuality.bitrate);
		mMediaRecorder.setOutputFile(TESTFILE);

		// We wait a little and stop recording
		mMediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
			public void onInfo(MediaRecorder mr, int what, int extra) {
				Log.d(TAG,"MediaRecorder callback called !");
				if (what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
					Log.d(TAG,"MediaRecorder: MAX_DURATION_REACHED");
				} else if (what==MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
					Log.d(TAG,"MediaRecorder: MAX_FILESIZE_REACHED");
				} else if (what==MediaRecorder.MEDIA_RECORDER_INFO_UNKNOWN) {
					Log.d(TAG,"MediaRecorder: INFO_UNKNOWN");
				} else {
					Log.d(TAG,"WTF ?. what: "+what);
				}
				mLock.release();
			}
		});

		// Start recording
		mMediaRecorder.prepare();
		mMediaRecorder.start();

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
			mMediaRecorder.stop();
			mMediaRecorder.release();
			mMediaRecorder = null;
			lockCamera();
		}

		// Retrieve SPS & PPS & ProfileId with MP4Config
		MP4Config config = new MP4Config(TESTFILE);

		// Delete dummy video
		File file = new File(TESTFILE);
		if (!file.delete()) Log.e(TAG,"Temp file could not be erased");

		// Restore flash state
		mFlashState = savedFlashState;

		Log.i(TAG,"H264 Test succeded...");

		// Save test result
		if (mSettings != null) {
			Editor editor = mSettings.edit();
			editor.putString("h264"+mQuality.framerate+","+mQuality.resX+","+mQuality.resY, config.getProfileLevel()+","+config.getB64SPS()+","+config.getB64PPS());
			editor.commit();
		}
		
		return config;

	}

}
