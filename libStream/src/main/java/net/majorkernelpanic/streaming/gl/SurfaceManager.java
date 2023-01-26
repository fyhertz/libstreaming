/*
 * Based on the work of fadden
 * 
 * Copyright 2012 Google Inc. All Rights Reserved.
 * Copyright (C) 2011-2015 GUIGUI Simon, fyhertz@gmail.com
 *
 * This file is part of libstreaming (https://github.com/fyhertz/libstreaming)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.majorkernelpanic.streaming.gl;

import android.annotation.SuppressLint;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.view.Surface;

@SuppressLint("NewApi")
public class SurfaceManager {

	public final static String TAG = "TextureManager";

	private static final int EGL_RECORDABLE_ANDROID = 0x3142;

	private EGLContext mEGLContext = null;
	private EGLContext mEGLSharedContext = null;
	private EGLSurface mEGLSurface = null;
	private EGLDisplay mEGLDisplay = null;

	private Surface mSurface;

	/**
	 * Creates an EGL context and an EGL surface.
	 */
	public SurfaceManager(Surface surface, SurfaceManager manager) {
		mSurface = surface;
		mEGLSharedContext = manager.mEGLContext;
		eglSetup();
	}

	/**
	 * Creates an EGL context and an EGL surface.
	 */
	public SurfaceManager(Surface surface) {
		mSurface = surface;
		eglSetup();
	}

	public void makeCurrent() {
		if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext))
			throw new RuntimeException("eglMakeCurrent failed");
	}

	public void swapBuffer() {
		EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface);
	}

	/**
	 * Sends the presentation time stamp to EGL.  Time is expressed in nanoseconds.
	 */
	public void setPresentationTime(long nsecs) {
		EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurface, nsecs);
		checkEglError("eglPresentationTimeANDROID");
	}

	/**
	 * Prepares EGL.  We want a GLES 2.0 context and a surface that supports recording.
	 */
	private void eglSetup() {
		mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
		if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
			throw new RuntimeException("unable to get EGL14 display");
		}
		int[] version = new int[2];
		if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
			throw new RuntimeException("unable to initialize EGL14");
		}

		// Configure EGL for recording and OpenGL ES 2.0.
		int[] attribList;
		if (mEGLSharedContext == null) {
			attribList = new int[] {
					EGL14.EGL_RED_SIZE, 8,
					EGL14.EGL_GREEN_SIZE, 8,
					EGL14.EGL_BLUE_SIZE, 8,
					EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
					EGL14.EGL_NONE
			};
		} else {
			attribList = new int[] {
					EGL14.EGL_RED_SIZE, 8,
					EGL14.EGL_GREEN_SIZE, 8,
					EGL14.EGL_BLUE_SIZE, 8,
					EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
					EGL_RECORDABLE_ANDROID, 1,
					EGL14.EGL_NONE
			};	
		}
		EGLConfig[] configs = new EGLConfig[1];
		int[] numConfigs = new int[1];
		EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.length,
				numConfigs, 0);
		checkEglError("eglCreateContext RGB888+recordable ES2");

		// Configure context for OpenGL ES 2.0.
		int[] attrib_list = {
				EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
				EGL14.EGL_NONE
		};

		if (mEGLSharedContext == null) {
			mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0], EGL14.EGL_NO_CONTEXT, attrib_list, 0);
		} else {
			mEGLContext = EGL14.eglCreateContext(mEGLDisplay, configs[0], mEGLSharedContext, attrib_list, 0);
		}
		checkEglError("eglCreateContext");

		// Create a window surface, and attach it to the Surface we received.
		int[] surfaceAttribs = {
				EGL14.EGL_NONE
		};
		mEGLSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, configs[0], mSurface,
				surfaceAttribs, 0);
		checkEglError("eglCreateWindowSurface");

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
		
	}

	/**
	 * Discards all resources held by this class, notably the EGL context.  Also releases the
	 * Surface that was passed to our constructor.
	 */
	public void release() {
		if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
			EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
					EGL14.EGL_NO_CONTEXT);
			EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
			EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
			EGL14.eglReleaseThread();
			EGL14.eglTerminate(mEGLDisplay);
		}
		mEGLDisplay = EGL14.EGL_NO_DISPLAY;
		mEGLContext = EGL14.EGL_NO_CONTEXT;
		mEGLSurface = EGL14.EGL_NO_SURFACE;
		mSurface.release();
	}

	/**
	 * Checks for EGL errors. Throws an exception if one is found.
	 */
	private void checkEglError(String msg) {
		int error;
		if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
			throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
		}
	}




}
