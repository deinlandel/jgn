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
 * Created: Oct 1, 2006
 */
package com.captiveimagination.jgn.test.so.basic;

import java.net.*;

import com.captiveimagination.jgn.*;
import com.captiveimagination.jgn.event.*;
import com.captiveimagination.jgn.so.*;

/**
 * @author Matthew D. Hicks
 *
 */
public class BasicSharedObjectServer {
	public static void main(String[] args) throws Exception {
		InetSocketAddress server1Address = new InetSocketAddress(InetAddress.getLocalHost(), 1000);
		// Create the server
		MessageServer server = new TCPMessageServer(server1Address);
		//server.addMessageListener(new DebugListener("Server"));
		// Create a single thread managing updates for the server and the SharedObjectManager
		JGN.createThread(server, SharedObjectManager.getInstance()).start();
		
		// Add a listener to see changes
		SharedObjectManager.getInstance().addListener(new SharedObjectListener() {
			public void changed(String name, Object object, String field, MessageClient client) {
				System.out.println("Changed: " + name + ", " + object + ", " + field + ", " + client);
			}

			public void created(String name, Object object, MessageClient client) {
				System.out.println("Created: " + name + ", " + object + ", " + client);
			}

			public void removed(String name, Object object, MessageClient client) {
				System.out.println("Removed: " + name + ", " + object + ", " + client);
			}
		});
		
		// Create our shared bean
		MySharedBean bean = SharedObjectManager.getInstance().createSharedBean("MyBean", MySharedBean.class);
		// Set a value that should be sent
		bean.setOne("This is set right after the bean was created");
		// Enable sharing on the server so it can comprehend events
		SharedObjectManager.getInstance().enable(server);
		// Register it in the server so changes get broadcast to all connections when changes are made
		SharedObjectManager.getInstance().addShare(bean, server);
	}
}
