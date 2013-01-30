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

package net.majorkernelpanic.spydroid.ui;

import java.io.IOException;
import java.util.Locale;

import net.majorkernelpanic.spydroid.R;
import net.majorkernelpanic.spydroid.SpydroidApplication;
import net.majorkernelpanic.spydroid.Utilities;
import net.majorkernelpanic.spydroid.api.CustomHttpServer;
import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.misc.HttpServer;
import net.majorkernelpanic.streaming.misc.RtspServer;
import net.majorkernelpanic.streaming.video.H264Stream;
import net.majorkernelpanic.streaming.video.VideoQuality;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.text.Layout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 
 * Spydroid launches an RtspServer, clients can then connect to it and receive audio/video streams from the phone
 */
public class SpydroidActivity extends Activity implements OnSharedPreferenceChangeListener {
    
    static final public String TAG = "SpydroidActivity";
    
    /** Default listening port for the RTSP server **/
    private final int defaultRtspPort = 8086;
    
    /** Default listening port for the HTTP server **/
    private final int defaultHttpPort = 8080;
    
    /** Default quality of video streams **/
	public static VideoQuality videoQuality = new VideoQuality(640,480,15,500000);
	
	/** By default AMR is the audio encoder **/
	public static int audioEncoder = Session.AUDIO_AMRNB;
	
	/** By default H.263 is the video encoder **/
	public static int videoEncoder = Session.VIDEO_H263;

    /** The HttpServer will use those variables to send reports about the state of the app to the http interface **/
    public static boolean activityPaused = true;
    public static Exception lastCaughtException;

    /** 
     * A little hack to allow video streaming while the app is running in the background, 
     * probably works very poorly on many devices...
     * The surface will be used to prevent garbage collection
     **/
    private boolean videoHackEnabled = false;
    private Surface videoHackSurface;
    
    static private CustomHttpServer httpServer = null;
    static private RtspServer rtspServer = null;
    
    private PowerManager.WakeLock wl;
    private SurfaceHolder holder;
    private SurfaceView camera;
    private TextView line1, line2, version, signWifi, signStreaming;
    private ImageView buttonSettings, buttonClient, buttonAbout;
    private LinearLayout signInformation;
    private Animation pulseAnimation;
    
