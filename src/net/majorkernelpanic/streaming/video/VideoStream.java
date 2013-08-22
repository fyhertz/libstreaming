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
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;

import net.majorkernelpanic.streaming.MediaStream;
import net.majorkernelpanic.streaming.rtp.MediaCodecInputStream;
import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;

/** 
 * Don't use this class directly.
 */
public abstract class VideoStream extends MediaStream {

	protected final static String TAG = "VideoStream";

	protected VideoQuality mQuality = VideoQuality.DEFAULT_VIDEO_QUALITY.clone();
	protected SurfaceHolder.Callback mSurfaceHolderCallback = null;
	protected SurfaceHolder mSurfaceHolder = null;
	protected int mVideoEncoder, mCameraId = 0;
	protected Camera mCamera;
	protected boolean mCameraOpenedManually = true;
	protected boolean mFlashState = false;
	protected boolean mSurfaceReady = false;
	protected boolean mUnlocked = false;

	/** 
	 * Don't use this class directly.
	 * Uses CAMERA_FACING_BACK by default.
	 */
	public VideoStream() {
		this(CameraInfo.CAMERA_FACING_BACK);
	}	

	/** 
	 * Don't use this class directly
	 * @param camera Can be either CameraInfo.CAMERA_FACING_BACK or CameraInfo.CAMERA_FACING_FRONT
	 */
	public VideoStream(int camera) {
		super();
		setCamera(camera);
	}

	/**
	 * Sets the camera that will be used to capture video.
	 * You can call this method at any time and changes will take effect next time you start the stream.
	 * @param camera Can be either CameraInfo.CAMERA_FACING_BACK or CameraInfo.CAMERA_FACING_FRONT
	 */
	public void setCamera(int camera) {
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

	/**	Switch between the front facing and the back facing camera of the phone. 
	 * If {@link #startPreview()} has been called, the preview will be  briefly interrupted. 
	 * If {@link #start()} has been called, the stream will be  briefly interrupted.
	 * You should not call this method from the main thread if you are already streaming. 
	 * @throws IOException 
	 * @throws RuntimeException 
	 **/
	public void switchCamera() throws RuntimeException, IOException {
		if (Camera.getNumberOfCameras() == 1) throw new IllegalStateException("Phone only has one camera !");
		boolean streaming = mStreaming;
		boolean previewing = mCamera!=null && mCameraOpenedManually; 
		mCameraId = (mCameraId == CameraInfo.CAMERA_FACING_BACK) ? CameraInfo.CAMERA_FACING_FRONT : CameraInfo.CAMERA_FACING_BACK; 
		setCamera(mCameraId);
		stopPreview();
		if (previewing) startPreview();
		if (streaming) start(); 
	}

	public int getCamera() {
		return mCameraId;
	}

	/**
	 * Sets a Surface to show a preview of recorded media (video). 
	 * You can call this method at any time and changes will take effect next time you call {@link #start()}.
	 */
	public synchronized void setPreviewDisplay(SurfaceHolder surfaceHolder) {
		if (mSurfaceHolderCallback != null && mSurfaceHolder != null) {
			mSurfaceHolder.removeCallback(mSurfaceHolderCallback);
		}
		if (surfaceHolder != null) {
			mSurfaceHolderCallback = new Callback() {
				@Override
				public void surfaceDestroyed(SurfaceHolder holder) {
					mSurfaceReady = false;
					if (VideoStream.this.mStreaming) {
						VideoStream.this.stop();
						Log.d(TAG,"Surface destroyed: video streaming stopped.");
					}
					if (mCamera != null) stopPreview();
				}
				@Override
				public void surfaceCreated(SurfaceHolder holder) {
					mSurfaceReady = true;
				}
				@Override
				public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
					Log.d(TAG,"Surface Changed !");
				}
			};
			mSurfaceHolder = surfaceHolder;
			mSurfaceHolder.addCallback(mSurfaceHolderCallback);
			mSurfaceReady = true;
		}
	}

