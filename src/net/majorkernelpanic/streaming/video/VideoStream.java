/*
 * Copyright (C) 2011-2012 GUIGUI Simon, fyhertz@gmail.com
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
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

/** 
 * Don't use this class directly
 */
public abstract class VideoStream extends MediaStream {

	protected final static String TAG = "VideoStream";

	protected VideoQuality mQuality = VideoQuality.defaultVideoQualiy.clone();
	protected SurfaceHolder.Callback mSurfaceHolderCallback = null;
	protected Surface mSurface = null;
	protected boolean mFlashState = false,  mQualityHasChanged = false;
	protected int mVideoEncoder, mCameraId = 0;
	protected Camera mCamera;

	/** 
	 * Don't use this class directly
	 * @param cameraId Can be either CameraInfo.CAMERA_FACING_BACK or CameraInfo.CAMERA_FACING_FRONT
	 */
	public VideoStream(int camera) {
		super();
		CameraInfo cameraInfo = new CameraInfo();
		int numberOfCameras = Camera.getNumberOfCameras();
		for (int i=0;i<numberOfCameras;i++) {
			Camera.getCameraInfo(i, cameraInfo);
			if (cameraInfo.facing == camera) {
				this.mCameraId = i;
				break;
			}
		}
	}

	/**
	 * Sets a Surface to show a preview of recorded media (video). 
	 * You can call this method at any time and changes will take effect next time you call {@link #prepare()}.
	 */
	public void setPreviewDisplay(Surface surface) {
		this.mSurface = surface;
	}

	/** Turn flash on or off if phone has one */
	public void setFlashState(boolean state) {
		mFlashState = state;
	}

	/** 
	 * Modifies the resolution of the stream. You can call this method at any time 
	 * and changes will take effect next time you call {@link #prepare()}.
	 * {@link #setVideoQuality(VideoQuality)} may be more convenient.
	 * @param width Width of the stream
	 * @param height height of the stream
	 */
	public void setVideoSize(int width, int height) {
		if (mQuality.resX != width || mQuality.resY != height) {
			mQuality.resX = width;
			mQuality.resY = height;
			mQualityHasChanged = true;
		}
	}

	/** 
	 * Modifies the framerate of the stream. You can call this method at any time 
	 * and changes will take effect next time you call {@link #prepare()}.
	 * {@link #setVideoQuality(VideoQuality)} may be more convenient.
	 * @param rate Framerate of the stream
	 */	
	public void setVideoFramerate(int rate) {
		if (mQuality.framerate != rate) {
			mQuality.framerate = rate;
			mQualityHasChanged = true;
		}
	}

	/** 
	 * Modifies the bitrate of the stream. You can call this method at any time 
	 * and changes will take effect next time you call {@link #prepare()}.
	 * {@link #setVideoQuality(VideoQuality)} may be more convenient.
	 * @param bitrate Bitrate of the stream in bit per second
	 */	
	public void setVideoEncodingBitrate(int bitrate) {
		if (mQuality.bitrate != bitrate) {
			mQuality.bitrate = bitrate;
			mQualityHasChanged = true;
		}
	}

	/** 
	 * Modifies the quality of the stream. You can call this method at any time 
	 * and changes will take effect next time you call {@link #prepare()}.
	 * @param videoQuality Quality of the stream
	 */
	public void setVideoQuality(VideoQuality videoQuality) {
		if (!mQuality.equals(videoQuality)) {
			mQuality = videoQuality;
			mQualityHasChanged = true;
		}
	}

	/** 
	 * Modifies the videoEncoder of the stream. You can call this method at any time 
	 * and changes will take effect next time you call {@link #prepare()}.
	 * @param videoEncoder Encoder of the stream
	 */
	public void setVideoEncoder(int videoEncoder) {
		this.mVideoEncoder = videoEncoder;
	}

