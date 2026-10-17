/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.CovalentContainerizedTests;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.ComponentAEvents;
import org.opales.covalent.container.tests.unit.RawComponentsContractTests.RawSettings;
import org.opales.covalent.container.tests.unit.RawComponentsContractTests.RawSettingsComponent;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.ComponentAServices;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.impl.ComponentA;

public class PortProxyInvalidationContainerizedTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	private final LifecycleServices lifecycleServices = Covalent.main.lcs();

	@Before
	public void setUp() {
		this.rcs.registerComponent(this);
		this.cm.getParameters().setSynchronousEvents(true);
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
	}

	@After
	public void tearDown() {
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.trace);

	}

	@Test
	public void serviceProxyFailsWhileProviderIsAbsentThenReachesTheReplacement() {
		ComponentA firstProvider = new ComponentA();
		this.rcs.registerComponent(firstProvider);

		ComponentAServices services = this.rcs.useServices(ComponentA.class, ComponentAServices.class);
		services.doSomething();
		Assert.assertTrue(firstProvider.doSomething);

		this.rcs.unregisterComponent(ComponentA.class);

		IllegalStateException e = Assert.assertThrows("The service must fail while no provider is declared.",
				IllegalStateException.class,
				() -> services.doSomething());
		Assert.assertTrue(e.getMessage(), e.getMessage().contains("No provider is currently declared"));
		Assert.assertFalse(this.isInvalidatedException(e));

		firstProvider.doSomething = false;
		ComponentA secondProvider = new ComponentA();
		this.rcs.registerComponent(secondProvider);

		services.doSomething();
		Assert.assertTrue("The held proxy must reach the replacement provider.", secondProvider.doSomething);
		Assert.assertFalse("The removed provider must not be called again.", firstProvider.doSomething);
	}

	@Test
	public void parameterProxyFailsWhileOfferorIsAbsentThenReachesTheReplacement() {
		RawSettingsComponent firstOfferor = new RawSettingsComponent();
		this.rcs.registerComponent(firstOfferor);

		RawSettings parameters = this.rcs.accessParameters(RawSettingsComponent.class, RawSettings.class);
		parameters.setValue("first");
		Assert.assertEquals("first", firstOfferor.getValue());

		this.rcs.unregisterComponent(RawSettingsComponent.class);

		IllegalStateException e = Assert.assertThrows("The parameters must fail while no offeror is declared.",
				IllegalStateException.class,
				() -> parameters.setValue("lost"));
		Assert.assertTrue(e.getMessage(), e.getMessage().contains("No provider is currently declared"));
		Assert.assertFalse(this.isInvalidatedException(e));

		RawSettingsComponent secondOfferor = new RawSettingsComponent();
		this.rcs.registerComponent(secondOfferor);

		parameters.setValue("second");
		Assert.assertEquals("second", secondOfferor.getValue());
		Assert.assertEquals("first", firstOfferor.getValue());
	}

	@Test
	public void serviceProxyFailsWhileProviderIsPassivatedAndWorksAgainAfterReactivation() {
		ComponentA provider = new ComponentA();
		this.rcs.registerComponent(provider);

		ComponentAServices services = this.rcs.useServices(ComponentA.class, ComponentAServices.class);
		this.lifecycleServices.passivateComponent(ComponentA.class);

		IllegalStateException e = Assert.assertThrows("Services should fail while the provider is passivated.",
				IllegalStateException.class,
				() -> services.doSomething());
		Assert.assertFalse("The usage link must survive the passivation.", this.isInvalidatedException(e));

		this.lifecycleServices.activateComponent(ComponentA.class);
		services.doSomething();
		Assert.assertTrue("The held proxy must work again after reactivation.", provider.doSomething);
	}

	@Test
	public void serviceProxyOfARemovedUserIsInvalidated() {
		ComponentA provider = new ComponentA();
		this.rcs.registerComponent(provider);

		ComponentAServices services = this.rcs.useServices(ComponentA.class, ComponentAServices.class);
		this.rcs.unuseServices(ComponentAServices.class);

		IllegalStateException e = Assert.assertThrows("A proxy whose usage was released must be invalidated.",
				IllegalStateException.class,
				() -> services.doSomething());
		Assert.assertTrue(this.isInvalidatedException(e));
	}

	@Test
	public void eventConsumerRemainsSubscribedAfterProducerPassivationAndReactivation() {
		ComponentA producer = new ComponentA(true);
		RecordingComponentAEvents consumer = new RecordingComponentAEvents();
		this.rcs.registerComponent(producer);
		this.rcs.subscribe(PortProxyInvalidationContainerizedTests.class, ComponentAEvents.class, consumer);

		producer.getProduceComponentAEvents().onUpdate("before passivation");
		Assert.assertEquals(List.of("create:init", "update:before passivation"), consumer.events);

		ComponentAEvents staleProducerProxy = producer.getProduceComponentAEvents();
		this.lifecycleServices.passivateComponent(ComponentA.class);
		IllegalStateException e = Assert.assertThrows("Old producer proxy should be stale after producer passivation.",
				IllegalStateException.class,
				() -> staleProducerProxy.onUpdate("while passivated"));
		Assert.assertTrue(this.isInvalidatedException(e) || this.isPassivatedException(e));

		this.lifecycleServices.activateComponent(ComponentA.class);
		producer.getProduceComponentAEvents().onUpdate("after reactivation");

		Assert.assertEquals(List.of("create:init", "update:before passivation", "create:init",
				"update:after reactivation"), consumer.events);
	}

	public static class RecordingComponentAEvents implements ComponentAEvents {
		final List<String> events = new ArrayList<>();

		@Override
		public void onCreate(final String data) {
			this.events.add("create:" + data);
		}

		@Override
		public void onUpdate(final String data) {
			this.events.add("update:" + data);
		}

		@Override
		public void onDelete(final String data) {
			this.events.add("delete:" + data);
		}

		@Override
		public void onEmpty() {
			this.events.add("empty");
		}
	}
}