	/** Turns the LED on or off if phone has one. */
	public synchronized void setFlashState(boolean state) {

		// FIXME: Is it possible to toggle the flash while streaming on android 2.3 ?
		// FIXME: It works on android 4.2 and 4.3
		
		mFlashState = state;

		// If the camera has already been opened, we apply the change immediately
		// FIXME: Will this work on Android 2.3 ?
		if (mCamera != null) {

			// Needed on Android 2.3
			if (mStreaming && mMode == MODE_STREAMING_LEGACY) {
				lockCamera();
			}

			Parameters parameters = mCamera.getParameters();

			// We test if the phone has a flash
			if (parameters.getFlashMode()==null) {
				// The phone has no flash or the choosen camera can not toggle the flash
				throw new RuntimeException("Can't turn the flash on !");
			} else {
				parameters.setFlashMode(mFlashState?Parameters.FLASH_MODE_TORCH:Parameters.FLASH_MODE_OFF);
				try {
					mCamera.setParameters(parameters);
				} catch (RuntimeException e) {
					throw new RuntimeException("Can't turn the flash on !");	
				}
			}

			// Needed on Android 2.3
			if (mStreaming && mMode == MODE_STREAMING_LEGACY) {
				unlockCamera();
			}

		}
	}

	/** Toggle the LED of the phone if it has one. */
	public void toggleFlash() {
		setFlashState(!mFlashState);
	}

	public boolean getFlashState() {
		return mFlashState;
	}

	/** 
	 * Modifies the resolution of the stream. You can call this method at any time 
	 * and changes will take effect next time you call {@link #start()}.
	 * {@link #setVideoQuality(VideoQuality)} may be more convenient.
	 * @param width Width of the stream
	 * @param height height of the stream
	 */
	public void setVideoSize(int width, int height) {
		if (mQuality.resX != width || mQuality.resY != height) {
			mQuality.resX = width;
			mQuality.resY = height;
		}
	}

	/** 
	 * Modifies the framerate of the stream. You can call this method at any time 
	 * and changes will take effect next time you call {@link #start()}.
	 * {@link #setVideoQuality(VideoQuality)} may be more convenient.
	 * @param rate Framerate of the stream
	 */	
	public void setVideoFramerate(int rate) {
		if (mQuality.framerate != rate) {
			mQuality.framerate = rate;
		}
	}

	/** 
	 * Modifies the bitrate of the stream. You can call this method at any time 
	 * and changes will take effect next time you call {@link #start()}.
	 * {@link #setVideoQuality(VideoQuality)} may be more convenient.
	 * @param bitrate Bitrate of the stream in bit per second
	 */	
	public void setVideoEncodingBitrate(int bitrate) {
		if (mQuality.bitrate != bitrate) {
			mQuality.bitrate = bitrate;
		}
	}

	/** 
	 * Modifies the quality of the stream. You can call this method at any time 
	 * and changes will take effect next time you call {@link #start()}.
	 * @param videoQuality Quality of the stream
	 */
	public void setVideoQuality(VideoQuality videoQuality) {
		if (!mQuality.equals(videoQuality)) {
			mQuality = videoQuality;
		}
	}
	
	/** 
	 * Returns the quality of the stream.  
	 */
	public VideoQuality getVideoQuality() {
		return mQuality;
	}	

	/** 
	 * Modifies the videoEncoder of the stream. You can call this method at any time 
	 * and changes will take effect next time you call {@link #start()}.
	 * @param videoEncoder Encoder of the stream
	 */
	protected void setVideoEncoder(int videoEncoder) {
		this.mVideoEncoder = videoEncoder;
	}

	/** Stops the stream. */
	public synchronized void stop() {
		if (mMode == MODE_STREAMING_JB) {
			mCamera.setPreviewCallback(null);
		}
		super.stop();
		if (mMode == MODE_STREAMING_LEGACY) {
			lockCamera();
		}
		if (!mCameraOpenedManually) {
			stopPreview();
		}
		mCameraOpenedManually = true;
	}

