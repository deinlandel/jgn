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
 * Created: Jul 3, 2006
 */
package com.captiveimagination.jgn.test.threaded;

import java.io.*;
import java.net.*;

import com.captiveimagination.jgn.*;
import com.captiveimagination.jgn.event.*;
import com.captiveimagination.jgn.message.*;
import com.captiveimagination.jgn.queue.*;
import com.captiveimagination.jgn.test.basic.*;

/**
 * @author Matthew D. Hicks
 */
public class ThreadingTest {
	public static void main(String[] args) throws Exception {
		JGN.register(BasicMessage.class);
		
		MessageServer server = new TCPMessageServer(new InetSocketAddress(InetAddress.getLocalHost(), 1000));
		server.addMessageListener(new MessageAdapter() {
			private int count = 0;
			
			public void messageReceived(Message m) {
				if (m instanceof BasicMessage) {
					BasicMessage message = (BasicMessage)m;
					System.out.println("Received message: " + message.getValue() + ", " + count++);
				}
			}
		});
		JGN.createMessageServerThread(server).start();
		
		int total = 20;
		for (int i = 0; i < total; i++) {
			try {
				startServerTest((i + 1) * 1000, 1001 + i);
			} catch(BindException exc) {
				total++;
			}
		}
	}
	
	public static final void startServerTest(final int id, int port) throws UnknownHostException, IOException, InterruptedException {
		final MessageServer server = new TCPMessageServer(new InetSocketAddress(InetAddress.getLocalHost(), port));
		JGN.createMessageServerThread(server).start();
		
		Runnable r = new Runnable() {
			public void run() {
				try {
					MessageClient client = server.connectAndWait(new InetSocketAddress(InetAddress.getLocalHost(), 1000), 5000);
					
					if (client == null) throw new RuntimeException("Unable to establish connection!");
					//System.out.println("Connected: " + id);
					int count = 0;
					BasicMessage message = new BasicMessage();
					while (count < 1000) {
						message.setValue(count + id);
						try {
							client.sendMessage(message);
							count++;
						} catch(QueueFullException exc) {
							Thread.sleep(1);
						}
					}
				} catch(Exception exc) {
					exc.printStackTrace();
				}
			}
		};
		Thread t = new Thread(r);
		t.start();
	}
}
