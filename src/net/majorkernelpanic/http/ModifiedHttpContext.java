package net.majorkernelpanic.http;

import java.net.Socket;

import org.apache.http.protocol.BasicHttpContext;

/**
 * Little modification of BasicHttpContext to add access to the Socket
 */
public class ModifiedHttpContext extends BasicHttpContext {

	private Socket socket;
	
	public ModifiedHttpContext(Socket socket) {
		super(null);
		this.socket = socket;
	}

	public Socket getSocket() {
		return socket;
	}
	
}
