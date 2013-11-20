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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.util.Log;
import android.util.SparseArray;

@SuppressLint("InlinedApi")
public class CodecManager {

	public final static String TAG = "CodecManager";

	public static final int[] SUPPORTED_COLOR_FORMATS = {
		MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
		MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
	};

	/**
	 * There currently is no way to know if an encoder is software or hardware from the MediaCodecInfo class,
	 * so we need to maintain a list of known software encoders.
	 */
	public static final String[] SOFTWARE_ENCODERS = {
		"OMX.google.h264.encoder"
	};

	/**
	 * Contains a list of encoders and color formats that we may use with a {@link CodecManager.YV12Translator}.  
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
	 * Iterates through the list of encoders present on the phone, execution of this method can be slow,
	 * it should be called off the main thread.
	 * @param The mime type
	 */
	public static void findSupportedColorFormats(String mimeType) {
		Selector.findSupportedColorFormats(mimeType);
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

			// Fix for the OMX.SEC.avc.enc that may not work with COLOR_FormatYUV420Planar (http://code.google.com/p/android/issues/detail?id=37769)
			// The S3 may have this encoder
			if (hardwareCodecs.get(21).contains("OMX.SEC.avc.enc")) {
				if (hardwareCodecs.get(21).get(0) != null) {
					list.hardwareCodec = hardwareCodecs.get(21).get(0);
					list.hardwareColorFormat = 21;
				}	
			}			

			if (list.hardwareCodec != null) {
				Log.v(TAG,"Choosen primary codec: "+list.hardwareCodec+" with color format: "+list.hardwareColorFormat);
			} else {
				Log.e(TAG,"No supported hardware codec found !");
			}
			if (list.softwareCodec != null) {
				Log.v(TAG,"Choosen secondary codec: "+list.softwareCodec+" with color format: "+list.softwareColorFormat);
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
		static void findSupportedColorFormats(String mimeType) {
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

	/**
	 *  Translates buffers from YV12 to a some color format specified in MediaCodecInfo.CodecCapabilities
	 *  Turns out YV12 is buggy on some phones running 4.0 and 4.1 :'(
	 **/	
	static class YV12Translator {

		private int mOutputColorFormat;
		private int mWidth; 
		private int mHeight;
		private int mYStride;
		private int mUVStride;
		private int mYSize;
		private int mUVSize;
		private int bufferSize;
		private int mMode;
		private int mApiLevel;
		private int i;
		private byte[] tmp;

		public YV12Translator(String encoderName, int outputColorFormat, int width, int height) {
			mApiLevel = Build.VERSION.SDK_INT;
			mOutputColorFormat = outputColorFormat;
			mWidth = width;
			mHeight = height;
			mYStride   = (int) Math.ceil(mWidth / 16.0) * 16;
			mUVStride  = (int) Math.ceil( (mYStride / 2) / 16.0) * 16;
			mYSize     = mYStride * mHeight;
			mUVSize    = mUVStride * mHeight / 2;
			bufferSize = mYSize + mUVSize * 2;
			tmp = new byte[bufferSize];

			// Handles the case of the OMX.qcom.video.encoder.avc encoder which do not behave properly on some devices before 4.3
			if (encoderName.equalsIgnoreCase("OMX.qcom.video.encoder.avc") && mApiLevel<18) mMode = 1;

			// Mode 0 is used by difault, it assumes that the encoder works properly...
			else mMode = 0;

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

		public void translate(byte[] data, ByteBuffer buffer) {
			if (data.length>buffer.capacity()) return;
			buffer.clear();
			
			// HANDLES THE CASE OF THE OMX.qcom.video.encoder.avc H.264 ENCODER
			// It may need some padding before the Chroma pane
			if (mMode == 1) {
				if (mOutputColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {

					int padding = 1024;			
					if ((mWidth==640 && mHeight==384) || (mWidth==384 && mHeight==256) || 
							(mWidth==640 && mHeight==480) || (mWidth==1280 && mHeight==720)) 
						padding = 0;

					// We need to interleave the U and V channel
					
					buffer.put(data, 0, mYSize); // Y
					buffer.position(buffer.position()+padding);
					for (i = 0; i < mUVSize; i++) {
						tmp[i*2] = data[mYSize + i + mUVSize]; // Cb (U)
						tmp[i*2+1] = data[mYSize + i]; // Cr (V)
					}
					buffer.put(tmp, 0, 2*mUVSize-1);
					return;
				}	
			}

			// HANDLES THE CASE OF NICE ENCODERS

			if (mOutputColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
				// FIXME: May be issues because of padding here :/
				buffer.put(data, 0, mYSize); // Y
				buffer.put(data, mYSize+mUVSize, mUVSize);
				buffer.put(data, mYSize, mUVSize);
				return;
			}

			else if (mOutputColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
				// We need to interleave the U and V channel
				buffer.put(data, 0, mYSize);
				for (i = 0; i < mUVSize; i++) {
					tmp[i*2] = data[mYSize + i + mUVSize]; // Cb (U)
					tmp[i*2+1] = data[mYSize + i]; // Cr (V)
				}
				buffer.put(tmp, 0, 2*mUVSize-1);
				return;
			}

			// If we do not handle the color format of the encoder we simply return the input
			buffer.put(data, 0, data.length);
			return;

		}

	}

	/**
	 *  Translates buffers from NV21 to a some color format specified in MediaCodecInfo.CodecCapabilities
	 **/
	static class NV21Translator {

		private int mOutputColorFormat;
		private int mWidth; 
		private int mHeight;
		private int mBufferSize;
		private int mMode;
		private int mApiLevel;
		private int i;

		public NV21Translator(String encoderName, int outputColorFormat, int width, int height) {
			mApiLevel = Build.VERSION.SDK_INT;
			mOutputColorFormat = outputColorFormat;
			mWidth = width;
			mHeight = height;
			mBufferSize = width * height * ImageFormat.getBitsPerPixel(ImageFormat.NV21) / 8;

			// Mode 1 handles the case of the OMX.qcom.video.encoder.avc encoder which do not behave properly on some devices before 4.3
			if (encoderName.equalsIgnoreCase("OMX.qcom.video.encoder.avc") && mApiLevel<18) mMode = 1;

			// Handles the case of the OMX.SEC.avc.enc present in the S3 
			else if (encoderName.equalsIgnoreCase("OMX.SEC.avc.enc") && mApiLevel<18) mMode = 2;				

			// Mode 0 is used by difault, it assumes that the encoder works properly...
			else mMode = 0;

		}

		public int getBufferSize() {
			return mBufferSize;
		}

		public void translate(byte[] data, ByteBuffer buffer) {
			if (data.length>buffer.capacity()) return;
			buffer.clear();

			// HANDLES THE CASE OF THE OMX.qcom.video.encoder.avc H.264 ENCODER
			// Some padding is needed for some resolutions
			if (mMode == 1) {
				if (mOutputColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
					int padding = 1024;
					if ((mWidth==640 && mHeight==384) || (mWidth==384 && mHeight==256) || 
							(mWidth==640 && mHeight==480) || (mWidth==1280 && mHeight==720)) 
						padding = 0;
					
					buffer.put(data, 0, mHeight*mWidth);
					buffer.position(buffer.position()+padding);
					
					// Swaps the Cb and Cr panes
					for (i = mWidth*mHeight; i < mBufferSize; i+=2) {
						buffer.put(data[i+1]);
						buffer.put(data[i]);
					}
					return;
				}	
			}

			// HANDLES THE CASE OF OMX.SEC.avc.enc ON THE S3
			else if (mMode == 2) {
				if (mOutputColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
					buffer.put(data, 0, data.length);
					return;
				}
			}

			// HANDLES THE CASE OF NICE ENCODERS

			if (mOutputColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
				// Y pane
				buffer.put(data, 0, mHeight*mWidth);
				// De-interleave Cb and Cr
				for (i = mWidth*mHeight; i < mBufferSize; i += 2) buffer.put(data[i+1]);
				for (i = mWidth*mHeight; i < mBufferSize; i += 2) buffer.put(data[i]);;
				return;
			}

			else if (mOutputColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
				buffer.put(data, 0, mHeight*mWidth);
				// Swaps the Cb and Cr panes
				for (i = mWidth*mHeight; i < mBufferSize; i += 2) {
					buffer.put(data[i+1]);
					buffer.put(data[i]);
				}
				return;
			}

			// If we do not handle the color format of the encoder we simply return the input
			buffer.put(data, 0, data.length);
			return;
			
		}		

	}


}
