/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.raw.impl;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.internals.Internal;
import org.opales.covalent.container.exceptions.ValidationException;
import org.opales.covalent.container.metamodel.MetamodelWriteServices;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.validation.impl.ComponentValidatorImpl;

/**
 * Verifies the stateless helpers shared by the raw port collaborators:
 * tracking and untracking of raw port interfaces per component type (an empty
 * entry is dropped, an unknown component type is ignored), defensive copying
 * of the tracked ports, detection of a managed implementation passed where a
 * raw component is expected (reported through the structural verification
 * level), and the preconditions that a producer-side raw port is only opened
 * for a known, registered, non-internal component type.
 */
public class RawPortSupportTests {

	interface FirstPort {
	}

	interface SecondPort {
	}

	static class Owner {
	}

	static class OtherOwner {
	}

	@Implementation
	static class ManagedImpl {
	}

	@Internal
	static class InternalOwner {
	}

	private static class FakeRegistry implements RegistryReadServices {
		private final Set<Class<?>> registered = new LinkedHashSet<>();

		@Override
		public <CT, CI extends CT> CI getComponentInstance(final Class<CT> componentType) {
			return null;
		}

		@Override
		public Map<Class<?>, Object> getInstances() {
			return new HashMap<>();
		}

		@Override
		public <T> boolean isRegistered(final Class<T> componentType) {
			return this.registered.contains(componentType);
		}
	}

	@Test
	public void trackedPortsAreGroupedPerComponentTypeWithoutDuplicates() {
		Map<Class<?>, Set<Class<?>>> tracked = new HashMap<>();

		RawPortSupport.trackRawPort(tracked, Owner.class, FirstPort.class);
		RawPortSupport.trackRawPort(tracked, Owner.class, FirstPort.class);
		RawPortSupport.trackRawPort(tracked, Owner.class, SecondPort.class);
		RawPortSupport.trackRawPort(tracked, OtherOwner.class, FirstPort.class);

		Assert.assertEquals(2, tracked.size());
		Assert.assertEquals(Set.of(FirstPort.class, SecondPort.class), tracked.get(Owner.class));
		Assert.assertEquals(Set.of(FirstPort.class), tracked.get(OtherOwner.class));
	}

	@Test
	public void untrackingTheLastPortDropsTheComponentEntry() {
		Map<Class<?>, Set<Class<?>>> tracked = new HashMap<>();
		RawPortSupport.trackRawPort(tracked, Owner.class, FirstPort.class);
		RawPortSupport.trackRawPort(tracked, Owner.class, SecondPort.class);

		RawPortSupport.untrackRawPort(tracked, Owner.class, FirstPort.class);
		Assert.assertEquals(Set.of(SecondPort.class), tracked.get(Owner.class));

		RawPortSupport.untrackRawPort(tracked, Owner.class, SecondPort.class);
		Assert.assertFalse(tracked.containsKey(Owner.class));
	}

	@Test
	public void untrackingAnUnknownComponentTypeChangesNothing() {
		Map<Class<?>, Set<Class<?>>> tracked = new HashMap<>();
		RawPortSupport.trackRawPort(tracked, Owner.class, FirstPort.class);

		RawPortSupport.untrackRawPort(tracked, OtherOwner.class, FirstPort.class);
		RawPortSupport.untrackRawPort(tracked, Owner.class, SecondPort.class);

		Assert.assertEquals(Map.of(Owner.class, Set.of(FirstPort.class)), tracked);
	}

	@Test
	public void copiedTrackedPortsAreIndependentOfTheOriginal() {
		Map<Class<?>, Set<Class<?>>> tracked = new HashMap<>();
		RawPortSupport.trackRawPort(tracked, Owner.class, FirstPort.class);

		Map<Class<?>, Set<Class<?>>> copy = RawPortSupport.copyTrackedPorts(tracked);
		RawPortSupport.trackRawPort(tracked, Owner.class, SecondPort.class);
		RawPortSupport.untrackRawPort(tracked, Owner.class, FirstPort.class);

		Assert.assertEquals(Map.of(Owner.class, Set.of(FirstPort.class)), copy);
	}

	@Test
	public void managedImplementationIsRecognizedAndReportedAtTheStructuralLevel() {
		ComponentValidatorImpl validator = new ComponentValidatorImpl();
		validator.setStructuralRulesVerificationLevel(VerificationLevel.silent);

		Assert.assertTrue(RawPortSupport.isImplementation(validator, ManagedImpl.class));
		Assert.assertFalse(RawPortSupport.isImplementation(validator, Owner.class));

		validator.setStructuralRulesVerificationLevel(VerificationLevel.strict);
		Assert.assertThrows(ValidationException.class,
				() -> RawPortSupport.isImplementation(validator, ManagedImpl.class));
		Assert.assertFalse(RawPortSupport.isImplementation(validator, Owner.class));
	}

	@Test
	public void unknownComponentTypeIsRejectedWithAnActionableMessage() {
		RawComponentsServices rcs = Covalent.container().rcs();
		rcs.registerComponent(this);
		MetamodelWriteServices metamodelWrite = rcs.useServices(MetamodelWriteServices.class);

		RawPortSupport.requireKnownComponentType(metamodelWrite, RawPortSupportTests.class, "provide services X");
		IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class,
				() -> RawPortSupport.requireKnownComponentType(metamodelWrite, Owner.class, "provide services X"));

		Assert.assertTrue(e.getMessage(), e.getMessage().contains("Cannot provide services X"));
		Assert.assertTrue(e.getMessage(), e.getMessage().contains("has no metamodel entry"));
	}

	@Test
	public void registeredNonInternalComponentTypeIsAccepted() {
		FakeRegistry registry = new FakeRegistry();
		registry.registered.add(Owner.class);

		RawPortSupport.requireRegisteredComponentType(registry, Owner.class, "produce events X");
	}

	@Test
	public void unregisteredComponentTypeIsRejectedAsIllegalState() {
		FakeRegistry registry = new FakeRegistry();

		IllegalStateException e = Assert.assertThrows(IllegalStateException.class,
				() -> RawPortSupport.requireRegisteredComponentType(registry, Owner.class, "produce events X"));

		Assert.assertTrue(e.getMessage(), e.getMessage().contains("Cannot produce events X"));
		Assert.assertTrue(e.getMessage(), e.getMessage().contains("is not registered"));
	}

	@Test
	public void internalComponentTypeIsRejectedEvenWhenRegistered() {
		FakeRegistry registry = new FakeRegistry();
		registry.registered.add(InternalOwner.class);

		IllegalArgumentException e = Assert.assertThrows(IllegalArgumentException.class,
				() -> RawPortSupport.requireRegisteredComponentType(registry, InternalOwner.class,
						"produce events X"));

		Assert.assertTrue(e.getMessage(), e.getMessage().contains("one of Covalent's own components"));
	}
}
