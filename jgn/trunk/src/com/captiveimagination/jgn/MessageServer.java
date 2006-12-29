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
import java.lang.reflect.*;
import java.net.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;

import com.captiveimagination.jgn.convert.*;
import com.captiveimagination.jgn.event.*;
import com.captiveimagination.jgn.message.*;
import com.captiveimagination.jgn.queue.*;
import com.captiveimagination.jgn.ro.*;
import com.captiveimagination.jgn.ro.ping.*;
import com.captiveimagination.jgn.translation.*;

/**
 * MessageServer is the abstract foundation from which all sending and receiving
 * of Messages occur.
 *
 * It implements Updatable which defines a method update(). Normally this method will be
 * called periodically to enable serving all MessageClients and related tasks.
 *
 * 
 *
 * @author Matthew D. Hicks
 */
public abstract class MessageServer implements Updatable {
	public static long DEFAULT_TIMEOUT = 30 * 1000;
	public static ConnectionController DEFAULT_CONNECTION_CONTROLLER = new DefaultConnectionController();
	
	private static HashMap<Class,ArrayList<Class>> classHierarchyCache = new HashMap<Class,ArrayList<Class>>();
	private static HashMap<Class,ArrayList<Method>> listenerDynamicMethods = new HashMap<Class, ArrayList<Method>>();
	
	private long serverId;
	private SocketAddress address;
	private int maxQueueSize;
	private long connectionTimeout;
	private ConnectionQueue incomingConnections;		// Waiting for ConnectionListener handling
	private ConnectionQueue negotiatedConnections;		// Waiting for ConnectionListener handling
	private ConnectionQueue disconnectedConnections;	// Waiting for ConnectionListener handling
	private ArrayList<ConnectionListener> connectionListeners;
	private ArrayList<MessageListener> messageListeners;
	private ArrayList<ConnectionFilter> filters;
	private ArrayList<DataTranslator> translators;
	private AbstractQueue<MessageClient> clients;
	protected boolean keepAlive;
	protected boolean alive;
	
	private ConnectionController controller;
	
	public MessageServer(SocketAddress address, int maxQueueSize) throws IOException {
		serverId = JGN.generateUniqueId();
		
		this.address = address;
		this.maxQueueSize = maxQueueSize;
		
		keepAlive = true;
		alive = true;
		
		connectionTimeout = DEFAULT_TIMEOUT;
		incomingConnections = new ConnectionQueue();
		negotiatedConnections = new ConnectionQueue();
		disconnectedConnections = new ConnectionQueue();
		connectionListeners = new ArrayList<ConnectionListener>();
		messageListeners = new ArrayList<MessageListener>();
		filters = new ArrayList<ConnectionFilter>();
		translators = new ArrayList<DataTranslator>();
		clients = new ConcurrentLinkedQueue<MessageClient>();
		
		controller = DEFAULT_CONNECTION_CONTROLLER;
		
		addConnectionListener(InternalListener.getInstance());
		addMessageListener(InternalListener.getInstance());
		
		// Default registered RemoteObjects
		RemoteObjectManager.registerRemoteObject(Ping.class, new ServerPing(), this);
	}
	
	public void setMessageServerId(long serverId) {
		this.serverId = serverId;
	}
	
	public long getMessageServerId() {
		return serverId;
	}
	
	public int getMaxQueueSize() {
		return maxQueueSize;
	}
	
	public void setConnectionTimeout(long connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
	}
	
	protected ConnectionQueue getIncomingConnectionQueue() {
		return incomingConnections;
	}
	
	protected ConnectionQueue getNegotiatedConnectionQueue() {
		return negotiatedConnections;
	}
	
	protected ConnectionQueue getDisconnectedConnectionQueue() {
		return disconnectedConnections;
	}
	
	protected AbstractQueue<MessageClient> getMessageClients() {
		return clients;
	}
	
	protected ConnectionController getConnectionController() {
		return controller;
	}

	/**
	 * Sends <code>message</code> to all connected clients.
	 * 
	 * @param message
	 * @return
	 * 		total number of clients messages sent to
	 */
	public int broadcast(Message message) {
		Iterator<MessageClient> iterator = getMessageClients().iterator();
		int sent = 0;
		while (iterator.hasNext()) {
			try {
				iterator.next().sendMessage(message);
				sent++;
			} catch(ConnectionException exc) {
				// Ignore when broadcasting
			}
		}
		return sent;
	}
	
