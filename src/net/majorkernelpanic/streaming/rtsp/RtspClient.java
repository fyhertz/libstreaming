/*
 * Copyright (C) 2011-2014 GUIGUI Simon, fyhertz@gmail.com
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

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.Stream;
import net.majorkernelpanic.streaming.rtp.RtpSocket;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

/**
 * RFC 2326.
 * A basic and asynchronous RTSP client.
 * The original purpose of this class was to implement a small RTSP client compatible with Wowza.
 * It implements Digest Access Authentication according to RFC 2069. 
 */
public class RtspClient {

	public final static String TAG = "RtspClient";

	/** Message sent when the connection to the RTSP server failed. */
	public final static int ERROR_CONNECTION_FAILED = 0x01;
	
	/** Message sent when the credentials are wrong. */
	public final static int ERROR_WRONG_CREDENTIALS = 0x03;
	
	/** Use this to use UDP for the transport protocol. */
	public final static int TRANSPORT_UDP = RtpSocket.TRANSPORT_UDP;
	
	/** Use this to use TCP for the transport protocol. */
	public final static int TRANSPORT_TCP = RtpSocket.TRANSPORT_TCP;	
	
	/** 
	 * Message sent when the connection with the RTSP server has been lost for 
	 * some reason (for example, the user is going under a bridge).
	 * When the connection with the server is lost, the client will automatically try to
	 * reconnect as long as {@link #stopStream()} is not called. 
	 **/
	public final static int ERROR_CONNECTION_LOST = 0x04;
	
	/**
	 * Message sent when the connection with the RTSP server has been reestablished.
	 * When the connection with the server is lost, the client will automatically try to
	 * reconnect as long as {@link #stopStream()} is not called.
	 */
	public final static int MESSAGE_CONNECTION_RECOVERED = 0x05;
	
	private final static int STATE_STARTED = 0x00;
	private final static int STATE_STARTING = 0x01;
	private final static int STATE_STOPPING = 0x02;
	private final static int STATE_STOPPED = 0x03;
	private int mState = 0;

	private class Parameters {
		public String host; 
		public String username;
		public String password;
		public String path;
		public Session session;
		public int port;
		public int transport;
		
		public Parameters clone() {
			Parameters params = new Parameters();
			params.host = host;
			params.username = username;
			params.password = password;
			params.path = path;
			params.session = session;
			params.port = port;
			params.transport = transport;
			return params;
		}
	}
	
	
	private Parameters mTmpParameters;
	private Parameters mParameters;

	private int mCSeq;
	private Socket mSocket;
	private String mSessionID;
	private String mAuthorization;
	private BufferedReader mBufferedReader;
	private OutputStream mOutputStream;
	private Callback mCallback;
	private Handler mMainHandler;
	private Handler mHandler;

	/**
	 * The callback interface you need to implement to know what's going on with the 
	 * RTSP server (for example your Wowza Media Server).
	 */
	public interface Callback {
		public void onRtspUpdate(int message, Exception exception);
	}

	public RtspClient() {
		mCSeq = 0;
		mTmpParameters = new Parameters();
		mTmpParameters.port = 1935;
		mTmpParameters.path = "/";
		mTmpParameters.transport = TRANSPORT_UDP;
		mAuthorization = null;
		mCallback = null;
		mMainHandler = new Handler(Looper.getMainLooper());
		mState = STATE_STOPPED;

		final Semaphore signal = new Semaphore(0);
		new HandlerThread("net.majorkernelpanic.streaming.RtspClient"){
			@Override
			protected void onLooperPrepared() {
				mHandler = new Handler();
				signal.release();
			}
		}.start();
		signal.acquireUninterruptibly();
		
	}

	/**
	 * Sets the callback interface that will be called on status updates of the connection
	 * with the RTSP server.
	 * @param cb The implementation of the {@link Callback} interface
	 */
	public void setCallback(Callback cb) {
		mCallback = cb;
	}

	/**
	 * The {@link Session} that will be used to stream to the server.
	 * If not called before {@link #startStream()}, a it will be created.
	 */
	public void setSession(Session session) {
		mTmpParameters.session = session;
	}

	public Session getSession() {
		return mTmpParameters.session;
	}	

	/**
	 * Sets the destination address of the RTSP server.
	 * @param host The destination address
	 * @param port The destination port
	 */
	public void setServerAddress(String host, int port) {
		mTmpParameters.port = port;
		mTmpParameters.host = host;
	}

