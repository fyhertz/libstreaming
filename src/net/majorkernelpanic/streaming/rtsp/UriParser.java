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

package net.majorkernelpanic.streaming.rtsp;

import static net.majorkernelpanic.streaming.SessionBuilder.AUDIO_AAC;
import static net.majorkernelpanic.streaming.SessionBuilder.AUDIO_AMRNB;
import static net.majorkernelpanic.streaming.SessionBuilder.AUDIO_NONE;
import static net.majorkernelpanic.streaming.SessionBuilder.VIDEO_H263;
import static net.majorkernelpanic.streaming.SessionBuilder.VIDEO_H264;
import static net.majorkernelpanic.streaming.SessionBuilder.VIDEO_NONE;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.List;

import net.majorkernelpanic.streaming.MediaStream;
import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.audio.AudioQuality;
import net.majorkernelpanic.streaming.video.VideoQuality;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import android.hardware.Camera.CameraInfo;

/**
 * This class parses URIs received by the RTSP server and configures a Session accordingly.
 */
public class UriParser {

	public final static String TAG = "UriParser";
	
	/**
	 * Configures a Session according to the given URI.
	 * Here are some examples of URIs that can be used to configure a Session:
	 * <ul><li>rtsp://xxx.xxx.xxx.xxx:8086?h264&flash=on</li>
	 * <li>rtsp://xxx.xxx.xxx.xxx:8086?h263&camera=front&flash=on</li>
	 * <li>rtsp://xxx.xxx.xxx.xxx:8086?h264=200-20-320-240</li>
	 * <li>rtsp://xxx.xxx.xxx.xxx:8086?aac</li></ul>
	 * @param uri The URI
	 * @throws IllegalStateException
	 * @throws IOException
	 * @return A Session configured according to the URI
	 */
	public static Session parse(String uri) throws IllegalStateException, IOException {		
		SessionBuilder builder = SessionBuilder.getInstance().clone();
		byte audioApi = 0, videoApi = 0;

		List<NameValuePair> params = URLEncodedUtils.parse(URI.create(uri),"UTF-8");
		if (params.size()>0) {

			builder.setAudioEncoder(AUDIO_NONE).setVideoEncoder(VIDEO_NONE);

			// Those parameters must be parsed first or else they won't necessarily be taken into account
			for (Iterator<NameValuePair> it = params.iterator();it.hasNext();) {
				NameValuePair param = it.next();

				// FLASH ON/OFF
				if (param.getName().equalsIgnoreCase("flash")) {
					if (param.getValue().equalsIgnoreCase("on")) 
						builder.setFlashEnabled(true);
					else 
						builder.setFlashEnabled(false);
				}

				// CAMERA -> the client can choose between the front facing camera and the back facing camera
				else if (param.getName().equalsIgnoreCase("camera")) {
					if (param.getValue().equalsIgnoreCase("back")) 
						builder.setCamera(CameraInfo.CAMERA_FACING_BACK);
					else if (param.getValue().equalsIgnoreCase("front")) 
						builder.setCamera(CameraInfo.CAMERA_FACING_FRONT);
				}

				// MULTICAST -> the stream will be sent to a multicast group
				// The default mutlicast address is 228.5.6.7, but the client can specify another
				else if (param.getName().equalsIgnoreCase("multicast")) {
					if (param.getValue()!=null) {
						try {
							InetAddress addr = InetAddress.getByName(param.getValue());
							if (!addr.isMulticastAddress()) {
								throw new IllegalStateException("Invalid multicast address !");
							}
							builder.setDestination(param.getValue());
						} catch (UnknownHostException e) {
							throw new IllegalStateException("Invalid multicast address !");
						}
					}
					else {
						// Default multicast address
						builder.setDestination("228.5.6.7");
					}
				}

				// UNICAST -> the client can use this to specify where he wants the stream to be sent
				else if (param.getName().equalsIgnoreCase("unicast")) {
					if (param.getValue()!=null) {
						builder.setDestination(param.getValue());
					}					
				}
				
				// VIDEOAPI -> can be used to specify what api will be used to encode video (the MediaRecorder API or the MediaCodec API)
				else if (param.getName().equalsIgnoreCase("videoapi")) {
					if (param.getValue()!=null) {
						if (param.getValue().equalsIgnoreCase("mr")) {
							videoApi = MediaStream.MODE_MEDIARECORDER_API;
						} else if (param.getValue().equalsIgnoreCase("mc")) {
							videoApi = MediaStream.MODE_MEDIACODEC_API;
						}
					}					
				}
				
				// AUDIOAPI -> can be used to specify what api will be used to encode audio (the MediaRecorder API or the MediaCodec API)
				else if (param.getName().equalsIgnoreCase("audioapi")) {
					if (param.getValue()!=null) {
						if (param.getValue().equalsIgnoreCase("mr")) {
							audioApi = MediaStream.MODE_MEDIARECORDER_API;
						} else if (param.getValue().equalsIgnoreCase("mc")) {
							audioApi = MediaStream.MODE_MEDIACODEC_API;
						}
					}					
				}		

				// TTL -> the client can modify the time to live of packets
				// By default ttl=64
				else if (param.getName().equalsIgnoreCase("ttl")) {
					if (param.getValue()!=null) {
						try {
							int ttl = Integer.parseInt(param.getValue());
							if (ttl<0) throw new IllegalStateException();
							builder.setTimeToLive(ttl);
						} catch (Exception e) {
							throw new IllegalStateException("The TTL must be a positive integer !");
						}
					}
				}

				// H.264
				else if (param.getName().equalsIgnoreCase("h264")) {
					VideoQuality quality = VideoQuality.parseQuality(param.getValue());
					builder.setVideoQuality(quality).setVideoEncoder(VIDEO_H264);
				}

				// H.263
				else if (param.getName().equalsIgnoreCase("h263")) {
					VideoQuality quality = VideoQuality.parseQuality(param.getValue());
					builder.setVideoQuality(quality).setVideoEncoder(VIDEO_H263);
				}

				// AMR
				else if (param.getName().equalsIgnoreCase("amrnb") || param.getName().equalsIgnoreCase("amr")) {
					AudioQuality quality = AudioQuality.parseQuality(param.getValue());
					builder.setAudioQuality(quality).setAudioEncoder(AUDIO_AMRNB);
				}

				// AAC
				else if (param.getName().equalsIgnoreCase("aac")) {
					AudioQuality quality = AudioQuality.parseQuality(param.getValue());
					builder.setAudioQuality(quality).setAudioEncoder(AUDIO_AAC);
				}

			}

		}

		if (builder.getVideoEncoder()==VIDEO_NONE && builder.getAudioEncoder()==AUDIO_NONE) {
			SessionBuilder b = SessionBuilder.getInstance();
			builder.setVideoEncoder(b.getVideoEncoder());
			builder.setAudioEncoder(b.getAudioEncoder());
		}

		Session session = builder.build();
		
		if (videoApi>0 && session.getVideoTrack() != null) {
			session.getVideoTrack().setStreamingMethod(videoApi);
		}
		
		if (audioApi>0 && session.getAudioTrack() != null) {
			session.getAudioTrack().setStreamingMethod(audioApi);
		}
		
		return session;

	}

}
