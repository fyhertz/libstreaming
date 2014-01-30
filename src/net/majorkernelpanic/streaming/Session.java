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
import java.util.concurrent.Semaphore;

import net.majorkernelpanic.streaming.audio.AudioQuality;
import net.majorkernelpanic.streaming.audio.AudioStream;
import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.video.VideoQuality;
import net.majorkernelpanic.streaming.video.VideoStream;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * This class makes use of all the streaming package.
 * It represents a streaming session between a client and the phone.
 * A stream is designated by the word "track" in this class.
 * To add tracks to the session you need to call addVideoTrack() or addAudioTrack().
 */
public class Session {

	public final static String TAG = "Session";

	private InetAddress mOrigin;
	private InetAddress mDestination;
	private int mTimeToLive = 64;
	private long mTimestamp;
	private Context mContext = null;
	private WifiManager.MulticastLock mLock = null;

	private AudioStream mAudioStream = null;
	private VideoStream mVideoStream = null;

	private Callback mCallback;
	private Handler mMainHandler;
	private Handler mHandler;

	/** 
	 * Creates a streaming session that can be customized by adding tracks.
	 */
	public Session() {
		long uptime = System.currentTimeMillis();
		mMainHandler = new Handler(Looper.getMainLooper());
		mTimestamp = (uptime/1000)<<32 & (((uptime-((uptime/1000)*1000))>>32)/1000); // NTP timestamp
		try {
			mOrigin = InetAddress.getLocalHost();
		} catch (Exception ignore) {
			mOrigin = null;
		}

		final Semaphore signal = new Semaphore(0);

		new Thread(new Runnable() {
			@Override
			public void run() {
				Looper.prepare();
				mHandler = new Handler();
				signal.release();
				Looper.loop();
				Log.e(TAG,"Thread stopped !");
			}
		}).start();		

		signal.acquireUninterruptibly();	

	}

	/**
	 * The callback interface you need to implement to get some feedback
	 * Those will be called from the UI thread.
	 */
	public interface Callback {

		/** Called periodically when streaming. */
		public void onBitrareUpdate(long bitrate);

		/** Called when the camera of the phone is alread being used by some app. */
		public void onCameraInUse();

		/** Called when some error occurs. */
		public void onError(Exception e);

	}

	public void addAudioTrack(AudioStream track) {
		removeAudioTrack();
		mAudioStream = track;
	}

	public void addVideoTrack(VideoStream track) {
		removeVideoTrack();
		mVideoStream = track;
	}

	public void removeAudioTrack() {
		if (mAudioStream != null) {
			mAudioStream.stop();
			mAudioStream = null;
		}
	}

	public void removeVideoTrack() {
		if (mVideoStream != null) {
			mVideoStream.stop();
			mVideoStream = null;
		}
	}

	public AudioStream getAudioTrack() {
		return mAudioStream;
	}

	public VideoStream getVideoTrack() {
		return mVideoStream;
	}	

	/** 
	 * Reference to the context is needed to aquire a MulticastLock. 
	 * If the Session has a multicast destination is address such a lock will be aquired.
	 * @param context reference to the application context 
	 **/
	public void setContext(Context context) {
		mContext = context;
	}

	/** 
	 * The origin address of the session.
	 * It appears in the sessionn description.
	 * @param origin The origin address
	 */
	public void setOrigin(InetAddress origin) {
		mOrigin = origin;
	}	

	/** 
	 * The destination address for all the streams of the session.
	 * You must stop all tracks before calling this method.
	 * @param destination The destination address
	 */
	public void setDestination(InetAddress destination) {
		mDestination =  destination;
	}

	/** 
	 * Set the TTL of all packets sent during the session.
	 * You must call this method before adding tracks to the session.
	 * @param ttl The Time To Live
	 */
	public void setTimeToLive(int ttl) {
		mTimeToLive = ttl;
	}

	/** 
	 * Sets the configuration of the stream. You can call this method at any time 
	 * and changes will take effect next time you call {@link #configure()}.
	 * @param videoQuality Quality of the stream
	 */
	public void setVideoQuality(VideoQuality quality) {
		if (mVideoStream != null) {
			mVideoStream.setVideoQuality(quality);
		}
	}
	
