/*
 * Copyright (C) 2011 GUIGUI Simon, fyhertz@gmail.com
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

import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.majorkernelpanic.spydroid.R;

import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.VideoView;

/** 
 * Allow a user with two smartphones to control one from the other
 * Feature disabled, do not work well enough :( because of MediaPlayer that really really sucks
 **/
public class ClientActivity extends Activity implements OnCompletionListener, OnPreparedListener, OnItemSelectedListener {

	private final static String TAG = "ClientActivity";

	private SharedPreferences settings;
	
	private EditText editTextIP; 
	private MyVideoView videoView;
	private MediaPlayer audioStream;
	private FrameLayout layoutContainer;
	private RelativeLayout layoutForm, layoutControl; 
	private ProgressBar progressBar;
	
	private String videoParameters = "", audioParameters="";
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.client);
     
        editTextIP = (EditText)findViewById(R.id.server_ip);
        layoutContainer = (FrameLayout)findViewById(R.id.video_container);
        layoutForm = (RelativeLayout)findViewById(R.id.form);
        layoutControl = (RelativeLayout)findViewById(R.id.control);
        progressBar = (ProgressBar)findViewById(R.id.progress);
        
        audioStream = new MediaPlayer();
        
        // Initiate connection with client
        findViewById(R.id.button_connect).setOnClickListener(new OnClickListener() {
        	@Override
			public void onClick(View v) {
        		Editor editor = settings.edit();
        		editor.putString("last_server_ip", editTextIP.getText().toString());
        		editor.commit();        		
        		layoutForm.setVisibility(View.GONE);
        		progressBar.setVisibility(View.VISIBLE);
        		getCurrentConfiguration();
			}
		});
        
        // Interrupt connection with the client
        findViewById(R.id.button_stop).setOnClickListener(new OnClickListener() {
        	@Override
			public void onClick(View v) {
        		stopStreaming();
        		layoutControl.setVisibility(View.GONE);
        		layoutForm.setVisibility(View.VISIBLE);
        		progressBar.setVisibility(View.GONE);
			}
		});        
        
        
        // Show configuration panel
        findViewById(R.id.button_config).setOnClickListener(new OnClickListener() {
        	@Override
			public void onClick(View v) {
        		findViewById(R.id.settings).setVisibility(View.VISIBLE);
			}
		});   
        
        findViewById(R.id.reconnect).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				findViewById(R.id.settings).setVisibility(View.GONE);
				updateSettings();
			}
		});
        
        // Resolution
        Spinner spinner = (Spinner) findViewById(R.id.spinner1);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
        		R.array.videoResolutionArray, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        
        // Framerate
        spinner = (Spinner) findViewById(R.id.spinner2);
        adapter = ArrayAdapter.createFromResource(this,
        		R.array.videoFramerateArray, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);
        
        // Bitrate
        spinner = (Spinner) findViewById(R.id.spinner3);
        adapter = ArrayAdapter.createFromResource(this,
        		R.array.videoBitrateArray, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);
        
        // Video Encoder
        spinner = (Spinner) findViewById(R.id.spinner4);
        adapter = ArrayAdapter.createFromResource(this,
        		R.array.videoEncoderArray, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);  
        spinner.setOnItemSelectedListener(this);
        
        // Audio Encoder
        spinner = (Spinner) findViewById(R.id.spinner5);
        adapter = ArrayAdapter.createFromResource(this,
        		R.array.audioEncoderArray, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);          
        spinner.setOnItemSelectedListener(this);
        
        settings = PreferenceManager.getDefaultSharedPreferences(this);
        editTextIP.setText(settings.getString("last_server_ip", "192.168.0.107"));
        
    }
    
	/** Fetch the current streaming configuration of the remote phone **/
	private void getCurrentConfiguration() {
		new AsyncTask<Void,Void,String>() {
			@Override
			protected String doInBackground(Void... params) {
				HttpParams httpParameters = new BasicHttpParams();
				HttpConnectionParams.setConnectionTimeout(httpParameters, 3000);
				HttpConnectionParams.setSoTimeout(httpParameters, 3000);
		        HttpClient client = new DefaultHttpClient(httpParameters);
		        HttpGet request = new HttpGet("http://"+editTextIP.getText().toString()+":8080/config.json?get");
		        ResponseHandler<String> responseHandler = new BasicResponseHandler();
		        String response="";
				try {
					response = client.execute(request, responseHandler);
				} catch (ConnectTimeoutException e) {
					Log.i(TAG,"Connection timeout ! ");
					onCompletion(null);
				} catch (Exception e) {
					Log.e(TAG,"Could not fetch current configuration on remote device !");
					e.printStackTrace();
				}
				return response;
			}
			@Override
			protected void onPostExecute(String response) {
		        try {
					JSONObject object = (JSONObject) new JSONTokener(response).nextValue();
					((CheckBox)findViewById(R.id.checkbox1)).setChecked(object.getBoolean("streamVideo"));
					((CheckBox)findViewById(R.id.checkbox2)).setChecked(object.getBoolean("streamAudio"));
					for (int spinner : new int[]{R.id.spinner1,R.id.spinner2,R.id.spinner3,R.id.spinner4,R.id.spinner5}) {
						Spinner view = (Spinner) findViewById(spinner);
						SpinnerAdapter adapter = view.getAdapter();
						for (int i=0;i<adapter.getCount();i++) {
							Iterator<String> keys = object.keys();
							while (keys.hasNext()) {
								String key = keys.next();
								if (adapter.getItem(i).equals(object.get(key))) {
									view.setSelection(i);
								}
										
							}
						}
					}
					generateURI();
					connectToServer();
				} catch (Exception e) {
					stopStreaming();
					e.printStackTrace();
				}
			}
		}.execute();
	}
	
	private void updateSettings() {
		final String oldVideoParameters = videoParameters, oldAudioParameters = audioParameters;
		generateURI();
		if (oldVideoParameters==videoParameters && oldAudioParameters==audioParameters) return;
		stopStreaming();
		progressBar.setVisibility(View.VISIBLE);
		new AsyncTask<Void,Void,Void>() {
			@Override
			protected Void doInBackground(Void... params) {
				HttpClient client = new DefaultHttpClient();
		        //HttpGet request = new HttpGet("http://"+editTextIP.getText().toString()+":8080?set&"+uriParameters);
		        try {
					Thread.sleep(2000);
				} catch (InterruptedException ignore) {}
				return null;
			}
			@Override
			protected void onPostExecute(Void weird) {
				Log.d(TAG,"Reconnecting to server...");
				connectToServer();
			}
		}.execute();
	}
	
	/** Generates the URL that will be used to configure the client **/
	private void generateURI() {
		int[] spinners = new int[]{R.id.spinner1,R.id.spinner2,R.id.spinner3,R.id.spinner4,R.id.spinner5};
		
		videoParameters = "";
		audioParameters = "";
		
		// Video streaming enabled ?
		if (((CheckBox)findViewById(R.id.checkbox1)).isChecked()) {
			
			int fps = 0, br = 0, resX = 0, resY = 0;
			Pattern p; Matcher m;
			
			// User has changed the resolution
			try {
				p = Pattern.compile("(\\d+)x(\\d+)");
				m = p.matcher(((String)((Spinner)findViewById(spinners[0])).getSelectedItem())); m.find();
				resX = Integer.parseInt(m.group(1));
				resY = Integer.parseInt(m.group(2));
			} catch (Exception ignore) {}

			// User has changed the framerate
			try {
				p = Pattern.compile("(\\d+)[^\\d]+");
				m = p.matcher(((String)((Spinner)findViewById(spinners[1])).getSelectedItem())); m.find();
				fps = Integer.parseInt(m.group(1));
			} catch (Exception ignore) {}

			// User has changed the bitrate
			try {
				p = Pattern.compile("(\\d+)[^\\d]+");
				m = p.matcher(((String)((Spinner)findViewById(spinners[2])).getSelectedItem())); m.find();
				br = Integer.parseInt(m.group(1));
			} catch (Exception ignore) {}

			videoParameters += ((String)((Spinner)findViewById(spinners[3])).getSelectedItem()).equals("H.264")?"h264":"h263";
			videoParameters += "="+br+"-"+fps+"-"+resX+"-"+resY;
		} else {
			videoParameters = "novideo";
		}
		
		// Audio streaming enabled ?
		if (((CheckBox)findViewById(R.id.checkbox2)).isChecked()) {
			audioParameters += ((String)((Spinner)findViewById(spinners[4])).getSelectedItem()).equals("AMR-NB")?"amr":"aac";
		}
		
		Log.d(TAG,"Cient configuration: video="+videoParameters+" audio="+audioParameters);
		
	}
	
	/** Connect to the RTSP server of the remote phone **/
	private void connectToServer() {
		
		// Start video streaming
		if (videoParameters.length()>0) {
			videoView = new MyVideoView(this);
			videoView.setLayoutParams(new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.FILL_PARENT,
					LinearLayout.LayoutParams.FILL_PARENT));
			layoutContainer.addView(videoView);
			videoView.setOnPreparedListener(this);
			videoView.setOnCompletionListener(this);
			videoView.setVideoURI(Uri.parse("rtsp://"+editTextIP.getText().toString()+":8086/"+(videoParameters.length()>0?("?"+videoParameters):"")));
			videoView.requestFocus();
		}
		
		// Start audio streaming
		if (audioParameters.length()>0) {
			try {
				audioStream.reset();
				audioStream.setDataSource(this, Uri.parse("rtsp://"+editTextIP.getText().toString()+":8086/"+(audioParameters.length()>0?("?"+audioParameters):"")));
				audioStream.setAudioStreamType(AudioManager.STREAM_MUSIC);
				audioStream.setOnPreparedListener(new OnPreparedListener() {
					@Override
					public void onPrepared(MediaPlayer mp) {
						audioStream.start();	
					}
				});
				audioStream.prepareAsync();
			} catch (Exception e) {
				Log.e(TAG,e.getMessage());
				e.printStackTrace();
			} 
		}
		
		Log.e(TAG,"rtsp://"+editTextIP.getText().toString()+":8086"+(videoParameters.length()>0?("?"+videoParameters):""));
		
	}
	
	private void stopStreaming() {
		try {
			if (videoView != null && videoView.isPlaying()) {
				layoutContainer.removeView(videoView);
				videoView.stopPlayback();
				videoView = null;
			}
		} catch (Exception ignore) {}
		try {
			if (audioStream != null && audioStream.isPlaying()) {
				audioStream.stop();
				audioStream.reset();
			}
		} catch (Exception ignore) {}
	}
	
    @Override
    public void onStart() {
    	super.onStart();
    }
    
    @Override
    public void onStop() {
    	super.onStop();
    }

	@Override
	public void onCompletion(MediaPlayer mp) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				layoutControl.setVisibility(View.GONE);
				progressBar.setVisibility(View.GONE);
				layoutForm.setVisibility(View.VISIBLE);
				stopStreaming();
			}
		});
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				progressBar.setVisibility(View.GONE);
				layoutControl.setVisibility(View.VISIBLE);
				videoView.start();
			}
		});
	}
	
	static class MyVideoView extends VideoView {
		public MyVideoView(Context context) {
			super(context);
		}
		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			int width = getDefaultSize(0, widthMeasureSpec);
			int height = getDefaultSize(0, heightMeasureSpec);
			setMeasuredDimension(width, height);
		}
	}

	@Override
	public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {

	}

	@Override
	public void onNothingSelected(AdapterView<?> arg0) {

	}
	
}

