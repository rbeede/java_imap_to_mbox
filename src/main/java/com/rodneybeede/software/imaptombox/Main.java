package com.rodneybeede.software.imaptombox;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.UIDFolder;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.rodneybeede.software.imaptombox.config.Configuration;
import com.sun.mail.imap.IMAPFolder;

public class Main {

	private static Logger log = Logger.getLogger(Main.class);
	
	private static final Date startTime = new Date();  // Needed for consistent directory names
	

	public static void main(final String[] args) throws ImapException, IOException {
		log.info("Starting");
		
		log.info("Current working directory is " + System.getProperty("user.dir"));

		if (args.length != 2) {
			log.error("Incorrect number of arguments");
			printUsage();
			LogManager.shutdown();  // cleanly flush logs
			System.exit(255);
		}


		final Configuration config = Configuration.getInstance();
		config.loadConfig(new File(args[0]));
		
		final File baseLocalStorageDirectory = new File(args[1]);
		
		log.info("Using " + config.getServer() + " with user " + config.getUsername());
		log.debug("Current working directory is " + System.getProperty("user.dir")); // not user's home directory but current working
		log.info("Base directory for local mailbox storage is " + baseLocalStorageDirectory.getAbsolutePath());

		
		final ImapClient imapClient = new ImapClient(config.getServer(), config.getUsername(), config.getPassword(), config.getConnectionProperties());
		

		// Get list of remote folders we need to check
		// Note that it is possible previous downloads include folders in the local store that no longer exists on the remote.  We just ignore these
		//	and do not delete them.  The remote site dictates the folder list we check.
		final IMAPFolder[] imapFolders = imapClient.getFolders();
		
		// Go through each folder and download new messages
		for(final IMAPFolder imapFolder : imapFolders) {
			final File localFolder = new File(baseLocalStorageDirectory, imapFolder.getFullName());
			
			log.info("Looking at remote folder " + imapFolder.getFullName());
			
			if(!localFolder.exists()) {
				log.info("Creating local folder " + localFolder.getAbsolutePath());
				
				if(!localFolder.mkdirs()) {
					log.error("FAILED TO CREATE LOCAL FOLDER " + localFolder.getAbsolutePath());
					LogManager.shutdown();  // cleanly flush logs
					System.exit(255);
				}
			}
			
			// Grab the last message on local disk
			final long localLastUID = getLocalLastUID(localFolder);
			
			log.debug("Last known local message UID is " + localLastUID);
			
			// If localLastUID indicates nothing ever downloaded then tell user how many messages to download
			if(0 == localLastUID) {
				int messageCount;
				try {
					messageCount = imapClient.getFolderMessageCount(imapFolder);  // may take a while
				} catch (final MessagingException e) {
					log.error("Unable to get folder message count.  Downloading may also fail");
					messageCount = -1;
				}  
				
				log.warn("First time to download anything for " + imapFolder.getFullName());
				log.warn(imapFolder.getFullName() + " has " + messageCount + " messages");
				
				if(messageCount < 1) {
					// Empty folder (or non-message folder) so skip
					log.info("Folder is empty so skipping");
					continue;
				}
			}
			
			
			// Download any new messages
			log.info("Beginning download of new messages");
			
			// The API isn't clear about getMessagesByUID but it does download just the light version of the message (no content)
			final Message[] messages;
			try {
				if(!imapFolder.isOpen()) {
					imapFolder.open(Folder.READ_ONLY);	
				}
				
				// Note that this always grabs the last message (if not empty) which is okay
				// We may want to redownload the last one in case it was actually cut-off during write (partial write)
				final long startUID = (0 == localLastUID) ? 1 : localLastUID;  // startUID cannot be < 1 or error occurs
				messages = imapFolder.getMessagesByUID(startUID, UIDFolder.LASTUID);
			} catch (final MessagingException e) {
				log.error(e,e);
				continue;
			}
			
			if(imapFolder.isOpen()) {
				try {
					imapFolder.close(false);
				} catch (final MessagingException e) {
					log.warn(e,e);
				}
			}
			
			// Save all the messages to disk
			log.info("Saving " + messages.length + " messages");
			for(final Message liteMessage : messages) {
				final File localMessageFile;
				try {
					localMessageFile = new File(localFolder, generateFilename(liteMessage, imapClient));
				} catch (final ImapException e) {
					log.error(e,e);
					continue;
				}
				
				final FileOutputStream fos;
				try {
					fos = new FileOutputStream(localMessageFile);
				} catch(final FileNotFoundException excep) {
					// Can happen for lots of reasons
					log.error("Unable to create file " + localMessageFile.getAbsolutePath());
					log.error("Disk full?  Too many files in the directory?");
					// No point in going on
					LogManager.shutdown();  // This allows log4j to cleanly shutdown and finish the log
					System.exit(255);
					return;
				}
				
				log.debug(localMessageFile.getAbsolutePath());
				
				try {
					liteMessage.writeTo(fos);
				} catch (final MessagingException e) {
					log.error(localMessageFile.getAbsolutePath() + " failed in write, probably download error", e);
					continue;
				}  finally {
					fos.close();	
				}
				
				
				// Set the last modified time of the local file to match the date and time of the e-mail (if available)
				try {
					final Date sentDate = liteMessage.getSentDate();
					
					if(null == sentDate) {
						// Type of message may not have a sent date (i.e. Microsoft Exchange/Outlook Calendar entry item in Calendars folder)
						log.warn("Entry does not have a Sent Date so no local modification time on the local file will be set");
						continue;
					}
					
					if(!localMessageFile.setLastModified(sentDate.getTime())) {
						log.error("Failed to set local file modification date to " + sentDate.toString() + " for file " + localMessageFile.getAbsolutePath());
						continue;
					}
				} catch(final MessagingException e) {
					log.error("Unable to set local file date because message had no sent date.  File:  " + localMessageFile.getAbsolutePath());
					continue;
				}
				
				
				log.trace(localMessageFile.getAbsolutePath() + "\t" + "successful!");
			}
		}
		
		

		final Date endTime = new Date();

		log.debug("Ending at " + endTime);
		log.info("Total run time " + (double) (endTime.getTime() - startTime.getTime()) / 1000 / 60 + " minutes");

		log.info("Results stored in " + baseLocalStorageDirectory.getAbsolutePath());
	}


