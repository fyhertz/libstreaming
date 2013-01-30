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

package net.majorkernelpanic.spydroid.api;

import java.lang.reflect.Field;

import net.majorkernelpanic.spydroid.R;
import net.majorkernelpanic.spydroid.SpydroidApplication;
import net.majorkernelpanic.spydroid.ui.SpydroidActivity;
import net.majorkernelpanic.streaming.Session;
import net.majorkernelpanic.streaming.video.VideoQuality;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;
import android.preference.PreferenceManager;
import android.util.Log;

public class RequestHandler {

	public final static String TAG = "RequestHandler";
	
	private final static SoundPool mSoundPool = new SoundPool(4,AudioManager.STREAM_MUSIC,0);
	
	static {
		mSoundPool.setOnLoadCompleteListener(new OnLoadCompleteListener() {
			public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
				soundPool.play(sampleId, 0.99f, 0.99f, 1, 0, 1);
			}
		});
	}
	
	/**
	 * Executes a batch of requests and returns all the results
	 * @param request Contains a json containing one or more requests
	 * @return A JSON to send back
	 */
	static public String handle(String request) {
		StringBuilder response = new StringBuilder();
		JSONTokener tokener = new JSONTokener(request);
		
		try {
			Object token = tokener.nextValue();
			
			response.append("{");
			
			// More than one request to execute
			if (token instanceof JSONArray) {
				JSONArray array = (JSONArray) token;
				for (int i=0;i<array.length();i++) {
					JSONObject object = (JSONObject) array.get(i);
					response.append("\"" + object.getString("action") + "\":" );
					exec(object, response);
					if (i != array.length()-1) response.append(",");
				}
			// Only One request
			} else if (token instanceof JSONObject) {
				JSONObject object = (JSONObject) token;
				response.append("\"" + object.getString("action") + "\":" );
				exec(object, response);
			}
			
			response.append("}");
			
		} catch (Exception e) {
			// Pokemon, gotta catch'em all !
			Log.e(TAG,"Invalid request: " + request);
			return "INVALID REQUEST";
		}

		Log.d(TAG,"Request: " + request);
		Log.d(TAG,"Answer: " + response.toString());
		return response.toString();
	}
	
	/** 
	 * The implementation of all the possible requests are here
	 * -> "sounds": returns a list of available sounds on the phone
	 * -> "screen": returns the screen state (whether the app. is on the foreground or not)
	 * -> "play": plays a sound on the phone
	 * -> "set": update Spydroid's configuration
	 * -> "get" returns Spydroid's configuration (framerate, bitrate...)
	 * -> "state": returns a JSON containing information about the state of the application
	 * -> "clear":
	 * @throws JSONException
	 * @throws IllegalAccessException 
	 * @throws IllegalArgumentException 
	 **/
	static private void exec(JSONObject object, StringBuilder response) throws JSONException, IllegalArgumentException, IllegalAccessException {
		String action = object.getString("action");
		
		// Returns a list of available sounds on the phone
		if (action.equals("sounds")) {
			Field[] raws = R.raw.class.getFields();
			response.append("[");
			for(int i=0; i < raws.length-1; i++) {
				response.append("\""+raws[i].getName() + "\",");
			}
			response.append("\""+raws[raws.length-1].getName() + "\"]");
		}
		
		// Returns the screen state (whether the app. is on the foreground or not)
		else if (action.equals("screen")) {
			response.append(SpydroidActivity.activityPaused ? "\"1\"" : "\"0\"");
		}
		
		// Plays a sound on the phone
		else if (action.equals("play")) {
			Field[] raws = R.raw.class.getFields();
			for(int i=0; i < raws.length; i++) {
				if (raws[i].getName().equals(object.getString("name"))) {
					mSoundPool.load(SpydroidApplication.getContext(), raws[i].getInt(null), 0);
				}
			}
			response.append("[]");
		}
		
		// Returns Spydroid's configuration (framerate, bitrate...)
		else if (action.equals("get")) {
			final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(SpydroidApplication.getContext());
			
			response.append("{\"streamAudio\":" + settings.getBoolean("stream_audio", false) + ",");
			response.append("\"audioEncoder\":\"" + (SpydroidActivity.audioEncoder==Session.AUDIO_AMRNB?"AMR-NB":"AAC") + "\",");
			response.append("\"streamVideo\":" + settings.getBoolean("stream_video", true) + ",");
			response.append("\"videoEncoder\":\"" + (SpydroidActivity.videoEncoder==Session.VIDEO_H263?"H.263":"H.264") + "\",");
			response.append("\"videoResolution\":\"" + SpydroidActivity.videoQuality.resX + "x" + SpydroidActivity.videoQuality.resY + "\",");
			response.append("\"videoFramerate\":\"" + SpydroidActivity.videoQuality.framerate + " fps\",");
			response.append("\"videoBitrate\":\"" + SpydroidActivity.videoQuality.bitrate/1000 + " kbps\"}");
			
		}
		
		// Update Spydroid's configuration
		else if (action.equals("set")) {
			final JSONObject settings = object.getJSONObject("settings");
			final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SpydroidApplication.getContext());
			final Editor editor = prefs.edit();
			
			editor.putBoolean("stream_video", settings.getBoolean("stream_video"));
			SpydroidActivity.videoQuality = VideoQuality.parseQuality(settings.getString("video_quality"));
			editor.putInt("video_resX", SpydroidActivity.videoQuality.resX);
			editor.putInt("video_resY", SpydroidActivity.videoQuality.resY);
			editor.putString("video_framerate", String.valueOf(SpydroidActivity.videoQuality.framerate));
			editor.putString("video_bitrate", String.valueOf(SpydroidActivity.videoQuality.bitrate/1000));
			editor.putString("video_encoder", settings.getString("video_encoder").equals("H.263")?"2":"1");
			editor.putBoolean("stream_audio", settings.getBoolean("stream_audio"));
			editor.putString("audio_encoder", settings.getString("audio_encoder").equals("AMR-NB")?"3":"5");
			editor.commit();
			response.append("[]");
			
		}
		
		// Returns a JSON containing information about the state of the application
		else if (action.equals("state")) {
			
			response.append("{");
			
			if (SpydroidActivity.lastCaughtException!=null) {
				
				// Used to display the message on the user interface
				String lastError = SpydroidActivity.lastCaughtException.getMessage();
				
				// Useful to display additional information to the user depending on the error
				StackTraceElement[] stack = SpydroidActivity.lastCaughtException.getStackTrace();
				StringBuilder builder = new StringBuilder(SpydroidActivity.lastCaughtException.getClass().getName()+" : "+lastError+"||");
				for (int i=0;i<stack.length;i++) builder.append("at "+stack[i].getClassName()+"."+stack[i].getMethodName()+" ("+stack[i].getFileName()+":"+stack[i].getLineNumber()+")||");
				
				response.append("\"lastError\":\""+(lastError!=null?lastError:"unknown error")+"\",");
				response.append("\"lastStackTrace\":\""+builder.toString()+"\",");
				
			}
			
			response.append("\"cameraInUse\":\""+Session.isCameraInUse()+"\",");
			response.append("\"microphoneInUse\":\""+Session.isMicrophoneInUse()+"\",");
			response.append("\"activityPaused\":\""+(SpydroidActivity.activityPaused?"1":"0")+"\"");
			response.append("}");
			
		}
		
		else if (action.equals("clear")) {
			SpydroidActivity.lastCaughtException = null;
			response.append("[]");
		}
		
	}

}