	/**
	 * Replace the current implementation of ConnectionController with another
	 * alternative.
	 * 
	 * @param controller
	 */
	public void setConnectionController(ConnectionController controller) {
		this.controller = controller;
	}
	
	/**
	 * @return
	 * 		the SocketAddress representing the remote host
	 * 		machine
	 */
	public SocketAddress getSocketAddress() {
		return address;
	}

	public MessageClient getMessageClient(SocketAddress address) {
		for (MessageClient client : getMessageClients()) {
			if (client.getAddress().equals(address)) return client;
		}
		return null;
	}
	
	/**
	 * Establishes a connection to the remote host distinguished by
	 * <code>address</code>. This method is non-blocking.
	 * 
	 * @param address
	 * @return
	 * 		A MessageClient will always be returned that references the connection
	 * 		either in-progress or that has already been established. Only one connection
	 * 		can exist from one MessageServer to another, so if a second request is made
	 * 		the original MessageClient will be returned and a new one will not be created.
	 * @throws IOException
	 */
	public abstract MessageClient connect(SocketAddress address) throws IOException;
	
	/**
	 * Exactly the same as connect, but blocks for <code>timeout</code> in milliseconds
	 * for the connection to be established and returned. If the connection is already
	 * established it will be immediately returned. If the connection is not established
	 * in the time allotted via timeout the client will be disconnected and will no longer
	 * attempt the connect.
	 * 
	 * <b>WARNING</b>: Do not execute this method in the same thread as is doing the
	 * update calls or you will end up with a timeout every time.
	 * 
	 * @param address
	 * @param timeout
	 * @return
	 * 		MessageClient for the connection that is established.
	 * @throws IOException
	 * @throws InterruptedException 
	 */
	public MessageClient connectAndWait(SocketAddress address, int timeout) throws IOException, InterruptedException {
		MessageClient client = connect(address);
		long time = System.currentTimeMillis();
		while (System.currentTimeMillis() < time + timeout) {
			if (client.isConnected()) {
				return client;
			}
			Thread.sleep(10);
		}
		// Last attempt before failing
		if (client.isConnected()) return client;
		client.disconnect();
		return null;
	}
		
	protected abstract void disconnectInternal(MessageClient client, boolean graceful) throws IOException;
	
	/**
	 * Closes all open connections to remote clients
	 */
	public void close() throws IOException {
		synchronized (getMessageClients()) {
			for (MessageClient client : getMessageClients()) {
				if (client.isConnected()) client.disconnect();
			}
		}
		keepAlive = false;
	}
	
	public void closeAndWait(long timeout) throws IOException, InterruptedException {
		close();
		long time = System.currentTimeMillis();
		while (System.currentTimeMillis() <= time + timeout) {
			if (!isAlive()) return;
			synchronized (getMessageClients()) {
				if ((getMessageClients().size() > 0) && (getMessageClients().peek().getStatus() == MessageClient.Status.CONNECTED)) {
					getMessageClients().peek().disconnect();
				}
			}
			Thread.sleep(1);
		}
		throw new IOException("MessageServer did not shutdown within the allotted time (" + getMessageClients().size() + ").");
	}
	
	/**
	 * Processing incoming/outgoing traffic for this MessageServer
	 * implementation. Should handle incoming connections, incoming
	 * messages, and outgoing messages.
	 * 
	 * @throws IOException
	 */
	public abstract void updateTraffic() throws IOException;
	
