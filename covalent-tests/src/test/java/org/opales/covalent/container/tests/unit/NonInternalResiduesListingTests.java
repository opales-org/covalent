/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.After;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Parameters;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.impl.CovalentContainerImpl;

/**
 * The internal managers list what a business component leaves in them: while
 * a business component is still deployed, started and wired, every manager
 * names it; once the container is stopped, nothing is listed.
 */
public class NonInternalResiduesListingTests {

	@Component
	public interface Business {
	}

	@Services
	public interface GreetingServices {
		String greet();
	}

	@Parameters
	public interface GreetingParameters {
		String getName();

		void setName(String name);
	}

	@Events
	public interface GreetingEvents {
		void onGreeting(String greeting);
	}

	@Implementation
	public static class BusinessImpl implements Business, GreetingServices, GreetingParameters {

		@ProducedEvents
		private GreetingEvents produceGreetingEvents;

		private String name = "world";

		@Override
		public String greet() {
			return "hello " + this.name;
		}

		@Override
		public String getName() {
			return this.name;
		}

		@Override
		public void setName(final String name) {
			this.name = name;
		}
	}

	@Component
	public interface Client {
	}

	@Implementation
	public static class ClientImpl implements Client {

		@UsedServices
		private GreetingServices greetingServices;
	}

	private CovalentContainerImpl container;

	@After
	public void tearDown() {
		if (this.container != null) {
			this.container.dispose();
		}
	}

	@SuppressWarnings("unchecked")
	private List<String> residues() throws Exception {
		Method method = CovalentContainerImpl.class.getDeclaredMethod("listNonInternalResidues");
		method.setAccessible(true);
		return (List<String>) method.invoke(this.container);
	}

	private static boolean mentions(final List<String> residues, final String owner) {
		return residues.stream().anyMatch(r -> r.startsWith(owner + ":") && r.contains(BusinessImpl.class.getName())
				|| r.startsWith(owner + ":") && r.contains(Business.class.getName()));
	}

	@Test
	public void everyManagerNamesTheBusinessComponentStillThereAndNothingIsLeftAfterStop() throws Exception {
		this.container = (CovalentContainerImpl) Covalent.container();
		this.container.deployComponents(BusinessImpl.class);
		this.container.start();

		List<String> residues = this.residues();

		for (String owner : List.of("Deployer", "LifecycleManager", "Metamodel", "ComponentRegistry",
				"DescriptorRegistry", "Directory")) {
			assertTrue(owner + " should name the business component: " + residues, mentions(residues, owner));
		}
		assertTrue("events manager should name the producer: " + residues,
				residues.stream().anyMatch(r -> r.startsWith("EventsManager:")));
		assertTrue("a services manager should name the provider: " + residues,
				residues.stream().anyMatch(r -> r.contains("provider still registered")));

		this.container.stop();

		assertEquals(List.of(), this.residues());
	}

	@Test
	public void aServicesManagerNamesTheClientStillThereAndNothingIsLeftAfterStop() throws Exception {
		this.container = (CovalentContainerImpl) Covalent.container();
		this.container.deployComponents(BusinessImpl.class, ClientImpl.class);
		this.container.start();

		List<String> residues = this.residues();

		assertTrue("a services manager should name the client: " + residues, residues.stream()
				.anyMatch(r -> r.contains("client still registered") && r.contains(Client.class.getName())));

		this.container.stop();

		assertEquals(List.of(), this.residues());
	}
}
