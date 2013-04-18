# Introduction

## What it does

**libstreaming** is an API that allows you, with only a few lines of code, to stream the camera and/or microphone of an android powered device using RTP over UDP. 

* Supported encoders includes H.264, H.263, AAC and AMR
* Since version 2.0, a basic support for RTCP has been implemented.
* libstreaming also features a RTSP server for easy remote control of the phones camera and microphone.

The full javadoc documentation of the API is available here: http://libstreaming.majorkernelpanic.net/

## How it does it

libstreaming uses various tricks to make streaming possible without the need of any native code. Access to the devices camera(s) and microphone is achieved by simply using a **MediaRecorder**, but configured to write to a **LocalSocket** instead of a regular file (**MediaStream.java**). Raw data from the peripheral are then processed in a thread doing syncronous read at the other end of the **LocalSocket**. Voila !

What the thread actually does is this: it waits for data from the peripheral and then packetizes it to make it fit into proper RTP packets that are then sent one by one on the network. The packetization algorithm that must be used for H.264, H.263, AMR and AAC are all specified in their respective RFC:

* RFC 3984 for H.264
* RFC 4629 for H.263
* RFC 3267 for AMR
* RFC 3640 for AAC

Therefore, depending on the nature of the data, the packetizer will be one out of the four packetizers implemented in the rtp package of libstreaming: **H264Packetizer.java**, **H263Packetizer.java**, **AACADTSPacketizer.java** or **AMRNBPacketizer.java**. I took the time to add lots of comments in those files so if you are looking for a basic implementation of one of the RFC mentionned above, they might come in handy.

RTCP packets are also sent by this thread since version 2.0 of libstreaming. Only Sender Reports are implemented.

# Using libstreaming in your app

## Required permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.CAMERA" />
```

And if you intend to use multicasting, add this one to the list:

```xml
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
```

## How to stream H.264 and AAC

```java
Session session = SessionBuilder.getInstance()
			.setSurfaceHolder(mSurfaceView.getHolder())
			.setContext(getApplicationContext())
			.setAudioEncoder(SessionBuilder.AUDIO_AAC)
			.setVideoEncoder(SessionBuilder.VIDEO_H264)
			.build();

session.setDestination(destination);
String sdp = session.getSessionDescription();
session.start();
```

The **SessionBuilder** simply facilitates the creation of **Session** objects. The call to **setSurfaceHolder** is needed for video streaming, that should not come up as a surprise since Android requires a valid surface for recording video (It's an ennoying limitation of the **MediaRecorder**). The call to **setContext** is optional but recommanded, it allows **H264Stream** objects and **AACStream** objects to store and recover data in the **SharedPreferences** of your app. Check the implementation of those two classes to find out exactly what data are stored. 

**Session** objects represents a streaming session to some peer. It contains one or more Stream objects that are started (resp. stopped) when the start() (resp. stop()) method is invoked. The method **setDestination** allows you to specify the ip address to which RTP and RTCP packets will be sent. The method **getSessionDescription** will return a SDP of the session in the form of a String.

The complete source code of this example is available here: http://libstreaming.majorkernelpanic.net/

## How to use the RTSP server

```java
// Starts the RTSP server
context.startService(new Intent(this,RtspServer.class));
// Stops the RTSP server
context.stopService(new Intent(this,RtspServer.class));
```

# Class diagramm

Soon !

# Spydroid-ipcamera

Visit [this github page](https://github.com/fyhertz/spydroid-ipcamera) to see how this streaming stack can be used and how it performs.
Further information about Spydroid can be found on the google page of the project [here](https://spydroid-ipcamera.googlecode.com).
The app. is also available on google play [here](https://play.google.com/store/apps/details?id=net.majorkernelpanic.spydroid).

# Licensing

This streaming stack is available under two licenses, the GPL and a commercial license. *If you are willing to integrate this project into a close source application, please contact me at fyhertz at gmail.com*. Thank you.