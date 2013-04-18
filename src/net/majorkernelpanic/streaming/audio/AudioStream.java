package net.majorkernelpanic.streaming.audio;

import java.io.IOException;

import net.majorkernelpanic.streaming.MediaStream;

/** 
 * Don't use this class directly.
 */
public abstract class AudioStream  extends MediaStream {

	protected int mAudioSource;
	protected int mOutputFormat;
	protected int mAudioEncoder;
	protected int mSamplingRate;
	
	public void setAudioSource(int audioSource) {
		mAudioSource = audioSource;
	}
	
	public void setOutputFormat(int outputFormat) {
		mOutputFormat = outputFormat;
	}
	
	public void setAudioEncoder(int audioEncoder) {
		mAudioEncoder = audioEncoder;
	}
	
	public void setAudioSamplingRate(int samplingRate) {
		mSamplingRate = samplingRate;
	}
	
	public void prepare() throws IllegalStateException, IOException {
		
		// Resets the recorder in case it is in a bad state
		mMediaRecorder.reset();
		
		mMediaRecorder.setAudioSource(mAudioSource);
		mMediaRecorder.setOutputFormat(mOutputFormat);
		mMediaRecorder.setAudioEncoder(mAudioEncoder);
		mMediaRecorder.setAudioChannels(1);
		mMediaRecorder.setAudioSamplingRate(mSamplingRate);
		
		super.prepare();
		
	}
	
}
