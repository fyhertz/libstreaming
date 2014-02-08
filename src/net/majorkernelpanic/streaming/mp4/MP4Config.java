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
	 * Finds sps & pps parameters inside a .mp4.
	 * @param path Path to the file to analyze
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public MP4Config (String path) throws IOException, FileNotFoundException {

		StsdBox stsdBox; 
		
		// We open the mp4 file
		mp4Parser = new MP4Parser(path);

		// We parse it
		try {
			mp4Parser.parse();
		} catch (IOException ignore) {
			// Maybe enough of the file has been parsed and we can get the stsd box
		}

		// We find the stsdBox
		stsdBox = mp4Parser.getStsdBox();
		mPPS = stsdBox.getB64PPS();
		mSPS = stsdBox.getB64SPS();
		mProfilLevel = stsdBox.getProfileLevel();
		
		// We're done !
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