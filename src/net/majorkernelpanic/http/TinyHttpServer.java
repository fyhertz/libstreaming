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
 * 
 * Based on that: http://hc.apache.org/httpcomponents-core-ga/examples.html.
 * 
 */

package net.majorkernelpanic.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Date;
import java.util.Map;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509KeyManager;

import org.apache.http.ConnectionClosedException;
import org.apache.http.HttpException;
import org.apache.http.HttpServerConnection;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.DefaultHttpResponseFactory;
import org.apache.http.impl.DefaultHttpServerConnection;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.BasicHttpProcessor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.protocol.HttpRequestHandlerRegistry;
import org.apache.http.protocol.ResponseConnControl;
import org.apache.http.protocol.ResponseContent;
import org.apache.http.protocol.ResponseDate;
import org.apache.http.protocol.ResponseServer;
import org.apache.http.protocol.UriPatternMatcher;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

/**
 * 
 * A Service that contains a HTTP server and a HTTPS server.
 * 
 * Check out the github page of this project for further information.
 * 
 * You may add some logic to this server with {@link #addRequestHandler(String, HttpRequestHandler)}.
 * By default it serves files from /assets/www.
 * 
 */
public class TinyHttpServer extends Service {

	/** The tag used by the server. */
	public static final String TAG = "TinyHttpServer";

	/** Default port for HTTP. */
	public final static int DEFAULT_HTTP_PORT = 8080;

	/** Default port for HTTPS. */
	public final static int DEFAULT_HTTPS_PORT = 8443;
	
	/** Key used in the SharedPreferences to store whether the HTTP server is enabled or not. */
	public static final String PREF_HTTP_ENABLED = "http_enabled";
	
	/** Key used in the SharedPreferences to store whether the HTTPS server is enabled or not. */
	public static final String PREF_HTTPS_ENABLED = "https_enabled";
	
	/** Key used in the SharedPreferences for the port used by the HTTP server. */
	public static final String PREF_HTTP_PORT = "http_port";
	
	/** Key used in the SharedPreferences for the port used by the HTTPS server. */
	public static final String PREF_HTTPS_PORT = "https_port";

	/** Key used in the SharedPreferences for storing the password of the keystore. */
	public final static String PREF_PASSWORD = "https_password";
	
	/** Port already in use. */
	public final static int ERROR_HTTP_BIND_FAILED = 0x00;

	/** Port already in use. */
	public final static int ERROR_HTTPS_BIND_FAILED = 0x01;

	/** You need to add the class {@link ModSSL} to the {@link net.majorkernelpanic.http} package. */
	public final static int ERROR_HTTPS_NOT_SUPPORTED = 0x02;

	/** An error occured with the HTTPS server :( */
	public final static int ERROR_HTTPS_SERVER_CRASHED = 0x03;
	
	/** Common name that will appear in the root certificate. */
	public final static String CA_COMMON_NAME = "TinyHttpServer CA";

	/** Name of the file used to store the keystore containing the certificates for HTTPS. */
	public final static String KEYSTORE_NAME = "keystore.jks";
	
	public final static String[] MODULES = new String[] {
		"ModAssetServer",
		"ModInternationalization"
		};
	
	/** Be careful: those callbacks won't necessarily be called from the ui thread ! */
	public interface CallbackListener {

		/** Called when an error occurs. */
		void onError(TinyHttpServer server, Exception e, int error);

	}

	/**
	 * See {@link TinyHttpServer.CallbackListener} to check out what events will be fired once you set up a listener.
	 * @param listener The listener
	 */
	public void setCallbackListener(CallbackListener listener) { 
		mListener = listener;
	}

	/** 
	 * You may add some HttpRequestHandler to modify the default behavior of the server.
	 * @param pattern Patterns may have three formats: * or *<uri> or <uri>*
	 * @param handler A HttpRequestHandler
	 */ 
	protected void addRequestHandler(String pattern, HttpRequestHandler handler) {
		mRegistry.register(pattern, handler);
	}
	
	/**
	 * Sets the port for the HTTP server to use.
	 * @param port The port to use
	 */
	public void setHttpPort(int port) {
		Editor editor = mSharedPreferences.edit();
		editor.putString("http_port", String.valueOf(port));
		editor.commit();
	}

