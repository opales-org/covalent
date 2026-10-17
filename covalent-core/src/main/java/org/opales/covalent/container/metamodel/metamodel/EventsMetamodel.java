/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.metamodel.metamodel;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * Describes clone behavior and method membership for an events interface.
 *
 * @param <EVENTS> events interface type
 */
public interface EventsMetamodel<EVENTS> {
	/**
	 * Reports whether at least one event method or event parameter is cloned.
	 *
	 * @return {@code true} when the events interface contains cloneable elements
	 */
	boolean hasCloneableElements();

	/**
	 * Reports whether the whole events interface is cloned.
	 *
	 * @return {@code true} when every event payload is cloned by interface rule
	 */
	boolean isCloneable();

	/**
	 * Reports whether a method payload is cloned.
	 *
	 * @param eventMethod event method to inspect
	 * @return {@code true} when the method payload is cloned
	 */
	boolean isMethodCloneable(final Method eventMethod);

	/**
	 * Reports whether one event method parameter is cloned.
	 *
	 * @param eventMethodParameter event method parameter to inspect
	 * @return {@code true} when the parameter is cloned
	 */
	boolean isMethodParameterClonable(Parameter eventMethodParameter);

	/**
	 * Reports whether a method belongs to the events interface.
	 *
	 * @param method method to inspect
	 * @return {@code true} when the method is declared by the events interface
	 */
	boolean containsMethod(Method method);

	/**
	 * Returns the events interface described by this metamodel.
	 *
	 * @return described events interface
	 */
	Class<EVENTS> getEvents();
}
