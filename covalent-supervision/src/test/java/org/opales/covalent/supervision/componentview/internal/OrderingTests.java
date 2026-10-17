/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

/**
 * What a view draws comes in a fixed order, whatever the order of the set or map it is given.
 */
public class OrderingTests {

	@Test
	public void typesAreSortedByFullName() {
		Set<Class<?>> scrambled = new LinkedHashSet<>(List.of(Thread.class, String.class, Integer.class, Runnable.class));

		Assert.assertEquals(List.of(Integer.class, Runnable.class, String.class, Thread.class),
				Ordering.byName(scrambled));
	}

	@Test
	public void twoTypesOfTheSameSimpleNameKeepTheirOwnPlaceByFullName() {
		Set<Class<?>> scrambled = new LinkedHashSet<>(List.of(java.sql.Date.class, java.util.Date.class));

		Assert.assertEquals(List.of(java.sql.Date.class, java.util.Date.class), Ordering.byName(scrambled));
		Assert.assertEquals(Ordering.byName(scrambled),
				Ordering.byName(new LinkedHashSet<>(List.of(java.util.Date.class, java.sql.Date.class))));
	}

	@Test
	public void entriesAreSortedByTheFullNameOfTheirKey() {
		Map<Class<?>, String> scrambled = new LinkedHashMap<>();
		scrambled.put(String.class, "s");
		scrambled.put(Integer.class, "i");
		scrambled.put(Long.class, "l");

		List<Class<?>> keys = Ordering.entriesByName(scrambled).stream().map(Map.Entry::getKey).toList();

		Assert.assertEquals(List.of(Integer.class, Long.class, String.class), keys);
	}

	@Test
	public void noTypeGivesNoEntry() {
		Assert.assertTrue(Ordering.byName(Set.of()).isEmpty());
		Assert.assertTrue(Ordering.entriesByName(Map.<Class<?>, String>of()).isEmpty());
	}
}
