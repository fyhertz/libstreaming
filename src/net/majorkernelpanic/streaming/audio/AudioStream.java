package net.majorkernelpanic.streaming.audio;

import java.io.IOException;

import net.majorkernelpanic.streaming.MediaStream;
import android.media.MediaRecorder;
import android.util.Log;

/** 
 * Don't use this class directly.
 */
public abstract class AudioStream  extends MediaStream {

	protected int mAudioSource;
	protected int mOutputFormat;
	protected int mAudioEncoder;
	protected AudioQuality mQuality = AudioQuality.DEFAULT_AUDIO_QUALITY.clone();
	
	public AudioStream() {
		setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
	}
	
	public void setAudioSource(int audioSource) {
		mAudioSource = audioSource;
	}
	
	public void setAudioSamplingRate(int samplingRate) {
		mQuality.samplingRate = samplingRate;
	}

	public void setAudioQuality(AudioQuality quality) {
		mQuality = quality;
	}
	
	/** 
	 * Returns the quality of the stream.  
	 */
	public AudioQuality getAudioQuality() {
		return mQuality;
	}	
	
	/**
	 * Sets the encoding bit rate for the stream.
	 * @param bitRate bit rate in bit per second
	 */
	public void setAudioEncodingBitRate(int bitRate) {
		mQuality.bitRate = bitRate;
	}
	
	protected void setAudioEncoder(int audioEncoder) {
		mAudioEncoder = audioEncoder;
	}
	
	protected void setOutputFormat(int outputFormat) {
		mOutputFormat = outputFormat;
	}
	
	@Override
	protected void encodeWithMediaRecorder() throws IOException {
		
		// We need a local socket to forward data output by the camera to the packetizer
		createSockets();

		Log.v(TAG,"Requested audio with "+mQuality.bitRate/1000+"kbps"+" at "+mQuality.samplingRate/1000+"kHz");
		
		mMediaRecorder = new MediaRecorder();
		mMediaRecorder.setAudioSource(mAudioSource);
		mMediaRecorder.setOutputFormat(mOutputFormat);
		mMediaRecorder.setAudioEncoder(mAudioEncoder);
		mMediaRecorder.setAudioChannels(1);
		mMediaRecorder.setAudioSamplingRate(mQuality.samplingRate);
		mMediaRecorder.setAudioEncodingBitRate(mQuality.bitRate);
		
		// We write the ouput of the camera in a local socket instead of a file !			
		// This one little trick makes streaming feasible quiet simply: data from the camera
		// can then be manipulated at the other end of the socket
		mMediaRecorder.setOutputFile(mSender.getFileDescriptor());

		mMediaRecorder.prepare();
		mMediaRecorder.start();

		try {
			// mReceiver.getInputStream contains the data from the camera
			// the mPacketizer encapsulates this stream in an RTP stream and send it over the network
			mPacketizer.setDestination(mDestination, mRtpPort, mRtcpPort);
			mPacketizer.setInputStream(mReceiver.getInputStream());
			mPacketizer.start();
			mStreaming = true;
		} catch (IOException e) {
			stop();
			throw new IOException("Something happened with the local sockets :/ Start failed !");
		}
		
	}
	
}
