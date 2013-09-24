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

package net.majorkernelpanic.streaming.rtsp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.Stream;
import android.util.Log;

/**
 * RFC 2326.
 * A basic and synchronous RTSP client.
 * The original purpose of this class was to implement a small RTSP client compatible with Wowza.
 * It implements Digest Access Authentication according to RFC 2069. 
 */
public class RtspClient {

	public final static String TAG = "RtspClient";

	private Socket mSocket;
	private String mHost;
	private String mUsername;
	private String mPassword;
	private String mPath;
	private String mSessionID;
	private String mAuthorization;
	private Session mSession;
	private BufferedReader mBufferedReader;
	private OutputStream mOutputStream;
	private int mCSeq, mPort;
	private boolean mRunning;

	public RtspClient() {
		mCSeq = 0;
		mPort = 1935;
		mPath = "/";
		mAuthorization = null;
		mRunning = false;
	}

	/**
	 * The {@link Session} that will be used to stream to the server.
	 * If not called before {@link #startStream(int)}, a it will be created.
	 */
	public void setSession(Session session) {
		mSession = session;
	}

	/**
	 * Sets the destination address of the RTSP server.
	 * @param host The destination address
	 * @param port The destination port
	 */
	public void setServerAddress(String host, int port) {
		mPort = port;
		mHost = host;
	}

	/**
	 * If authentication is enabled on the server, you need to call this with a valid username/password pair.
	 * Only implements Digest Access Authentication according to RFC 2069.
	 * @param username The username
	 * @param password The password
	 */
	public void setCredentials(String username, String password) {
		mUsername = username;
		mPassword = password;
	}

	/**
	 * The path to which the stream will be sent to. 
	 * @param path The path
	 */
	public void setStreamPath(String path) {
		mPath = path;
	}

	public synchronized boolean isRunning() {
		return mRunning;
	}
	
	/**
	 * Connects to the RTSP server to publish the stream, and the effectively starts streaming.
	 * You need to call {@link #setServerAddress(String, int)} and optionnally {@link #setSession(Session)} 
	 * and {@link #setCredentials(String, String)} before calling this.
	 * Should be called of the main thread !
	 * @param retries The number of retries that will be
	 * @throws RuntimeException Thrown if wrong credentials have been provided, or if an error occurs with {@link Session#start()}
	 * @throws IllegalStateException Thrown if {@link #setServerAddress(String, int)} was never called, if the server requires authentication of the client, or if an error occurs with {@link Session#start()} 
	 * @throws UnknownHostException Thrown if the hostname specified with {@link #setServerAddress(String, int)} can't be resolved
	 * @throws IOException Thrown if an error occurs with {@link Session#start()}
	 */
	public synchronized void startStream(int retries) throws RuntimeException, IllegalStateException, UnknownHostException, IOException {

		if (mRunning) return;
		if (mHost == null) throw new IllegalStateException("setServerAddress(String,int) has not been called !");
		if (mSession == null) mSession = SessionBuilder.getInstance().build();

		mSocket = new Socket(mHost, mPort);
		mBufferedReader = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
		mOutputStream = mSocket.getOutputStream();
		mSession.setDestination(InetAddress.getByName(mHost));

		sendRequestAnnounce();
		sendRequestSetup();
		sendRequestRecord();

		try {
			mSession.start();
		} catch (RuntimeException e) {
			stopStream();
			throw e;
		} catch (IOException e) {
			stopStream();
			throw e;
		}
		
		mRunning = true;

	}

	/**
	 * Stops the stream, and informs the RTSP server.
	 */
	public synchronized void stopStream() {

		if (!mRunning) return;
		
		try {
			sendRequestTeardown();
		} catch (Exception ignore) {}
		try {
			mSocket.close();
		} catch (Exception ignore) {}

		mSession.stop();
		mRunning = false;
	}