	/**
	 * Sets the port for the HTTPS server to use.
	 * @param port The port to use
	 */
	public void setHttpsPort(int port) {
		Editor editor = mSharedPreferences.edit();
		editor.putString("https_port", String.valueOf(port));
		editor.commit();
	}

	/** Enables the HTTP server, you then need to call {@link start()} to actually start it. */
	public void setHttpEnabled(boolean enable) {
		mHttpEnabled = enable;
	}

	/** Enables the HTTPS server, you then need to call {@link start()} to actually start it. */	
	public void setHttpsEnabled(boolean enable) {
		mHttpsEnabled = enable;
	}

	/** Returns the port used by the HTTP server. */	
	public int getHttpPort() {
		return mHttpPort;
	}

	/** Returns the port used by the HTTPS server. */
	public int getHttpsPort() {
		return mHttpsPort;
	}

	/** Indicates whether or not the HTTP server is enabled. */
	public boolean isHttpEnabled() {
		return mHttpEnabled;
	}

	/** Indicates whether or not the HTTPS server is enabled. */
	public boolean isHttpsEnabled() {
		return mHttpsEnabled;
	}	

	/** Starting this Service is not enough, you must bind a client to it and call this method. */
	public void start() {

		// Stops the HTTP server if it has been disabled or if it needs to be restarted
		if ((!mHttpEnabled || mHttpUpdate) && mHttpRequestListener != null) {
			mHttpRequestListener.kill();
			mHttpRequestListener = null;
		}
		// Stops the HTTPS server if it has been disabled or if it needs to be restarted
		if ((!mHttpsEnabled || mHttpsUpdate) && mHttpsRequestListener != null) {
			mHttpsRequestListener.kill();
			mHttpsRequestListener = null;
		}
		// Starts the HTTP server if needed
		if (mHttpEnabled && mHttpRequestListener == null) {
			try {
				mHttpRequestListener = new HttpRequestListener(mHttpPort);
			} catch (Exception e) {
				mHttpRequestListener = null;
			}
		}
		// Starts the HTTPS server if needed
		if (mHttpsEnabled && mHttpsRequestListener == null) {
			try {
				mHttpsRequestListener = new HttpsRequestListener(mHttpsPort);
			} catch (Exception e) {
				mHttpsRequestListener = null;
			}
		}

		mHttpUpdate = false;
		mHttpsUpdate = false;

	}

	/** Stops the HTTP server and/or the HTTPS server but not the Android service. */
	public void stop() {
		if (mHttpRequestListener != null) {
			// Stops the HTTP server
			mHttpRequestListener.kill();
			mHttpRequestListener = null;
		}
		if (mHttpsRequestListener != null) {
			// Stops the HTTPS server
			mHttpsRequestListener.kill();
			mHttpsRequestListener = null;
		}
	}
	
	Date mLastModified;
	MHttpRequestHandlerRegistry mRegistry;
	Context mContext;
	
	private BasicHttpProcessor mHttpProcessor;
	private HttpParams mParams; 

	private HttpRequestListener mHttpRequestListener = null;
	private HttpsRequestListener mHttpsRequestListener = null;

	private int mHttpPort = DEFAULT_HTTP_PORT;
	private int mHttpsPort = DEFAULT_HTTPS_PORT;

	private boolean mHttpEnabled = true, mHttpUpdate = false;
	private boolean mHttpsEnabled = false, mHttpsUpdate = false;

	private SharedPreferences mSharedPreferences;
	
	protected CallbackListener mListener = null;
	
