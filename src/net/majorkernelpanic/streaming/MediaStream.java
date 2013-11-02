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
import java.util.Random;

import net.majorkernelpanic.streaming.rtp.AbstractPacketizer;
import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaRecorder;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

/**
 * A MediaRecorder that streams what it records using a packetizer from the rtp package.
 * You can't use this class directly !
 */
public abstract class MediaStream implements Stream {

	protected static final String TAG = "MediaStream";

	/** MediaStream forwards data to a packetizer through a LocalSocket. */
	public static final byte MODE_MEDIARECORDER_API = 0x01;

	/** MediaStream uses the new MediaCodec API introduced in JB 4.1 to stream audio/video. */
	public static final byte MODE_MEDIACODEC_API = 0x02;
	
	/** MediaStream uses the new features of the MediaCodec API introduced in JB 4.3 to stream audio/video. */
	public static final byte MODE_MEDIACODEC_API_2 = 0x05;

	/** The packetizer that will read the output of the camera and send RTP packets over the networkd. */
	protected AbstractPacketizer mPacketizer = null;

	protected MediaRecorder mMediaRecorder;
	protected MediaCodec mMediaCodec;

	private int mSocketId;

	protected boolean mStreaming = false;
	protected byte mMode = MODE_MEDIARECORDER_API;
	protected static byte sSuggestedMode = MODE_MEDIARECORDER_API; 

	private LocalServerSocket mLss = null;
	protected LocalSocket mReceiver, mSender = null;

	protected int mRtpPort = 0, mRtcpPort = 0;
	protected InetAddress mDestination;

	static {
		// We determine wether or not the MediaCodec API should be used
		try {
			Class.forName("android.media.MediaCodec");
			// Will be set to MODE_MEDIACODEC_API at some point...
			sSuggestedMode = MODE_MEDIACODEC_API;
			Log.i(TAG,"Phone supports the MediaCoded API");
		} catch (ClassNotFoundException e) {
			sSuggestedMode = MODE_MEDIARECORDER_API;
			Log.i(TAG,"Phone does not support the MediaCodec API");
		}
	}
	
	public MediaStream() {
		mMode = sSuggestedMode;
	}
	
	/** 
	 * Sets the destination ip address of the stream.
	 * @param dest The destination address of the stream 
	 */	
	public void setDestinationAddress(InetAddress dest) {
		mDestination = dest;
	}

	/** 
	 * Sets the destination ports of the stream.
	 * If an odd number is supplied for the destination port then the next 
	 * lower even number will be used for RTP and it will be used for RTCP.
	 * If an even number is supplied, it will be used for RTP and the next odd
	 * number will be used for RTCP.
	 * @param dport The destination port
	 */
	public void setDestinationPorts(int dport) {
		if (dport % 2 == 1) {
			mRtpPort = dport-1;
			mRtcpPort = dport;
		} else {
			mRtpPort = dport;
			mRtcpPort = dport+1;
		}
	}

	/**
	 * Sets the destination ports of the stream.
	 * @param rtpPort Destination port that will be used for RTP
	 * @param rtcpPort Destination port that will be used for RTCP
	 */
	public void setDestinationPorts(int rtpPort, int rtcpPort) {
		mRtpPort = rtpPort;
		mRtcpPort = rtcpPort;
	}	

	/**
	 * Sets the Time To Live of packets sent over the network.
	 * @param ttl The time to live
	 * @throws IOException
	 */
	public void setTimeToLive(int ttl) throws IOException {
		mPacketizer.setTimeToLive(ttl);
	}

	/** 
	 * Returns a pair of destination ports, the first one is the 
	 * one used for RTP and the second one is used for RTCP. 
	 **/
	public int[] getDestinationPorts() {
		return new int[] {
				mRtpPort,
				mRtcpPort
		};
	}

	/** 
	 * Returns a pair of source ports, the first one is the 
	 * one used for RTP and the second one is used for RTCP. 
	 **/	
	public int[] getLocalPorts() {
		return new int[] {
				this.mPacketizer.getRtpSocket().getLocalPort(),
				this.mPacketizer.getRtcpSocket().getLocalPort()
		};
	}

	/**
	 * Sets the mode of the {@link MediaStream}.
	 * If the mode is set to {@link #MODE_MEDIARECORDER_API}, video is forwarded to a UDP socket.
	 * @param mode Either {@link #MODE_MEDIARECORDER_API} or {@link #MODE_MEDIACODEC_API} 
	 */
	public void setMode(byte mode) throws IllegalStateException {
		if (mStreaming) throw new IllegalStateException("Can't be called while streaming !");
		this.mMode = mode;
	}

	/**
	 * Returns the packetizer associated with the {@link MediaStream}.
	 * @return The packetizer
	 */
	public AbstractPacketizer getPacketizer() { 
		return mPacketizer;
	}

	/**
	 * Returns an approximation of the bitrate of the stream in bit per seconde.
	 */
	public long getBitrate() {
		return !mStreaming ? 0 : mPacketizer.getRtpSocket().getBitrate(); 
	}

	/**
	 * Indicates if the {@link MediaStream} is streaming.
	 * @return A boolean indicating if the {@link MediaStream} is streaming
	 */
	public boolean isStreaming() {
		return mStreaming;
	}

	/** Starts the stream. */
	public synchronized void start() throws IllegalStateException, IOException {

		if (mDestination==null)
			throw new IllegalStateException("No destination ip address set for the stream !");

		if (mRtpPort<=0 || mRtcpPort<=0)
			throw new IllegalStateException("No destination ports set for the stream !");

		if ((mMode&MODE_MEDIACODEC_API)!=0) {
			encodeWithMediaCodec();
		} else {
			encodeWithMediaRecorder();
		}
		
	}

	/** Stops the stream. */
	@SuppressLint("NewApi")
	public synchronized  void stop() {
		if (mStreaming) {
			mPacketizer.stop();
			try {
				if (mMode==MODE_MEDIARECORDER_API) {
					mMediaRecorder.stop();
					mMediaRecorder.release();
					mMediaRecorder = null;
				} else {
					mMediaCodec.stop();
					mMediaCodec.release();
					mMediaCodec = null;
				}
				closeSockets();
			} catch (Exception ignore) {}	
			mStreaming = false;
		}
	}

	protected abstract void encodeWithMediaRecorder() throws IOException;
	
	protected abstract void encodeWithMediaCodec() throws IOException;
	
	/**
	 * Returns the SSRC of the underlying {@link net.majorkernelpanic.streaming.rtp.RtpSocket}.
	 * @return the SSRC of the stream
	 */
	public int getSSRC() {
		return getPacketizer().getSSRC();
	}

	public abstract String generateSessionDescription()  throws IllegalStateException, IOException;

	protected void createSockets() throws IOException {

		final String LOCAL_ADDR = "net.majorkernelpanic.streaming-";

		for (int i=0;i<10;i++) {
			try {
				mSocketId = new Random().nextInt();
				mLss = new LocalServerSocket(LOCAL_ADDR+mSocketId);
				break;
			} catch (IOException e1) {}
		}

		mReceiver = new LocalSocket();
		mReceiver.connect( new LocalSocketAddress(LOCAL_ADDR+mSocketId) );
		mReceiver.setReceiveBufferSize(500000);
		mSender = mLss.accept();
		mSender.setSendBufferSize(500000);
	}

	protected void closeSockets() {
		try {
			mSender.close();
			mSender = null;
			mReceiver.close();
			mReceiver = null;
			mLss.close();
			mLss = null;
		} catch (Exception ignore) {}
	}

}
