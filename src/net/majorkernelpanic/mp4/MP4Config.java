/*
 * Copyright (C) 2011 GUIGUI Simon, fyhertz@gmail.com
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

package net.majorkernelpanic.mp4;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Finds SPS & PPS parameters in mp4 file.
 */
public class MP4Config {

	private final StsdBox stsdBox; 
	private final MP4Parser mp4Parser;

	/**
	 * Finds sps & pps parameters inside a .mp4.
	 * @param path Path to the file to analyze
	 * @throws IOException
	 * @throws FileNotFoundException
	 */
	public MP4Config (String path) throws IOException, FileNotFoundException {

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

		// We're done !
		mp4Parser.close();

	}

	public String getProfileLevel() {
		return stsdBox.getProfileLevel();
	}

	public String getB64PPS() {
		return stsdBox.getB64PPS();
	}

	public String getB64SPS() {
		return stsdBox.getB64SPS();
	}

}