/*
 * Copyright (C) 2011-2015 GUIGUI Simon, fyhertz@gmail.com
 *
 * This file is part of libstreaming (https://github.com/fyhertz/libstreaming)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.majorkernelpanic.streaming.mp4;

import java.io.FileNotFoundException;
import java.io.IOException;
import android.util.Base64;
import android.util.Log;

/**
 * Finds SPS & PPS parameters in mp4 file.
 */
public class MP4Config {

	public final static String TAG = "MP4Config";
	
	private MP4Parser mp4Parser;
	private String mProfilLevel, mPPS, mSPS;

	public MP4Config(String profil, String sps, String pps) {
		mProfilLevel = profil; 
		mPPS = pps; 
		mSPS = sps;
	}

	public MP4Config(String sps, String pps) {
		mPPS = pps;
		mSPS = sps;
		mProfilLevel = MP4Parser.toHexString(Base64.decode(sps, Base64.NO_WRAP),1,3);
	}	
	
	public MP4Config(byte[] sps, byte[] pps) {
		mPPS = Base64.encodeToString(pps, 0, pps.length, Base64.NO_WRAP);
		mSPS = Base64.encodeToString(sps, 0, sps.length, Base64.NO_WRAP);
		mProfilLevel = MP4Parser.toHexString(sps,1,3);
	}
	
	/**
	 * Finds SPS & PPS parameters inside a .mp4.
	 * @param path Path to the file to analyze
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public MP4Config (String path) throws IOException, FileNotFoundException {

		StsdBox stsdBox; 
		
		// We open the mp4 file and parse it
		try {
			mp4Parser = MP4Parser.parse(path);
		} catch (IOException ignore) {
			// Maybe enough of the file has been parsed and we can get the stsd box
		}

		// We find the stsdBox
		stsdBox = mp4Parser.getStsdBox();
		mPPS = stsdBox.getB64PPS();
		mSPS = stsdBox.getB64SPS();
		mProfilLevel = stsdBox.getProfileLevel();

		mp4Parser.close();
		
	}

	public String getProfileLevel() {
		return mProfilLevel;
	}

	public String getB64PPS() {
		Log.d(TAG, "PPS: "+mPPS);
		return mPPS;
	}

	public String getB64SPS() {
		Log.d(TAG, "SPS: "+mSPS);
		return mSPS;
	}

}