    private boolean streaming = false, notificationEnabled = true;
    
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.main);

        camera = (SurfaceView)findViewById(R.id.smallcameraview);
        line1 = (TextView)findViewById(R.id.line1);
        line2 = (TextView)findViewById(R.id.line2);
        version = (TextView)findViewById(R.id.version);
        buttonSettings = (ImageView)findViewById(R.id.button_settings);
        //buttonClient = (ImageView)findViewById(R.id.button_client);
        buttonAbout = (ImageView)findViewById(R.id.button_about);
        signWifi = (TextView)findViewById(R.id.advice);
        signStreaming = (TextView)findViewById(R.id.streaming);
        signInformation = (LinearLayout)findViewById(R.id.information);
        pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse);
        
        if (SpydroidApplication.DONATE_VERSION) {
        	((LinearLayout)findViewById(R.id.adcontainer)).removeAllViews();
        }
        
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        settings.registerOnSharedPreferenceChangeListener(this);
       	
        camera.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        holder = camera.getHolder();
		
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "net.majorkernelpanic.spydroid.wakelock");
    
    	// Print version number
        try {
			version.setText("v"+this.getPackageManager().getPackageInfo(this.getPackageName(), 0 ).versionName);
		} catch (Exception e) {
			version.setText("v???");
		}
        
        // On android 3.* AAC ADTS is not supported so we set the default encoder to AMR-NB, on android 4.* AAC is the default encoder
        audioEncoder = (Integer.parseInt(android.os.Build.VERSION.SDK)<14) ? Session.AUDIO_AMRNB : Session.AUDIO_AAC;
        audioEncoder = Integer.parseInt(settings.getString("audio_encoder", String.valueOf(audioEncoder)));
        videoEncoder = Integer.parseInt(settings.getString("video_encoder", String.valueOf(videoEncoder)));
        
        // Read video quality settings from the preferences 
        videoQuality = VideoQuality.merge(
        		new VideoQuality(
        				settings.getInt("video_resX", 0),
        				settings.getInt("video_resY", 0), 
        				Integer.parseInt(settings.getString("video_framerate", "0")), 
        				Integer.parseInt(settings.getString("video_bitrate", "0"))*1000
        		),
        		videoQuality);

        Session.setHandler(handler);
        Session.setDefaultAudioEncoder(audioEncoder);
        Session.setDefaultVideoEncoder(videoEncoder);
        Session.setDefaultVideoQuality(videoQuality);
        H264Stream.setPreferences(settings);

        // There is the ugly hack to make video streaming possible even if spydroid is running background
        videoHackEnabled = settings.getBoolean("video_hack", false);
        Session.setSurfaceHolder(holder, !videoHackEnabled);
        holder.addCallback(new SurfaceHolder.Callback() {
			public void surfaceDestroyed(SurfaceHolder holder) {
				// We remove the reference to allow garbage collection if videoHackEnabled is false 
				if (!videoHackEnabled) videoHackSurface = null;
			}
			public void surfaceCreated(SurfaceHolder holder) {
				// We store a reference to the surface just to make sure that it won't be 
				// garbage collected
				videoHackSurface = holder.getSurface();
			}
			public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
		});
        
        if (rtspServer == null) rtspServer = new RtspServer(defaultRtspPort, handler);
        if (httpServer == null) httpServer = new CustomHttpServer(defaultHttpPort, this.getApplicationContext(), handler);

        buttonSettings.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
	            // Starts QualityListActivity where user can change the quality of the stream
				Intent intent = new Intent(getApplicationContext(),OptionsActivity.class);
	            startActivityForResult(intent, 0);
			}
		});
        /*buttonClient.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				// Starts ClientActivity, the user can then capture the stream from another phone running Spydroid
	            Intent intent = new Intent(context,ClientActivity.class);
	            startActivityForResult(intent, 0);
			}
		});*/
        buttonAbout.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
	            // Display some information
	            Intent intent = new Intent(getApplicationContext(),AboutActivity.class);
	            startActivityForResult(intent, 0);
			}
		});
        
        // Did the user disabled the notification ?
        notificationEnabled = settings.getBoolean("notification_enabled", true);
        
    }
    
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    	if (key.equals("video_resX") || key.equals("video_resY")) {
    		videoQuality.resX = sharedPreferences.getInt("video_resX", 0);
    		videoQuality.resY = sharedPreferences.getInt("video_resY", 0);
    	}
    	else if (key.equals("video_framerate")) {
    		videoQuality.framerate = Integer.parseInt(sharedPreferences.getString("video_framerate", "0"));
    	}
    	else if (key.equals("video_bitrate")) {
    		videoQuality.bitrate = Integer.parseInt(sharedPreferences.getString("video_bitrate", "0"))*1000;
    	}
    	else if (key.equals("stream_audio")) {
    		if (!sharedPreferences.getBoolean("stream_audio", true)) Session.setDefaultAudioEncoder(0);
    	}
    	else if (key.equals("audio_encoder")) { 
    		audioEncoder = Integer.parseInt(sharedPreferences.getString("audio_encoder", "0"));
    		Session.setDefaultAudioEncoder( audioEncoder );
    	}
    	else if (key.equals("stream_video")) {
    		if (!sharedPreferences.getBoolean("stream_video", true)) Session.setDefaultVideoEncoder(0);
    	}
    	else if (key.equals("video_encoder")) {
    		videoEncoder = Integer.parseInt(sharedPreferences.getString("video_encoder", "0"));
    		Session.setDefaultVideoEncoder( videoEncoder );
    	}
    	else if (key.equals("enable_http")) {
    		if (sharedPreferences.getBoolean("enable_http", true)) {
    			if (httpServer == null) httpServer = new CustomHttpServer(defaultHttpPort, this.getApplicationContext(), handler);
    		} else {
    			if (httpServer != null) {
    				httpServer.stop();
    				httpServer = null;
    			}
    		}
    	}
    	else if (key.equals("enable_rtsp")) {
    		if (sharedPreferences.getBoolean("enable_rtsp", true)) {
    			if (rtspServer == null) rtspServer = new RtspServer(defaultRtspPort, handler);
    		} else {
    			if (rtspServer != null) {
    				rtspServer.stop();
    				rtspServer = null;
    			}
    		}
    	}
    	else if (key.equals("notification_enabled")) {
    		notificationEnabled  = sharedPreferences.getBoolean("notification_enabled", true);
    		removeNotification();
    	}
    	else if (key.equals("video_hack")) {
    		videoHackEnabled = sharedPreferences.getBoolean("video_hack", false);
    		Session.setSurfaceHolder(holder,!videoHackEnabled); 
    	}
    }
    
    public void onStart() {
    	super.onStart();
    	
    	// Lock screen
    	wl.acquire();
    	
    	
    	if (notificationEnabled) {
    		Intent notificationIntent = new Intent(this, SpydroidActivity.class);
    		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);

    		NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
    		Notification notification = builder.setContentIntent(pendingIntent)
    				.setWhen(System.currentTimeMillis())
    				.setTicker(getText(R.string.notification_title))
    				.setSmallIcon(R.drawable.icon)
    				.setContentTitle(getText(R.string.notification_title))
    				.setContentText(getText(R.string.notification_content)).build();
    		notification.flags |= Notification.FLAG_ONGOING_EVENT;
    		((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).notify(0,notification);
    	}
    	
    }
    	
    public void onStop() {
    	super.onStop();
    	// A WakeLock should only be released when isHeld() is true !
    	if (wl.isHeld()) wl.release();
    }
    
    public void onResume() {
    	super.onResume();
    	// Determines if user is connected to a wireless network & displays ip 
    	if (!streaming) displayIpAddress();
    	activityPaused = true;
    	startServers();
    	registerReceiver(wifiStateReceiver,new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
    }
    
    public void onPause() {
    	super.onPause();
    	activityPaused = false;
    	unregisterReceiver(wifiStateReceiver);
    }
    
    public void onDestroy() {
    	Log.d(TAG,"SpydroidActivity destroyed");
    	super.onDestroy();
    }
    
    public void onBackPressed() {
    	Intent setIntent = new Intent(Intent.ACTION_MAIN);
    	setIntent.addCategory(Intent.CATEGORY_HOME);
    	setIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    	startActivity(setIntent);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }
    
    public boolean onOptionsItemSelected(MenuItem item) {
    	Intent intent;
    	
        switch (item.getItemId()) {
        /*case R.id.client:
            // Starts ClientActivity where user can view stream from another phone
            intent = new Intent(this.getBaseContext(),ClientActivity.class);
            startActivityForResult(intent, 0);
            return true;*/
        case R.id.options:
            // Starts QualityListActivity where user can change the streaming quality
            intent = new Intent(this.getBaseContext(),OptionsActivity.class);
            startActivityForResult(intent, 0);
            return true;
        case R.id.quit:
        	// Quits Spydroid i.e. stops the HTTP & RTSP servers
        	stopServers();  
        	// Remove notification
        	if (notificationEnabled) removeNotification();          	
        	finish();	
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
    
    private void startServers() {
    	if (rtspServer != null) {
    		try {
    			rtspServer.start();
    		} catch (IOException e) {
    			log("RtspServer could not be started : "+(e.getMessage()!=null?e.getMessage():"Unknown error"));
    		}
    	}
    	if (httpServer != null) {
    		try {
    			httpServer.start();
    		} catch (IOException e) {
    			log("HttpServer could not be started : "+(e.getMessage()!=null?e.getMessage():"Unknown error"));
    		}
    	}
    }

    private void stopServers() {
    	if (httpServer != null) {
    		httpServer.stop();
    		httpServer = null;
    	}
    	if (rtspServer != null) {
    		rtspServer.stop();
    		rtspServer = null;
    	}
    }
    
    
    // BroadcastReceiver that detects wifi state changements
    private final BroadcastReceiver wifiStateReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
        	String action = intent.getAction();
        	// This intent is also received when app resumes even if wifi state hasn't changed :/
        	if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
        		if (!streaming) displayIpAddress();
        	}
        } 
    };
    
    // The Handler that gets information back from the RtspServer and Session
    private final Handler handler = new Handler() {
    	
    	public void handleMessage(Message msg) { 
    		switch (msg.what) {
    		case RtspServer.MESSAGE_ERROR:
    			Exception e1 = (Exception)msg.obj;
    			lastCaughtException = e1;
    			log(e1.getMessage()!=null?e1.getMessage():"An error occurred !");
    			break;
    		case RtspServer.MESSAGE_LOG:
    			//log((String)msg.obj);
    			break;
    		case HttpServer.MESSAGE_ERROR:
    			Exception e2 = (Exception)msg.obj;
    			lastCaughtException = e2;
    			break;    			
    		case Session.MESSAGE_START:
    			streaming = true;
    			streamingState(1);
    			break;
    		case Session.MESSAGE_STOP:
    			streaming = false;
    			displayIpAddress();
    			break;
    		}
    	}
    	
    };
    
    private void displayIpAddress() {
		WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
		WifiInfo info = wifiManager.getConnectionInfo();
		String ipaddress = null;
		Log.d("SpydroidActivity","getNetworkId "+info.getNetworkId());
    	if (info!=null && info.getNetworkId()>-1) {
	    	int i = info.getIpAddress();
	        String ip = String.format(Locale.ENGLISH,"%d.%d.%d.%d", i & 0xff, i >> 8 & 0xff,i >> 16 & 0xff,i >> 24 & 0xff);
	    	line1.setText("http://");
	    	line1.append(ip);
	    	line1.append(":"+defaultHttpPort);
	    	line2.setText("rtsp://");
	    	line2.append(ip);
	    	line2.append(":"+defaultRtspPort);
	    	streamingState(0);
    	} else if((ipaddress = Utilities.getLocalIpAddress(true)) != null) {
    		line1.setText("http://");
	    	line1.append(ipaddress);
	    	line1.append(":"+defaultHttpPort);
	    	line2.setText("rtsp://");
	    	line2.append(ipaddress);
	    	line2.append(":"+defaultRtspPort);
	    	streamingState(0);
    	} else {
      		line1.setText("HTTP://xxx.xxx.xxx.xxx:"+defaultHttpPort);
    		line2.setText("RTSP://xxx.xxx.xxx.xxx:"+defaultHttpPort);
    		streamingState(2);
    	}
    	
    }
    
    public void log(String s) {
    	Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
    }

	private void streamingState(int state) {
		// Not streaming
		if (state==0) {
			signStreaming.clearAnimation();
			signWifi.clearAnimation();
			signStreaming.setVisibility(View.GONE);
			signInformation.setVisibility(View.VISIBLE);
			signWifi.setVisibility(View.GONE);
		} else if (state==1) {
			// Streaming
			signWifi.clearAnimation();
			signStreaming.setVisibility(View.VISIBLE);
			signStreaming.startAnimation(pulseAnimation);
			signInformation.setVisibility(View.INVISIBLE);
			signWifi.setVisibility(View.GONE);
		} else if (state==2) {
			// No wifi !
			signStreaming.clearAnimation();
			signStreaming.setVisibility(View.GONE);
			signInformation.setVisibility(View.INVISIBLE);
			signWifi.setVisibility(View.VISIBLE);
			signWifi.startAnimation(pulseAnimation);
		}
	}
	
	private void removeNotification() {
		((NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE)).cancel(0);
	}
    
}