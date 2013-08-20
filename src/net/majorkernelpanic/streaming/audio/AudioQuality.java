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

/**
 * A class that represents the quality of an audio stream. 
 */
public class AudioQuality {

	/** Default audio stream quality. */
	public final static AudioQuality DEFAULT_AUDIO_QUALITY = new AudioQuality(8000,32000);

	/**	Represents a quality for a video stream. */ 
	public AudioQuality() {}

	/**
	 * Represents a quality for an audio stream.
	 * @param samplingRate The sampling rate
	 * @param bitRate The bitrate in bit per seconds
	 */
	public AudioQuality(int samplingRate, int bitRate) {
		this.samplingRate = samplingRate;
		this.bitRate = bitRate;
	}	

	public int samplingRate = 0;
	public int bitRate = 0;

	public boolean equals(AudioQuality quality) {
		if (quality==null) return false;
		return (quality.samplingRate == this.samplingRate 				&
				quality.bitRate == this.bitRate);
	}

	public AudioQuality clone() {
		return new AudioQuality(samplingRate, bitRate);
	}

	public static AudioQuality parseQuality(String str) {
		AudioQuality quality = new AudioQuality(0,0);
		if (str != null) {
			String[] config = str.split("-");
			try {
				quality.bitRate = Integer.parseInt(config[0])*1000; // conversion to bit/s
				quality.samplingRate = Integer.parseInt(config[1]);
			}
			catch (IndexOutOfBoundsException ignore) {}
		}
		return quality;
	}

	public static AudioQuality merge(AudioQuality audioQuality, AudioQuality withAudioQuality) {
		if (withAudioQuality != null && audioQuality != null) {
			if (audioQuality.samplingRate==0) audioQuality.samplingRate = withAudioQuality.samplingRate;
			if (audioQuality.bitRate==0) audioQuality.bitRate = withAudioQuality.bitRate;
		}
		return audioQuality;
	}

}
