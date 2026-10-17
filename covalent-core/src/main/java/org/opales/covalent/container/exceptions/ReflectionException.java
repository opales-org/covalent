/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.exceptions;

/**
 * Thrown when the container fails to perform a reflective operation it
 * needs to manage a component - such as instantiating an implementation
 * class, or reading or writing an annotated field - typically wrapping the
 * underlying {@link ReflectiveOperationException} or similar JDK cause.
 */
public class ReflectionException extends RuntimeException {

	/**
	 *
	 */
	private static final long serialVersionUID = -25426768406165685L;

	/**
	 * Creates a new exception wrapping the given cause.
	 *
	 * @param cause the underlying cause of the reflective failure
	 */
	public ReflectionException(final Throwable cause) {
		super(cause);
	}

	/**
	 * Creates a new exception with the given detail message.
	 *
	 * @param aMessage the detail message
	 */
	public ReflectionException(final String aMessage) {
		super(aMessage);
	}

	/**
	 * Creates a new exception with the given detail message and cause.
	 *
	 * @param aMessage the detail message
	 * @param cause    the underlying cause of the reflective failure
	 */
	public ReflectionException(final String aMessage, final Throwable cause) {
		super(aMessage, cause);
	}

}
