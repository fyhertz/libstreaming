package net.majorkernelpanic.streaming.video;

import android.graphics.ImageFormat;
import android.media.MediaCodecInfo;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;

public class ColorFormatTranslator {

	private int mInputColorFormat;
	private int mOutputColorFormat;

	private int mWidth; 
	private int mHeight;

	private int mYStride;
	private int mUVStride;
	private int mYSize;
	private int mUVSize;

	private int bufferSize;

	byte[] tmp;
	private int i;

	public ColorFormatTranslator(int inputColorFormat, int outputColorFormat, int width, int height) {
		mInputColorFormat = inputColorFormat;
		mOutputColorFormat = outputColorFormat;
		mWidth = width;
		mHeight = height;

		if (mInputColorFormat == ImageFormat.YV12) {
			mYStride   = (int) Math.ceil(mWidth / 16.0) * 16;
			mUVStride  = (int) Math.ceil( (mYStride / 2) / 16.0) * 16;
			mYSize     = mYStride * mHeight;
			mUVSize    = mUVStride * mHeight / 2;
			bufferSize = mYSize + mUVSize * 2;
			tmp = new byte[mUVSize*2];
		}

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

		if (mInputColorFormat == ImageFormat.YV12) {
			if (mOutputColorFormat == COLOR_FormatYUV420Planar) {
				// FIXME: May be issues because of padding here :/
				int wh4 = bufferSize/6; //wh4 = width*height/4
				byte tmp;
				for (i=wh4*4; i<wh4*5; i++) {
					tmp = buffer[i];
					buffer[i] = buffer[i+wh4];
					buffer[i+wh4] = tmp;
				}
			}

			else if (mOutputColorFormat == COLOR_FormatYUV420SemiPlanar) {
				// We need to interleave the U and V channel
				System.arraycopy(buffer, mYSize, tmp, 0, mUVSize*2); // Y

				for (i = 0; i < mUVSize; i++) {
					buffer[mYSize + i*2] = tmp[i + mUVSize]; // Cb (U)
					buffer[mYSize + i*2 + 1] = tmp[i]; // Cr (V)
				}

			}
		}

		return buffer;
	}

}
