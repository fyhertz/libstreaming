package net.majorkernelpanic.spydroid;

/**
 * Licensed under the GNU LESSER GENERAL PUBLIC LICENSE, version 2.1, dated February 1999.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the latest version of the GNU Lesser General
 * Public License as published by the Free Software Foundation;
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program (LICENSE.txt); if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
//package org.jamwiki.utils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * This class provides a variety of basic utility methods that are not
 * dependent on any other classes within the org.jamwiki package structure.
 */
public class Utilities {
	private static Pattern VALID_IPV4_PATTERN = null;
	private static Pattern VALID_IPV6_PATTERN = null;
	private static final String ipv4Pattern = "(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])";
	private static final String ipv6Pattern = "([0-9a-f]{1,4}:){7}([0-9a-f]){1,4}";

	static {
		try {
			VALID_IPV4_PATTERN = Pattern.compile(ipv4Pattern, Pattern.CASE_INSENSITIVE);
			VALID_IPV6_PATTERN = Pattern.compile(ipv6Pattern, Pattern.CASE_INSENSITIVE);
		} catch (PatternSyntaxException e) {
			//logger.severe("Unable to compile pattern", e);
		}
	}

	/**
	 * Determine if the given string is a valid IPv4 or IPv6 address.  This method
	 * uses pattern matching to see if the given string could be a valid IP address.
	 *
	 * @param ipAddress A string that is to be examined to verify whether or not
	 *  it could be a valid IP address.
	 * @return <code>true</code> if the string is a value that is a valid IP address,
	 *  <code>false</code> otherwise.
	 */
	public static boolean isIpAddress(String ipAddress) {
		Matcher m1 = Utilities.VALID_IPV4_PATTERN.matcher(ipAddress);
		if (m1.matches()) {
			return true;
		}
		Matcher m2 = Utilities.VALID_IPV6_PATTERN.matcher(ipAddress);
		return m2.matches();
	}

	public static boolean isIpv4Address(String ipAddress) {
		Matcher m1 = Utilities.VALID_IPV4_PATTERN.matcher(ipAddress);
		return m1.matches();
	}

	public static boolean isIpv6Address(String ipAddress) {
		Matcher m1 = Utilities.VALID_IPV6_PATTERN.matcher(ipAddress);
		return m1.matches();
	}


	/**
	 * Returns the IP address of the first configured interface of the device
	 * @param removeIPv6 If true, IPv6 addresses are ignored
	 * @return the IP address of the first configured interface or null
	 */
	public static String getLocalIpAddress(boolean removeIPv6) {
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					if (inetAddress.isSiteLocalAddress() &&
							!inetAddress.isAnyLocalAddress() &&
							(!removeIPv6 || isIpv4Address(inetAddress.getHostAddress().toString())) ) {
						return inetAddress.getHostAddress().toString();
					}
				}
			}
		} catch (SocketException ignore) {}
		return null;
	}


}