	@Override
	public void onCreate() {

		super.onCreate();

		mContext = getApplicationContext();
		mRegistry = new MHttpRequestHandlerRegistry();
		mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

		mParams = new BasicHttpParams();
		mParams
		.setIntParameter(CoreConnectionPNames.SO_TIMEOUT, 5000)
		.setIntParameter(CoreConnectionPNames.SOCKET_BUFFER_SIZE, 8 * 1024)
		.setBooleanParameter(CoreConnectionPNames.STALE_CONNECTION_CHECK, false)
		.setBooleanParameter(CoreConnectionPNames.TCP_NODELAY, true)
		.setParameter(CoreProtocolPNames.ORIGIN_SERVER, "MajorKernelPanic HTTP Server");

		// Set up the HTTP protocol processor
		mHttpProcessor = new BasicHttpProcessor();
		mHttpProcessor.addInterceptor(new ResponseDate());
		mHttpProcessor.addInterceptor(new ResponseServer());
		mHttpProcessor.addInterceptor(new ResponseContent());
		mHttpProcessor.addInterceptor(new ResponseConnControl());

		// Will be used in the "Last-Modifed" entity-header field
		try {
			String packageName = mContext.getPackageName();
			mLastModified = new Date(mContext.getPackageManager().getPackageInfo(packageName, 0).lastUpdateTime);
		} catch (NameNotFoundException e) {
			mLastModified = new Date(0);
		}

		// Let's restore the state of the service 
		mHttpPort = Integer.parseInt(mSharedPreferences.getString("http_port", String.valueOf(mHttpPort)));
		mHttpsPort = Integer.parseInt(mSharedPreferences.getString("https_port", String.valueOf(mHttpsPort)));
		mHttpEnabled = mSharedPreferences.getBoolean("http_enabled", mHttpEnabled);
		mHttpsEnabled = mSharedPreferences.getBoolean("https_enabled", mHttpsEnabled);

		// If the configuration is modified, the server will adjust
		mSharedPreferences.registerOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);

