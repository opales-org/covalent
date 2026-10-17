/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.exceptions;

/**
 * Thrown when a component type, or one of its annotated members, does not
 * respect the container's naming conventions - for example, a service
 * accessor method whose name does not start with {@code get} or {@code is}.
 */
public class NamingException extends RuntimeException {

	/**
	 *
	 */
	private static final long serialVersionUID = 4473432688381247794L;

	/**
	 * Creates a new exception with the given detail message.
	 *
	 * @param aMessage the detail message
	 */
	public NamingException(final String aMessage) {
		super(aMessage);
	}

}
