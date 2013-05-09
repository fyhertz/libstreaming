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

package net.majorkernelpanic.streaming.audio;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;

import net.majorkernelpanic.streaming.exceptions.AACNotSupportedException;
import net.majorkernelpanic.streaming.rtp.AACADTSPacketizer;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.MediaRecorder;
import android.os.Environment;
import android.util.Log;

/**
 * A class for streaming AAC from the microphone of an android device using RTP.
 * Call {@link #setDestinationAddress(java.net.InetAddress)}, {@link #prepare()} & {@link #start()} and that's it !
 * Call {@link #stop()} to stop the stream.
 * Do not forget to call {@link #release()} when you're done. 
 */
public class AACStream extends AudioStream {

	public final static String TAG = "AACStream";

	/** MPEG-4 Audio Object Types supported by ADTS. **/
	private static final String[] sAudioObjectTypes = {
		"NULL",							  // 0
		"AAC Main",						  // 1
		"AAC LC (Low Complexity)",		  // 2
		"AAC SSR (Scalable Sample Rate)", // 3
		"AAC LTP (Long Term Prediction)"  // 4	
	};

	/** There are 13 supported frequencies by ADTS. **/
	private static final int[] sADTSSamplingRates = {
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

	private int mActualSamplingRate;
	private int mProfile, mSamplingRateIndex, mChannel, mConfig;
	private SharedPreferences mSettings = null;

	public AACStream() throws IOException {
		super();

		mPacketizer  = new AACADTSPacketizer();

		setAudioSource(MediaRecorder.AudioSource.CAMCORDER);

		try {
			Field name = MediaRecorder.OutputFormat.class.getField("AAC_ADTS");
			Log.d(TAG,"AAC ADTS seems to be supported: AAC_ADTS="+name.getInt(null));
			setOutputFormat(name.getInt(null));
		} catch (Exception e) {
			Log.e(TAG,"AAC ADTS not supported on this phone");
			throw new AACNotSupportedException();
		}

		setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
		setAudioSamplingRate(16000);

	}

	/**
	 * Some data (the actual sampling rate) needs to be stored once {@link #generateSessionDescription()} is called.
	 * @param prefs The SharedPreferences that will be used to store the sampling rate 
	 */
	public void setPreferences(SharedPreferences prefs) {
		mSettings = prefs;
	}

	public void prepare() throws IllegalStateException, IOException {
		testADTS();
		((AACADTSPacketizer)mPacketizer).setSamplingRate(mActualSamplingRate);
		super.prepare();

	}

	/**
	 * Returns a description of the stream using SDP. It can then be included in an SDP file.
	 * Will fail if called when streaming.
	 */
	public String generateSessionDescription() throws IllegalStateException, IOException {
		testADTS();

		// All the MIME types parameters used here are described in RFC 3640
		// SizeLength: 13 bits will be enough because ADTS uses 13 bits for frame length
		// config: contains the object type + the sampling rate + the channel number

		// TODO: streamType always 5 ? profile-level-id always 15 ?

		return "m=audio "+String.valueOf(getDestinationPorts()[0])+" RTP/AVP 96\r\n" +
		"a=rtpmap:96 mpeg4-generic/"+mActualSamplingRate+"\r\n"+
		"a=fmtp:96 streamtype=5; profile-level-id=15; mode=AAC-hbr; config="+Integer.toHexString(mConfig)+"; SizeLength=13; IndexLength=3; IndexDeltaLength=3;\r\n";
	}

	/** 
	 * Records a short sample of AAC ADTS from the microphone to find out what the sampling rate really is
	 * On some phone indeed, no error will be reported if the sampling rate used differs from the 
	 * one selected with setAudioSamplingRate 
	 * @throws IOException 
	 * @throws IllegalStateException
	 */
	private void testADTS() throws IllegalStateException, IOException {

		if (mSettings!=null) {
			if (mSettings.contains("aac-"+mSamplingRate)) {
				String[] s = mSettings.getString("aac-"+mSamplingRate, "").split(",");
				mActualSamplingRate = Integer.valueOf(s[0]);
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

		// That means the H264Stream will behave as a regular MediaRecorder object
		// it will not start the packetizer thread and can be used to save video in a file
		setMode(MODE_DEFAULT);
		mMediaRecorder.setOutputFile(TESTFILE);

		super.prepare();
		start();

		// We record for 1 sec
		// TODO: use the MediaRecorder.OnInfoListener
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {}

		stop();
		setMode(MODE_STREAMING);

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

		mSamplingRateIndex = (buffer[1]&0x3C) >> 2;
		mProfile = ( (buffer[1]&0xC0) >> 6 ) + 1 ;
		mChannel = (buffer[1]&0x01) << 2 | (buffer[2]&0xC0) >> 6 ;
		mActualSamplingRate = sADTSSamplingRates[mSamplingRateIndex];

		// 5 bits for the object type / 4 bits for the sampling rate / 4 bits for the channel / padding
		mConfig = mProfile<<11 | mSamplingRateIndex<<7 | mChannel<<3;

		Log.i(TAG,"MPEG VERSION: " + ( (buffer[0]&0x08) >> 3 ) );
		Log.i(TAG,"PROTECTION: " + (buffer[0]&0x01) );
		Log.i(TAG,"PROFILE: " + sAudioObjectTypes[ mProfile ] );
		Log.i(TAG,"SAMPLING FREQUENCY: " + mActualSamplingRate );
		Log.i(TAG,"CHANNEL: " + mChannel );

		raf.close();

		if (mSettings!=null) {
			Editor editor = mSettings.edit();
			editor.putString("aac-"+mSamplingRate, mActualSamplingRate+","+mConfig+","+mChannel);
			editor.commit();
		}

		if (!file.delete()) Log.e(TAG,"Temp file could not be erased");

	}

}
