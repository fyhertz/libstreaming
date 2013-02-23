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

package net.majorkernelpanic.streaming;

import java.io.IOException;
import java.net.InetAddress;

/**
 * An interface that represents a Stream. 
 */
public interface Stream {

	public void start() throws IllegalStateException;
	public void prepare() throws IllegalStateException,IOException;
	public void stop();
	public void release();

	public void setTimeToLive(int ttl) throws IOException;
	public void setDestination(InetAddress dest, int dport);

	public int getLocalPort();
	public int getDestinationPort();
	public int getSSRC();

	public String generateSessionDescription() throws IllegalStateException, IOException;

	public boolean isStreaming();

}
