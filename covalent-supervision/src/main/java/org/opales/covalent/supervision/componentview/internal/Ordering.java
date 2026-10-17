/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * A fixed order for what a view draws. The sets and maps the views are given have no order of their own, and Java
 * changes the order of an immutable copy from one launch to the next. The views add their cells in the order given
 * here, so that the automatic layout, which depends on that order, draws the same container the same way each time.
 */
public final class Ordering {

	private static final Comparator<Class<?>> BY_NAME = Comparator.comparing(Class::getName);

	private Ordering() {
		// Not meant to be instantiated.
	}

	/**
	 * Returns component types by their full name.
	 *
	 * @param types the types, in any order
	 * @return the same types, sorted by full name
	 */
	public static List<Class<?>> byName(final Collection<? extends Class<?>> types) {
		List<Class<?>> sorted = new ArrayList<>(types);
		sorted.sort(BY_NAME);
		return sorted;
	}

	/**
	 * Returns the entries of a map keyed by component types by the full name of their key.
	 *
	 * @param <V>     what the map holds
	 * @param entries a map keyed by component types, in any order
	 * @return its entries, sorted by the full name of their key
	 */
	public static <V> List<Map.Entry<Class<?>, V>> entriesByName(final Map<Class<?>, V> entries) {
		List<Map.Entry<Class<?>, V>> sorted = new ArrayList<>(entries.entrySet());
		sorted.sort(Map.Entry.comparingByKey(BY_NAME));
		return sorted;
	}
}