	/**
	 * If authentication is enabled on the server, you need to call this with a valid username/password pair.
	 * Only implements Digest Access Authentication according to RFC 2069.
	 * @param username The username
	 * @param password The password
	 */
	public void setCredentials(String username, String password) {
		mTmpParameters.username = username;
		mTmpParameters.password = password;
	}

	/**
	 * The path to which the stream will be sent to. 
	 * @param path The path
	 */
	public void setStreamPath(String path) {
		mTmpParameters.path = path;
	}

	/**
	 * Call this with {@link #TRANSPORT_TCP} or {@value #TRANSPORT_UDP} to choose the 
	 * transport protocol that will be used to send RTP/RTCP packets.
	 * Not ready yet !
	 */
	public void setTransportMode(int mode) {
		mTmpParameters.transport = mode;
	}
	
	public boolean isStreaming() {
		return mState==STATE_STARTED|mState==STATE_STARTING;
	}

	/**
	 * Connects to the RTSP server to publish the stream, and the effectively starts streaming.
	 * You need to call {@link #setServerAddress(String, int)} and optionnally {@link #setSession(Session)} 
	 * and {@link #setCredentials(String, String)} before calling this.
	 * Should be called of the main thread !
	 */
	public void startStream() {
		if (mTmpParameters.host == null) throw new IllegalStateException("setServerAddress(String,int) has not been called !");
		if (mTmpParameters.session == null) throw new IllegalStateException("setSession() has not been called !");
		mHandler.post(new Runnable () {
			@Override
			public void run() {
				if (mState != STATE_STOPPED) return;
				mState = STATE_STARTING;
				
				Log.d(TAG,"Connecting to RTSP server...");
				
				// If the user calls some methods to configure the client, it won't modify its behavior until the stream is restarted
				mParameters = mTmpParameters.clone();
				mParameters.session.setDestination(mTmpParameters.host);
				
				try {
					mParameters.session.syncConfigure();
				} catch (Exception e) {
					mParameters.session = null;
					mState = STATE_STOPPED;
					return;
				}				
				
				try {
					tryConnection();
				} catch (Exception e) {
					postError(ERROR_CONNECTION_FAILED, e);
					abort();
					return;
				}
				
				try {
					mParameters.session.syncStart();
					mState = STATE_STARTED;
					if (mParameters.transport == TRANSPORT_UDP) {
						mHandler.post(mConnectionMonitor);
					}
				} catch (Exception e) {
					abort();
				}

			}
		});

	}

	/**
	 * Stops the stream, and informs the RTSP server.
	 */
	public void stopStream() {
		mHandler.post(new Runnable () {
			@Override
			public void run() {
				if (mParameters != null && mParameters.session != null) {
					mParameters.session.stop();
				}
				if (mState != STATE_STOPPED) {
					mState = STATE_STOPPING;
					abort();
				}
			}
		});
	}

	public void release() {
		stopStream();
		mHandler.getLooper().quit();
	}
	
	private void abort() {
		try {
			sendRequestTeardown();
		} catch (Exception ignore) {}
		try {
			mSocket.close();
		} catch (Exception ignore) {}
		mHandler.removeCallbacks(mConnectionMonitor);
		mHandler.removeCallbacks(mRetryConnection);
		mState = STATE_STOPPED;
	}
	
	private void tryConnection() throws IOException {
		mCSeq = 0;
		mSocket = new Socket(mParameters.host, mParameters.port);
		mBufferedReader = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
		mOutputStream = new BufferedOutputStream(mSocket.getOutputStream());
		sendRequestAnnounce();
		sendRequestSetup();
		sendRequestRecord();
	}
	
