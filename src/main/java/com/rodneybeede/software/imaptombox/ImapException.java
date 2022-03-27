/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.rodneybeede.software.imaptombox;

/**
 *
 * @author rbeede
 */
public class ImapException extends Exception {
	private static final long serialVersionUID = 4254871059695428923L;
	

	public ImapException(final Throwable cause) {
		super(cause);
	}


	public ImapException(final String message, final Throwable cause) {
		super(message, cause);
	}
}
