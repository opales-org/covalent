/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.exclusionpolicy;

import java.util.List;

import org.opales.covalent.hmi.HmiCore;

/**
 * The component types every exclusion policy starts with: two artifacts of the supervision
 * tool itself, which the {@code @Internal} flag does not catch since neither is a Covalent
 * framework component.
 * <ul>
 * <li>{@code ObservedContainerDelegate} - raw-registered into whichever container is being
 * observed. Resolved by fully-qualified name rather than referenced directly: it lives in
 * {@code containermanager.raw}, package-private by design. It is also hidden: it has nothing
 * to do with the observed application, so the exclusions panel does not show it.</li>
 * <li>{@link HmiCore} - the supervision tool's own ActionManager, deployed as a sibling in
 * the supervision container purely so that its action services resolve. Excluded by its type, the
 * level the container registers components at, not by its implementation class.</li>
 * </ul>
 * They stay excluded: the exclusions panel does not offer to remove them, and does not show the
 * hidden ones at all.
 */
public final class BuiltInExclusions {

	private static final String OBSERVED_CONTAINER_DELEGATE_TYPE_NAME =
			"org.opales.covalent.supervision.containermanager.raw.ObservedContainerDelegate";

	private static final List<Class<?>> TYPES = resolve();

	private static final List<Class<?>> HIDDEN = TYPES.stream()
			.filter(type -> type.getName().equals(OBSERVED_CONTAINER_DELEGATE_TYPE_NAME)).toList();

	private BuiltInExclusions() {
	}

	/**
	 * Returns the built-in excluded component types.
	 *
	 * @return the built-in excluded component types, immutable
	 */
	public static List<Class<?>> types() {
		return TYPES;
	}

	/**
	 * Tells whether a component type is one of the built-in exclusions.
	 *
	 * @param componentType a component type
	 * @return whether it is excluded by the supervisor itself
	 */
	public static boolean contains(final Class<?> componentType) {
		return TYPES.contains(componentType);
	}

	/**
	 * Tells whether a component type is a built-in exclusion the user is never shown.
	 *
	 * @param componentType a component type
	 * @return whether it is excluded by the supervisor itself and kept out of the exclusions panel
	 */
	public static boolean isHidden(final Class<?> componentType) {
		return HIDDEN.contains(componentType);
	}

	private static List<Class<?>> resolve() {
		try {
			return List.of(Class.forName(OBSERVED_CONTAINER_DELEGATE_TYPE_NAME), HmiCore.class);
		} catch (ClassNotFoundException e) {
			// ObservedContainerDelegate is part of this same module: falls back to HmiCore
			// alone rather than failing the supervision tool's startup over a default.
			return List.of(HmiCore.class);
		}
	}
}
