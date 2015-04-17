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

import java.util.ArrayList;
import java.util.HashMap;

import android.annotation.SuppressLint;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.util.Log;
import android.util.SparseArray;

@SuppressLint("InlinedApi")
public class CodecManager {

	public final static String TAG = "CodecManager";
	
	public static final int[] SUPPORTED_COLOR_FORMATS = {
		MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
		MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
	};

	/**
	 * There currently is no way to know if an encoder is software or hardware from the MediaCodecInfo class,
	 * so we need to maintain a list of known software encoders.
	 */
	public static final String[] SOFTWARE_ENCODERS = {
		"OMX.google.h264.encoder"
	};

	/**
	 * Contains a list of encoders and color formats that we may use with a {@link CodecManager.Translator}.  
	 */
	static class Codecs {
		/** A hardware encoder supporting a color format we can use. */
		public String hardwareCodec;
		public int hardwareColorFormat;
		/** A software encoder supporting a color format we can use. */
		public String softwareCodec;
		public int softwareColorFormat;
	}

	/**
	 *  Contains helper functions to choose an encoder and a color format.
	 */
	static class Selector {

		private static HashMap<String,SparseArray<ArrayList<String>>> sHardwareCodecs = new HashMap<String, SparseArray<ArrayList<String>>>();
		private static HashMap<String,SparseArray<ArrayList<String>>> sSoftwareCodecs = new HashMap<String, SparseArray<ArrayList<String>>>();

		/**
		 * Determines the most appropriate encoder to compress the video from the Camera
		 */
		public static Codecs findCodecsFormMimeType(String mimeType, boolean tryColorFormatSurface) {
			findSupportedColorFormats(mimeType);
			SparseArray<ArrayList<String>> hardwareCodecs = sHardwareCodecs.get(mimeType);
			SparseArray<ArrayList<String>> softwareCodecs = sSoftwareCodecs.get(mimeType);
			Codecs list = new Codecs();

			// On devices running 4.3, we need an encoder supporting the color format used to work with a Surface
			if (Build.VERSION.SDK_INT>=18 && tryColorFormatSurface) {
				int colorFormatSurface = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
				try {
					// We want a hardware encoder
					list.hardwareCodec = hardwareCodecs.get(colorFormatSurface).get(0);
					list.hardwareColorFormat = colorFormatSurface;
				} catch (Exception e) {}
				try {
					// We want a software encoder
					list.softwareCodec = softwareCodecs.get(colorFormatSurface).get(0);
					list.softwareColorFormat = colorFormatSurface;
				} catch (Exception e) {}

				if (list.hardwareCodec != null) {
					Log.v(TAG,"Choosen primary codec: "+list.hardwareCodec+" with color format: "+list.hardwareColorFormat);
				} else {
					Log.e(TAG,"No supported hardware codec found !");
				}
				if (list.softwareCodec != null) {
					Log.v(TAG,"Choosen secondary codec: "+list.hardwareCodec+" with color format: "+list.hardwareColorFormat);
				} else {
					Log.e(TAG,"No supported software codec found !");
				}
				return list;
			}

			for (int i=0;i<SUPPORTED_COLOR_FORMATS.length;i++) {
				try {
					list.hardwareCodec = hardwareCodecs.get(SUPPORTED_COLOR_FORMATS[i]).get(0);
					list.hardwareColorFormat = SUPPORTED_COLOR_FORMATS[i];
					break;
				} catch (Exception e) {}
			}
			for (int i=0;i<SUPPORTED_COLOR_FORMATS.length;i++) {
				try {
					list.softwareCodec = softwareCodecs.get(SUPPORTED_COLOR_FORMATS[i]).get(0);
					list.softwareColorFormat = SUPPORTED_COLOR_FORMATS[i];
					break;
				} catch (Exception e) {}
			}

			if (list.hardwareCodec != null) {
				Log.v(TAG,"Choosen primary codec: "+list.hardwareCodec+" with color format: "+list.hardwareColorFormat);
			} else {
				Log.e(TAG,"No supported hardware codec found !");
			}
			if (list.softwareCodec != null) {
				Log.v(TAG,"Choosen secondary codec: "+list.hardwareCodec+" with color format: "+list.softwareColorFormat);
			} else {
				Log.e(TAG,"No supported software codec found !");
			}

			return list;
		}			

