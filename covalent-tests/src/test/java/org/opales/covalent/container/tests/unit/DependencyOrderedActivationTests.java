/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.ComposedComponent;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;

/**
 * Dependency-ordered activation: when enabled through
 * {@code CovalentContainerParameters#setDependencyOrderedActivation(boolean)},
 * a provider is activated before the components that use its services,
 * whatever the deployment order; otherwise, and whenever the computed order
 * cannot be trusted (circular dependency), deployment order is kept.
 */
public class DependencyOrderedActivationTests {

	/** Names of the components, in the order their {@code @Activation} callback ran. */
	private static final List<String> ACTIVATIONS = Collections.synchronizedList(new ArrayList<String>());

	private CovalentContainer container;

	@Before
	public void setUp() {
		ACTIVATIONS.clear();
		this.container = Covalent.container();
	}

	@After
	public void tearDown() {
		if (this.container != null) {
			this.container.dispose();
		}
	}

	@Test
	public void dependencyOrderedActivationDefaultsToDisabledAndRoundTrips() {
		Assert.assertFalse(this.container.getParameters().isDependencyOrderedActivation());

		this.container.getParameters().setDependencyOrderedActivation(true);
		Assert.assertTrue(this.container.getParameters().isDependencyOrderedActivation());

		this.container.getParameters().setDependencyOrderedActivation(false);
		Assert.assertFalse(this.container.getParameters().isDependencyOrderedActivation());
	}

	@Test
	public void disabledKeepsDeploymentOrder() {
		this.container.deployComponents(ClientImpl.class, ProviderImpl.class);

		this.container.start();

		Assert.assertEquals(Arrays.asList("client", "provider"), ACTIVATIONS);
	}

	@Test
	public void enabledActivatesTheProviderBeforeItsClient() {
		this.container.getParameters().setDependencyOrderedActivation(true);
		this.container.deployComponents(ClientImpl.class, ProviderImpl.class);

		this.container.start();

		Assert.assertEquals(Arrays.asList("provider", "client"), ACTIVATIONS);
	}

	@Test
	public void enabledKeepsAnAlreadyCorrectDeploymentOrder() {
		this.container.getParameters().setDependencyOrderedActivation(true);
		this.container.deployComponents(ProviderImpl.class, ClientImpl.class);

		this.container.start();

		Assert.assertEquals(Arrays.asList("provider", "client"), ACTIVATIONS);
	}

	@Test
	public void enabledWithoutAnyDeployedComponentStartsNormally() {
		this.container.getParameters().setDependencyOrderedActivation(true);

		this.container.start();

		Assert.assertTrue(this.container.isStarted());
		Assert.assertTrue(ACTIVATIONS.isEmpty());
	}

	@Test
	public void enabledReordersTheChildrenOfAComposite() {
		this.container.getParameters().setDependencyOrderedActivation(true);
		this.container.deployComponents(CompositeImpl.class);

		this.container.start();

		Assert.assertEquals(Arrays.asList("childProvider", "childClient", "composite"), ACTIVATIONS);
	}

	@Test
	public void disabledKeepsTheDeclarationOrderOfCompositeChildren() {
		this.container.deployComponents(CompositeImpl.class);

		this.container.start();

		Assert.assertEquals(Arrays.asList("childClient", "childProvider", "composite"), ACTIVATIONS);
	}

	@Test
	public void aCircularDependencyKeepsTheDeploymentOrder() {
		this.container.getParameters().setDependencyOrderedActivation(true);
		this.container.deployComponents(CycleAImpl.class, CycleBImpl.class);

		this.container.start();

		Assert.assertEquals(Arrays.asList("cycleA", "cycleB"), ACTIVATIONS);
	}

	// --- Fixtures ---

	@Component
	public interface Provider {
	}

	@Component
	public interface Client {
	}

	@Services
	public interface ProviderServices {
		void ping();
	}

	@Implementation
	public static class ProviderImpl implements Provider, ProviderServices {
		@Override
		public void ping() {
			// no-op
		}

		@Activation
		public void activate() {
			ACTIVATIONS.add("provider");
		}
	}

	@Implementation
	public static class ClientImpl implements Client {
		@UsedServices
		private ProviderServices providerServices;

		@Activation
		public void activate() {
			ACTIVATIONS.add("client");
		}
	}

	@Component
	public interface Composite {
	}

	@Component
	public interface ChildProvider {
	}

	@Component
	public interface ChildClient {
	}

	@Services
	public interface ChildProviderServices {
		void ping();
	}

	@Implementation
	public static class CompositeImpl implements Composite {
		@ComposedComponent
		private final ChildClientImpl client = null;
		@ComposedComponent
		private final ChildProviderImpl provider = null;

		@Activation
		public void activate() {
			ACTIVATIONS.add("composite");
		}
	}

	@Implementation
	public static class ChildProviderImpl implements ChildProvider, ChildProviderServices {
		@Override
		public void ping() {
			// no-op
		}

		@Activation
		public void activate() {
			ACTIVATIONS.add("childProvider");
		}
	}

	@Implementation
	public static class ChildClientImpl implements ChildClient {
		@UsedServices
		private ChildProviderServices providerServices;

		@Activation
		public void activate() {
			ACTIVATIONS.add("childClient");
		}
	}

	@Component
	public interface CycleA {
	}

	@Component
	public interface CycleB {
	}

	@Services
	public interface CycleAServices {
		void ping();
	}

	@Services
	public interface CycleBServices {
		void ping();
	}

	@Implementation
	public static class CycleAImpl implements CycleA, CycleAServices {
		@UsedServices
		private CycleBServices other;

		@Override
		public void ping() {
			// no-op
		}

		@Activation
		public void activate() {
			ACTIVATIONS.add("cycleA");
		}
	}

	@Implementation
	public static class CycleBImpl implements CycleB, CycleBServices {
		@UsedServices
		private CycleAServices other;

		@Override
		public void ping() {
			// no-op
		}

		@Activation
		public void activate() {
			ACTIVATIONS.add("cycleB");
		}
	}
}
