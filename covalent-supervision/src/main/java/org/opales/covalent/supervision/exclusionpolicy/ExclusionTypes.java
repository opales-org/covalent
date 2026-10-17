/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.exclusionpolicy;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

import org.opales.covalent.container.component.ComponentAnnotations;

/**
 * Brings the class a user names for an exclusion back to the component type the container
 * registers the component under. A component type has a single implementation, so naming either
 * one excludes the same component; the exclusions are kept by type, which is what the screens
 * show and what the container's registry is keyed by.
 */
public final class ExclusionTypes {

	private ExclusionTypes() {
	}

	/**
	 * Returns the component type a class stands for.
	 *
	 * @param aClass a component type, a component implementation, or any other class
	 * @return the {@code @Component} interface {@code aClass} implements when it is a component
	 *         implementation; {@code aClass} itself otherwise (a component type, or a raw component
	 *         registered under its own class)
	 */
	public static Class<?> typeOf(final Class<?> aClass) {
		if (ComponentAnnotations.isComponent(aClass) || !ComponentAnnotations.isImplementation(aClass)) {
			return aClass;
		}
		Deque<Class<?>> pending = new ArrayDeque<>();
		Set<Class<?>> seen = new HashSet<>();
		pending.add(aClass);
		while (!pending.isEmpty()) {
			Class<?> current = pending.poll();
			if (!seen.add(current)) {
				continue;
			}
			if (current != aClass && ComponentAnnotations.isComponent(current)) {
				return current;
			}
			if (current.getSuperclass() != null) {
				pending.add(current.getSuperclass());
			}
			pending.addAll(Set.of(current.getInterfaces()));
		}
		return aClass;
	}
}
