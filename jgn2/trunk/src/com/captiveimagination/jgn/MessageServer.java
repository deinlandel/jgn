/**
 * Copyright (c) 2005-2006 JavaGameNetworking
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * * Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright
 *   notice, this list of conditions and the following disclaimer in the
 *   documentation and/or other materials provided with the distribution.
 *
 * * Neither the name of 'JavaGameNetworking' nor the names of its contributors 
 *   may be used to endorse or promote products derived from this software 
 *   without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * Created: Jun 5, 2006
 */
package com.captiveimagination.jgn;

import java.io.*;
import java.net.*;

/**
 * MessageServer is the abstract foundation from which all sending and receiving
 * of Messages occur.
 * @author Matthew D. Hicks
 */
public abstract class MessageServer {
	private InetSocketAddress address;

	public MessageServer(InetSocketAddress address) {
		this.address = address;
	}

	/**
	 * @return
	 * 		the InetSocketAddress representing the remote host
	 * 		machine
	 */
	public InetSocketAddress getSocketAddress() {
		return address;
	}

	/**
	 * Establishes a connection the remote host distinguished by
	 * <code>address</code>.
	 * 
	 * @param address
	 * @return
	 * 		MessageClient representing the connection to the remote
	 * 		server
	 */
	public abstract MessageClient connect(InetSocketAddress address);
	
	/**
	 * Sends a message to an established connection.
	 * 
	 * @param client
	 * @param message
	 */
	public abstract void sendMessage(MessageClient client, Message message);
	
	/**
	 * Disconnects from the referenced <code>client</code> and sends
	 * a notification to the remote host informing of the break in
	 * communication.
	 * 
	 * @param client
	 * @return
	 * 		true if the disconnect was graceful
	 */
	public abstract boolean disconnect(MessageClient client);
	
	/**
	 * Closes all open connections to remote clients
	 */
	public abstract void close();
	
	public abstract void updateTraffic() throws IOException;
	
	public void updateEvents() {
		// TODO implement
	}
}
