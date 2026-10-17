/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.exceptions;

/**
 * Thrown when an operation requires a port (services, parameters, or events
 * subscription) to be currently bound to a provider or offeror, but no such
 * binding exists - for example, using a {@code @UsedServices} port when no
 * component currently provides the corresponding {@code @Services}
 * interface.
 */
public class NotBoundException extends IllegalStateException {

	/**
	 *
	 */
	private static final long serialVersionUID = -1344428129482872121L;

	/**
	 * Creates a new exception with the given detail message.
	 *
	 * @param aMessage the detail message
	 */
	public NotBoundException(final String aMessage) {
		super(aMessage);
	}

}
