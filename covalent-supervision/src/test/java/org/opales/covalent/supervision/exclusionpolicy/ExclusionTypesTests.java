/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.exclusionpolicy;

import java.util.ArrayList;

import org.junit.Assert;
import org.junit.Test;

import org.opales.covalent.container.component.ComponentAnnotations;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.hmi.HmiCore;
import org.opales.covalent.hmi.impl.HmiCoreImpl;
import org.opales.covalent.supervision.exclusionpolicy.controller.ExclusionPolicyController;
import org.opales.covalent.supervision.exclusionpolicy.controller.impl.ExclusionPolicyControllerImpl;

/** {@link ExclusionTypes}: a component implementation stands for its type, anything else for itself. */
public class ExclusionTypesTests {

	@Test
	public void anImplementationStandsForItsComponentType() {
		Assert.assertEquals(HmiCore.class, ExclusionTypes.typeOf(HmiCoreImpl.class));
		Assert.assertEquals(ExclusionPolicyController.class, ExclusionTypes.typeOf(ExclusionPolicyControllerImpl.class));
	}

	@Test
	public void aComponentTypeStandsForItself() {
		Assert.assertEquals(HmiCore.class, ExclusionTypes.typeOf(HmiCore.class));
	}

	@Test
	public void anyOtherClassStandsForItself() {
		Assert.assertEquals(ArrayList.class, ExclusionTypes.typeOf(ArrayList.class));
	}

	/** Two interfaces that share a parent, none of them a component. */
	interface Shared {
	}

	interface LeftOfTheDiamond extends Shared {
	}

	interface RightOfTheDiamond extends Shared {
	}

	@Implementation
	static class ImplementationWithoutAComponentType implements LeftOfTheDiamond, RightOfTheDiamond {
	}

	@Test
	public void anImplementationWithoutAnyComponentTypeStandsForItselfWhateverItsInterfaces() {
		// the search walks the hierarchy: the shared parent is reached through both interfaces
		Assert.assertTrue(ComponentAnnotations.isImplementation(ImplementationWithoutAComponentType.class));
		Assert.assertEquals(ImplementationWithoutAComponentType.class,
				ExclusionTypes.typeOf(ImplementationWithoutAComponentType.class));
	}

	@Test
	public void theBuiltInExclusionsAreComponentTypesOrRawClasses() {
		for (Class<?> excluded : BuiltInExclusions.types()) {
			Assert.assertEquals(excluded, ExclusionTypes.typeOf(excluded));
		}
	}
}