	/**
	 * Stops the stream
	 */
	public synchronized void stop() {
		super.stop();
		if (mCamera != null) {
			try {
				mCamera.reconnect();
			} catch (Exception e) {
				Log.e(TAG,e.getMessage()!=null?e.getMessage():"unknown error");
			}
			mCamera.stopPreview();
			try {
				mCamera.release();
			} catch (Exception e) {
				Log.e(TAG,e.getMessage()!=null?e.getMessage():"unknown error");
			}
			mCamera = null;
		}
	}

	/**
	 * Prepare the VideoStream, you can then call {@link #start()}.
	 * The underlying Camera will be opened and configured whaen you call this method so don't forget to deal with the RuntimeExceptions !
	 * Camera.open, Camera.setParameter, Camera.unlock may throw one !
	 */
	public void prepare() throws IllegalStateException, IOException, RuntimeException {
		if (mCamera == null) {
			mCamera = Camera.open(mCameraId);
			mCamera.setErrorCallback(new Camera.ErrorCallback() {
				@Override
				public void onError(int error, Camera camera) {
					// On some phones when trying to use the camera facing front the media server will die
					// Whether or not this callback may be called really depends on the phone
					if (error == Camera.CAMERA_ERROR_SERVER_DIED) {
						// In this case the application must release the camera and instantiate a new one
						Log.e(TAG,"Media server died !");
						// We don't know in what thread we are so stop needs to be synchronized
						stop();
					} else {
						Log.e(TAG,"Error unknown with the camera: "+error);
					}	
				}
			});
		}

		// If an exception is thrown after the camera was open, we must absolutly release it !
		try {

			// We reconnect to camera to change flash state if needed
			Parameters parameters = mCamera.getParameters();
			if (mFlashState) {
				if (parameters.getFlashMode()==null) {
					// The phone has no flash or the choosen camera can not toggle the flash
					throw new IllegalStateException("Can't turn the flash on !");
				} else {
					parameters.setFlashMode(mFlashState?Parameters.FLASH_MODE_TORCH:Parameters.FLASH_MODE_OFF);
					mCamera.setParameters(parameters);
				}
			}
			mCamera.setDisplayOrientation(mQuality.orientation);
			mCamera.unlock();
			super.setCamera(mCamera);

			// MediaRecorder should have been like this according to me:
			// all configuration methods can be called at any time and
			// changes take effects when prepare() is called
			super.setVideoSource(MediaRecorder.VideoSource.CAMERA);
			super.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
			if (mode==MODE_DEFAULT) {
				super.setMaxDuration(1000);
				super.setMaxFileSize(Integer.MAX_VALUE);
			} else if (mModeDefaultWasUsed) {
				// On some phones a RuntimeException might be thrown :/
				try {
					super.setMaxDuration(0);
					super.setMaxFileSize(Integer.MAX_VALUE); 
				} catch (RuntimeException e) {
					Log.e(TAG,"setMaxDuration or setMaxFileSize failed !");
				}
			}
			super.setVideoEncoder(mVideoEncoder);
			super.setPreviewDisplay(mSurface);
			super.setVideoSize(mQuality.resX,mQuality.resY);
			super.setVideoFrameRate(mQuality.framerate);
			super.setVideoEncodingBitRate(mQuality.bitrate);

			super.prepare();

			// Reset flash state to ensure that default behavior is to turn it off
			mFlashState = false;

			// Quality has been updated
			mQualityHasChanged = false;

		} catch (RuntimeException e) {
			mCamera.release();
			mCamera = null;
			throw e;
		} catch (IOException e) {
			mCamera.release();
			mCamera = null;
			throw e;
		}

	}

	public abstract String generateSessionDescription() throws IllegalStateException, IOException;

	/** 
	 * Releases ressources associated with the {@link VideoStream}. 
	 * The object can't be reused once this function has been called. 
	 **/
	public void release() {
		stop();
		super.release();
	}

}
