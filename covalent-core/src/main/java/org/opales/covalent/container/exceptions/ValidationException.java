/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.exceptions;

/**
 * Thrown when a component type, or one of its annotated members, fails the
 * container's structural validation rules - for example, an
 * {@code @Implementation} class missing a public no-arg constructor, or an
 * {@code @EventsFilter} interface with a method that does not follow the
 * required naming convention.
 */
public class ValidationException extends RuntimeException {

	/**
	 *
	 */
	private static final long serialVersionUID = 9030107705135997889L;

	/**
	 * Creates a new exception with the given detail message.
	 *
	 * @param aMessage the detail message
	 */
	public ValidationException(final String aMessage) {
		super(aMessage);
	}

	/**
	 * Creates a new exception with the given detail message and cause.
	 *
	 * @param aMessage the detail message
	 * @param cause    the underlying cause of the validation failure
	 */
	public ValidationException(final String aMessage, final Throwable cause) {
		super(aMessage, cause);
	}

}
