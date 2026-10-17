/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.container.component.ComponentAnnotations;
import org.opales.covalent.container.util.CallerFinder;
import org.opales.covalent.container.util.Reflection;

/**
 * <p>
 * {@link ComponentAnnotations}, {@link CallerFinder} and {@link Reflection}
 * only expose static members, so they are final with a private constructor.
 * </p>
 */
public class UtilityDefaultConstructorsTests {

	@Test
	public void utilityClassesCannotBeInstantiatedNorExtended() {
		for (Class<?> utility : new Class<?>[] { ComponentAnnotations.class, CallerFinder.class, Reflection.class }) {
			Assert.assertTrue(utility.getSimpleName() + " must be final", Modifier.isFinal(utility.getModifiers()));
			Constructor<?>[] constructors = utility.getDeclaredConstructors();
			Assert.assertEquals(utility.getSimpleName(), 1, constructors.length);
			Assert.assertTrue(utility.getSimpleName() + " constructor must be private",
					Modifier.isPrivate(constructors[0].getModifiers()));
		}
	}
}
