/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.Parameters;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.lifecycle.Initialization;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.raw.RawComponentsParameters;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.tests.CovalentContainerizedTests;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.ComponentAServices;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.impl.ComponentA;
import org.opales.covalent.container.tests.unit.rawcomponents.componentb.impl.ComponentB;

public class RawComponentsContractContainerizedTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	private final LifecycleServices lifecycleServices = Covalent.main.lcs();

	private RegistryReadServices registryReadServices;

	@Before
	public void setUp() {
		this.rcs.registerComponent(this);
		this.cm.getParameters().setSynchronousEvents(true);
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
		// Looked up here, not as a field initializer: the base class's own
		// @Before (which runs first) is what starts the container, and this raw
		// lookup needs it already running.
		this.registryReadServices = this.cm.rcs().useServices(RegistryReadServices.class);
	}

	@After
	public void tearDown() {
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.trace);
	}

	@Test
	public void duplicateRawRegistrationFailsWithoutReplacingExistingInstance() {
		// registerComponent rejects a distinct instance under an already-occupied
		// componentType up front (see RawComponentProgressiveAdoptionTests for the full
		// coverage, including the case of asking only for ComponentState.instantiated to
		// be reached), rather than relying on a downstream "Already initialized"/"Already
		// activated" exception from LifecycleManagerImpl - which only fires for
		// stateToReach values past instantiated.
		PlainRawComponent first = new PlainRawComponent();
		PlainRawComponent second = new PlainRawComponent();

		this.rcs.registerComponent(PlainRawComponent.class, first);

		try {
			RuntimeException failure = Assert.assertThrows(RuntimeException.class,
					() -> this.rcs.registerComponent(PlainRawComponent.class, second));
			Assert.assertTrue(this.isExceptionIncludingText(failure, "already registered under this component type"));
			Assert.assertSame(first, this.registryReadServices.getComponentInstance(PlainRawComponent.class));
		} finally {
			this.rcs.unregisterComponent(PlainRawComponent.class);
		}
	}

	@Test
	public void duplicateRawRegistrationFailsWithoutReplacingExistingInstanceAtEveryValidationLevel() {
		for (VerificationLevel verificationLevel : VerificationLevel.values()) {
			PlainRawComponent first = new PlainRawComponent();
			PlainRawComponent second = new PlainRawComponent();
			this.cm.getParameters().setStructuralRulesChecking(verificationLevel);
			this.rcs.registerComponent(PlainRawComponent.class, first);

			try {
				Assert.assertThrows(RuntimeException.class,
						() -> this.rcs.registerComponent(PlainRawComponent.class, second));
				Assert.assertSame(first, this.registryReadServices.getComponentInstance(PlainRawComponent.class));
			} finally {
				this.rcs.unregisterComponent(PlainRawComponent.class);
			}
		}
	}

	@Test
	public void rawComponentCanUseExplicitComponentTypeImplementedByInstance() {
		RawPojo pojo = new RawPojo();

		this.rcs.registerComponent(ExternalRawComponentType.class, pojo, ComponentState.initialized);

		Assert.assertTrue(this.registryReadServices.isRegistered(ExternalRawComponentType.class));
		Assert.assertEquals(ComponentState.initialized,
				this.lifecycleServices.readComponentState(ExternalRawComponentType.class));
		Assert.assertSame(pojo, this.registryReadServices.getComponentInstance(ExternalRawComponentType.class));

		this.rcs.unregisterComponent(ExternalRawComponentType.class);
		Assert.assertFalse(this.registryReadServices.isRegistered(ExternalRawComponentType.class));
	}

	@Test
	public void rawComponentCanBeRegisteredInitializedThenActivatedLater() {
		RawLifecycleComponent component = new RawLifecycleComponent();

		this.rcs.registerComponent(ExternalRawComponentType.class, component, ComponentState.initialized);

		Assert.assertSame(component, this.registryReadServices.getComponentInstance(ExternalRawComponentType.class));
		Assert.assertEquals(ComponentState.initialized,
				this.lifecycleServices.readComponentState(ExternalRawComponentType.class));
		Assert.assertEquals(1, component.initializations);
		Assert.assertEquals(0, component.activations);

		this.rcs.activateComponent(ExternalRawComponentType.class, component);

		Assert.assertSame(component, this.registryReadServices.getComponentInstance(ExternalRawComponentType.class));
		Assert.assertEquals(ComponentState.activated,
				this.lifecycleServices.readComponentState(ExternalRawComponentType.class));
		Assert.assertEquals(1, component.initializations);
		Assert.assertEquals(1, component.activations);

		this.rcs.unregisterComponent(ExternalRawComponentType.class);
		Assert.assertFalse(this.registryReadServices.isRegistered(ExternalRawComponentType.class));
	}

	@Test
	public void rawComponentCanBeRegisteredDirectlyPassivated() {
		RawLifecycleComponent component = new RawLifecycleComponent();

		this.rcs.registerComponent(ExternalRawComponentType.class, component, ComponentState.passivated);

		Assert.assertSame(component, this.registryReadServices.getComponentInstance(ExternalRawComponentType.class));
		Assert.assertEquals(ComponentState.passivated,
				this.lifecycleServices.readComponentState(ExternalRawComponentType.class));
		Assert.assertEquals(1, component.initializations);
		Assert.assertEquals(1, component.activations);
		Assert.assertEquals(1, component.passivations);

		this.rcs.unregisterComponent(ExternalRawComponentType.class);
	}

	@Test
	public void rawRegistrationRejectsUnreachableLifecycleStates() {
		PlainRawComponent component = new PlainRawComponent();

		RuntimeException deployedFailure = Assert.assertThrows(RuntimeException.class,
				() -> this.rcs.registerComponent(PlainRawComponent.class, component, ComponentState.deployed));
		Assert.assertTrue(this.isExceptionIncludingText(deployedFailure, "not allowed as state to reach"));

		RuntimeException removedFailure = Assert.assertThrows(RuntimeException.class,
				() -> this.rcs.registerComponent(PlainRawComponent.class, component, ComponentState.removed));
		Assert.assertTrue(this.isExceptionIncludingText(removedFailure, "not allowed as state to reach"));
	}

	@Test
	public void activateRawComponentRegistersItWhenNeeded() {
		RawLifecycleComponent component = new RawLifecycleComponent();

		this.rcs.activateComponent(ExternalRawComponentType.class, component);

		Assert.assertSame(component, this.registryReadServices.getComponentInstance(ExternalRawComponentType.class));
		Assert.assertEquals(ComponentState.activated,
				this.lifecycleServices.readComponentState(ExternalRawComponentType.class));
		Assert.assertEquals(1, component.initializations);
		Assert.assertEquals(1, component.activations);

		this.rcs.unregisterComponent(ExternalRawComponentType.class);
	}

	@Test
	public void unregisterRawComponentRemovesServicesAndProducedEvents() {
		ComponentA producer = new ComponentA(true);
		ComponentB consumer = new ComponentB();

		this.rcs.registerComponent(producer);
		this.rcs.registerComponent(consumer);
		consumer.consumeEventA(true);

		ComponentAServices services = this.rcs.useServices(ComponentA.class, ComponentAServices.class);
		services.doSomething();
		Assert.assertTrue(producer.doSomething);
		Assert.assertEquals(1, consumer.onCreate);

		this.rcs.unregisterComponent(ComponentA.class);

		Assert.assertFalse(this.registryReadServices.isRegistered(ComponentA.class));
		IllegalStateException e = Assert.assertThrows("Raw services should be unavailable after unregistering the provider.",
				IllegalStateException.class,
				() -> services.doSomething());
		Assert.assertFalse(this.isInvalidatedException(e));
		Assert.assertTrue(e.getMessage(), e.getMessage().contains("No provider is currently declared"));

		IllegalStateException laterFailure = Assert.assertThrows("Produced events proxy should be invalidated after unregistering the producer.",
				IllegalStateException.class,
				() -> producer.getProduceComponentAEvents().onCreate("after unregister"));
		Assert.assertTrue(this.isInvalidatedException(laterFailure));
		Assert.assertEquals(1, consumer.onCreate);

		this.rcs.unregisterComponent(ComponentB.class);
	}

	@Test
	public void rawDefaultStateToReachIsAppliedToRegistration() {
		RawComponentsParameters parameters = this.rcs.accessParameters(RawComponentsParameters.class);
		ComponentState originalState = parameters.getDefaultStateToReach();
		parameters.setDefaultStateToReach(ComponentState.initialized);

		try {
			PlainRawComponent pojo = new PlainRawComponent();
			this.rcs.registerComponent(pojo);
			Assert.assertEquals(ComponentState.initialized,
					this.lifecycleServices.readComponentState(PlainRawComponent.class));
			this.rcs.unregisterComponent(PlainRawComponent.class);
		} finally {
			parameters.setDefaultStateToReach(originalState);
		}
	}

	@Test
	public void rawComponentWithComponentItemIsRejected() {
		RuntimeException failure = Assert.assertThrows(RuntimeException.class,
				() -> this.rcs.registerComponent(RawComponentWithItem.class, new RawComponentWithItem()));

		this.assertRawComponentContractFailure(failure);
		Assert.assertFalse(this.registryReadServices.isRegistered(RawComponentWithItem.class));
	}

	@Test
	public void rawComponentWithComposedComponentIsRejected() {
		RuntimeException failure = Assert.assertThrows(RuntimeException.class,
				() -> this.rcs.registerComponent(RawComponentWithComposedComponent.class,
						new RawComponentWithComposedComponent()));

		this.assertRawComponentContractFailure(failure);
		Assert.assertFalse(this.registryReadServices.isRegistered(RawComponentWithComposedComponent.class));
	}

	@Test
	public void rawRegistrationRejectsManagedImplementation() {
		RuntimeException failure = Assert.assertThrows(RuntimeException.class,
				() -> this.rcs.registerComponent(RawNestedComponentImpl.class, new RawNestedComponentImpl()));

		this.assertRawComponentContractFailure(failure);
		Assert.assertFalse(this.registryReadServices.isRegistered(RawNestedComponentImpl.class));
	}

	@Test
	public void rawRegistrationRejectsComponentImplementingManagedServicesPort() {
		RuntimeException failure = Assert.assertThrows(RuntimeException.class,
				() -> this.rcs.registerComponent(RawManagedServicesComponent.class,
						new RawManagedServicesComponent()));

		this.assertRawComponentContractFailure(failure);
		Assert.assertFalse(this.registryReadServices.isRegistered(RawManagedServicesComponent.class));
	}

	@Test
	public void rawRegistrationRejectsComponentImplementingManagedParametersPort() {
		RuntimeException failure = Assert.assertThrows(RuntimeException.class,
				() -> this.rcs.registerComponent(RawManagedParametersComponent.class,
						new RawManagedParametersComponent()));

		this.assertRawComponentContractFailure(failure);
		Assert.assertFalse(this.registryReadServices.isRegistered(RawManagedParametersComponent.class));
	}

	private void assertRawComponentContractFailure(final RuntimeException failure) {
		Assert.assertTrue(this.isExceptionIncludingText(failure, "fully managed by the container"));
		Assert.assertTrue(this.isExceptionIncludingText(failure, "RawComponentsServices"));
		Assert.assertTrue(this.isExceptionIncludingText(failure, "non-Covalent world"));
	}

	public interface ExternalRawComponentType {
	}

	public static class RawPojo implements ExternalRawComponentType {
	}

	public static class PlainRawComponent {
	}

	public static class RawLifecycleComponent implements ExternalRawComponentType {
		private int initializations;

		private int activations;

		@Initialization
		public void initialize() {
			this.initializations++;
		}

		@Activation
		public void activate() {
			this.activations++;
		}

		@Passivation
		public void passivate() {
			this.passivations++;
		}

		private int passivations;
	}

	public static class RawComponentWithItem {
		@Item
		private final RawComponentItem item = new RawComponentItem();
	}

	@Item
	public static class RawComponentItem {
	}

	public static class RawComponentWithComposedComponent {
		@Implementation
		private final RawNestedComponentImpl nestedComponent = new RawNestedComponentImpl();
	}

	@Component
	public interface RawNestedComponent {
	}

	@Implementation
	public static class RawNestedComponentImpl implements RawNestedComponent {
	}

	@Services
	public interface RawManagedServices {
		void call();
	}

	public static class RawManagedServicesComponent implements RawManagedServices {
		@Override
		public void call() {
		}
	}

	@Parameters
	public interface RawManagedParameters {
		String getValue();
	}

	public static class RawManagedParametersComponent implements RawManagedParameters {
		@Override
		public String getValue() {
			return "managed";
		}
	}

}
