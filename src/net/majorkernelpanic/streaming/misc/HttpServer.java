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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.URI;
import java.util.Iterator;
import java.util.List;

import net.majorkernelpanic.http.BasicHttpServer;
import net.majorkernelpanic.http.ModifiedHttpContext;
import net.majorkernelpanic.streaming.Session;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.entity.ContentProducer;
import org.apache.http.entity.EntityTemplate;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

/**
 * 
 * An HTTP server that can start and stop streams on an android device.
 * 
 * For example visiting "http://phoneip:8080/spydroid.sdp?h264" would start an H.264 stream
 * of the phone's front facing camera to the client of the request. 
 * The HTTP server response contains a proper Session Description (SDP).
 * All the option supported by the HTTP server are described in UriParser.java
 *
 */
public class HttpServer extends BasicHttpServer{

	/** This messsage will be sent to the handler if an error occurs. **/
	public static final int MESSAGE_ERROR = 0x07;
	
	/** Maximal number of streams that you can start from the HTTP server. **/
	protected static final int MAX_STREAM_NUM = 2;
	
	public HttpServer(int port, Context context, Handler handler) {
		super(port, context.getAssets());
		addRequestHandler("/spydroid.sdp*", new DescriptionRequestHandler(handler));
	} 
	
	public void stop() {
		super.stop();
		// If user has started a session with the HTTP Server, we need to stop it
		for (int i=0;i<DescriptionRequestHandler.sSessionList.length;i++) {
			if (DescriptionRequestHandler.sSessionList[i] != null) {
				DescriptionRequestHandler.sSessionList[i].stopAll();
				DescriptionRequestHandler.sSessionList[i].flush();
			}
		}
		
	}
	
	/** 
	 * Allow user to start streams (a session contains one or more streams) from the HTTP server by requesting 
	 * this URL: http://ip/spydroid.sdp (the RTSP server is not needed here). 
	 **/
	static class DescriptionRequestHandler implements HttpRequestHandler {

		private static Session[] sSessionList = new Session[MAX_STREAM_NUM];
		private final Handler mHandler;
		
		public DescriptionRequestHandler(final Handler handler) {
			mHandler = handler;
		}
		
		public synchronized void handle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException {
			Socket socket = ((ModifiedHttpContext)context).getSocket();
			String uri = request.getRequestLine().getUri();
			int id = 0;
			
			try {
				
				// A stream id can be specified in the URI, this id is associated to a session
				List<NameValuePair> params = URLEncodedUtils.parse(URI.create(uri),"UTF-8");
				uri = "";
				if (params.size()>0) {
					for (Iterator<NameValuePair> it = params.iterator();it.hasNext();) {
						NameValuePair param = it.next();
						if (param.getName().equals("id")) {
							try {	
								id = Integer.parseInt(param.getValue());
							} catch (Exception ignore) {}
						}
					}	
				}	

				params.remove("id");
				uri = "http://c?" + URLEncodedUtils.format(params, "UTF-8");
				
				// Stop all streams if a Session already exists
				if (sSessionList[id] != null) {
					if (sSessionList[id].getRoutingScheme()=="unicast") {
						sSessionList[id].stopAll();
						sSessionList[id].flush();
						sSessionList[id] = null;
					}
				}

				// Create new Session
				sSessionList[id] = new Session(socket.getLocalAddress(), socket.getInetAddress());

				// Parse URI and configure the Session accordingly 
				UriParser.parse(uri, sSessionList[id]);

				final String sessionDescriptor = sSessionList[id].getSessionDescription().replace("Unnamed", "Stream-"+id);

				response.setStatusCode(HttpStatus.SC_OK);
				EntityTemplate body = new EntityTemplate(new ContentProducer() {
					public void writeTo(final OutputStream outstream) throws IOException {
						OutputStreamWriter writer = new OutputStreamWriter(outstream, "UTF-8"); 
						writer.write(sessionDescriptor);
						writer.flush();
					}
				});
				body.setContentType("text/plain; charset=UTF-8");
				response.setEntity(body);

				// Start all streams associated to the Session
				sSessionList[id].startAll();

			} catch (Exception e) {
				response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
				Log.e(TAG,e.getMessage()!=null?e.getMessage():"An unknown error occurred");
				e.printStackTrace();
				mHandler.obtainMessage(MESSAGE_ERROR, e);
			}

		}

	}

}