		/** 
		 * Returns an associative array of the supported color formats and the names of the encoders for a given mime type
		 * This can take up to sec on certain phones the first time you run it...
		 **/
		@SuppressLint("NewApi")
		static private void findSupportedColorFormats(String mimeType) {
			SparseArray<ArrayList<String>> softwareCodecs = new SparseArray<ArrayList<String>>();
			SparseArray<ArrayList<String>> hardwareCodecs = new SparseArray<ArrayList<String>>();

			if (sSoftwareCodecs.containsKey(mimeType)) {
				return; 
			}

			Log.v(TAG,"Searching supported color formats for mime type \""+mimeType+"\"...");

			// We loop through the encoders, apparently this can take up to a sec (testes on a GS3)
			for(int j = MediaCodecList.getCodecCount() - 1; j >= 0; j--){
				MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(j);
				if (!codecInfo.isEncoder()) continue;

				String[] types = codecInfo.getSupportedTypes();
				for (int i = 0; i < types.length; i++) {
					if (types[i].equalsIgnoreCase(mimeType)) {
						MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);

						boolean software = false;
						for (int k=0;k<SOFTWARE_ENCODERS.length;k++) {
							if (codecInfo.getName().equalsIgnoreCase(SOFTWARE_ENCODERS[i])) {
								software = true;
							}
						}

						// And through the color formats supported
						for (int k = 0; k < capabilities.colorFormats.length; k++) {
							int format = capabilities.colorFormats[k];
							if (software) {
								if (softwareCodecs.get(format) == null) softwareCodecs.put(format, new ArrayList<String>());
								softwareCodecs.get(format).add(codecInfo.getName());
							} else {
								if (hardwareCodecs.get(format) == null) hardwareCodecs.put(format, new ArrayList<String>());
								hardwareCodecs.get(format).add(codecInfo.getName());
							}
						}

					}
				}
			}

			// Logs the supported color formats on the phone
			StringBuilder e = new StringBuilder();
			e.append("Supported color formats on this phone: ");
			for (int i=0;i<softwareCodecs.size();i++) e.append(softwareCodecs.keyAt(i)+", ");
			for (int i=0;i<hardwareCodecs.size();i++) e.append(hardwareCodecs.keyAt(i)+(i==hardwareCodecs.size()-1?".":", "));
			Log.v(TAG, e.toString());

			sSoftwareCodecs.put(mimeType, softwareCodecs);
			sHardwareCodecs.put(mimeType, hardwareCodecs);
			return;
		}


	}

	static class Translator {

		private int mOutputColorFormat;
		private int mWidth; 
		private int mHeight;
		private int mYStride;
		private int mUVStride;
		private int mYSize;
		private int mUVSize;
		private int bufferSize;
		private int i;
		private byte[] tmp;

		public Translator(int outputColorFormat, int width, int height) {
			mOutputColorFormat = outputColorFormat;
			mWidth = width;
			mHeight = height;
			mYStride   = (int) Math.ceil(mWidth / 16.0) * 16;
			mUVStride  = (int) Math.ceil( (mYStride / 2) / 16.0) * 16;
			mYSize     = mYStride * mHeight;
			mUVSize    = mUVStride * mHeight / 2;
			bufferSize = mYSize + mUVSize * 2;
			tmp = new byte[mUVSize*2];
		}

		public int getBufferSize() {
			return bufferSize;
		}

		public int getUVStride() {
			return mUVStride;
		}

		public int getYStride() {
			return mYStride;
		}

		public byte[] translate(byte[] buffer) {

			if (mOutputColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
				// FIXME: May be issues because of padding here :/
				int wh4 = bufferSize/6; //wh4 = width*height/4
				byte tmp;
				for (i=wh4*4; i<wh4*5; i++) {
					tmp = buffer[i];
					buffer[i] = buffer[i+wh4];
					buffer[i+wh4] = tmp;
				}
			}

			else if (mOutputColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
				// We need to interleave the U and V channel
				System.arraycopy(buffer, mYSize, tmp, 0, mUVSize*2); // Y
				for (i = 0; i < mUVSize; i++) {
					buffer[mYSize + i*2] = tmp[i + mUVSize]; // Cb (U)
					buffer[mYSize + i*2+1] = tmp[i]; // Cr (V)
				}
			}

			return buffer;
		}


	}


}
