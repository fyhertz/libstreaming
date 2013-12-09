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
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

import net.majorkernelpanic.streaming.MediaStream;
import net.majorkernelpanic.streaming.rtp.MediaCodecInputStream;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;

/** 
 * Don't use this class directly.
 */
public abstract class VideoStream extends MediaStream {

	protected final static String TAG = "VideoStream";

	private static HashMap<String,SparseArray<ArrayList<String>>> sSupportedColorFormats = new HashMap<String, SparseArray<ArrayList<String>>>(); 

	protected VideoQuality mQuality = VideoQuality.DEFAULT_VIDEO_QUALITY.clone();
	protected VideoQuality mActualQuality = mQuality.clone(); 
	protected SurfaceHolder.Callback mSurfaceHolderCallback = null;
	protected SurfaceHolder mSurfaceHolder = null;
	protected SharedPreferences mSettings = null;
	protected int mVideoEncoder, mCameraId = 0;
	protected Camera mCamera;
	protected Thread mCameraThread;
	protected Looper mCameraLooper;


	protected boolean mCameraOpenedManually = true;
	protected boolean mFlashState = false;
	protected boolean mSurfaceReady = false;
	protected boolean mUnlocked = false;
	protected boolean mPreviewStarted = false;

	protected CodecManager.Codecs mCodecs;
	protected String mMimeType;
	protected String mEncoderName;
	protected int mEncoderColorFormat;
	protected int mCameraImageFormat;
	protected int mMaxFps = 0;	

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
	@SuppressLint("InlinedApi")
	public VideoStream(int camera) {
		super();
		setCamera(camera);
	}

