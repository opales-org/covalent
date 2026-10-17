/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.exceptions.ComponentLifecycleException;
import org.opales.covalent.container.tests.CovalentContainerizedTests;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.deployment.DeploymentEvents;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.metamodel.metamodel.ComponentItemMetamodel;
import org.opales.covalent.container.metamodel.metamodel.ComponentMetamodel;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.componentformetamodeltest.ComponentForMetamodelTest;
import org.opales.covalent.container.tests.unit.annotatedcomponents.componentformetamodeltest.ComponentForMetamodelTestEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.componentformetamodeltest.ComponentForMetamodelTestEvents.ComponentForMetamodelTestEventsSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.componentformetamodeltest.ComponentForMetamodelTestParameters;
import org.opales.covalent.container.tests.unit.annotatedcomponents.componentformetamodeltest.ComponentForMetamodelTestServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.componentformetamodeltest.impl.ComponentForMetamodelTestImpl;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumercomponent.ConsumerComponent;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumercomponent.ConsumerComponentServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.consumercomponent.impl.ConsumerComponentImpl;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.FullyConnectedParameters;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.simpleproducer.SimpleProducer;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.simpleproducer.impl.SimpleProducerImpl;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent.ProducerSomeEvents.ProducerSomeEventsSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producercomponent.SomeEventsWithIdEvents.SomeEventsWithIdEventsSubscribe;
import org.opales.covalent.container.tests.unit.rawcomponents.pojocomponent.PojoComponent;

