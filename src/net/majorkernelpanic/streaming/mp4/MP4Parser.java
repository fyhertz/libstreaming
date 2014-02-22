package net.majorkernelpanic.streaming.mp4;
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


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.HashMap;

import android.util.Base64;
import android.util.Log;

/**
 * Parse an mp4 file.
 * An mp4 file contains a tree where each node has a name and a size.
 * This class is used by H264Stream.java to determine the SPS and PPS parameters of a short video recorded by the phone.
 */
public class MP4Parser {

	private static final String TAG = "MP4Parser";

	private HashMap<String, Long> mBoxes = new HashMap<String, Long>();
	private final RandomAccessFile mFile;
	private long mPos = 0;


	/** Parses the mp4 file. **/
	public static MP4Parser parse(String path) throws IOException {
		return new MP4Parser(path);
	}	
	
	private MP4Parser(final String path) throws IOException, FileNotFoundException {
		mFile = new RandomAccessFile(new File(path), "r");
		try {
			parse("",mFile.length());
		} catch (Exception e) {
			e.printStackTrace();
			throw new IOException("Parse error: malformed mp4 file");
		}
	}
	
	public void close() {
		try {
			mFile.close();
		} catch (Exception e) {};
	}
	
	public long getBoxPos(String box) throws IOException {
		Long r = mBoxes.get(box);

		if (r==null) throw new IOException("Box not found: "+box);
		return mBoxes.get(box);
	}

	public StsdBox getStsdBox() throws IOException {
		try {
			return new StsdBox(mFile,getBoxPos("/moov/trak/mdia/minf/stbl/stsd"));
		} catch (IOException e) {
			throw new IOException("stsd box could not be found");
		}
	}

	private void parse(String path, long len) throws IOException {
		ByteBuffer byteBuffer;
		long sum = 0, newlen = 0;
		byte[] buffer = new byte[8];
		String name = "";
		
		if(!path.equals("")) mBoxes.put(path, mPos-8);

		while (sum<len) {
			mFile.read(buffer,0,8);
			mPos += 8; sum += 8; 

			if (validBoxName(buffer)) {
				name = new String(buffer,4,4);

				if (buffer[3] == 1) {
					// 64 bits atom size
					mFile.read(buffer,0,8);
					mPos += 8; sum += 8;
					byteBuffer = ByteBuffer.wrap(buffer,0,8);
					newlen = byteBuffer.getLong()-16;
				} else {
					// 32 bits atom size
					byteBuffer = ByteBuffer.wrap(buffer,0,4);
					newlen = byteBuffer.getInt()-8;
				}

				// 1061109559+8 correspond to "????" in ASCII the HTC Desire S seems to write that sometimes, maybe other phones do
				// "wide" atom would produce a newlen == 0, and we shouldn't throw an exception because of that
				if (newlen < 0 || newlen == 1061109559) throw new IOException();
				
				Log.d(TAG, "Atom -> name: "+name+" position: "+mPos+", length: "+newlen);
				sum += newlen;
				parse(path+'/'+name,newlen);

			}
			else {
				if( len < 8){
					mFile.seek(mFile.getFilePointer() - 8 + len);
					sum += len-8;
				} else {
					int skipped = mFile.skipBytes((int)(len-8));
					if (skipped < ((int)(len-8))) {
						throw new IOException();
					}
					mPos += len-8;
					sum += len-8;
				}
			}
		}
	}

	private boolean validBoxName(byte[] buffer) {
		for (int i=0;i<4;i++) {
			// If the next 4 bytes are neither lowercase letters nor numbers
			if ((buffer[i+4]< 'a' || buffer[i+4]>'z') && (buffer[i+4]<'0'|| buffer[i+4]>'9') ) return false;
		}
		return true;
	}
	
	static String toHexString(byte[] buffer,int start, int len) {
		String c;
		StringBuilder s = new StringBuilder();
		for (int i=start;i<start+len;i++) {
			c = Integer.toHexString(buffer[i]&0xFF);
			s.append( c.length()<2 ? "0"+c : c );
		}
		return s.toString();
	}

}

class StsdBox {

	private RandomAccessFile fis;
	private byte[] buffer = new byte[4];
	private long pos = 0;

	private byte[] pps;
	private byte[] sps;
	private int spsLength, ppsLength;

	/** Parse the sdsd box in an mp4 file
	 * fis: proper mp4 file
	 * pos: stsd box's position in the file
	 */
	public StsdBox (RandomAccessFile fis, long pos) {

		this.fis = fis;
		this.pos = pos;

		findBoxAvcc();
		findSPSandPPS();

	}

	public String getProfileLevel() {
		return MP4Parser.toHexString(sps,1,3);
	}

	public String getB64PPS() {
		return Base64.encodeToString(pps, 0, ppsLength, Base64.NO_WRAP);
	}

	public String getB64SPS() {
		return Base64.encodeToString(sps, 0, spsLength, Base64.NO_WRAP);
	}

	private boolean findSPSandPPS() {
		/*
		 *  SPS and PPS parameters are stored in the avcC box
		 *  You may find really useful information about this box 
		 *  in the document ISO-IEC 14496-15, part 5.2.4.1.1
		 *  The box's structure is described there
		 *  
		 *  aligned(8) class AVCDecoderConfigurationRecord {
		 *		unsigned int(8) configurationVersion = 1;
		 *		unsigned int(8) AVCProfileIndication;
		 *		unsigned int(8) profile_compatibility;
		 *		unsigned int(8) AVCLevelIndication;
		 *		bit(6) reserved = ‘111111’b;
		 *		unsigned int(2) lengthSizeMinusOne;
		 *		bit(3) reserved = ‘111’b;
		 *		unsigned int(5) numOfSequenceParameterSets;
		 *		for (i=0; i< numOfSequenceParameterSets; i++) {
		 *			unsigned int(16) sequenceParameterSetLength ;
		 *			bit(8*sequenceParameterSetLength) sequenceParameterSetNALUnit;
		 *		}
		 *		unsigned int(8) numOfPictureParameterSets;
		 *		for (i=0; i< numOfPictureParameterSets; i++) {
		 *			unsigned int(16) pictureParameterSetLength;
		 *			bit(8*pictureParameterSetLength) pictureParameterSetNALUnit;
		 *		}
		 *	}
		 *
		 *  
		 *  
		 */
		try {

			// TODO: Here we assume that numOfSequenceParameterSets = 1, numOfPictureParameterSets = 1 !
			// Here we extract the SPS parameter
			fis.skipBytes(7);
			spsLength  = 0xFF&fis.readByte();
			sps = new byte[spsLength];
			fis.read(sps,0,spsLength);
			// Here we extract the PPS parameter
			fis.skipBytes(2);
			ppsLength = 0xFF&fis.readByte();
			pps = new byte[ppsLength];
			fis.read(pps,0,ppsLength);

		} catch (IOException e) {
			return false;
		}

		return true;
	}

	private boolean findBoxAvcc() {
		try {
			fis.seek(pos+8);
			while (true) {
				while (fis.read() != 'a');
				fis.read(buffer,0,3);
				if (buffer[0] == 'v' && buffer[1] == 'c' && buffer[2] == 'C') break;
			}
		} catch (IOException e) {
			return false;
		}
		return true;

	}

}