	/**
	 * Forges and sends the ANNOUNCE request 
	 */
	private void sendRequestAnnounce() throws IllegalStateException, SocketException, IOException {

		String body = mParameters.session.getSessionDescription();
		String request = "ANNOUNCE rtsp://"+mParameters.host+":"+mParameters.port+mParameters.path+" RTSP/1.0\r\n" +
				"CSeq: " + (++mCSeq) + "\r\n" +
				"Content-Length: " + body.length() + "\r\n" +
				"Content-Type: application/sdp \r\n\r\n" +
				body;
		Log.i(TAG,request.substring(0, request.indexOf("\r\n")));

		mOutputStream.write(request.getBytes("UTF-8"));
		mOutputStream.flush();
		Response response = Response.parseResponse(mBufferedReader);

		if (response.headers.containsKey("server")) {
			Log.v(TAG,"RTSP server name:" + response.headers.get("server"));
		} else {
			Log.v(TAG,"RTSP server name unknown");
		}

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

			if (mParameters.username == null || mParameters.password == null) throw new IllegalStateException("Authentication is enabled and setCredentials(String,String) was not called !");

			try {
				m = Response.rexegAuthenticate.matcher(response.headers.get("www-authenticate")); m.find();
				nonce = m.group(2);
				realm = m.group(1);
			} catch (Exception e) {
				throw new IOException("Invalid response from server");
			}

			String uri = "rtsp://"+mParameters.host+":"+mParameters.port+mParameters.path;
			String hash1 = computeMd5Hash(mParameters.username+":"+m.group(1)+":"+mParameters.password);
			String hash2 = computeMd5Hash("ANNOUNCE"+":"+uri);
			String hash3 = computeMd5Hash(hash1+":"+m.group(2)+":"+hash2);

			mAuthorization = "Digest username=\""+mParameters.username+"\",realm=\""+realm+"\",nonce=\""+nonce+"\",uri=\""+uri+"\",response=\""+hash3+"\"";

			request = "ANNOUNCE rtsp://"+mParameters.host+":"+mParameters.port+mParameters.path+" RTSP/1.0\r\n" +
					"CSeq: " + (++mCSeq) + "\r\n" +
					"Content-Length: " + body.length() + "\r\n" +
					"Authorization: " + mAuthorization + "\r\n" +
					"Session: " + mSessionID + "\r\n" +
					"Content-Type: application/sdp \r\n\r\n" +
					body;

			Log.i(TAG,request.substring(0, request.indexOf("\r\n")));

			mOutputStream.write(request.getBytes("UTF-8"));
			mOutputStream.flush();
			response = Response.parseResponse(mBufferedReader);

			if (response.status == 401) throw new RuntimeException("Bad credentials !");

		} else if (response.status == 403) {
			throw new RuntimeException("Access forbidden !");
		}

	}

	/**
	 * Forges and sends the SETUP request 
	 */
	private void sendRequestSetup() throws IllegalStateException, SocketException, IOException {
		for (int i=0;i<2;i++) {
			Stream stream = mParameters.session.getTrack(i);
			if (stream != null) {
				String params = mParameters.transport==TRANSPORT_TCP ? 
						("TCP;interleaved="+2*i+"-"+(2*i+1)) : ("UDP;unicast;client_port="+(5000+2*i)+"-"+(5000+2*i+1)+";mode=receive");
				String request = "SETUP rtsp://"+mParameters.host+":"+mParameters.port+mParameters.path+"/trackID="+i+" RTSP/1.0\r\n" +
						"Transport: RTP/AVP/"+params+"\r\n" +
						addHeaders();

				Log.i(TAG,request.substring(0, request.indexOf("\r\n")));

				mOutputStream.write(request.getBytes("UTF-8"));
				mOutputStream.flush();
				Response response = Response.parseResponse(mBufferedReader);
				Matcher m;
				if (mParameters.transport == TRANSPORT_UDP) {
					try {
						m = Response.rexegTransport.matcher(response.headers.get("transport")); m.find();
						stream.setDestinationPorts(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
						Log.d(TAG, "Setting destination ports: "+Integer.parseInt(m.group(3))+", "+Integer.parseInt(m.group(4)));
					} catch (Exception e) {
						e.printStackTrace();
						int[] ports = stream.getDestinationPorts();
						Log.d(TAG,"Server did not specify ports, using default ports: "+ports[0]+"-"+ports[1]);
					}
				} else {
					stream.setOutputStream(mOutputStream, (byte)(2*i));
				}
			}
		}
	}

	/**
	 * Forges and sends the RECORD request 
	 */
	private void sendRequestRecord() throws IllegalStateException, SocketException, IOException {
		String request = "RECORD rtsp://"+mParameters.host+":"+mParameters.port+mParameters.path+" RTSP/1.0\r\n" +
				"Range: npt=0.000-\r\n" +
				addHeaders();
		Log.i(TAG,request.substring(0, request.indexOf("\r\n")));
		mOutputStream.write(request.getBytes("UTF-8"));
		mOutputStream.flush();
		Response.parseResponse(mBufferedReader);
	}

	/**
	 * Forges and sends the TEARDOWN request 
	 */
	private void sendRequestTeardown() throws IOException {
		String request = "TEARDOWN rtsp://"+mParameters.host+":"+mParameters.port+mParameters.path+" RTSP/1.0\r\n" + addHeaders();
		Log.i(TAG,request.substring(0, request.indexOf("\r\n")));
		mOutputStream.write(request.getBytes("UTF-8"));
		mOutputStream.flush();
	}
	
	/**
	 * Forges and sends the OPTIONS request 
	 */
	private void sendRequestOption() throws IOException {
		String request = "OPTIONS rtsp://"+mParameters.host+":"+mParameters.port+mParameters.path+" RTSP/1.0\r\n" + addHeaders();
		Log.i(TAG,request.substring(0, request.indexOf("\r\n")));
		mOutputStream.write(request.getBytes("UTF-8"));
		mOutputStream.flush();
		Response.parseResponse(mBufferedReader);
	}	

	private String addHeaders() {
		return "CSeq: " + (++mCSeq) + "\r\n" +
				"Content-Length: 0\r\n" +
				"Session: " + mSessionID + "\r\n" +
				// For some reason you may have to remove last "\r\n" in the next line to make the RTSP client work with your wowza server :/
				(mAuthorization != null ? "Authorization: " + mAuthorization + "\r\n":"") + "\r\n";
	}

	/**
	 * If the connection with the RTSP server is lost, we try to reconnect to it as
	 * long as {@link #stopStream()} is not called.
	 */
	private Runnable mConnectionMonitor = new Runnable() {
		@Override
		public void run() {
			if (mState == STATE_STARTED) {
				try {
					// We poll the RTSP server with OPTION requests
					sendRequestOption();
					mHandler.postDelayed(mConnectionMonitor, 6000);
				} catch (IOException e) {
					// Happens if the OPTION request fails
					postMessage(ERROR_CONNECTION_LOST);
					Log.e(TAG, "Connection lost with the server...");
					mParameters.session.stop();
					mHandler.post(mRetryConnection);
				}
			}
		}
	};

	/** Here, we try to reconnect to the RTSP. */
	private Runnable mRetryConnection = new Runnable() {
		@Override
		public void run() {
			if (mState == STATE_STARTED) {
				try {
					Log.e(TAG, "Trying to reconnect...");
					tryConnection();
					try {
						mParameters.session.start();
						mHandler.post(mConnectionMonitor);
						postMessage(MESSAGE_CONNECTION_RECOVERED);
					} catch (Exception e) {
						abort();
					}
				} catch (IOException e) {
					mHandler.postDelayed(mRetryConnection,1000);
				}
			}
		}
	};
	
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

	/** Needed for the Digest Access Authentication. */
	private String computeMd5Hash(String buffer) {
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("MD5");
			return bytesToHex(md.digest(buffer.getBytes("UTF-8")));
		} catch (NoSuchAlgorithmException ignore) {
		} catch (UnsupportedEncodingException e) {}
		return "";
	}

	private void postMessage(final int message) {
		mMainHandler.post(new Runnable() {
			@Override
			public void run() {
				if (mCallback != null) {
					mCallback.onRtspUpdate(message, null); 
				}
			}
		});
	}

	private void postError(final int message, final Exception e) {
		mMainHandler.post(new Runnable() {
			@Override
			public void run() {
				if (mCallback != null) {
					mCallback.onRtspUpdate(message, e); 
				}
			}
		});
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

		/** Parse the method, URI & headers of a RTSP request */
		public static Response parseResponse(BufferedReader input) throws IOException, IllegalStateException, SocketException {
			Response response = new Response();
			String line;
			Matcher matcher;
			// Parsing request method & URI
			if ((line = input.readLine())==null) throw new SocketException("Connection lost");
			matcher = regexStatus.matcher(line);
			matcher.find();
			response.status = Integer.parseInt(matcher.group(1));

			// Parsing headers of the request
			while ( (line = input.readLine()) != null) {
				//Log.e(TAG,"l: "+line.length()+", c: "+line);
				if (line.length()>3) {
					matcher = rexegHeader.matcher(line);
					matcher.find();
					response.headers.put(matcher.group(1).toLowerCase(Locale.US),matcher.group(2));
				} else {
					break;
				}
			}
			if (line==null) throw new SocketException("Connection lost");

			Log.d(TAG, "Response from server: "+response.status);

			return response;
		}
	}

}