public class DeploymentContainerizedTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	private LifecycleServices lifecycleServices;

	@Before
	public void setUp() {
		this.rcs.registerComponent(this);
		// Looked up here, not as a field initializer: the base class's own
		// @Before (which runs first) is what starts the container, and this raw
		// lookup needs it already running.
		this.lifecycleServices = this.cm.rcs().useServices(LifecycleServices.class);
	}

	@Test
	public void duplicateDeploymentFailsWithoutUndeployingExistingComponent() {
		this.lifecycleServices.deployComponent(SimpleProducerImpl.class);
		Assert.assertTrue(this.lifecycleServices.isDeployed(SimpleProducer.class));

		RuntimeException exception = Assert.assertThrows(RuntimeException.class,
				() -> this.lifecycleServices.deployComponent(SimpleProducerImpl.class));
		Assert.assertTrue(exception.toString(), exception instanceof ComponentLifecycleException);
		Assert.assertTrue(exception.getMessage().contains("During deployment of SimpleProducerImpl"));

		Assert.assertTrue(this.lifecycleServices.isDeployed(SimpleProducer.class));
	}

	@Test
	public void deploySimpleComponent() {
		ComponentMetamodel metamodel = this.lifecycleServices.deployComponent(PojoComponent.class);
		Assert.assertTrue(metamodel.getImplementation().equals(PojoComponent.class));
		Assert.assertTrue(metamodel.getComponentType().equals(PojoComponent.class));
		Assert.assertEquals(0, metamodel.getProvidedServices().size());
		Assert.assertEquals(0, metamodel.getProducedEvents().size());
		Assert.assertEquals(0, metamodel.getProvidedSubscribers().size());
		Assert.assertEquals(0, metamodel.getUsedSubscribers().size());
		Assert.assertEquals(0, metamodel.getUsedServices().size());
	}

	@Test
	public void deployAnnotatedMonolithicComponent() {
		ComponentMetamodel metamodel = this.lifecycleServices.deployComponent(ComponentForMetamodelTestImpl.class);
		Class<?> componentType = metamodel.getComponentType();
		Assert.assertTrue(ComponentForMetamodelTest.class.equals(componentType));

		Class<?> componentImpl = metamodel.getImplementation();
		Assert.assertTrue(ComponentForMetamodelTestImpl.class.equals(componentImpl));

		List<Class<?>> providedServices = metamodel.getProvidedServices();
		Assert.assertEquals(1, providedServices.size());
		Assert.assertTrue(providedServices.iterator().next().equals(ComponentForMetamodelTestServices.class));

		List<Class<?>> producedEvents = metamodel.getProducedEvents();
		Assert.assertEquals(1, producedEvents.size());
		Assert.assertTrue(producedEvents.iterator().next().equals(ComponentForMetamodelTestEvents.class));

		List<Class<?>> providedEventsSubscribes = metamodel.getProvidedSubscribers();
		Assert.assertEquals(1, providedEventsSubscribes.size());
		Assert.assertTrue(
				providedEventsSubscribes.iterator().next().equals(ComponentForMetamodelTestEventsSubscribe.class));

		List<Class<?>> usedEventsSubscribed = metamodel.getUsedSubscribers();
		Assert.assertEquals(1, usedEventsSubscribed.size());
		Assert.assertTrue(usedEventsSubscribed.iterator().next().equals(ProducerSomeEventsSubscribe.class));

		List<Class<?>> usedServices = metamodel.getUsedServices();
		Assert.assertEquals(1, usedServices.size());
		Assert.assertTrue(usedServices.iterator().next().equals(ConsumerComponentServices.class));

		List<Class<?>> accessedParameters = metamodel.getAccessedParameters();
		Assert.assertEquals(1, accessedParameters.size());
		Assert.assertTrue(accessedParameters.iterator().next().equals(FullyConnectedParameters.class));

		List<Class<?>> offeredParameters = metamodel.getOfferedParameters();
		Assert.assertEquals(1, offeredParameters.size());
		Assert.assertTrue(offeredParameters.iterator().next().equals(ComponentForMetamodelTestParameters.class));

	}

	@Test
	public void deployAnnotatedSegmentedComponent() {
		ComponentMetamodel metamodel = this.lifecycleServices.deployComponent(ConsumerComponentImpl.class);
		Assert.assertTrue(metamodel.getComponentType().equals(ConsumerComponent.class));
		Assert.assertTrue(metamodel.getImplementation().equals(ConsumerComponentImpl.class));
		Assert.assertEquals(2, metamodel.getItemMetamodels().size());
		Assert.assertEquals(1, metamodel.getUsedSubscribers().size());
		Assert.assertEquals(ProducerSomeEventsSubscribe.class, metamodel.getUsedSubscribers().iterator().next());
		Assert.assertEquals(0, metamodel.getProducedEvents().size());

		// The two items use SomeEventsWithIdEventsSubscribe; one of them also uses
		// ProducerSomeEventsSubscribe. No order among the items is assumed.
		List<Integer> usedSubscribersCounts = new ArrayList<>();
		for (ComponentItemMetamodel itemMetamodel : metamodel.getItemMetamodels()) {
			Assert.assertTrue(itemMetamodel.getUsedSubscribers().contains(SomeEventsWithIdEventsSubscribe.class));
			usedSubscribersCounts.add(itemMetamodel.getUsedSubscribers().size());
		}
		Collections.sort(usedSubscribersCounts);
		Assert.assertEquals(List.of(1, 2), usedSubscribersCounts);
	}

	@Test
	public void deploymentAndUndeploymentAreNotified() throws InterruptedException {
		DeploymentEventsConsumer deploymentEventsConsumer = new DeploymentEventsConsumer();
		this.rcs.subscribe(DeploymentEvents.class, deploymentEventsConsumer);
		LifecycleServices lifecycleServices = Covalent.main.rcs().useServices(LifecycleServices.class);

		CountDownLatch latch = new CountDownLatch(1);
		deploymentEventsConsumer.setLatch(latch);
		this.cm.deployComponents(SimpleProducerImpl.class);
		Assert.assertTrue("No deployment event received.", latch.await(5, TimeUnit.SECONDS));

		Assert.assertTrue(deploymentEventsConsumer.componentDeployed);
		Assert.assertEquals(SimpleProducer.class, deploymentEventsConsumer.deployedComponentType);

		latch = new CountDownLatch(1);
		deploymentEventsConsumer.setLatch(latch);
		lifecycleServices.undeployComponent(SimpleProducerImpl.class);
		Assert.assertTrue("No undeployment event received.", latch.await(5, TimeUnit.SECONDS));
		Assert.assertTrue(deploymentEventsConsumer.componentUndeployed);
		Assert.assertEquals(SimpleProducer.class, deploymentEventsConsumer.undeployedComponentType);
	}

	class DeploymentEventsConsumer implements DeploymentEvents {
		CountDownLatch latch;

		void setLatch(final CountDownLatch latch) {
			this.latch = latch;
		}

		@Override
		public void onComponentDeployed(final Class<?> componentType, final Class<?> componentImpl) {
			this.deployedComponentType = componentType;
			this.componentDeployed = true;
			this.latch.countDown();
		}

		@Override
		public void onComponentUndeployed(final Class<?> componentType, final Class<?> componentImpl) {
			this.undeployedComponentType = componentType;
			this.componentUndeployed = true;
			this.latch.countDown();
		}

		boolean componentDeployed;
		boolean componentUndeployed;

		Class<?> deployedComponentType;
		Class<?> undeployedComponentType;
	}

}
