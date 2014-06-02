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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.nio.ByteBuffer;

import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.rtp.AACADTSPacketizer;
import net.majorkernelpanic.streaming.rtp.AACLATMPacketizer;
import net.majorkernelpanic.streaming.rtp.MediaCodecInputStream;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.service.textservice.SpellCheckerService.Session;
import android.util.Log;

/**
 * A class for streaming AAC from the camera of an android device using RTP.
 * You should use a {@link Session} instantiated with {@link SessionBuilder} instead of using this class directly.
 * Call {@link #setDestinationAddress(InetAddress)}, {@link #setDestinationPorts(int)} and {@link #setAudioQuality(AudioQuality)}
 * to configure the stream. You can then call {@link #start()} to start the RTP stream.
 * Call {@link #stop()} to stop the stream.
 */
public class AACStream extends AudioStream {

	public final static String TAG = "AACStream";

	/** MPEG-4 Audio Object Types supported by ADTS. **/
	private static final String[] AUDIO_OBJECT_TYPES = {
		"NULL",							  // 0
		"AAC Main",						  // 1
		"AAC LC (Low Complexity)",		  // 2
		"AAC SSR (Scalable Sample Rate)", // 3
		"AAC LTP (Long Term Prediction)"  // 4	
	};

	/** There are 13 supported frequencies by ADTS. **/
	public static final int[] AUDIO_SAMPLING_RATES = {
		96000, // 0
		88200, // 1
		64000, // 2
		48000, // 3
		44100, // 4
		32000, // 5
		24000, // 6
		22050, // 7
		16000, // 8
		12000, // 9
		11025, // 10
		8000,  // 11
		7350,  // 12
		-1,   // 13
		-1,   // 14
		-1,   // 15
	};

	private String mSessionDescription = null;
	private int mProfile, mSamplingRateIndex, mChannel, mConfig;
	private SharedPreferences mSettings = null;
	private AudioRecord mAudioRecord = null;
	private Thread mThread = null;

	public AACStream() {
		super();

		if (!AACStreamingSupported()) {
			Log.e(TAG,"AAC not supported on this phone");
			throw new RuntimeException("AAC not supported by this phone !");
		} else {
			Log.d(TAG,"AAC supported on this phone");
		}

	}