	/**
	 * Called by subclasses. 
	 */
	protected void init(String mimeType) {
		mCameraImageFormat = ImageFormat.NV21;
		mMimeType = mimeType;

		if (sSuggestedMode == MODE_MEDIACODEC_API) {
			mCodecs = CodecManager.Selector.findCodecsFormMimeType(mMimeType, false);

			if (mCodecs.hardwareCodec == null && mCodecs.softwareCodec == null ) {
				mMode = MODE_MEDIARECORDER_API;
				Log.e(TAG,"No encoder can be used on this phone, we will use the MediaRecorder API");

			} else {
				if (mCodecs.hardwareCodec != null) {
					mEncoderColorFormat = mCodecs.hardwareColorFormat;
					mEncoderName = mCodecs.hardwareCodec;
				} else {
					mEncoderColorFormat = mCodecs.softwareColorFormat;
					mEncoderName = mCodecs.softwareCodec;	
				}
			}

		}
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
					stopPreview();
					Log.d(TAG,"Surface destroyed !");
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
			if (mStreaming && mMode == MODE_MEDIARECORDER_API) {
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
			if (mStreaming && mMode == MODE_MEDIARECORDER_API) {
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
			mQuality = videoQuality.clone();
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

	/**
	 * Some data (SPS and PPS params) needs to be stored when {@link #generateSessionDescription()} is called 
	 * @param prefs The SharedPreferences that will be used to save SPS and PPS parameters
	 */
	public void setPreferences(SharedPreferences prefs) {
		mSettings = prefs;
	}	

	/**
	 * Starts the stream.
	 * This will also open the camera and dispay the preview 
	 * if {@link #startPreview()} has not aready been called.
	 */
	public synchronized void start() throws IllegalStateException, IOException {
		if (!mPreviewStarted) mCameraOpenedManually = false;
		super.start();
	}	

	/** Stops the stream. */
	public synchronized void stop() {
		if (mCamera != null) {
			if (mMode == MODE_MEDIACODEC_API) {
				mCamera.setPreviewCallbackWithBuffer(null);
			}
			super.stop();
			// We need to restart the preview
			if (!mCameraOpenedManually) {
				destroyCamera();
			} else {
				try {
					startPreview();
				} catch (RuntimeException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public synchronized void startPreview() throws RuntimeException, IOException {
		if (!mPreviewStarted) {
			createCamera();
			try {
				mCamera.startPreview();
				mPreviewStarted = true;
				mCameraOpenedManually = true;
			} catch (RuntimeException e) {
				destroyCamera();
				throw e;
			}
		}
	}

	/**
	 * Stops the preview.
	 */
	public synchronized void stopPreview() {
		mCameraOpenedManually = false;
		stop();
	}

	/**
	 * Encoding of the audio/video is done by a MediaRecorder.
	 */
	protected void encodeWithMediaRecorder() throws IOException {

		// We need a local socket to forward data output by the camera to the packetizer
		createSockets();

		// Opens the camera if needed
		createCamera();

		// Stops the preview if needed
		if (mPreviewStarted) {
			lockCamera();
			try {
				mCamera.stopPreview();
			} catch (Exception e) {}
			mPreviewStarted = false;
		}

		// Unlock the camera if needed
		unlockCamera();

		mMediaRecorder = new MediaRecorder();
		mMediaRecorder.setCamera(mCamera);
		mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
		mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
		mMediaRecorder.setVideoEncoder(mVideoEncoder);
		mMediaRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
		mMediaRecorder.setVideoSize(mQuality.resX,mQuality.resY);
		mMediaRecorder.setVideoFrameRate(mQuality.framerate);

		// The bandwidth actually consumed is often above what was requested 
		mMediaRecorder.setVideoEncodingBitRate((int)(mQuality.bitrate*0.8));

		// We write the ouput of the camera in a local socket instead of a file !			
		// This one little trick makes streaming feasible quiet simply: data from the camera
		// can then be manipulated at the other end of the socket
		mMediaRecorder.setOutputFile(mSender.getFileDescriptor());

		mMediaRecorder.prepare();
		mMediaRecorder.start();

		// This will skip the MPEG4 header if this step fails we can't stream anything :(
		InputStream is = mReceiver.getInputStream();
		try {
			byte buffer[] = new byte[4];
			// Skip all atoms preceding mdat atom
			while (!Thread.interrupted()) {
				while (is.read() != 'm');
				is.read(buffer,0,3);
				if (buffer[0] == 'd' && buffer[1] == 'a' && buffer[2] == 't') break;
			}
		} catch (IOException e) {
			Log.e(TAG,"Couldn't skip mp4 header :/");
			stop();
		}

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
	protected void encodeWithMediaCodec() throws RuntimeException, IOException {
		if ((mMode&MODE_MEDIACODEC_API_2)!=0) {
			// Uses the method MediaCodec.createInputSurface to feed the encoder
			encodeWithMediaCodecMethod2();
		} else {
			// Uses dequeueInputBuffer to feed the encoder
			encodeWithMediaCodecMethod1();
		}
	}	

	/**
	 * Encoding of the audio/video is done by a MediaCodec.
	 */
	@SuppressLint("NewApi")
	protected void encodeWithMediaCodecMethod1() throws RuntimeException, IOException {
		// Reopens the camera if needed
		createCamera();
		updateCamera();

		// Starts the preview if needed
		if (!mPreviewStarted) {
			try {
				mCamera.startPreview();
				mPreviewStarted = true;
			} catch (RuntimeException e) {
				destroyCamera();
				throw e;
			}
		}

		mMediaCodec = MediaCodec.createByCodecName(mEncoderName);
		MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", mActualQuality.resX, mActualQuality.resY);
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mActualQuality.bitrate);
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mActualQuality.framerate);	
		mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,mEncoderColorFormat);
		mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
		mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		mMediaCodec.start();

		final CodecManager.NV21Translator convertor = new CodecManager.NV21Translator(mEncoderName, mEncoderColorFormat, mActualQuality.resX, mActualQuality.resY);
		
		Camera.PreviewCallback callback = new Camera.PreviewCallback() {
			long now = System.nanoTime()/1000, oldnow = now, i=0;
			ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
			@Override
			public void onPreviewFrame(byte[] data, Camera camera) {
				now = System.nanoTime()/1000;
				if (i++>3) {
					i = 0;
					//Log.d(TAG,"Measured: "+1000000L/(now-oldnow)+" fps.");
				}
				try {
					int bufferIndex = mMediaCodec.dequeueInputBuffer(500000);
					if (bufferIndex>=0) {
						convertor.translate(data, inputBuffers[bufferIndex]);
						mMediaCodec.queueInputBuffer(bufferIndex, 0, inputBuffers[bufferIndex].position(), now, 0);
					} else {
						Log.e(TAG,"No buffer available !");
					}
				} finally {
					mCamera.addCallbackBuffer(data);
				}				
				oldnow = now;
			}
		};

		for (int i=0;i<10;i++) mCamera.addCallbackBuffer(new byte[convertor.getBufferSize()]);
		mCamera.setPreviewCallbackWithBuffer(callback);
		
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

	/**
	 * Encoding of the audio/video is done by a MediaCodec.
	 */
	@SuppressLint({ "InlinedApi", "NewApi" })	
	protected void encodeWithMediaCodecMethod2() throws RuntimeException, IOException {

	}

	public abstract String generateSessionDescription() throws IllegalStateException, IOException;

	/**
	 * Opens the camera in a new Looper thread so that the preview callback is not called from the main thread
	 * If an exception is thrown in this Looper thread, we bring it back into the main thread.
	 * @throws RuntimeException Might happen if another app is already using the camera.
	 */
	private void openCamera() throws RuntimeException {
		final Semaphore lock = new Semaphore(0);
		final RuntimeException[] exception = new RuntimeException[1]; 
		mCameraThread = new Thread(new Runnable() {
			@Override
			public void run() {
				Looper.prepare();
				mCameraLooper = Looper.myLooper();
				try {
					mCamera = Camera.open(mCameraId);
				} catch (RuntimeException e) {
					exception[0] = e;
				} finally {
					lock.release();
					Looper.loop();
				}
			}
		});
		mCameraThread.start();
		lock.acquireUninterruptibly();
		if (exception[0] != null) throw exception[0];
	}
	
	protected synchronized void createCamera() throws RuntimeException, IOException {
		if (mSurfaceHolder == null || mSurfaceHolder.getSurface() == null || !mSurfaceReady)
			throw new IllegalStateException("Invalid surface holder !");

		if (mCamera == null) {
			openCamera();
			mUnlocked = false;
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


			try {
				Parameters parameters = mCamera.getParameters();

				if (mFlashState) {
					if (parameters.getFlashMode()==null) {
						// The phone has no flash or the choosen camera can not toggle the flash
						throw new IllegalStateException("Can't turn the flash on !");
					} else {
						parameters.setFlashMode(mFlashState?Parameters.FLASH_MODE_TORCH:Parameters.FLASH_MODE_OFF);
					}
				}

				int[] max = VideoQuality.determineMaximumSupportedFramerate(parameters);
				VideoQuality quality = new VideoQuality(352,288);
				quality = VideoQuality.determineClosestSupportedResolution(parameters, quality);
				
				parameters.setPreviewFpsRange(max[0], max[1]);
				parameters.setPreviewSize(quality.resX, quality.resY);
				mCamera.setParameters(parameters);
				mCamera.setDisplayOrientation(mQuality.orientation);
				mCamera.setPreviewDisplay(mSurfaceHolder);

			} catch (RuntimeException e) {
				destroyCamera();
				throw e;
			} catch (IOException e) {
				destroyCamera();
				throw e;
			}
		}
	}

	protected synchronized void destroyCamera() {
		if (mCamera != null) {
			if (mStreaming) super.stop();
			lockCamera();
			mCamera.stopPreview();
			try {
				mCamera.release();
			} catch (Exception e) {
				Log.e(TAG,e.getMessage()!=null?e.getMessage():"unknown error");
			}
			mCamera = null;
			mCameraLooper.quit();
			mUnlocked = false;
			mPreviewStarted = false;
		}	
	}

	protected synchronized void updateCamera() throws IOException, RuntimeException {
		if ((mMode&MODE_MEDIACODEC_API)!=0) {
			if (mPreviewStarted) {
				mPreviewStarted = false;
				mCamera.stopPreview();
			}

			Parameters parameters = mCamera.getParameters();
			
			mActualQuality.resX = mQuality.resX;
			mActualQuality.resY = mQuality.resY;
			mActualQuality.bitrate = mQuality.bitrate;

			// Hack needed for now because there are weird artefacts at lower resolutions (when resX or resY os not a multiple of 16)
			/*if (mActualQuality.resX<352) {
				mActualQuality.resX = 352;
				mActualQuality.resY = 288;
			}*/

			mActualQuality = VideoQuality.determineClosestSupportedResolution(parameters, mActualQuality);
			int[] max = VideoQuality.determineMaximumSupportedFramerate(parameters);
			
			parameters.setPreviewFormat(mCameraImageFormat);
			parameters.setPreviewSize(mActualQuality.resX, mActualQuality.resY);
			//parameters.setPreviewFpsRange(max[0], max[1]);
			
			try {
				Log.e(TAG,"FPS: "+mActualQuality.framerate+" X: "+mActualQuality.resX+" Y: "+mActualQuality.resY);
				mCamera.setParameters(parameters);
				mCamera.setDisplayOrientation(mQuality.orientation);
				mCamera.setPreviewDisplay(mSurfaceHolder);
				mCamera.startPreview();
				mPreviewStarted = true;
				measureActualFramerate();
			} catch (RuntimeException e) {
				destroyCamera();
				throw e;
			} catch (IOException e) {
				destroyCamera();
				throw e;
			}
		}
	}

	/** 
	 * Returns an associative array of the supported color formats and the names of the encoders for a given mime type
	 * The goal here will be to check if either YUV420SemiPlanar or YUV420Planar color formats 
	 * are supported by one of the encoders of the phone.
	 * This can take up to sec to return apparently !
	 **/
	@SuppressLint("NewApi")
	static protected SparseArray<ArrayList<String>> findSupportedColorFormats(String mimeType) {
		SparseArray<ArrayList<String>> list = new SparseArray<ArrayList<String>>();

		if (sSupportedColorFormats.containsKey(mimeType)) {
			return sSupportedColorFormats.get(mimeType); 
		}

		Log.v(TAG,"Searching supported color formats for mime type "+mimeType);

		// We loop through the encoders, apparently this can take up to a sec (testes on a GS3)
		for(int j = MediaCodecList.getCodecCount() - 1; j >= 0; j--){
			MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(j);
			if (!codecInfo.isEncoder()) continue;

			String[] types = codecInfo.getSupportedTypes();
			for (int i = 0; i < types.length; i++) {
				if (types[i].equalsIgnoreCase(mimeType)) {
					MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
					// And through the color formats supported
					for (int k = 0; k < capabilities.colorFormats.length; k++) {
						int format = capabilities.colorFormats[k];
						if (list.get(format) == null) list.put(format, new ArrayList<String>());
						list.get(format).add(codecInfo.getName());
					}
				}
			}
		}

		// Logs the supported color formats on the phone
		StringBuilder e = new StringBuilder();
		e.append("Supported color formats on this phone: ");
		for (int i=0;i<list.size();i++) e.append(list.keyAt(i)+(i==list.size()-1?".":", "));
		Log.v(TAG, e.toString());

		sSupportedColorFormats.put(mimeType, list);
		return list;
	}

	protected void lockCamera() {
		if (mUnlocked) {
			Log.d(TAG,"Locking camera");
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
			Log.d(TAG,"Unlocking camera");
			try {	
				mCamera.unlock();
			} catch (Exception e) {
				Log.e(TAG,e.getMessage());
			}
			mUnlocked = true;
		}
	}


	/**
	 * Computes the average frame rate at which the preview callback is called.
	 * We will then use this average framerate with the MediaCodec.  
	 * Blocks the thread in which this function is called.
	 */
	private void measureActualFramerate() {

		if (mSettings != null) {
			String key = PREF_PREFIX+"fps-"+mQuality.framerate+","+mCameraImageFormat+","+mQuality.resX+","+mQuality.resY;
			if (mSettings.contains(key)) {
				mActualQuality.framerate = mSettings.getInt(key, 0);
				Log.d(TAG,"Actual framerate: "+mActualQuality.framerate);
				return;
			}
		}
		
		final Semaphore lock = new Semaphore(0);

		final Camera.PreviewCallback callback = new Camera.PreviewCallback() {
			int i = 0, t = 0;
			long now, oldnow, count = 0;
			@Override
			public void onPreviewFrame(byte[] data, Camera camera) {
				i++;
				now = System.nanoTime()/1000;
				if (i>3) {
					t += now - oldnow;
					count++;
				}
				if (i>15) {
					mActualQuality.framerate = (int) (1000000/(t/count)+1);
					Log.d(TAG,"Actual framerate: "+mActualQuality.framerate);
					lock.release();
				}
				oldnow = now;
			}
		};

		mCamera.setPreviewCallback(callback);

		try {
			lock.acquire();
		} catch (InterruptedException e) {}

		mCamera.setPreviewCallback(null);

		if (mSettings != null) {
			Editor editor = mSettings.edit();
			editor.putInt(PREF_PREFIX+"fps"+mQuality.framerate+","+mCameraImageFormat+","+mQuality.resX+mQuality.resY, mActualQuality.framerate);
			editor.commit();
		}

	}	

	private class CameraFifo {

		private final int MAX = 10;

		private CodecManager.NV21Translator mConvertor = null;
		private byte[][] mBuffers = new byte[MAX][];
		private long[] mTimestamps = new long[MAX];
		private Thread mThread = null;
		private int mHead, mTail;
		private Semaphore mAvailable;
		private Semaphore mRemaining;
		private boolean mRunning; 

		public void start() {
			if (mThread == null) {

				mHead = 0;
				mTail = 0;
				mAvailable = new Semaphore(0);
				mRemaining = new Semaphore(MAX);

				mRunning = true;
				mThread = new Thread(mRunnable);
				mThread.start();

				mConvertor = new CodecManager.NV21Translator(mEncoderName, mEncoderColorFormat, mActualQuality.resX, mActualQuality.resY);
				for (int i=0;i<MAX;i++) mCamera.addCallbackBuffer(new byte[mConvertor.getBufferSize()]);
				mCamera.setPreviewCallbackWithBuffer(mCameraCallback);

			}
		} 

		public void stop() {
			if (mThread != null) {
				mCamera.setPreviewCallback(null);
				mRunning = false;
				mThread.interrupt();
				try {
					mThread.join();
				} catch (InterruptedException e) {}
				mThread = null;
			}
		}

		private void queue(byte[] buffer, long ts) {
			try {
				mRemaining.acquire();
			} catch (InterruptedException e) {}
			mBuffers[mHead] = buffer;
			mTimestamps[mHead] = ts;
			mHead++;
			if (mHead>=MAX) mHead = 0;
			mAvailable.release();
		}

		/**
		 * This will be called from the main thread, doing the color format conversion and feeding the encoder 
		 * from there was too long, that is why this FIFO is needed
		 */
		Camera.PreviewCallback mCameraCallback = new Camera.PreviewCallback() {
			private long now = System.nanoTime()/1000, oldnow = now;
			private int i=0;
			@Override
			public void onPreviewFrame(byte[] data, Camera camera) {
				now = System.nanoTime()/1000;
				if (i++>3) {
					i = 0;
					Log.d(TAG,"Measured: "+1000000L/(now-oldnow)+" fps.");
				}
				oldnow = now;
				queue(data, now);
			}
		};

		/**
		 * Thread in which we do the conversion between color formats and we feed the MediaCodec
		 */
		private Runnable mRunnable = new Runnable() {
			@SuppressLint("NewApi")
			@Override
			public void run() {
				ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();

				while (mRunning && !Thread.interrupted()) {
					//Log.d(TAG, "a: "+mRemaining.availablePermits());
					try {
						mAvailable.acquire();
					} catch (InterruptedException e) {
						break;
					}

					try {
						int bufferIndex = mMediaCodec.dequeueInputBuffer(500000);
						if (bufferIndex>=0) {
							mConvertor.translate(mBuffers[mTail], inputBuffers[bufferIndex]);
							mMediaCodec.queueInputBuffer(bufferIndex, 0, inputBuffers[bufferIndex].position(), mTimestamps[mTail], 0);
						} else {
							Log.e(TAG,"No buffer available !");
						}
					} finally {
						mCamera.addCallbackBuffer(mBuffers[mTail]);
						mTail++;
						if (mTail>=MAX) mTail = 0;
						mRemaining.release();
					}

				}
			}
		};

	}

}