		// Loads plugins available in the package net.majorkernelpanic.http
		for (int i=0; i<MODULES.length; i++) {
			try {
				Class<?> pluginClass = Class.forName(TinyHttpServer.class.getPackage().getName()+"."+MODULES[i]);
				Constructor<?> pluginConstructor = pluginClass.getConstructor(new Class[]{TinyHttpServer.class});
				addRequestHandler((String) pluginClass.getField("PATTERN").get(null), (HttpRequestHandler)pluginConstructor.newInstance(this));
			} catch (ClassNotFoundException ignore) {
				// Module disabled
			} catch (Exception e) {
				Log.e(TAG, "Bad module: "+MODULES[i]);
				e.printStackTrace();
			}
		}
		
	}

	@Override
	public void onDestroy() {
		stop();
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		settings.unregisterOnSharedPreferenceChangeListener(mOnSharedPreferenceChangeListener);
	}
	
	@Override
	public int onStartCommand (Intent intent, int flags, int startId) {
		//Log.d(TAG,"TinyServerHttp started !");
		return START_STICKY;
	}
	
	private OnSharedPreferenceChangeListener mOnSharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {
		@Override
		public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

			if (key.equals("http_port")) {
				int port = Integer.parseInt(sharedPreferences.getString("http_port", String.valueOf(mHttpPort)));
				if (port != mHttpPort) {
					mHttpPort = port;
					mHttpUpdate = true;
					start();
				}
			}

			else if (key.equals("https_port")) {
				int port = Integer.parseInt(sharedPreferences.getString("https_port", String.valueOf(mHttpsPort)));
				if (port != mHttpsPort) {
					mHttpsPort = port;
					mHttpUpdate = true;
					start();
				}
			}

			else if (key.equals("https_enabled")) {
				mHttpsEnabled = sharedPreferences.getBoolean("https_enabled", true);
				start();
			}			

			else if (key.equals("http_enabled")) {
				mHttpEnabled = sharedPreferences.getBoolean("http_enabled", true);
				start();
			}
		}
	};

	/** The Binder you obtain when a connection with the Service is established. */
	public class LocalBinder extends Binder {
		public TinyHttpServer getService() {
			return TinyHttpServer.this;
		}
	}

	/** See {@link TinyHttpServer.LocalBinder}. */
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	private final IBinder mBinder = new LocalBinder();

	protected class HttpRequestListener extends RequestListener {

		public HttpRequestListener(final int port) throws Exception {
			try {
				ServerSocket serverSocket = new ServerSocket(port);
				construct(serverSocket);
				Log.i(TAG,"HTTP server listening on port " + serverSocket.getLocalPort());
			} catch (BindException e) {
				mListener.onError(TinyHttpServer.this, e, ERROR_HTTP_BIND_FAILED); 
				throw e;
			}
		}

		protected void kill() {
			super.kill();
			Log.i(TAG,"HTTP server stopped !");
		}

	}

	protected class HttpsRequestListener extends RequestListener {

		private X509KeyManager mKeyManager;
		private char[] mPassword;
		private boolean mNotSupported = false;

		private final String mClasspath = TinyHttpServer.class.getPackage().getName()+".ModSSL$X509KeyManager";

		public HttpsRequestListener(final int port) throws Exception {

			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(TinyHttpServer.this);
			
			if (!settings.contains(PREF_PASSWORD)) {
				// Generates a password for the keystore
				// TODO: entropy of Math.random() ?
				String password = Integer.toString((int) (Math.random() * Integer.MAX_VALUE), 36);
				Editor editor = settings.edit();
				editor.putString(PREF_PASSWORD, password);
				editor.commit();
				mPassword = password.toCharArray();
				mContext.deleteFile(KEYSTORE_NAME);
			} else {
				mPassword = settings.getString(PREF_PASSWORD, "XX").toCharArray();
			}

			// We create the X509KeyManager through reflexion so that SSL support can easily be removed if not needed
			try {
				Class<?> X509KeyManager = Class.forName(mClasspath);
				Method loadFromKeyStore = X509KeyManager.getDeclaredMethod("loadFromKeyStore", InputStream.class, char[].class);

				try {
					InputStream is = mContext.openFileInput(KEYSTORE_NAME);
					mKeyManager = (X509KeyManager) loadFromKeyStore.invoke(null, is, mPassword);
				} catch (Exception e) {
					Constructor<?> constructor = X509KeyManager.getConstructor(new Class[]{char[].class, String.class});
					mKeyManager = (javax.net.ssl.X509KeyManager) constructor.newInstance(mPassword, CA_COMMON_NAME);
				}

				SSLContext sslContext = SSLContext.getInstance("TLS");
				sslContext.init(new KeyManager[] {mKeyManager}, null, null);
				ServerSocket serverSocket = sslContext.getServerSocketFactory().createServerSocket(port);
				construct(serverSocket);
				Log.i(TAG,"HTTPS server listening on port " + serverSocket.getLocalPort());

			} catch (NoSuchMethodException e) {
				// HTTPS support disabled !
				Log.e(TAG,"HTTPS not supported !");
				mListener.onError(TinyHttpServer.this, e, ERROR_HTTPS_NOT_SUPPORTED);
				throw e;
			} catch (BindException e) {
				mListener.onError(TinyHttpServer.this, e, ERROR_HTTPS_BIND_FAILED); 
				throw e;
			} catch (Exception e) {
				Log.e(TAG,"HTTPS server crashed !");
				e.printStackTrace();
				mListener.onError(TinyHttpServer.this, e, ERROR_HTTPS_SERVER_CRASHED);
				throw e;
			}

		}

		/** Stops the {@link TinyHttpServer.RequestListener} */
		protected void kill() {
			if (!mNotSupported) {
				super.kill();
				// Saves all the certificates generated by the our KeyManager in a keystore
				try {
					Method saveToKeyStore = Class.forName(mClasspath).getDeclaredMethod("saveToKeyStore", OutputStream.class, char[].class);
					// Prevents concurrent write operation in the keystore  
					OutputStream os = mContext.openFileOutput(KEYSTORE_NAME, Context.MODE_PRIVATE); 
					saveToKeyStore.invoke(mKeyManager, os, mPassword);
				} catch (NoSuchMethodException e) {
					// HTTPS support disabled !
					Log.e(TAG,"HTTPS not supported !");
					mListener.onError(TinyHttpServer.this, e, ERROR_HTTPS_NOT_SUPPORTED);
				} catch (Exception e) {
					System.out.println("An error occured while saving the KeyStore");
					e.printStackTrace();
				}
				Log.i(TAG,"HTTPS server stopped !");
			}
		}

	}

	private class RequestListener extends Thread {

		private ServerSocket mServerSocket;
		private final org.apache.http.protocol.HttpService mHttpService;

		protected RequestListener() throws Exception {

			mHttpService = new org.apache.http.protocol.HttpService(
					mHttpProcessor, 
					new DefaultConnectionReuseStrategy(), 
					new DefaultHttpResponseFactory());
			mHttpService.setHandlerResolver(mRegistry);
			mHttpService.setParams(mParams);

		}

		protected void construct(ServerSocket serverSocket) {
			mServerSocket = serverSocket;
			start();
		}

		protected void kill() {
			try {
				mServerSocket.close();
			} catch (IOException ignore) {}
			try {
				this.join();
			} catch (InterruptedException ignore) {}
		}

		public void run() {
			while (!Thread.interrupted()) {
				try {
					// Set up HTTP connection
					Socket socket = this.mServerSocket.accept();
					DefaultHttpServerConnection conn = new DefaultHttpServerConnection();
					Log.d(TAG,"Incoming connection from " + socket.getInetAddress());
					conn.bind(socket, mParams);

					// Start worker thread
					Thread t = new WorkerThread(this.mHttpService, conn, socket);
					t.setDaemon(true);
					t.start();
				} catch (SocketException e) {
					break;
				} catch (InterruptedIOException ex) {
					Log.e(TAG,"Interrupted !");
					break;
				} catch (IOException e) {
					Log.d(TAG,"I/O error initialising connection thread: " + e.getMessage());
					break;
				}
			}
		}
	}

	static class WorkerThread extends Thread {

		private final org.apache.http.protocol.HttpService httpservice;
		private final HttpServerConnection conn;
		private final Socket socket;

		public WorkerThread(
				final org.apache.http.protocol.HttpService httpservice, 
				final HttpServerConnection conn,
				final Socket socket) {
			super();
			this.httpservice = httpservice;
			this.conn = conn;
			this.socket = socket;
		}

		public void run() {
			Log.d(TAG,"New connection thread");
			HttpContext context = new MHttpContext(socket);
			try {
				while (!Thread.interrupted() && this.conn.isOpen()) {
					try {
						this.httpservice.handleRequest(this.conn, context);
					} catch (UnsupportedOperationException e) {
						e.printStackTrace();
						// shutdownOutput is not implemented by SSLSocket, and it is called in the implementation
						// of org.apache.http.impl.SocketHttpServerConnection.close().
					}
				}
			} catch (ConnectionClosedException e) {
				Log.d(TAG,"Client closed connection");
				e.printStackTrace();
			} catch (SocketTimeoutException e) {
				Log.d(TAG,"Socket timeout");
			} catch (IOException e) {
				Log.e(TAG,"I/O error: " + e.getMessage());
			} catch (HttpException e) {
				Log.e(TAG,"Unrecoverable HTTP protocol violation: " + e.getMessage());
			} finally {
				/*try {
					socket.close();
				} catch (IOException e) {}*/
				try {
					this.conn.shutdown();
				} catch (Exception ignore) {}
			}
		}
	}

	/** Little modification of BasicHttpContext to add access to the underlying tcp socket. */
	public static class MHttpContext extends BasicHttpContext {

		private Socket socket;

		public MHttpContext(Socket socket) {
			super(null);
			this.socket = socket;
		}

		/** Returns a reference to the underlying socket of the connection. */
		public Socket getSocket() {
			return socket;
		}

	}

	/**
	 * A slightly modified version of org.apache.http.protocol.HttpRequestHandlerRegistry 
	 * that allows the registry to be modified while some threads may be using it.
	 * Recent versions of this file are thread-safe, but Android seems to be using an old version. 
	 */
	public static class MHttpRequestHandlerRegistry extends HttpRequestHandlerRegistry {

		private final UriPatternMatcher matcher;

		public MHttpRequestHandlerRegistry() {
			matcher = new UriPatternMatcher();
		}

		public synchronized void register(final String pattern, final HttpRequestHandler handler) {
			matcher.register(pattern, handler);
		}

		public synchronized void unregister(final String pattern) {
			matcher.unregister(pattern);
		}

		public synchronized void setHandlers(final Map map) {
			matcher.setHandlers(map);
		}

		public synchronized HttpRequestHandler lookup(final String requestURI) {
			// This is the only function that will often be called by threads of the HTTP server
			// and it seems like a rather small crtical section to me, so it should not slow things down much
			return (HttpRequestHandler) matcher.lookup(requestURI);
		}

	}

}

