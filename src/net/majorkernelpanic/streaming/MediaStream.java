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
import android.media.MediaRecorder;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

/**
 * A MediaRecorder that streams what it records using a packetizer from the rtp package.
 * You can't use this class directly !
 */
public abstract class MediaStream implements Stream {

	protected static final String TAG = "MediaStream";

	/** MediaStream forwards data to a packetizer through a LocalSocket. */
	public static final int MODE_STREAMING = 0;

	/** MediaStream will just act as a regular MediaRecorder. */
	public static final int MODE_DEFAULT = 1;

	/** The packetizer that will read the output of the camera and send RTP packets over the networkd. */
	protected AbstractPacketizer mPacketizer = null;

	/** The underlying MediaRecorder. */
	protected MediaRecorder mMediaRecorder;

	private int mSocketId;

	protected boolean mStreaming = false, mModeDefaultWasUsed = false;
	protected int mode = MODE_STREAMING;

	private LocalServerSocket mLss = null;
	private LocalSocket mReceiver, mSender = null;

	private int mRtpPort = 0, mRtcpPort = 0;
	private InetAddress mDestination;

	public MediaStream() {

		mMediaRecorder = new MediaRecorder();

		for (int i=0;i<10;i++) {
			try {
				mSocketId = new Random().nextInt();
				mLss = new LocalServerSocket("net.majorkernelpanic.librtp-"+mSocketId);
				break;
			} catch (IOException e1) {}
		}

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
	 * If the mode is set to {@link #MODE_STREAMING}, video is forwarded to a UDP socket,
	 * and if the mode is {@link #MODE_DEFAULT}, video is recorded in a file.
	 * @param mode Either {@link #MODE_STREAMING} or {@link #MODE_DEFAULT} 
	 * @throws IllegalStateException
	 */
	public void setMode(int mode) throws IllegalStateException {
		if (!mStreaming) {
			this.mode = mode;
			if (mode == MODE_DEFAULT) mModeDefaultWasUsed = true;
		}
		else {
			throw new IllegalStateException("You can't call setMode() while streaming !");
		}
	}

	/**
	 * Returns the packetizer associated with the {@link MediaStream}.
	 * @return The packetizer
	 */
	public AbstractPacketizer getPacketizer() { 
		return mPacketizer;
	}

	/**
	 * Indicates if the {@link MediaStream} is streaming.
	 * @return A boolean indicating if the {@link MediaStream} is streaming
	 */
	public boolean isStreaming() {
		return mStreaming;
	}

	/** Prepares the stream. */
	public void prepare() throws IllegalStateException,IOException {
		if (mode==MODE_STREAMING) {
			createSockets();
			// We write the ouput of the camera in a local socket instead of a file !			
			// This one little trick makes streaming feasible quiet simply: data from the camera
			// can then be manipulated at the other end of the socket
			mMediaRecorder.setOutputFile(mSender.getFileDescriptor());
		}
		mMediaRecorder.prepare();
	}

	/** Starts the stream. */
	public void start() throws IllegalStateException {

		if (mode==MODE_STREAMING) {
			if (mPacketizer==null)
				throw new IllegalStateException("setPacketizer() should be called before start().");

			if (mDestination==null)
				throw new IllegalStateException("No destination ip address set for the stream !");

			if (mRtpPort<=0 || mRtcpPort<=0)
				throw new IllegalStateException("No destination ports set for the stream !");
		}

		mMediaRecorder.start();
		try {
			if (mode==MODE_STREAMING) {
				// mReceiver.getInputStream contains the data from the camera
				// the mPacketizer encapsulates this stream in an RTP stream and send it over the network
				mPacketizer.setDestination(mDestination, mRtpPort, mRtcpPort);
				mPacketizer.setInputStream(mReceiver.getInputStream());
				mPacketizer.start();
			}
			mStreaming = true;
		} catch (IOException e) {
			mMediaRecorder.stop();
			throw new IllegalStateException("Something happened with the local sockets :/ Start failed !");
		}
	}

	/** Stops the stream. */
	public void stop() {
		if (mStreaming) {
			try {
				mMediaRecorder.stop();
			}
			catch (Exception ignore) {}
			finally {
				closeSockets();
				if (mode==MODE_STREAMING) mPacketizer.stop();
				mStreaming = false;
			}
		}
		try {
			mMediaRecorder.reset();
		}
		catch (Exception ignore) {}	
	}

	/** 
	 * Releases ressources associated with the stream. 
	 * The object can not be used anymore when this function is called. 
	 **/
	public void release() {
		stop();
		try {
			mLss.close();
		}
		catch (Exception ignore) {}
		mMediaRecorder.release();
	}	

	/**
	 * Returns the SSRC of the underlying {@link net.majorkernelpanic.streaming.rtp.RtpSocket}.
	 * @return the SSRC of the stream
	 */
	public int getSSRC() {
		return getPacketizer().getSSRC();
	}

	public abstract String generateSessionDescription()  throws IllegalStateException, IOException;

	private void createSockets() throws IOException {
		mReceiver = new LocalSocket();
		mReceiver.connect( new LocalSocketAddress("net.majorkernelpanic.librtp-" + mSocketId ) );
		mReceiver.setReceiveBufferSize(500000);
		mSender = mLss.accept();
		mSender.setSendBufferSize(500000);
	}

	private void closeSockets() {
		try {
			mSender.close();
			mReceiver.close();
		} catch (Exception ignore) {}
	}

}