	/**
	 * Handles processing events for incoming/outgoing messages sending to
	 * the registered listeners for both the MessageServer and MessageClients
	 * associated as well as the incoming established connection events.
	 */
	public synchronized void updateEvents() {
		// Process incoming connections first
		while (!incomingConnections.isEmpty()) {
			MessageClient client = incomingConnections.poll();
			synchronized (connectionListeners) {
				for (ConnectionListener listener : connectionListeners) {
					listener.connected(client);
				}
			}
		}
		
		// Process incoming Messages to the listeners
		for (MessageClient client : clients) {
			MessageQueue incomingMessages = client.getIncomingMessageQueue();
			while (!incomingMessages.isEmpty()) {
				Message message = incomingMessages.poll();
//				System.out.println("Processing message: " + message);
				synchronized (client.getMessageListeners()) {
					for (MessageListener listener : client.getMessageListeners()) {
						//listener.messageReceived(message);
						sendToListener(message, listener, MessageListener.RECEIVED);
					}
				}
				synchronized (messageListeners) {
					for (MessageListener listener : messageListeners) {
						//listener.messageReceived(message);
						sendToListener(message, listener, MessageListener.RECEIVED);
					}
				}
			}
		}

		// Process outgoing Messages to the listeners
		for (MessageClient client : clients) {
			MessageQueue outgoingMessages = client.getOutgoingMessageQueue();
			while (!outgoingMessages.isEmpty()) {
				Message message = outgoingMessages.poll();
				synchronized (client.getMessageListeners()) {
					for (MessageListener listener : client.getMessageListeners()) {
						//listener.messageReceived(message);'
						sendToListener(message, listener, MessageListener.SENT);
					}
				}
				synchronized (messageListeners) {
					for (MessageListener listener : messageListeners) {
						//listener.messageSent(message);
						sendToListener(message, listener, MessageListener.SENT);
					}
				}
			}
		}
		
		// Process certified Messages to the listeners
		for (MessageClient client : clients) {
			MessageQueue certifiedMessages = client.getCertifiedMessageQueue();
			while (!certifiedMessages.isEmpty()) {
				Message message = certifiedMessages.poll();
				synchronized (client.getMessageListeners()) {
					for (MessageListener listener : client.getMessageListeners()) {
						sendToListener(message, listener, MessageListener.CERTIFIED);
					}
				}
				synchronized (messageListeners) {
					for (MessageListener listener : messageListeners) {
						sendToListener(message, listener, MessageListener.CERTIFIED);
					}
				}
			}
		}
		
		// Process the list of certified messages that have failed
		for (MessageClient client : clients) {
			MessageQueue failedMessages = client.getFailedMessageQueue();
			while (!failedMessages.isEmpty()) {
				Message message = failedMessages.poll();
				synchronized (client.getMessageListeners()) {
					for (MessageListener listener : client.getMessageListeners()) {
						sendToListener(message, listener, MessageListener.FAILED);
					}
				}
				synchronized (messageListeners) {
					for (MessageListener listener : messageListeners) {
						sendToListener(message, listener, MessageListener.FAILED);
					}
				}
			}
		}
		
		// Process the list of certified messages that still are waiting for certification
		for (MessageClient client : clients) {
			List<Message> messages = client.getCertifiableMessageQueue().clonedList();
			for (Message m : messages) {
				if ((m.getTimestamp() != -1) && (m.getTimestamp() + m.getTimeout() < System.currentTimeMillis())) {
					if ((m.getTries() == m.getMaxTries()) || (!m.getMessageClient().isConnected())) {
						// Message failed
						client.getFailedMessageQueue().add(m);
						client.getCertifiableMessageQueue().remove(m);
					} else {
						System.out.println("Lets try to resend: " + m.getClass());
						m.setTries(m.getTries() + 1);
						m.setTimestamp(-1);
						client.getOutgoingQueue().add(m);	// We don't want to clone or reset the unique id
					}
				}
			}
		}

		// Process incoming negotiated connections
		while (!negotiatedConnections.isEmpty()) {
			MessageClient client = negotiatedConnections.poll();
			synchronized (connectionListeners) {
				for (ConnectionListener listener : connectionListeners) {
					listener.negotiationComplete(client);
				}
			}
		}
		
		// Process disconnected connections
		while (!disconnectedConnections.isEmpty()) {
			MessageClient client = disconnectedConnections.poll();
			synchronized (connectionListeners) {
				for (ConnectionListener listener : connectionListeners) {
					if (client.getKickReason() != null) {
						listener.kicked(client, client.getKickReason());
					}
					listener.disconnected(client);
				}
			}
		}
	}
	
