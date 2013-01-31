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

package net.majorkernelpanic.spydroid;

import static org.acra.ReportField.ANDROID_VERSION;
import static org.acra.ReportField.APP_VERSION_NAME;
import static org.acra.ReportField.BRAND;
import static org.acra.ReportField.DEVICE_FEATURES;
import static org.acra.ReportField.LOGCAT;
import static org.acra.ReportField.PHONE_MODEL;
import static org.acra.ReportField.PRODUCT;
import static org.acra.ReportField.SHARED_PREFERENCES;
import static org.acra.ReportField.STACK_TRACE;
import static org.acra.ReportField.USER_APP_START_DATE;
import static org.acra.ReportField.USER_CRASH_DATE;

import org.acra.annotation.ReportsCrashes;

import android.content.Context;

@ReportsCrashes(formKey = "dGhWbUlacEV6X0hlS2xqcmhyYzNrWlE6MQ", customReportContent = { APP_VERSION_NAME, PHONE_MODEL, BRAND, PRODUCT, ANDROID_VERSION, STACK_TRACE, USER_APP_START_DATE, USER_CRASH_DATE, LOGCAT, DEVICE_FEATURES, SHARED_PREFERENCES })
public class SpydroidApplication extends android.app.Application {
	
	public final static boolean DONATE_VERSION = false;
	
	private static Context context;
	
	@Override
	public void onCreate() {
		// The following line triggers the initialization of ACRA
		// Please do not uncomment this line unless you change the form id or I will receive your crash reports !
		//ACRA.init(this);
		SpydroidApplication.context = getApplicationContext();
		super.onCreate();
	}
	
	public static Context getContext() {
        return SpydroidApplication.context;
    }
	
}