	public synchronized void startPreview() throws RuntimeException, IOException {

		if (mSurfaceHolder == null || mSurfaceHolder.getSurface() == null || !mSurfaceReady)
			throw new IllegalStateException("Invalid surface holder !");

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
						mCameraOpenedManually = false;
						stop();
					} else {
						Log.e(TAG,"Error unknown with the camera: "+error);
					}	
				}
			});

			Parameters parameters = mCamera.getParameters();

			if (mMode == MODE_STREAMING_JB) {
				getClosestSupportedQuality(parameters);
				parameters.setPreviewFormat(ImageFormat.YV12);
				parameters.setPreviewSize(mQuality.resX, mQuality.resY);
				parameters.setPreviewFrameRate(mQuality.framerate);
			}

			if (mFlashState) {
				if (parameters.getFlashMode()==null) {
					// The phone has no flash or the choosen camera can not toggle the flash
					throw new IllegalStateException("Can't turn the flash on !");
				} else {
					parameters.setFlashMode(mFlashState?Parameters.FLASH_MODE_TORCH:Parameters.FLASH_MODE_OFF);
				}
			}

			try {
			mCamera.setParameters(parameters);
			mCamera.setDisplayOrientation(mQuality.orientation);
			mCamera.setPreviewDisplay(mSurfaceHolder);
			if (mCameraOpenedManually) mCamera.startPreview();
			
			} catch (RuntimeException e) {
				stopPreview();
				throw e;
			} catch (IOException e) {
				stopPreview();
				throw e;
			}

		}

	}

	public synchronized void stopPreview() {

		if (mStreaming) super.stop();

		if (mCamera != null) {
			lockCamera();
			if (!mCameraOpenedManually) mCamera.stopPreview();
			try {
				mCamera.release();
			} catch (Exception e) {
				Log.e(TAG,e.getMessage()!=null?e.getMessage():"unknown error");
			}
			mCamera = null;
			mUnlocked = false;
		}		
	}

	/**
	 * Starts the stream.
	 * This will also open the camera and dispay the preview if {@link #startPreview()}
	 * has not laready been called.
	 */
	public synchronized void start() throws IllegalStateException, IOException {
		super.start();
	}

	/**
	 * Encoding of the audio/video is done by a MediaRecorder.
	 */
	protected void encodeWithMediaRecorder() throws IOException {

		// We need a local socket to forward data output by the camera to the packetizer
		createSockets();

		// Opens the camera if needed
		if (mCamera == null) {
			mCameraOpenedManually = false;
			// Will start the preview if not already started !
			Log.d(TAG,"Preview must be started to record video !");
			startPreview();
		}

		unlockCamera();
	
		mMediaRecorder = new MediaRecorder();
		mMediaRecorder.setCamera(mCamera);
		mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
		mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
		mMediaRecorder.setVideoEncoder(mVideoEncoder);
		mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
		mMediaRecorder.setVideoSize(mQuality.resX,mQuality.resY);
		mMediaRecorder.setVideoFrameRate(mQuality.framerate);
		mMediaRecorder.setVideoEncodingBitRate(mQuality.bitrate);

		// We write the ouput of the camera in a local socket instead of a file !			
		// This one little trick makes streaming feasible quiet simply: data from the camera
		// can then be manipulated at the other end of the socket
		mMediaRecorder.setOutputFile(mSender.getFileDescriptor());

		mMediaRecorder.prepare();
		mMediaRecorder.start();

		try {
			// mReceiver.getInputStream contains the data from the camera
			// the mPacketizer encapsulates this stream in an RTP stream and send it over the network
			mPacketizer.setDestination(mDestination, mRtpPort, mRtcpPort);
			mPacketizer.setInputStream(mReceiver.getInputStream());
			mPacketizer.start();
			mStreaming = true;
		} catch (IOException e) {
			stop();
			throw new IOException("Something happened with the local sockets :/ Start failed !");
		}

	}

	/**
	 * Encoding of the audio/video is done by a MediaCodec.
	 */
	@SuppressLint({ "InlinedApi", "NewApi" })
	protected void encodeWithMediaCodec() throws RuntimeException, IOException {

		// Opens the camera if needed
		if (mCamera == null) {
			mCameraOpenedManually = false;
			// Will start the preview if not already started !
			startPreview();
		}

		mMediaCodec = MediaCodec.createEncoderByType("video/avc");
		MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", mQuality.resX, mQuality.resY);
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mQuality.bitrate);
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mQuality.framerate);	
		mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);
		mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 4);
		mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		mMediaCodec.start();

		final ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();

		mCamera.setPreviewCallback(new Camera.PreviewCallback() {
			@Override
			public void onPreviewFrame(byte[] data, Camera camera) {
				long now = System.nanoTime()/1000, timeout = 1000000/mQuality.framerate;
				int bufferIndex = mMediaCodec.dequeueInputBuffer(timeout);

				if (bufferIndex>=0) {
					inputBuffers[bufferIndex].clear();
					inputBuffers[bufferIndex].put(data, 0, data.length);
					mMediaCodec.queueInputBuffer(bufferIndex, 0, data.length, System.nanoTime()/1000, 0);
				} else {
					Log.e(TAG,"No buffer available !");
				}

			}
		});

		try {
			// mReceiver.getInputStream contains the data from the camera
			// the mPacketizer encapsulates this stream in an RTP stream and send it over the network
			mPacketizer.setDestination(mDestination, mRtpPort, mRtcpPort);
			mPacketizer.setInputStream(new MediaCodecInputStream(mMediaCodec));
			mPacketizer.start();
			mStreaming = true;
		} catch (IOException e) {
			stop();
			throw new IOException("Something happened with the local sockets :/ Start failed !");
		}

	}

	public abstract String generateSessionDescription() throws IllegalStateException, IOException;

	/** Verifies if streaming using the MediaCodec API is feasable. */
	@SuppressLint("NewApi")
	private void checkMediaCodecAPI() {
		for(int j = MediaCodecList.getCodecCount() - 1; j >= 0; j--){
			MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(j);
			if (codecInfo.isEncoder()) {
				MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType("video/avc");
				for (int i = 0; i < capabilities.colorFormats.length; i++) {
					int format = capabilities.colorFormats[i];
					Log.e(TAG, codecInfo.getName()+" with color format " + format);           
				}
				/*for (int i = 0; i < capabilities.profileLevels; i++) {
					int format = capabilities.colorFormats[i];
					Log.e(TAG, codecInfo.getName()+" with color format " + format);           
				}*/
			}
		}
	}

	/** 
	 * Checks if the resolution and the framerate selected are supported by the camera.
	 * If not, it modifies it by supported parameters. 
	 **/
	private void getClosestSupportedQuality(Camera.Parameters parameters) {

		// Resolutions
		String supportedSizesStr = "Supported resolutions: ";
		List<Size> supportedSizes = parameters.getSupportedPreviewSizes();
		for (Iterator<Size> it = supportedSizes.iterator(); it.hasNext();) {
			Size size = it.next();
			supportedSizesStr += size.width+"x"+size.height+(it.hasNext()?", ":"");
		}
		Log.v(TAG,supportedSizesStr);

		// Frame rates
		String supportedFrameRatesStr = "Supported frame rates: ";
		List<Integer> supportedFrameRates = parameters.getSupportedPreviewFrameRates();
		for (Iterator<Integer> it = supportedFrameRates.iterator(); it.hasNext();) {
			supportedFrameRatesStr += it.next()+"fps"+(it.hasNext()?", ":"");
		}
		//Log.v(TAG,supportedFrameRatesStr);

		int minDist = Integer.MAX_VALUE, newFps = mQuality.framerate;
		if (!supportedFrameRates.contains(mQuality.framerate)) {
			for (Iterator<Integer> it = supportedFrameRates.iterator(); it.hasNext();) {
				int fps = it.next();
				int dist = Math.abs(fps - mQuality.framerate);
				if (dist<minDist) {
					minDist = dist;
					newFps = fps;
				}
			}
			Log.v(TAG,"Frame rate modified: "+mQuality.framerate+"->"+newFps);
			//mQuality.framerate = newFps;
		}

	}
	
	protected void lockCamera() {
		if (mUnlocked) {
			try {
				mCamera.reconnect();
			} catch (Exception e) {
				Log.e(TAG,e.getMessage());
			}
			mUnlocked = false;
		}
	}

	protected void unlockCamera() {
		if (!mUnlocked) {
			try {	
				mCamera.unlock();
			} catch (Exception e) {
				Log.e(TAG,e.getMessage());
			}
			mUnlocked = true;
		}
	}
	
}
