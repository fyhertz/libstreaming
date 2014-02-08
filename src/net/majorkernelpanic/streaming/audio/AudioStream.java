/*
 * Copyright (C) 2011-2014 GUIGUI Simon, fyhertz@gmail.com
 * 
 * This file is part of libstreaming (https://github.com/fyhertz/libstreaming)
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

package net.majorkernelpanic.streaming.audio;

import java.io.IOException;

import net.majorkernelpanic.streaming.MediaStream;
import android.media.MediaRecorder;
import android.util.Log;

/** 
 * Don't use this class directly.
 */
public abstract class AudioStream  extends MediaStream {

	protected int mAudioSource;
	protected int mOutputFormat;
	protected int mAudioEncoder;
	protected AudioQuality mRequestedQuality = AudioQuality.DEFAULT_AUDIO_QUALITY.clone();
	protected AudioQuality mQuality = mRequestedQuality.clone();
	
	public AudioStream() {
		setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
	}
	
	public void setAudioSource(int audioSource) {
		mAudioSource = audioSource;
	}

	public void setAudioQuality(AudioQuality quality) {
		mRequestedQuality = quality;
	}
	
	/** 
	 * Returns the quality of the stream.  
	 */
	public AudioQuality getAudioQuality() {
		return mQuality;
	}	
	
	protected void setAudioEncoder(int audioEncoder) {
		mAudioEncoder = audioEncoder;
	}
	
	protected void setOutputFormat(int outputFormat) {
		mOutputFormat = outputFormat;
	}
	
	@Override
	protected void encodeWithMediaRecorder() throws IOException {
		
		// We need a local socket to forward data output by the camera to the packetizer
		createSockets();

		Log.v(TAG,"Requested audio with "+mQuality.bitRate/1000+"kbps"+" at "+mQuality.samplingRate/1000+"kHz");
		
		mMediaRecorder = new MediaRecorder();
		mMediaRecorder.setAudioSource(mAudioSource);
		mMediaRecorder.setOutputFormat(mOutputFormat);
		mMediaRecorder.setAudioEncoder(mAudioEncoder);
		mMediaRecorder.setAudioChannels(1);
		mMediaRecorder.setAudioSamplingRate(mQuality.samplingRate);
		mMediaRecorder.setAudioEncodingBitRate(mQuality.bitRate);
		
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
	
}
