/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.rodneybeede.software.imaptombox;

import java.util.Properties;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Store;

import com.sun.mail.imap.IMAPFolder;


/**
 *
 * @author rbeede
 */
public class ImapClient {
	private final String server;
	private final String username;
	private final String password;
	private final Properties connectionProperties;
	
	/**
	 * 
	 * May be null.  Use {@link #getConnectedStore()}
	 */
	private Store _cachedStore;
	

	public ImapClient(final String server, final String username, final String password, final Properties connectionProperties) {
		this.server = server;
		this.username = username;
		this.password = password;
		this.connectionProperties = connectionProperties;
	}
	
	/**
	 * @return List of folders excluding the root folder
	 * @throws ImapException
	 */
	public IMAPFolder[] getFolders() throws ImapException {
		final Store connectedStore;
		try {
			connectedStore = this.getConnectedStore();
		} catch(final MessagingException excep) {
			throw new ImapException(excep);
		}
		
		final Folder rootFolder;
		try {
			rootFolder = connectedStore.getDefaultFolder();
		} catch(final javax.mail.MessagingException excep) {
			throw new ImapException(excep);
		}
		
		
		try {
			return (IMAPFolder[]) rootFolder.list("*");  // versus % which isn't recursive
		} catch (final javax.mail.MessagingException excep) {
			throw new ImapException(excep);
		}
	}
	
	
	public int getFolderMessageCount(final Folder folder) throws MessagingException {
		return folder.getMessageCount();
	}
	
	
	/**
	 * Assumes message has live connection back to folder which has live connection back to connected store
	 * 
	 * @param message
	 * @return
	 * @throws MessagingException
	 */
	public long getMessageUID(final Message message) throws ImapException {
		final IMAPFolder folder = (IMAPFolder) message.getFolder();
		
		if(null == folder) {
			throw new ImapException("Folder for message was null, disconnect?", new NullPointerException());
		}
		
		if(!folder.isOpen()) {
			try {
				folder.open(Folder.READ_ONLY);
			} catch (final MessagingException e) {
				throw new ImapException(e);
			}
		}
		
		try {
			return folder.getUID(message);
		} catch (final MessagingException e) {
			throw new ImapException(e);
		}
	}
	
	
	private Store getConnectedStore() throws javax.mail.MessagingException {
		if(null != this._cachedStore && this._cachedStore.isConnected())  return this._cachedStore;
		
		final Session session = Session.getDefaultInstance(connectionProperties);
		
		this._cachedStore = session.getStore(); // protocol is set from properties

		this._cachedStore.connect(this.server, this.username, this.password);

		
		return this._cachedStore;
	}
}
