/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.exclusionpolicy;

import java.util.HashSet;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.opales.covalent.supervision.exclusionpolicy.controller.ExclusionParameters;

/**
 * Applies an exclusion to a whole composite: a component type is left out when it is excluded
 * itself or when any composite containing it, at any depth, is. Excluding a composite therefore
 * hides its parts too, which would otherwise surface at the top level.
 */
public final class ExclusionCascade {

	private ExclusionCascade() {
	}

	/**
	 * Tells whether a component type is left out, by itself or through one of its containers.
	 *
	 * @param componentType the component type to check
	 * @param parameters the exclusion rules applied to each type of the containment chain
	 * @param parentOf gives the composite directly containing a type, or {@code null} for a
	 *        top-level type
	 * @return {@code true} if {@code componentType} or one of its containers is excluded
	 */
	public static boolean isExcluded(final Class<?> componentType, final ExclusionParameters parameters,
			final UnaryOperator<Class<?>> parentOf) {
		Set<Class<?>> visited = new HashSet<>();
		Class<?> current = componentType;
		while (current != null && visited.add(current)) {
			if (parameters.isExcluded(current)) {
				return true;
			}
			current = parentOf.apply(current);
		}
		return false;
	}
}
