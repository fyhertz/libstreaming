package net.majorkernelpanic.streaming.audio;

import java.io.IOException;

import android.media.MediaRecorder;

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

	@Override
	protected void encodeWithMediaRecorder() throws IOException {
		
		// We need a local socket to forward data output by the camera to the packetizer
		createSockets();

		mMediaRecorder = new MediaRecorder();
		mMediaRecorder.setAudioSource(mAudioSource);
		mMediaRecorder.setOutputFormat(mOutputFormat);
		mMediaRecorder.setAudioEncoder(mAudioEncoder);
		mMediaRecorder.setAudioChannels(1);
		mMediaRecorder.setAudioSamplingRate(mSamplingRate);
		
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

	@Override
	protected void encodeWithMediaCodec() throws IOException {
		// TODO: Implement this !
		encodeWithMediaRecorder();
	}
	
}
