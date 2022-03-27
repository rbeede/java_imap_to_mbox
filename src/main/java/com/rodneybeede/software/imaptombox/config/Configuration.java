package com.rodneybeede.software.imaptombox.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.xml.bind.DatatypeConverter;

import org.apache.log4j.Logger;

/**
 * Singleton using the enum method which also provides serialization and protects against reflection
 * See http://stackoverflow.com/questions/70689/efficient-way-to-implement-singleton-pattern-in-java
 * 
 * Access with the traditional {@link #getInstance()} method
 *
 * @author rbeede
 * 
 */
public enum Configuration {
	INSTANCE;
	
	public static Configuration getInstance() {
		return Configuration.INSTANCE;
	}
	
	private static final Logger log = Logger.getLogger(Configuration.class);
	
	private final Properties configProperties = new Properties();

	/**
	 * Dumps any existing configuration and loads the configuration from the given file.  If an error occurs configuration will be blank.
	 * 
	 * @param xmlFile File to load which should be an xml formatted properties format
	 * @throws IOException If any errors occur while reading the file or if the file is corrupt
	 */
	public void loadConfig(final File xmlFile) throws IOException {
		loadConfig(new FileInputStream(xmlFile));
	}
	
	/**
	 * Dumps any existing configuration and loads the configuration from the given xml inputstream.  If an error occurs configuration will be blank.
	 * 
	 * @param file File to load which should be an xml formatted properties format
	 * @throws IOException If any errors occur while reading the file or if the file is corrupt
	 */
	public void loadConfig(final InputStream is) throws IOException {
		this.configProperties.clear();
		
		try {
			this.configProperties.loadFromXML(is);
		} catch(final IOException e) {
			log.error(e,e);
			this.configProperties.clear();  // Empty on error
			throw e;
		}
		
		if(log.isDebugEnabled()) {
			for(final String settingName : this.configProperties.stringPropertyNames()) {
				if(settingName.toLowerCase().contains("password")) {
					log.debug("Config setting:  " + settingName + " ==> " + "[MASKED]");
				} else {
					log.debug("Config setting:  " + settingName + " ==> " + this.configProperties.getProperty(settingName));	
				}
			}
		}
	}
	
	/**
	 * Retrieves the value for any configuration property
	 * 
	 * @param name
	 * @return
	 */
	protected String get(final String name) {
		return this.configProperties.getProperty(name);
	}
	
	
	public String getServer() {
		return get("SERVER");
	}
	
	public String getUsername() {
		return get("USERNAME");
	}
	
	public String getPassword() {
		return new String(DatatypeConverter.parseBase64Binary(get("PASSWORD")));
	}
	
	/**
	 * @return Hard-coded connection properties for use with an IMAPS (secure) server
	 */
	public Properties getConnectionProperties() {
		final Properties connectionProperties = new Properties();
		connectionProperties.put("mail.imap.starttls.enable", "true");
		connectionProperties.put("mail.imap.starttls.required", "true"); // it must be secure before wee send the username and password
		connectionProperties.put("mail.store.protocol", "imaps");  // Only one supported is IMAP secure
		return connectionProperties;
	}
	
}