	private static boolean AACStreamingSupported() {
		if (Build.VERSION.SDK_INT<14) return false;
		try {
			MediaRecorder.OutputFormat.class.getField("AAC_ADTS");
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Some data (the actual sampling rate used by the phone and the AAC profile) needs to be stored once {@link #getSessionDescription()} is called.
	 * @param prefs The SharedPreferences that will be used to store the sampling rate 
	 */
	public void setPreferences(SharedPreferences prefs) {
		mSettings = prefs;
	}

	@Override
	public synchronized void start() throws IllegalStateException, IOException {
		if (!mStreaming) {
			configure();
			super.start();
		}
	}

	public synchronized void configure() throws IllegalStateException, IOException {
		super.configure();
		mQuality = mRequestedQuality.clone();

		// Checks if the user has supplied an exotic sampling rate
		int i=0;
		for (;i<AUDIO_SAMPLING_RATES.length;i++) {
			if (AUDIO_SAMPLING_RATES[i] == mQuality.samplingRate) {
				mSamplingRateIndex = i;
				break;
			}
		}
		// If he did, we force a reasonable one: 16 kHz
		if (i>12) mQuality.samplingRate = 16000;

		if (mMode != mRequestedMode || mPacketizer==null) {
			mMode = mRequestedMode;
			if (mMode == MODE_MEDIARECORDER_API) {
				mPacketizer = new AACADTSPacketizer();
			} else { 
				mPacketizer = new AACLATMPacketizer();
			}
			mPacketizer.setDestination(mDestination, mRtpPort, mRtcpPort);
			mPacketizer.getRtpSocket().setOutputStream(mOutputStream, mChannelIdentifier);
		}

		if (mMode == MODE_MEDIARECORDER_API) {

			testADTS();

			// All the MIME types parameters used here are described in RFC 3640
			// SizeLength: 13 bits will be enough because ADTS uses 13 bits for frame length
			// config: contains the object type + the sampling rate + the channel number

			// TODO: streamType always 5 ? profile-level-id always 15 ?

			mSessionDescription = "m=audio "+String.valueOf(getDestinationPorts()[0])+" RTP/AVP 96\r\n" +
					"a=rtpmap:96 mpeg4-generic/"+mQuality.samplingRate+"\r\n"+
					"a=fmtp:96 streamtype=5; profile-level-id=15; mode=AAC-hbr; config="+Integer.toHexString(mConfig)+"; SizeLength=13; IndexLength=3; IndexDeltaLength=3;\r\n";

		} else {

			mProfile = 2; // AAC LC
			mChannel = 1;
			mConfig = (mProfile & 0x1F) << 11 | (mSamplingRateIndex & 0x0F) << 7 | (mChannel & 0x0F) << 3;

			mSessionDescription = "m=audio "+String.valueOf(getDestinationPorts()[0])+" RTP/AVP 96\r\n" +
					"a=rtpmap:96 mpeg4-generic/"+mQuality.samplingRate+"\r\n"+
					"a=fmtp:96 streamtype=5; profile-level-id=15; mode=AAC-hbr; config="+Integer.toHexString(mConfig)+"; SizeLength=13; IndexLength=3; IndexDeltaLength=3;\r\n";			

		}

	}

	@Override
	protected void encodeWithMediaRecorder() throws IOException {
		testADTS();
		((AACADTSPacketizer)mPacketizer).setSamplingRate(mQuality.samplingRate);
		super.encodeWithMediaRecorder();
	}

	@Override
	@SuppressLint({ "InlinedApi", "NewApi" })
	protected void encodeWithMediaCodec() throws IOException {

		final int bufferSize = AudioRecord.getMinBufferSize(mQuality.samplingRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)*2;

		((AACLATMPacketizer)mPacketizer).setSamplingRate(mQuality.samplingRate);

		mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, mQuality.samplingRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
		mMediaCodec = MediaCodec.createEncoderByType("audio/mp4a-latm");
		MediaFormat format = new MediaFormat();
		format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
		format.setInteger(MediaFormat.KEY_BIT_RATE, mQuality.bitRate);
		format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1);
		format.setInteger(MediaFormat.KEY_SAMPLE_RATE, mQuality.samplingRate);
		format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
		format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize);
		mMediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		mAudioRecord.startRecording();
		mMediaCodec.start();

		final MediaCodecInputStream inputStream = new MediaCodecInputStream(mMediaCodec);
		final ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();

		mThread = new Thread(new Runnable() {
			@Override
			public void run() {
				int len = 0, bufferIndex = 0;
				try {
					while (!Thread.interrupted()) {
						bufferIndex = mMediaCodec.dequeueInputBuffer(10000);
						if (bufferIndex>=0) {
							inputBuffers[bufferIndex].clear();
							len = mAudioRecord.read(inputBuffers[bufferIndex], bufferSize);
							if (len ==  AudioRecord.ERROR_INVALID_OPERATION || len == AudioRecord.ERROR_BAD_VALUE) {
								Log.e(TAG,"An error occured with the AudioRecord API !");
							} else {
								//Log.v(TAG,"Pushing raw audio to the decoder: len="+len+" bs: "+inputBuffers[bufferIndex].capacity());
								mMediaCodec.queueInputBuffer(bufferIndex, 0, len, System.nanoTime()/1000, 0);
							}
						}
					}
				} catch (RuntimeException e) {
					e.printStackTrace();
				}
			}
		});

		mThread.start();

		// The packetizer encapsulates this stream in an RTP stream and send it over the network
		mPacketizer.setInputStream(inputStream);
		mPacketizer.start();

		mStreaming = true;

	}

	/** Stops the stream. */
	public synchronized void stop() {
		if (mStreaming) {
			if (mMode==MODE_MEDIACODEC_API) {
				Log.d(TAG, "Interrupting threads...");
				mThread.interrupt();
				mAudioRecord.stop();
				mAudioRecord.release();
				mAudioRecord = null;
			}
			super.stop();
		}
	}

	/**
	 * Returns a description of the stream using SDP. It can then be included in an SDP file.
	 * Will fail if called when streaming.
	 */
	public String getSessionDescription() throws IllegalStateException {
		if (mSessionDescription == null) throw new IllegalStateException("You need to call configure() first !");
		return mSessionDescription;
	}

	/** 
	 * Records a short sample of AAC ADTS from the microphone to find out what the sampling rate really is
	 * On some phone indeed, no error will be reported if the sampling rate used differs from the 
	 * one selected with setAudioSamplingRate 
	 * @throws IOException 
	 * @throws IllegalStateException
	 */
	@SuppressLint("InlinedApi")
	private void testADTS() throws IllegalStateException, IOException {
		
		setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
		try {
			Field name = MediaRecorder.OutputFormat.class.getField("AAC_ADTS");
			setOutputFormat(name.getInt(null));
		}
		catch (Exception ignore) {
			setOutputFormat(6);
		}

		String key = PREF_PREFIX+"aac-"+mQuality.samplingRate;

		if (mSettings!=null) {
			if (mSettings.contains(key)) {
				String[] s = mSettings.getString(key, "").split(",");
				mQuality.samplingRate = Integer.valueOf(s[0]);
				mConfig = Integer.valueOf(s[1]);
				mChannel = Integer.valueOf(s[2]);
				return;
			}
		}

		final String TESTFILE = Environment.getExternalStorageDirectory().getPath()+"/spydroid-test.adts";

		if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			throw new IllegalStateException("No external storage or external storage not ready !");
		}

		// The structure of an ADTS packet is described here: http://wiki.multimedia.cx/index.php?title=ADTS

		// ADTS header is 7 or 9 bytes long
		byte[] buffer = new byte[9];

		mMediaRecorder = new MediaRecorder();
		mMediaRecorder.setAudioSource(mAudioSource);
		mMediaRecorder.setOutputFormat(mOutputFormat);
		mMediaRecorder.setAudioEncoder(mAudioEncoder);
		mMediaRecorder.setAudioChannels(1);
		mMediaRecorder.setAudioSamplingRate(mQuality.samplingRate);
		mMediaRecorder.setAudioEncodingBitRate(mQuality.bitRate);
		mMediaRecorder.setOutputFile(TESTFILE);
		mMediaRecorder.setMaxDuration(1000);
		mMediaRecorder.prepare();
		mMediaRecorder.start();

		// We record for 1 sec
		// TODO: use the MediaRecorder.OnInfoListener
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {}

		mMediaRecorder.stop();
		mMediaRecorder.release();
		mMediaRecorder = null;

		File file = new File(TESTFILE);
		RandomAccessFile raf = new RandomAccessFile(file, "r");

		// ADTS packets start with a sync word: 12bits set to 1
		while (true) {
			if ( (raf.readByte()&0xFF) == 0xFF ) {
				buffer[0] = raf.readByte();
				if ( (buffer[0]&0xF0) == 0xF0) break;
			}
		}

		raf.read(buffer,1,5);

		mSamplingRateIndex = (buffer[1]&0x3C)>>2 ;
		mProfile = ( (buffer[1]&0xC0) >> 6 ) + 1 ;
		mChannel = (buffer[1]&0x01) << 2 | (buffer[2]&0xC0) >> 6 ;
		mQuality.samplingRate = AUDIO_SAMPLING_RATES[mSamplingRateIndex];

		// 5 bits for the object type / 4 bits for the sampling rate / 4 bits for the channel / padding
		mConfig = (mProfile & 0x1F) << 11 | (mSamplingRateIndex & 0x0F) << 7 | (mChannel & 0x0F) << 3;

		Log.i(TAG,"MPEG VERSION: " + ( (buffer[0]&0x08) >> 3 ) );
		Log.i(TAG,"PROTECTION: " + (buffer[0]&0x01) );
		Log.i(TAG,"PROFILE: " + AUDIO_OBJECT_TYPES[ mProfile ] );
		Log.i(TAG,"SAMPLING FREQUENCY: " + mQuality.samplingRate );
		Log.i(TAG,"CHANNEL: " + mChannel );

		raf.close();

		if (mSettings!=null) {
			Editor editor = mSettings.edit();
			editor.putString(key, mQuality.samplingRate+","+mConfig+","+mChannel);
			editor.commit();
		}

		if (!file.delete()) Log.e(TAG,"Temp file could not be erased");

	}

}
