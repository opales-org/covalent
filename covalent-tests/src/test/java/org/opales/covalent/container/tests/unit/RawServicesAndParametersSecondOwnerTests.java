/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.exceptions.ValidationException;
import org.opales.covalent.container.rr.parameters.ParametersManagerServices;
import org.opales.covalent.container.rr.services.ServicesManagerServices;

/**
 * Each services interface has exactly one provider and each parameters
 * interface exactly one offeror. When a second, distinct raw component
 * declares an interface that is already owned, the first owner is kept and the
 * second declaration leaves no trace: at the strict level it is rejected, at
 * the other levels it is only reported. Same rule as for raw produced events
 * ({@link RawEventsSecondProducerTests}).
 */
public class RawServicesAndParametersSecondOwnerTests {

	public interface Greeting {
		String greet();
	}

	public interface Settings {
		String getValue();
	}

	public static class FirstProvider implements Greeting {
		@Override
		public String greet() {
			return "first";
		}
	}

	public static class SecondProvider implements Greeting {
		@Override
		public String greet() {
			return "second";
		}
	}

	public static class FirstOfferor implements Settings {
		@Override
		public String getValue() {
			return "first";
		}
	}

	public static class SecondOfferor implements Settings {
		@Override
		public String getValue() {
			return "second";
		}
	}

	private CovalentContainer container;

	@Before
	public void setUp() {
		this.container = Covalent.container();
		this.container.start();
		this.container.rcs().registerComponent(this);
		this.container.rcs().registerComponent(new FirstProvider());
		this.container.rcs().registerComponent(new SecondProvider());
		this.container.rcs().registerComponent(new FirstOfferor());
		this.container.rcs().registerComponent(new SecondOfferor());
	}

	@After
	public void tearDown() {
		this.container.dispose();
	}

	private Set<Class<?>> serviceProviders() {
		Map<Class<?>, Set<Class<?>>> all = this.container.rcs().useServices(ServicesManagerServices.class)
				.getProviderToServices();
		Set<Class<?>> providers = new HashSet<>(all.keySet());
		providers.retainAll(Set.of(FirstProvider.class, SecondProvider.class));
		return providers;
	}

	private Set<Class<?>> parametersOfferors() {
		Map<Class<?>, Set<Class<?>>> all = this.container.rcs().useServices(ParametersManagerServices.class)
				.getOfferorToParameters();
		Set<Class<?>> offerors = new HashSet<>(all.keySet());
		offerors.retainAll(Set.of(FirstOfferor.class, SecondOfferor.class));
		return offerors;
	}

	// --- services ---

	@Test
	public void aSecondServicesProviderDoesNotBecomeAnotherProvider() {
		this.container.rcs().provideServices(FirstProvider.class, Greeting.class);

		this.container.rcs().provideServices(SecondProvider.class, Greeting.class);

		assertEquals(Set.of(FirstProvider.class), this.serviceProviders());
		assertEquals("first", this.container.rcs().useServices(Greeting.class).greet());
	}

	@Test
	public void unregisteringTheRejectedServicesProviderKeepsTheFirstOneWorking() {
		this.container.rcs().provideServices(FirstProvider.class, Greeting.class);
		this.container.rcs().provideServices(SecondProvider.class, Greeting.class);

		this.container.rcs().unregisterComponent(SecondProvider.class);

		assertEquals(Set.of(FirstProvider.class), this.serviceProviders());
		assertEquals("first", this.container.rcs().useServices(Greeting.class).greet());
		this.container.rcs().unregisterComponent(FirstProvider.class);
		assertTrue(this.serviceProviders().toString(), this.serviceProviders().isEmpty());
	}

	@Test
	public void aSecondServicesProviderIsRejectedAtTheStrictLevelAndLeavesNoTrace() {
		this.container.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		this.container.rcs().provideServices(FirstProvider.class, Greeting.class);

		assertThrows(ValidationException.class,
				() -> this.container.rcs().provideServices(SecondProvider.class, Greeting.class));

		assertEquals(Set.of(FirstProvider.class), this.serviceProviders());
		assertEquals("first", this.container.rcs().useServices(Greeting.class).greet());
	}

	// --- parameters ---

	@Test
	public void aSecondParametersOfferorDoesNotBecomeAnotherOfferor() {
		this.container.rcs().offerParameters(FirstOfferor.class, Settings.class);

		this.container.rcs().offerParameters(SecondOfferor.class, Settings.class);

		assertEquals(Set.of(FirstOfferor.class), this.parametersOfferors());
		assertEquals("first", this.container.rcs().accessParameters(Settings.class).getValue());
	}

	@Test
	public void unregisteringTheRejectedParametersOfferorKeepsTheFirstOneWorking() {
		this.container.rcs().offerParameters(FirstOfferor.class, Settings.class);
		this.container.rcs().offerParameters(SecondOfferor.class, Settings.class);

		this.container.rcs().unregisterComponent(SecondOfferor.class);

		assertEquals(Set.of(FirstOfferor.class), this.parametersOfferors());
		assertEquals("first", this.container.rcs().accessParameters(Settings.class).getValue());
		this.container.rcs().unregisterComponent(FirstOfferor.class);
		assertTrue(this.parametersOfferors().toString(), this.parametersOfferors().isEmpty());
	}

	@Test
	public void aSecondParametersOfferorIsRejectedAtTheStrictLevelAndLeavesNoTrace() {
		this.container.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		this.container.rcs().offerParameters(FirstOfferor.class, Settings.class);

		assertThrows(ValidationException.class,
				() -> this.container.rcs().offerParameters(SecondOfferor.class, Settings.class));

		assertEquals(Set.of(FirstOfferor.class), this.parametersOfferors());
		assertEquals("first", this.container.rcs().accessParameters(Settings.class).getValue());
	}
}
