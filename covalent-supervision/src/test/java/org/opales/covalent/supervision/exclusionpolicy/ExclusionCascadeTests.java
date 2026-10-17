/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.exclusionpolicy;

import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.junit.Assert;
import org.junit.Test;

import org.opales.covalent.supervision.exclusionpolicy.controller.ExclusionParameters;

/** Verifies that an exclusion applies to a type and to everything contained in it. */
public class ExclusionCascadeTests {

	private static final class Root {
	}

	private static final class Middle {
	}

	private static final class Leaf {
	}

	private static final class Other {
	}

	private static final Map<Class<?>, Class<?>> CONTAINERS = Map.of(Middle.class, Root.class, Leaf.class,
			Middle.class);

	private static boolean excluded(final Class<?> type, final Set<Class<?>> rules) {
		ExclusionParameters parameters = rules::contains;
		return ExclusionCascade.isExcluded(type, parameters, CONTAINERS::get);
	}

	@Test
	public void anExcludedTypeIsExcluded() {
		Assert.assertTrue(excluded(Middle.class, Set.of(Middle.class)));
	}

	@Test
	public void everyLevelBelowAnExcludedContainerIsExcluded() {
		Assert.assertTrue(excluded(Middle.class, Set.of(Root.class)));
		Assert.assertTrue(excluded(Leaf.class, Set.of(Root.class)));
	}

	@Test
	public void excludingAPartDoesNotExcludeItsContainerOrItsSiblings() {
		Assert.assertFalse(excluded(Root.class, Set.of(Leaf.class)));
		Assert.assertFalse(excluded(Middle.class, Set.of(Leaf.class)));
		Assert.assertFalse(excluded(Other.class, Set.of(Root.class)));
	}

	@Test
	public void aContainmentLoopTerminates() {
		UnaryOperator<Class<?>> loop = type -> type == Root.class ? Middle.class : Root.class;
		Assert.assertFalse(ExclusionCascade.isExcluded(Leaf.class, (ExclusionParameters) type -> false, loop));
		Assert.assertTrue(ExclusionCascade.isExcluded(Leaf.class, (ExclusionParameters) type -> type == Middle.class,
				loop));
	}
}
