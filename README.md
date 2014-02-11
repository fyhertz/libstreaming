# Introduction

## What it does

**libstreaming** is an API that allows you, with only a few lines of code, to stream the camera and/or microphone of an android powered device using RTP over UDP. 

* Android 4.0 or more recent is required.
* Supported encoders include H.264, H.263, AAC and AMR.

The first step you will need to achieve to start a streaming session to some peer is called 'signaling'. During this step you will contact the receiver and send a description of the incomming streams. You have three ways to do that with libstreaming.

* With the RTSP client: if you want to stream to a Wowza Media Server, it's the way to go. [The example 3](https://github.com/fyhertz/libstreaming-examples#example-3) illustrates that use case.
* With the RTSP server: in that case the phone will act as a RTSP server and wait for a RTSP client to request a stream. This use case is illustated in [the example 1](https://github.com/fyhertz/libstreaming-examples#example-1).
* Or you use libstreaming without using the RTSP protocol at all, and signal the session using SDP over a protocol you like. [The example 2](https://github.com/fyhertz/libstreaming-examples#example-2) illustrates that use case.

The full javadoc documentation of the API is available here: http://majorkernelpanic.com/libstreaming/doc-v4

## How it does it

There are three ways on Android to get encoded data from the peripherals:

* With the **MediaRecorder** API and a simple hack.
* With the **MediaCodec** API and the buffer-to-buffer method which requires Android 4.1.
* With the **MediaCodec** API and the surface-to-buffer method which requires Android 4.3.

### Encoding with the MediaRecorder API

The **MediaRecorder** API was not intended for streaming applications but can be used to retrieve encoded data from the peripherals of the phone.  The trick is to configure a MediaRecorder instance to write to a **LocalSocket** instead of a regular file (see **MediaStream.java**).

This hack has some limitations:
* Lip sync can be approximative.
* The MediaRecorder internal buffers can lead to some important jitter. libstreaming tries to compensate that jitter.

It's hard to tell how well this hack is going to work on a phone. It does work well on many devices though.

### Encoding with the MediaCodec API

The **MediaCodec** API do not present the limitations I just mentionned, but has its own issues. There are actually two ways to use the MediaCodec API: with buffers or with a surface.

The buffer-to-buffer method uses calls to [**dequeueInputBuffer**](http://developer.android.com/reference/android/media/MediaCodec.html#dequeueInputBuffer(long)) and [**queueInputBuffer**](http://developer.android.com/reference/android/media/MediaCodec.html#queueInputBuffer(int, int, int, long, int)) to feed the encoder with raw data.
That seems easy right ? Well it's not, because video encoders that you get access to with this API are using different color formats and you need to support all of them. A list of those color formats is available [here](http://developer.android.com/reference/android/media/MediaCodecInfo.CodecCapabilities.html). Moreover, many encoders claim support for color formats they don't actually support properly or can present little glitches.

All the [**hw**](http://majorkernelpanic.com/libstreaming/doc-v4/net/majorkernelpanic/streaming/hw/package-summary.html) package is dedicated to solving those issues. See in particular [**EncoderDebugger**](http://majorkernelpanic.com/libstreaming/doc-v4/net/majorkernelpanic/streaming/hw/EncoderDebugger.html) class. 

If streaming with that API fails, libstreaming fallbacks on streaming with the **MediaRecorder API**.

The surface-to-buffer method uses the [createInputSurface()](http://developer.android.com/reference/android/media/MediaCodec.html#createInputSurface()) method. This method is probably the best way to encode raw video from the camera but it requires android 4.3 and up.

The [**gl**](http://majorkernelpanic.com/libstreaming/doc-v4/net/majorkernelpanic/streaming/gl/package-summary.html) package is dedicated to using the MediaCodec API with a surface.

It is not yet enabled by default in libstreaming but you can force it with the [**setStreamingMethod(byte)**](http://majorkernelpanic.com/libstreaming/doc-v4/net/majorkernelpanic/streaming/MediaStream.html#setStreamingMethod(byte)) method.

### Packetization process

Once raw data from the peripherals has been encoded, it is encapsulated in a proper RTP stream. The packetization algorithm that must be used depends on the format of the data (H.264, H.263, AMR and AAC) and are all specified in their respective RFC:

* RFC 3984 for H.264: **H264Packetizer.java**
* RFC 4629 for H.263: **H263Packetizer.java**
* RFC 3267 for AMR: **AMRNBPacketizer.java**
* RFC 3640 for AAC: **AACADTSPacketizer.java** or **AACLATMPacketizer.java**

If you are looking for a basic implementation of one of the RFC mentionned above, check the sources of corresponding class.

RTCP packets are also sent to the receiver since version 2.0 of libstreaming. Only Sender Reports are implemented. They are actually needed for lip sync.

The [**rtp**](http://majorkernelpanic.com/libstreaming/doc-v4/net/majorkernelpanic/streaming/rtp/package-summary.html) package handles packetization of encoded data in RTP packets.

# Using libstreaming in your app

## Required permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.CAMERA" />
```

## How to stream H.264 and AAC

This example is extracted from [this simple android app](https://github.com/fyhertz/libstreaming-examples#example-2). This could be a part of an Activity, a Fragment or a Service. 

```java
    protected void onCreate(Bundle savedInstanceState) {

        ...

		mSession = SessionBuilder.getInstance()
		.setCallback(this)
		.setSurfaceView(mSurfaceView)
		.setPreviewOrientation(90)
		.setContext(getApplicationContext())
		.setAudioEncoder(SessionBuilder.AUDIO_NONE)
		.setAudioQuality(new AudioQuality(16000, 32000))
		.setVideoEncoder(SessionBuilder.VIDEO_H264)
		.setVideoQuality(new VideoQuality(320,240,20,500000))
		.build();

		mSurfaceView.getHolder().addCallback(this);

        ...

    }

	public void onPreviewStarted() {
		Log.d(TAG,"Preview started.");
	}

	@Override
	public void onSessionConfigured() {
		Log.d(TAG,"Preview configured.");
		// Once the stream is configured, you can get a SDP formated session description
		// that you can send to the receiver of the stream.
		// For example, to receive the stream in VLC, store the session description in a .sdp file
		// and open it with VLC while streming.
		Log.d(TAG, mSession.getSessionDescription());
		mSession.start();
	}

	@Override
	public void onSessionStarted() {
		Log.d(TAG,"Streaming session started.");
        ...
	}

	@Override
	public void onSessionStopped() {
		Log.d(TAG,"Streaming session stopped.");
        ...
	}	

	@Override
	public void onBitrareUpdate(long bitrate) {
        // Informs you of the bandwidth consumption of the streams
		Log.d(TAG,"Bitrate: "+bitrate);
	}

	@Override
	public void onSessionError(int message, int streamType, Exception e) {
        // Might happen if the streaming at the requested resolution is not supported
        // or if the preview surface is not ready...
        // Check the Session class for a list of the possible errors.
		Log.e(TAG, "An error occured", e);
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
		
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
        // Starts the preview of the Camera
		mSession.startPreview();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
        // Stops the streaming session
		mSession.stop();
	}

```

The **SessionBuilder** simply facilitates the creation of **Session** objects. The call to **setSurfaceView** is needed for video streaming, that should not come up as a surprise since Android requires a valid surface for recording video (it's an annoying limitation of the **MediaRecorder** API). On Android 4.3, streaming with no **SurfaceView** is possible but not yet implemented. The call to **setContext(Context)** is necessary, it allows **H264Stream** objects and **AACStream** objects to store and recover data using **SharedPreferences**.

A **Session** object represents a streaming session to some peer. It contains one or more **Stream** objects that are started (resp. stopped) when the **start()** (resp. **stop()**) method is invoked.

The method **getSessionDescription()** will return a SDP of the session in the form of a String. Before calling it, you must make sure that the **Session** has been configured. After calling **configure()** or **startPreview()** on you Session instance, the callback **onSessionConfigured()** will be called.

**In the example presented above, the Session instance is used in an asynchronous manner and calls to its methods do not block. You know when stuff is done when callbacks are called.**

**You can also use a Session object in a synchronous manner like that:**

```java
    // Blocks until the all streams are configured
    try {
         mSession.syncConfigure();
    } catch (Exception e) {
         ...
    }
    Strinf sdp = mSession.getSessionDescription();
    ...
    // Blocks until streaming actually starts.
    try {
         mSession.syncStart();
    } catch (Exception e) {
         ...
    }
    ...
    mSession.syncStop();
```

## How to use the RTSP client

Check out [this page of the wiki](https://github.com/fyhertz/libstreaming/wiki/Using-libstreaming-with-Wowza-Media-Server) and the [example 3](https://github.com/fyhertz/libstreaming-examples#example-3).

## How to use the RTSP server

#### Add this to your manifest:

```xml
<service android:name="net.majorkernelpanic.streaming.rtsp.RtspServer"/>
```

If you decide to override **RtspServer** change the line above accordingly.

#### You can change the port used by the RtspServer:

```java
Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
editor.putString(RtspServer.KEY_PORT, String.valueOf(1234));
editor.commit();
```

The port is indeed stored as a String in the preferences, there is a good reason to that. The EditTextPreference object saves its input as a String and cannot easily (one would need to override it) be configured to store it as an Integer.

#### Configure its behavior with the SessionBuilder:

```java
SessionBuilder.getInstance()    
			.setSurfaceHolder(mSurfaceView.getHolder())
			.setContext(getApplicationContext())
			.setAudioEncoder(SessionBuilder.AUDIO_AAC)
			.setVideoEncoder(SessionBuilder.VIDEO_H264);
```

#### Start and stop the server like this:

```java
// Starts the RTSP server
context.startService(new Intent(this,RtspServer.class));
// Stops the RTSP server
context.stopService(new Intent(this,RtspServer.class));
```

# Spydroid-ipcamera

Visit [this github page](https://github.com/fyhertz/spydroid-ipcamera) to see how this streaming stack can be used and how it performs.
Further information about Spydroid can be found on the google page of the project [here](https://spydroid-ipcamera.googlecode.com).
The app. is also available on google play [here](https://play.google.com/store/apps/details?id=net.majorkernelpanic.spydroid).

# Licensing

This streaming stack is available under two licenses, the GPL and a commercial license. *If you are willing to integrate this project into a close source application, please contact me at fyhertz at gmail.com*. Thank you.
