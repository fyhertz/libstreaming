/*
 * Copyright (C) 2011-2013 GUIGUI Simon, fyhertz@gmail.com
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

package net.majorkernelpanic.streaming.misc;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.List;

import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.video.VideoQuality;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import android.hardware.Camera.CameraInfo;

/**
 * This class parses URIs and configures Sessions accordingly.
 * It is used by the HttpServer and the Rtsp server of this package to configure Sessions
 */
public class UriParser {

	/**
	 * Configures a Session according to the given URI.
	 * Here are some examples of URIs that can be used to configure a Session:
	 * <ul><li>rtsp://xxx.xxx.xxx.xxx:8086?h264&flash=on</li>
	 * <li>rtsp://xxx.xxx.xxx.xxx:8086?h263&camera=front&flash=on</li>
	 * <li>rtsp://xxx.xxx.xxx.xxx:8086?h264=200-20-320-240</li>
	 * <li>rtsp://xxx.xxx.xxx.xxx:8086?aac</li></ul>
	 * @param uri The URI
	 * @param session The Session that will be configured
	 * @throws IllegalStateException
	 * @throws IOException
	 */
	public static void parse(String uri, Session session) throws IllegalStateException, IOException {
		boolean flash = false;
		int camera = CameraInfo.CAMERA_FACING_BACK;
		
		List<NameValuePair> params = URLEncodedUtils.parse(URI.create(uri),"UTF-8");
		if (params.size()>0) {
			
			// Those parameters must be parsed first or else they won't necessarily be taken into account
			for (Iterator<NameValuePair> it = params.iterator();it.hasNext();) {
				NameValuePair param = it.next();
				
				// FLASH ON/OFF
				if (param.getName().equals("flash")) {
					if (param.getValue().equals("on")) flash = true;
					else flash = false;
				}
				
				// CAMERA -> the client can choose between the front facing camera and the back facing camera
				else if (param.getName().equals("camera")) {
					if (param.getValue().equals("back")) camera = CameraInfo.CAMERA_FACING_BACK;
					else if (param.getValue().equals("front")) camera = CameraInfo.CAMERA_FACING_FRONT;
				}
				
				// MULTICAST -> the stream will be sent to a multicast group
				// The default mutlicast address is 228.5.6.7, but the client can specify one 
				else if (param.getName().equals("multicast")) {
					session.setRoutingScheme(Session.MULTICAST);
					if (param.getValue()!=null) {
						try {
							InetAddress addr = InetAddress.getByName(param.getValue());
							if (!addr.isMulticastAddress()) {
								throw new IllegalStateException("Invalid multicast address !");
							}
							session.setDestination(addr);
						} catch (UnknownHostException e) {
							throw new IllegalStateException("Invalid multicast address !");
						}
					}
					else {
						// Default multicast address
						session.setDestination(InetAddress.getByName("228.5.6.7"));
					}
				}
				
				// UNICAST -> the client can use this so specify where he wants the stream to be sent
				else if (param.getName().equals("unicast")) {
					if (param.getValue()!=null) {
						try {
							InetAddress addr = InetAddress.getByName(param.getValue());
							session.setDestination(addr);
						} catch (UnknownHostException e) {
							throw new IllegalStateException("Invalid destination address !");
						}
					}					
				}
				
				// TTL -> the client can modify the time to live of packets
				// By default ttl=64
				else if (param.getName().equals("ttl")) {
					if (param.getValue()!=null) {
						try {
							int ttl = Integer.parseInt(param.getValue());
							if (ttl<0) throw new IllegalStateException("The TTL must be a positive integer !");
							session.setTimeToLive(ttl);
						} catch (Exception e) {
							throw new IllegalStateException("The TTL must be a positive integer !");
						}
					}
				}
				
				// No tracks will be added to the session
				else if (param.getName().equals("stop")) {
					return;
				}
				
			}
			
			for (Iterator<NameValuePair> it = params.iterator();it.hasNext();) {
				NameValuePair param = it.next();
				
				// H264
				if (param.getName().equals("h264")) {
					VideoQuality quality = VideoQuality.parseQuality(param.getValue());
					session.addVideoTrack(Session.VIDEO_H264, camera, quality, flash);
				}
				
				// H263
				else if (param.getName().equals("h263")) {
					VideoQuality quality = VideoQuality.parseQuality(param.getValue());
					session.addVideoTrack(Session.VIDEO_H263, camera, quality, flash);
				}
				
				// AMRNB
				else if (param.getName().equals("amrnb") || param.getName().equals("amr")) {
					session.addAudioTrack(Session.AUDIO_AMRNB);
				}
				
				// AAC
				else if (param.getName().equals("aac")) {
					session.addAudioTrack(Session.AUDIO_AAC);
				}
				
				// Generic Audio Stream -> make use of api level 12
				// TODO: Doesn't work :/
				else if (param.getName().equals("testnewapi")) {
					session.addAudioTrack(Session.AUDIO_ANDROID_AMR);
				}
				
			}
			
			// The default behavior is to only add one video track
			if (session.getTrackCount()==0) {
				session.addVideoTrack();
				session.addAudioTrack();
			}
			
		} 
		// Uri has no parameters: the default behavior is to only add one video track
		else {
			session.addVideoTrack();
			session.addAudioTrack();
		}
	}
	
}
