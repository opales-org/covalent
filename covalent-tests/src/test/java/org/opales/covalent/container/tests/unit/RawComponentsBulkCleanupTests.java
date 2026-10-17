/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.exceptions.ValidationException;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.raw.impl.RawComponentsImpl;
import org.opales.covalent.container.registry.RegistryReadServices;

/**
 * Verifies the teardown of raw ports: passivating the raw components manager closes every services, parameters
 * and events port still held by raw components, users and subscribers first, and unregistering a raw component
 * through its implementation class is reported as a structural verification and still removes the component.
 */
public class RawComponentsBulkCleanupTests {

	/** Services provided by the fixture. */
	public interface Hello {
		String hello(String name);
	}

	/** Parameters offered by the fixture. */
	public interface Settings {
		String value();
	}

	/** Events produced by the fixture. */
	public interface Ping {
		void onPing(String message);
	}

	/** Raw provider of services, parameters and events. */
	public static class Provider implements Hello, Settings {
		@Override
		public String hello(final String name) {
			return "hello " + name;
		}

		@Override
		public String value() {
			return "configured";
		}
	}

	/** Type under which {@link Provider} is registered when it is registered by an explicit type. */
	public interface ProviderType {
	}

	/** Raw provider registered under an explicit component type. */
	public static class TypedProvider implements ProviderType {
	}

	private CovalentContainer container;

	private RawComponentsServices rcs;

	@Before
	public void setUp() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
		this.container.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		this.container.start();
		this.rcs = this.container.rcs();
		this.rcs.registerComponent(this);
	}

	@After
	public void tearDown() {
		this.container.dispose();
	}

	@Test
	public void passivatingTheRawComponentsManagerClosesEveryRawPort() {
		this.rcs.registerComponent(new Provider());
		this.rcs.provideServices(Provider.class, Hello.class);
		this.rcs.offerParameters(Provider.class, Settings.class);
		Ping producer = this.rcs.produceEvents(Provider.class, Ping.class);
		Hello hello = this.rcs.useServices(Provider.class, Hello.class);
		Settings settings = this.rcs.accessParameters(Provider.class, Settings.class);
		List<String> received = new ArrayList<>();
		this.rcs.subscribe(Ping.class, (Ping) received::add);
		producer.onPing("before");
		assertEquals("hello x", hello.hello("x"));
		assertEquals("configured", settings.value());

		((RawComponentsImpl) this.rcs).passivate();

		assertThrows(IllegalStateException.class, () -> hello.hello("x"));
		assertThrows(IllegalStateException.class, () -> settings.value());
		assertThrows(IllegalStateException.class, () -> producer.onPing("after"));
		assertEquals(List.of("before"), received);
		// every port was released: declaring them again is not a duplicate
		this.rcs.provideServices(Provider.class, Hello.class);
		this.rcs.offerParameters(Provider.class, Settings.class);
		this.rcs.produceEvents(Provider.class, Ping.class);
	}

	@Test
	public void unregisteringByImplementationIsReportedStructurallyInStrictMode() {
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.strict);
		this.rcs.registerComponent(ProviderType.class, new TypedProvider());

		ValidationException failure = assertThrows(ValidationException.class,
				() -> this.rcs.unregisterComponent(TypedProvider.class));

		assertTrue(failure.getMessage(), failure.getMessage().contains(
				"unregisterComponent takes a component type as argument, but provided class 'TypedProvider' is a component implementation."));
		this.rcs.unregisterComponent(ProviderType.class);
	}

	@Test
	public void unregisteringByImplementationStillRemovesTheComponentWhenTheViolationIsTolerated() {
		this.rcs.registerComponent(ProviderType.class, new TypedProvider());
		RegistryReadServices registry = this.rcs.useServices(RegistryReadServices.class);
		assertNotNull(registry.getComponentInstance(ProviderType.class));

		this.rcs.unregisterComponent(TypedProvider.class);

		assertThrows(IllegalArgumentException.class, () -> registry.getComponentInstance(ProviderType.class));
		this.rcs.registerComponent(ProviderType.class, new TypedProvider());
		assertNotNull(registry.getComponentInstance(ProviderType.class));
	}
}