	/**
	 * Forges and sends the ANNOUNCE request 
	 */
	private void sendRequestAnnounce() throws IllegalStateException, SocketException, IOException {

		String body = mSession.getSessionDescription();
		String request = "ANNOUNCE rtsp://"+mHost+":"+mPort+mPath+" RTSP/1.0\r\n" +
				"CSeq: " + (++mCSeq) + "\r\n" +
				"Content-Length: " + body.length() + "\r\n" +
				"Content-Type: application/sdp \r\n\r\n" +
				body;
		Log.i(TAG,request.substring(0, request.indexOf("\r\n")));

		mOutputStream.write(request.getBytes("UTF-8"));
		Response response = Response.parseResponse(mBufferedReader);

		try {
			Matcher m = Response.rexegSession.matcher(response.headers.get("session"));
			m.find();
			mSessionID = m.group(1);
		} catch (Exception e) {
			throw new IOException("Invalid response from server. Session id: "+mSessionID);
		}

		if (response.status == 401) {
			String nonce, realm;
			Matcher m;

			if (mUsername == null || mPassword == null) throw new IllegalStateException("Authentication is enabled and setCredentials(String,String) was not called !");

			try {
				m = Response.rexegAuthenticate.matcher(response.headers.get("www-authenticate")); m.find();
				nonce = m.group(2);
				realm = m.group(1);
			} catch (Exception e) {
				throw new IOException("Invalid response from server");
			}

			String uri = "rtsp://"+mHost+":"+mPort+mPath;
			String hash1 = computeMd5Hash(mUsername+":"+m.group(1)+":"+mPassword);
			String hash2 = computeMd5Hash("ANNOUNCE"+":"+uri);
			String hash3 = computeMd5Hash(hash1+":"+m.group(2)+":"+hash2);

			mAuthorization = "Digest username=\""+mUsername+"\",realm=\""+realm+"\",nonce=\""+nonce+"\",uri=\""+uri+"\",response=\""+hash3+"\"\r\n";

			request = "ANNOUNCE rtsp://"+mHost+":"+mPort+mPath+" RTSP/1.0\r\n" +
					"CSeq: " + (++mCSeq) + "\r\n" +
					"Content-Length: " + body.length() + "\r\n" +
					"Authorization: " + mAuthorization +
					"Session: " + mSessionID + "\r\n" +
					"Content-Type: application/sdp \r\n\r\n" +
					body;

			Log.i(TAG,request.substring(0, request.indexOf("\r\n")));

			mOutputStream.write(request.getBytes("UTF-8"));
			response = Response.parseResponse(mBufferedReader);

			if (response.status == 401) throw new RuntimeException("Bad credentials !");

		}

	}

