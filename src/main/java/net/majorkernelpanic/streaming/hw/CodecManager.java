/*
 * Copyright (C) 2011-2014 GUIGUI Simon, fyhertz@gmail.com
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

package net.majorkernelpanic.streaming.hw;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import android.annotation.SuppressLint;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.util.Log;

@SuppressLint("InlinedApi")
public class CodecManager {

	public final static String TAG = "CodecManager";

	public static final int[] SUPPORTED_COLOR_FORMATS = {
		MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
		MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar,
		MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
		MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar,
		MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar
	};		

	private static Codec[] sEncoders = null;
	private static Codec[] sDecoders = null;

	static class Codec {
		public Codec(String name, Integer[] formats) {
			this.name = name;
			this.formats = formats;
		}
		public String name;
		public Integer[] formats;
	}

	/**
	 * Lists all encoders that claim to support a color format that we know how to use.
	 * @return A list of those encoders
	 */
	@SuppressLint("NewApi")
	public synchronized static Codec[] findEncodersForMimeType(String mimeType) {
		if (sEncoders != null) return sEncoders;

		ArrayList<Codec> encoders = new ArrayList<Codec>();

		// We loop through the encoders, apparently this can take up to a sec (testes on a GS3)
		for(int j = MediaCodecList.getCodecCount() - 1; j >= 0; j--){
			MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(j);
			if (!codecInfo.isEncoder()) continue;

			String[] types = codecInfo.getSupportedTypes();
			for (int i = 0; i < types.length; i++) {
				if (types[i].equalsIgnoreCase(mimeType)) {
					try {
						MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
						Set<Integer> formats = new HashSet<Integer>();

						// And through the color formats supported
						for (int k = 0; k < capabilities.colorFormats.length; k++) {
							int format = capabilities.colorFormats[k];

							for (int l=0;l<SUPPORTED_COLOR_FORMATS.length;l++) {
								if (format == SUPPORTED_COLOR_FORMATS[l]) {
									formats.add(format);
								}
							}
						}
						
						Codec codec = new Codec(codecInfo.getName(), (Integer[]) formats.toArray(new Integer[formats.size()]));
						encoders.add(codec);
					} catch (Exception e) {
						Log.wtf(TAG,e);
					}
				}
			}
		}

		sEncoders = (Codec[]) encoders.toArray(new Codec[encoders.size()]);
		return sEncoders;

	}

	/**
	 * Lists all decoders that claim to support a color format that we know how to use.
	 * @return A list of those decoders
	 */
	@SuppressLint("NewApi")
	public synchronized static Codec[] findDecodersForMimeType(String mimeType) {
		if (sDecoders != null) return sDecoders;
		ArrayList<Codec> decoders = new ArrayList<Codec>();

		// We loop through the decoders, apparently this can take up to a sec (testes on a GS3)
		for(int j = MediaCodecList.getCodecCount() - 1; j >= 0; j--){
			MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(j);
			if (codecInfo.isEncoder()) continue;

			String[] types = codecInfo.getSupportedTypes();
			for (int i = 0; i < types.length; i++) {
				if (types[i].equalsIgnoreCase(mimeType)) {
					try {
						MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
						Set<Integer> formats = new HashSet<Integer>();

						// And through the color formats supported
						for (int k = 0; k < capabilities.colorFormats.length; k++) {
							int format = capabilities.colorFormats[k];

							for (int l=0;l<SUPPORTED_COLOR_FORMATS.length;l++) {
								if (format == SUPPORTED_COLOR_FORMATS[l]) {
									formats.add(format);
								}
							}
						}

						Codec codec = new Codec(codecInfo.getName(), (Integer[]) formats.toArray(new Integer[formats.size()]));
						decoders.add(codec);
					} catch (Exception e) {
						Log.wtf(TAG,e);
					}
				}
			}
		}

		sDecoders = (Codec[]) decoders.toArray(new Codec[decoders.size()]);

		// We will use the decoder from google first, it seems to work properly on many phones
		for (int i=0;i<sDecoders.length;i++) {
			if (sDecoders[i].name.equalsIgnoreCase("omx.google.h264.decoder")) {
				Codec codec = sDecoders[0];
				sDecoders[0] = sDecoders[i];
				sDecoders[i] = codec;
			} 
		}

		return sDecoders;
	}

}