	/**
	 * Sets a Surface to show a preview of recorded media (video). 
	 * You can call this method at any time and changes will take effect next time you call {@link #start()}.
	 */
	public void setSurfaceView(final SurfaceView view) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				if (mVideoStream != null) {
					mVideoStream.setSurfaceView(view);
				}
			}				
		});
	}
	
	/** 
	 * Sets the configuration of the stream. You can call this method at any time 
	 * and changes will take effect next time you call {@link #configure()}.
	 * @param videoQuality Quality of the stream
	 */
	public void setAudioQuality(AudioQuality quality) {
		if (mVideoStream != null) {
			mAudioStream.setAudioQuality(quality);
		}
	}
	
	/** 
	 * Returns a Session Description that can be stored in a file or sent to a client with RTSP.
	 * @return The Session Description.
	 * @throws IllegalStateException Thrown when {@link #setDestination(InetAddress)} has never been called.
	 */
	public String getSessionDescription() {
		StringBuilder sessionDescription = new StringBuilder();
		if (mDestination==null) {
			throw new IllegalStateException("setDestination() has not been called !");
		}
		sessionDescription.append("v=0\r\n");
		// TODO: Add IPV6 support
		sessionDescription.append("o=- "+mTimestamp+" "+mTimestamp+" IN IP4 "+(mOrigin==null?"127.0.0.1":mOrigin.getHostAddress())+"\r\n");
		sessionDescription.append("s=Unnamed\r\n");
		sessionDescription.append("i=N/A\r\n");
		sessionDescription.append("c=IN IP4 "+mDestination.getHostAddress()+"\r\n");
		// t=0 0 means the session is permanent (we don't know when it will stop)
		sessionDescription.append("t=0 0\r\n");
		sessionDescription.append("a=recvonly\r\n");
		// Prevents two different sessions from using the same peripheral at the same time
		if (mAudioStream != null) {
			sessionDescription.append(mAudioStream.getSessionDescription());
			sessionDescription.append("a=control:trackID="+0+"\r\n");
		}
		if (mVideoStream != null) {
			sessionDescription.append(mVideoStream.getSessionDescription());
			sessionDescription.append("a=control:trackID="+1+"\r\n");
		}			
		return sessionDescription.toString();
	}

	public InetAddress getDestination() {
		return mDestination;
	}

	public boolean trackExists(int id) {
		if (id==0) 
			return mAudioStream!=null;
		else
			return mVideoStream!=null;
	}

	public Stream getTrack(int id) {
		if (id==0)
			return mAudioStream;
		else
			return mVideoStream;
	}

	/**
	 * Returns an approximation of the bandwidth consumed by the session in bit per seconde. 
	 */
	public long getBitrate() {
		long sum = 0;
		if (mAudioStream != null) sum += mAudioStream.getBitrate();
		if (mVideoStream != null) sum += mVideoStream.getBitrate();
		return sum;
	}

	/** Indicates if a track is currently running. */
	public boolean isStreaming() {
		if ( (mAudioStream!=null && mAudioStream.isStreaming()) || (mVideoStream!=null && mVideoStream.isStreaming()) )
			return true;
		else 
			return false;
	}

	/** 
	 * Configures one of the stream of the session.
	 * @param id The id of the stream to configure
	 **/
	public void configure(final int id) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				Stream stream = id==0 ? mAudioStream : mVideoStream;
				if (stream!=null && !stream.isStreaming()) {
					try {
						stream.configure();
					} catch (IOException e) {
						postError(e);
					} catch (IllegalStateException e) {
						postError(e);
					}
				}
			}				
		});
	}	
	
	/** Configures all streams. */
	public void configure()  {
		configure(0);
		configure(1);
	}	
	
	/** 
	 * Starts one of the stream of the session.
	 * @param id The id of the stream to start
	 **/
	public void start(final int id) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				if (!isStreaming()) mMainHandler.post(mUpdateBitrate);
				Stream stream = id==0 ? mAudioStream : mVideoStream;
				if (stream!=null && !stream.isStreaming()) {
					try {
						stream.setTimeToLive(mTimeToLive);
						stream.setDestinationAddress(mDestination);
						stream.start();
					} catch (IOException e) {
						postError(e);
					} catch (RuntimeException e) {
						postError(e);
					}
				}
			}				
		});
	}

	/** Starts all streams. */
	public void start()  {
		if (mDestination.isMulticastAddress()) {
			if (mContext != null) {
				// Aquire a MulticastLock to allow multicasted UDP packet
				WifiManager wifi = (WifiManager)mContext.getSystemService( Context.WIFI_SERVICE );
				if(wifi != null){
					mLock = wifi.createMulticastLock("net.majorkernelpanic.streaming");
					mLock.acquire();
				}
			}
		}
		start(0);
		start(1);
	}

	/** 
	 * Stops one stream.
	 * @param id The id of the stream to stop
	 **/	
	public void stop(int id) {
		// Release the MulticastLock if one was previoulsy aquired
		if (mLock != null) {
			if (mLock.isHeld()) {
				mLock.release();
			}
			mLock = null;
		}
		Stream stream = id==0 ? mAudioStream : mVideoStream;
		if (stream!=null) {
			stream.stop();
		}
	}	

	/** Stops all existing streams. */
	public void stop() {
		stop(0);
		stop(1);
	}

	public void switchCamera() {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				if (mVideoStream != null) {
					try {
						mVideoStream.switchCamera();
					} catch (RuntimeException e) {
						postError(e);
					} catch (IOException e) {
						postError(e);
					}
				}
			}
		});
	}
	
	public void toggleFlash() {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				if (mVideoStream != null) {
					try {
						mVideoStream.toggleFlash();
					} catch (RuntimeException e) {
						postError(e);
					}
				}
			}
		});
	}	
	
	/** Deletes all existing tracks & release associated resources. */
	public void flush() {
		removeAudioTrack();
		removeVideoTrack();
	}

	private void postError(final Exception e) {
		mMainHandler.post(new Runnable() {
			@Override
			public void run() {
				if (mCallback != null) {
					mCallback.onError(e); 
				}
			}
		});
	}	

	private Runnable mUpdateBitrate = new Runnable() {
		@Override
		public void run() {
			if (isStreaming()) { 
				if (mCallback != null) {
					mCallback.onBitrareUpdate(getBitrate()); 
				}
				mMainHandler.postDelayed(mUpdateBitrate, 500);
			}
		}
	};

}