	private static void printUsage() {
		System.out.println("java -jar JavaImapToMbox.jar <config.xml> <base storage directory>");
		System.out.println();
		System.out.println("\tRemote server MUST support starttls (secure connection)");
	}
	
	/**
	 * @param localFolder
	 * @return Last UID for this folder or 0 if no LAST-UID
	 */
	private static long getLocalLastUID(final File localFolder) {
		// Do a listing and grab the last file in the local folder (sorted)
		// The first 19 characters is the UID for the downloaded message
		
		final File[] sortedMessageFiles = localFolder.listFiles(new FileFilter() {
			@Override
			public boolean accept(final File pathname) {
				return pathname.isFile();
			}
		});
		
		if(0 == sortedMessageFiles.length) {
			return 0;  // No messages so no last uid
		}
		
		java.util.Arrays.sort(sortedMessageFiles);
		
		// Last one in array is last id (sorted on first 19 characters)
		final String uidAsStr = sortedMessageFiles[sortedMessageFiles.length - 1].getName().substring(0, 20);
		
		// radix must be forced otherwise preceding 0's are seen as octal
		final long uid = Long.parseLong(uidAsStr, 10);
		
		
		return uid;
	}


    private static String generateFilename(final Message message, final ImapClient imapClient) throws ImapException {
        final StringBuilder filename = new StringBuilder();
        
        // Grab the UID
        final long uid = imapClient.getMessageUID(message);
        
        //19 digits length number with leading 0's so file sort works nicely :)
        filename.append(String.format("%020d", uid));
        filename.append('_');
        String subject;
		try {
			subject = message.getSubject();  // could be null
		} catch (final MessagingException e) {
			throw new ImapException(e);
		}
        if (null == subject) {
            subject = "";
        }
        subject = subject.replaceAll("[^a-zA-Z0-9'!~]", "");  // white listed

        if (subject.length() > 50) {
            subject = subject.substring(0, 50);  // 50 char limit
        }
        filename.append(subject);

        return filename.toString();
    }
}