	/**
	 * Processes all MessageClients associated with this MessageServer and
	 * checks for connections that have been closed or have timed out and
	 * removes them.
	 */
	public synchronized void updateConnections() {
		// Cycle through connections and see if any have timed out
		for (MessageClient client : clients) {
			if ((client.isConnected()) && (client.lastReceived() + connectionTimeout < System.currentTimeMillis())) {
				client.disconnect();
				client.received();
			}
		}
		
		// Cycle through connections in a disconnecting state for too long
		for (MessageClient client : clients) {
			if ((client.getStatus() == MessageClient.Status.DISCONNECTING) && (client.lastReceived() + (connectionTimeout / 3) < System.currentTimeMillis())) {
				try {
					client.getMessageServer().disconnectInternal(client, false);
					clients.remove(client);
				} catch(IOException exc) {
					exc.printStackTrace();
					// TODO handle more gracefully
				}
			}
		}
		
		// Send Noops to connections that are still alive
		NoopMessage message = null;
		for (MessageClient client : clients) {
			if ((client.isConnected()) && (client.lastSent() + (connectionTimeout / 3) < System.currentTimeMillis())) {
				if (message == null) {
					message = new NoopMessage();
				}
				try {
					client.sendMessage(message);
					client.sent();
				} catch(QueueFullException exc) {
				}
			}
		}
		
		// If attempting to shutdown make sure all MessageClients are closed before alive = false
		if ((!keepAlive) && (alive)) {
			synchronized (getMessageClients()) {
				for (MessageClient client : getMessageClients()) {
					if (client.getStatus() != MessageClient.Status.DISCONNECTED) {
						break;
					}
				}
				alive = false;
			}
		}
	}
	
	/**
	 * Convenience method to call updateTraffic() and updateEvents().
	 * This is only necessary if you aren't explicitly calling these
	 * methods already.
	 * 
	 * @throws IOException
	 */
	public void update() throws IOException {
		updateTraffic();
		updateEvents();
		updateConnections();
	}
	
	/**
	 * Adds a ConnectionListener to this MessageServer
	 * 
	 * @param listener
	 */
	public void addConnectionListener(ConnectionListener listener) {
		synchronized (connectionListeners) {
			connectionListeners.add(listener);
		}
	}
	
	/**
	 * Removes a ConnectionListener from this MessageServer
	 * 
	 * @param listener
	 * @return
	 * 		true if the listener was contained in the list
	 */
	public boolean removeConnectionListener(ConnectionListener listener) {
		synchronized (connectionListeners) {
			return connectionListeners.remove(listener);
		}
	}
	
	/**
	 * Adds a MessageListener to this MessageServer
	 * 
	 * @param listener
	 */
	public void addMessageListener(MessageListener listener) {
		synchronized (messageListeners) {
			messageListeners.add(listener);
		}
	}
	
	/**
	 * Removes a MessageListener from this MessageServer
	 * 
	 * @param listener
	 * @return
	 * 		true if the listener was contained in the list
	 */
	public boolean removeMessageListener(MessageListener listener) {
		synchronized (messageListeners) {
			return messageListeners.remove(listener);
		}
	}

	/**
	 * Adds a ConnectionFilter to this MessageServer
	 * 
	 * @param filter
	 */
	public void addConnectionFilter(ConnectionFilter filter) {
		synchronized (filters) {
			filters.add(filter);
		}
	}
	
	/**
	 * Removes a ConnectionFilter from this MessageServer
	 * 
	 * @param filter
	 * @return
	 * 	true if the filter was contained in the list
	 */
	public boolean removeConnectionFilter(ConnectionFilter filter) {
		synchronized (filters) {
			return filters.remove(filter);
		}
	}
	
	protected String shouldFilterConnection(MessageClient client) {
		for (ConnectionFilter filter : filters) {
			String reason = filter.filter(client);
			if (reason != null) {
				return reason;
			}
		}
		return null;
	}
	
	/**
	 * Adds a DataTranslator to this MessageServer
	 * 
	 * @param translator
	 */
	public void addDataTranslator(DataTranslator translator) {
		translators.add(translator);
	}
	
	/**
	 * Removes a DataTranslator from this MessageServer
	 * 
	 * @param translator
	 * @return
	 * 	true if the translator was contained in the list
	 */
	public boolean removeDataTranslator(DataTranslator translator) {
		return translators.remove(translator);
	}
	
	private void translateMessage(Message message) throws MessageHandlingException {
		if (translators.size() == 0) {
			return;
		}
		if (message instanceof LocalRegistrationMessage) {
			return;
		}
		try {
			TranslatedMessage tm = TranslationManager.createTranslatedMessage(message);
			byte[] b = tm.getTranslated();
			for (DataTranslator translator : translators) {
				b = translator.outbound(b);
			}
			tm.setTranslated(b);
			tm.setMessageClient(message.getMessageClient());
			message.setTranslatedMessage(tm);
		} catch(Exception exc) {
			throw new MessageException("Exception during translation", exc);
		}
	}
	
