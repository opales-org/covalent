/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.exceptions.ValidationException;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.rr.services.ServicesManagerServices;

/**
 * Verifies how the services manager (through its request/response base) reacts to inconsistent requests: removing
 * services that were never provided, or that a component never used, is a dynamic verification, thrown at the
 * {@code strict} level and tolerated at the {@code silent} one; consistent removals leave nothing behind.
 */
public class ServicesManagerDynamicVerificationTests {

	/** Services provided by the fixture. */
	public interface Hello {
		String hello(String name);
	}

	/** Provider of {@link Hello}. */
	public static class HelloProvider implements Hello {
		@Override
		public String hello(final String name) {
			return "hello " + name;
		}
	}

	private CovalentContainer container;

	private RawComponentsServices rcs;

	private ServicesManagerServices services;

	@Before
	public void setUp() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
		this.container.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		this.container.start();
		this.rcs = this.container.rcs();
		this.rcs.registerComponent(this);
		this.services = this.rcs.useServices(ServicesManagerServices.class);
	}

	@After
	public void tearDown() {
		this.container.dispose();
	}

	@Test
	public void deletingServicesThatWereNeverProvidedIsRejectedInStrictMode() {
		ValidationException failure = assertThrows(ValidationException.class,
				() -> this.services.deleteProvidedServices(Hello.class));

		assertTrue(failure.getMessage(), failure.getMessage().contains("not provided"));
	}

	@Test
	public void deletingServicesThatWereNeverProvidedIsToleratedInSilentMode() {
		this.container.getParameters().setDynamicRulesChecking(VerificationLevel.silent);

		this.services.deleteProvidedServices(Hello.class);

		assertFalse(this.services.isServicesProvided(Hello.class));
	}

	@Test
	public void deletingUsedServicesThatTheComponentNeverUsedIsRejectedInStrictMode() {
		ValidationException failure = assertThrows(ValidationException.class,
				() -> this.services.deleteUsedServices(HelloProvider.class, Hello.class));

		assertTrue(failure.getMessage(), failure.getMessage().contains("not used by component " + HelloProvider.class));
	}

	@Test
	public void deletingUsedServicesThatTheComponentNeverUsedIsToleratedInSilentMode() {
		this.container.getParameters().setDynamicRulesChecking(VerificationLevel.silent);

		this.services.deleteUsedServices(HelloProvider.class, Hello.class);

		assertFalse(this.services.isServicesUsed(HelloProvider.class, Hello.class));
	}

	@Test
	public void providedServicesAreRemovedOnceAndLeaveNoProviderBehind() {
		this.rcs.registerComponent(new HelloProvider());
		this.rcs.provideServices(HelloProvider.class, Hello.class);
		assertTrue(this.services.isServicesProvided(Hello.class));
		assertEquals("hello bob", this.rcs.useServices(HelloProvider.class, Hello.class).hello("bob"));

		this.rcs.unprovideServices(HelloProvider.class, Hello.class);

		assertFalse(this.services.isServicesProvided(Hello.class));
		assertFalse(this.services.getProviderToServices().containsKey(HelloProvider.class));
		assertThrows(ValidationException.class, () -> this.services.deleteProvidedServices(Hello.class));
	}

	@Test
	public void reconnectingUsedServicesReturnsTheExistingProxyForTheSameUser() {
		this.rcs.registerComponent(new HelloProvider());
		this.rcs.provideServices(HelloProvider.class, Hello.class);

		Hello first = this.services.reconnectUsedServices(String.class, Hello.class);
		Hello second = this.services.reconnectUsedServices(String.class, Hello.class);
		Hello otherUser = this.services.reconnectUsedServices(Integer.class, Hello.class);

		assertSame(first, second);
		assertNotSame(first, otherUser);
		assertTrue(this.services.isServicesUsed(String.class, Hello.class));
		assertEquals("hello alice", first.hello("alice"));
	}
}
