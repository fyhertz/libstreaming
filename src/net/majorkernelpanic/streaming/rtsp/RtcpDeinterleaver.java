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

package net.majorkernelpanic.streaming.rtsp;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

class RtcpDeinterleaver extends InputStream implements Runnable {
	
	public final static String TAG = "RtcpDeinterleaver";
	
	private IOException mIOException;
	private InputStream mInputStream;
	private PipedInputStream mPipedInputStream;
	private PipedOutputStream mPipedOutputStream;
	private byte[] mBuffer;
	
	public RtcpDeinterleaver(InputStream inputStream) {
		mInputStream = inputStream;
		mPipedInputStream = new PipedInputStream(4096);
		try {
			mPipedOutputStream = new PipedOutputStream(mPipedInputStream);
		} catch (IOException e) {}
		mBuffer = new byte[1024];
		new Thread(this).start();
	}

	@Override
	public void run() {
		try {
			while (true) {
				int len = mInputStream.read(mBuffer, 0, 1024);
				mPipedOutputStream.write(mBuffer, 0, len);
			}
		} catch (IOException e) {
			try {
				mPipedInputStream.close();
			} catch (IOException ignore) {}
			mIOException = e;
		}
	}

	@Override
	public int read(byte[] buffer) throws IOException {
		if (mIOException != null) {
			throw mIOException;
		}
		return mPipedInputStream.read(buffer);
	}		
	
	@Override
	public int read(byte[] buffer, int offset, int length) throws IOException {
		if (mIOException != null) {
			throw mIOException;
		}
		return mPipedInputStream.read(buffer, offset, length);
	}	
	
	@Override
	public int read() throws IOException {
		if (mIOException != null) {
			throw mIOException;
		}
		return mPipedInputStream.read();
	}

	@Override
	public void close() throws IOException {
		mInputStream.close();
	}

}