	protected Message revertTranslated(TranslatedMessage tm) throws MessageHandlingException {
		try {
			byte[] b = tm.getTranslated();
			for (DataTranslator translator : translators) {
				b = translator.inbound(b);
			}
			Message message = TranslationManager.createMessage(b);
			return message;
		} catch(Exception exc) {
			throw new MessageException("Exception during translation", exc);
		}
	}
	
	protected void convertMessage(Message message, ByteBuffer buffer) throws MessageHandlingException {
		translateMessage(message);
		
		Message m = message;
		if (message.getTranslatedMessage() != null) {
			m = message.getTranslatedMessage();
		}
		
		ConversionHandler handler = JGN.getConverter(m.getClass());
		handler.sendMessage(m, buffer);
	}
	
	private static final void sendToListener(Message message, MessageListener listener, int type) {
		if (type == MessageListener.RECEIVED) {
			if (listener instanceof DynamicMessageListener) {
				callMethod(listener, "messageReceived", message, false);
			} else {
				listener.messageReceived(message);
			}
		} else if (type == MessageListener.SENT) {
			if (listener instanceof DynamicMessageListener) {
				callMethod(listener, "messageSent", message, false);
			} else {
				listener.messageSent(message);
			}
		} else if (type == MessageListener.CERTIFIED) {
			if (listener instanceof DynamicMessageListener) {
				callMethod(listener, "messageCertified", message, false);
			} else {
				listener.messageCertified(message);
			}
		} else if (type == MessageListener.FAILED) {
			if (listener instanceof DynamicMessageListener) {
				callMethod(listener, "messageFailed", message, false);
			} else {
				listener.messageFailed(message);
			}
		} else {
			throw new RuntimeException("Unknown listener type specified: " + type);
		}
	}
	
	private static void callMethod(MessageListener listener, String methodName, Message message, boolean callAll) {
        try {
        	ArrayList<Method> methods = listenerDynamicMethods.get(listener.getClass());
        	if (methods == null) {	// Not already cached
        		methods = new ArrayList<Method>();
        		listenerDynamicMethods.put(listener.getClass(), methods);
	            Method[] allMethods = listener.getClass().getMethods();
	            ArrayList<Method> m = new ArrayList<Method>();
	            for (int i = 0; i < allMethods.length; i++) {
	                if ((allMethods[i].getName().equals(methodName)) && (allMethods[i].getParameterTypes().length == 1)) {
	                    m.add(allMethods[i]);
	                }
	            }
	            
	            // Check to see if an interface is found first
	            ArrayList classes = getClassList(message.getClass());
	            for (int j = 0; j < classes.size(); j++) {
	            	for (int i = 0; i < m.size(); i++) {
	                    if (m.get(i).getParameterTypes()[0] == classes.get(j)) {
	                        m.get(i).setAccessible(true);
	                        m.get(i).invoke(listener, new Object[] {message});
	                        methods.add(m.get(i));
	                        if (!callAll) return;
	                    }
	                }
	            }
        	} else {		// Utilize cache of methods for listener
        		for (Method m : methods) {
        			m.invoke(listener, new Object[] {message});
        		}
        	}
        } catch(Throwable t) {
            System.err.println("Object: " + listener + ", MethodName: " + methodName + ", Var: " + message + ", callAll: " + callAll);
            t.printStackTrace();
        }
    }
	
	private static ArrayList getClassList(Class c) {
    	if (classHierarchyCache.containsKey(c)) {
    		return (ArrayList)classHierarchyCache.get(c);
    	}
    	
    	ArrayList<Class> list = new ArrayList<Class>();
    	Class[] interfaces;
    	do {
    		list.add(c);
    		interfaces = c.getInterfaces();
    		for (int i = 0; i < interfaces.length; i++) {
    			list.add(interfaces[i]);
    		}
    	} while ((c = c.getSuperclass()) != null);
    	
    	classHierarchyCache.put(c, list);
    	
    	return list;
    }

	public boolean isAlive() {
		return alive;
	}
}