	/**
	 * Forges and sends the SETUP request 
	 */
	private void sendRequestSetup() throws IllegalStateException, SocketException, IOException {
		for (int i=0;i<2;i++) {
			Stream stream = mSession.getTrack(i);
			if (stream != null) {
				String request = "SETUP rtsp://"+mHost+":"+mPort+mPath+"/trackID="+i+" RTSP/1.0\r\n" +
						"CSeq: " + (++mCSeq) + "\r\n" +
						"Transport: RTP/AVP/UDP;unicast;client_port="+(5000+2*i)+"-"+(5000+2*i+1)+";mode=receive\r\n" +
						"Session: " + mSessionID + "\r\n" +
						"Authorization: " + mAuthorization+ "\r\n\r\n";

				Log.i(TAG,request.substring(0, request.indexOf("\r\n")));

				mOutputStream.write(request.getBytes("UTF-8"));
				Response response = Response.parseResponse(mBufferedReader);
				Matcher m;
				try {
					m = Response.rexegTransport.matcher(response.headers.get("transport")); m.find();
					stream.setDestinationPorts(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
					Log.d(TAG, "Setting destination ports: "+Integer.parseInt(m.group(3))+", "+Integer.parseInt(m.group(4)));
				} catch (Exception e) {
					e.printStackTrace();
					int[] ports = stream.getDestinationPorts();
					Log.d(TAG,"Server did not specify ports, using default ports: "+ports[0]+"-"+ports[1]);
				}
			}
		}
	}

	/**
	 * Forges and sends the RECORD request 
	 */
	private void sendRequestRecord() throws IllegalStateException, SocketException, IOException {
		String request = "RECORD rtsp://"+mHost+":"+mPort+mPath+" RTSP/1.0\r\n" +
				"Range: npt=0.000-" +
				"CSeq: " + (++mCSeq) + "\r\n" +
				"Session: " + mSessionID + "\r\n" +
				"Authorization: " + mAuthorization+ "\r\n\r\n";
		Log.i(TAG,request.substring(0, request.indexOf("\r\n")));
		mOutputStream.write(request.getBytes("UTF-8"));
		Response.parseResponse(mBufferedReader);
	}
	
	/**
	 * Forges and sends the TEARDOWN request 
	 */
	private void sendRequestTeardown() throws IOException {
		String request = "TEARDOWN rtsp://"+mHost+":"+mPort+mPath+" RTSP/1.0\r\n" +
				"CSeq: " + (++mCSeq) + "\r\n" +
				"Session: " + mSessionID + "\r\n" +
				"Authorization: " + mAuthorization+ "\r\n";
		Log.i(TAG,request.substring(0, request.indexOf("\r\n")));
		mOutputStream.write(request.getBytes("UTF-8"));
		Response.parseResponse(mBufferedReader);
	}

	final protected static char[] hexArray = {'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f'};

	private static String bytesToHex(byte[] bytes) {
		char[] hexChars = new char[bytes.length * 2];
		int v;
		for ( int j = 0; j < bytes.length; j++ ) {
			v = bytes[j] & 0xFF;
			hexChars[j * 2] = hexArray[v >>> 4];
			hexChars[j * 2 + 1] = hexArray[v & 0x0F];
		}
		return new String(hexChars);
	}

	private String computeMd5Hash(String buffer) {
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("MD5");
			return bytesToHex(md.digest(buffer.getBytes("UTF-8")));
		} catch (NoSuchAlgorithmException ignore) {
		} catch (UnsupportedEncodingException e) {}
		return "";
	}

	static class Response {

		// Parses method & uri
		public static final Pattern regexStatus = Pattern.compile("RTSP/\\d.\\d (\\d+) (\\w+)",Pattern.CASE_INSENSITIVE);
		// Parses a request header
		public static final Pattern rexegHeader = Pattern.compile("(\\S+):(.+)",Pattern.CASE_INSENSITIVE);
		// Parses a WWW-Authenticate header
		public static final Pattern rexegAuthenticate = Pattern.compile("realm=\"(.+)\",\\s+nonce=\"(\\w+)\"",Pattern.CASE_INSENSITIVE);
		// Parses a Session header
		public static final Pattern rexegSession = Pattern.compile("(\\d+)",Pattern.CASE_INSENSITIVE);
		// Parses a Transport header
		public static final Pattern rexegTransport = Pattern.compile("client_port=(\\d+)-(\\d+).+server_port=(\\d+)-(\\d+)",Pattern.CASE_INSENSITIVE);


		public int status;
		public HashMap<String,String> headers = new HashMap<String,String>();

		/** Parse the method, uri & headers of a RTSP request */
		public static Response parseResponse(BufferedReader input) throws IOException, IllegalStateException, SocketException {
			Response response = new Response();
			String line;
			Matcher matcher;
			// Parsing request method & uri
			if ((line = input.readLine())==null) throw new SocketException("Connection lost");
			matcher = regexStatus.matcher(line);
			matcher.find();
			response.status = Integer.parseInt(matcher.group(1));

			// Parsing headers of the request
			while ( (line = input.readLine()) != null && line.length()>3 ) {
				matcher = rexegHeader.matcher(line);
				matcher.find();
				response.headers.put(matcher.group(1).toLowerCase(Locale.US),matcher.group(2));
			}
			if (line==null) throw new SocketException("Connection lost");

			// It's not an error, it's just easier to follow what's happening in logcat with the request in red
			Log.d(TAG, "Response from server: "+response.status);

			return response;
		}
	}

}
