/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.exceptions;

/**
 * Thrown when a lifecycle operation on a component fails: deployment,
 * instantiation, initialization, activation, passivation or removal. The
 * message starts with the phase and the component, as in
 * {@code During activation of component Foo}, and the cause is the failure
 * raised by the component or by the container.
 */
public class ComponentLifecycleException extends RuntimeException {

	private static final long serialVersionUID = 6217284709814652335L;

	/**
	 * Creates a new exception with the given detail message and cause.
	 *
	 * @param aMessage the detail message, naming the phase and the component
	 * @param cause    the failure that interrupted the operation
	 */
	public ComponentLifecycleException(final String aMessage, final Throwable cause) {
		super(aMessage, cause);
	}

}
