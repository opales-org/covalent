/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.containermanager.interaction.impl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.supervision.SupervisionExtension;
import org.opales.covalent.supervision.containermanager.raw.ObservedContainerBridge;
import org.opales.covalent.supervision.observed.ObservedContainerConnectionEvents;
import org.opales.covalent.supervision.observed.ObservedRegistryReadServices;
import org.opales.covalent.supervision.testsupport.observed.FlatParametersOfferorComponent;
import org.opales.covalent.supervision.testsupport.observed.impl.FlatParametersOfferorComponentImpl;
import org.opales.covalent.supervision.testsupport.observed.impl.StubContainerManagerViewComponentImpl;

/**
 * The supervision follows the observed managed container across its stops,
 * restarts and disposal: disconnected as soon as it is stopping or being
 * disposed, the views emptied and the view told why; connected again, under
 * the same name, once it is started or created anew.
 */
public class ContainerManagerFollowTests {

	private static final String OBSERVED = "container-manager-follow-tests";

	private static final long TIMEOUT_MILLIS = 5000;

	private CovalentContainer supervision;

	private ObservedRegistryReadServices observedRegistry;

	private final List<String> connections = new CopyOnWriteArrayList<>();

	@Before
	public void setUp() {
		StubContainerManagerViewComponentImpl.reports.clear();
		System.setProperty(SupervisionExtension.DEFAULT_CONTAINER_PROPERTY, OBSERVED);
		CovalentContainer observed = Covalent.managedContainer(OBSERVED);
		observed.deployComponents(FlatParametersOfferorComponentImpl.class);
		observed.start();

		this.supervision = Covalent.container();
		this.supervision.getParameters().setSynchronousEvents(true);
		RawComponentsServices rcs = this.supervision.rcs();
		rcs.registerComponent(ContainerManagerFollowTests.class, this);
		rcs.subscribe(ContainerManagerFollowTests.class, ObservedContainerConnectionEvents.class,
				new ObservedContainerConnectionEvents() {

					@Override
					public void onContainerConnected() {
						ContainerManagerFollowTests.this.connections.add("connected");
					}

					@Override
					public void onContainerDisconnected() {
						ContainerManagerFollowTests.this.connections.add("disconnected");
					}
				});
		this.supervision.deployComponents(ObservedContainerBridge.class, StubContainerManagerViewComponentImpl.class,
				ContainerManagerInteractionImpl.class);
		this.supervision.start();
		this.observedRegistry = rcs.useServices(ObservedRegistryReadServices.class);
	}

	@After
	public void tearDown() {
		System.clearProperty(SupervisionExtension.DEFAULT_CONTAINER_PROPERTY);
		this.supervision.dispose();
		Covalent.disposeManagedContainer(OBSERVED);
	}

	@Test
	public void aStoppedThenRestartedContainerIsFollowed() {
		Assert.assertEquals(List.of("succeeded " + OBSERVED), StubContainerManagerViewComponentImpl.reports);
		Assert.assertTrue(this.observes(FlatParametersOfferorComponent.class));

		Covalent.getManagedContainer(OBSERVED).stop();

		Assert.assertEquals(List.of("succeeded " + OBSERVED, "stopped " + OBSERVED),
				StubContainerManagerViewComponentImpl.reports);
		Assert.assertEquals(List.of("connected", "disconnected"), this.connections);
		Assert.assertTrue(this.observedRegistry.getInstances().isEmpty());

		CovalentContainer observed = Covalent.getManagedContainer(OBSERVED);
		observed.deployComponents(FlatParametersOfferorComponentImpl.class);
		observed.start();

		this.await(() -> StubContainerManagerViewComponentImpl.reports.size() == 3);
		Assert.assertEquals(List.of("connected", "disconnected", "connected"), this.connections);
		Assert.assertEquals(List.of("succeeded " + OBSERVED, "stopped " + OBSERVED, "succeeded " + OBSERVED),
				StubContainerManagerViewComponentImpl.reports);
		Assert.assertTrue(this.observes(FlatParametersOfferorComponent.class));
	}

	@Test
	public void aDisposedThenRecreatedContainerIsFollowed() {
		Covalent.disposeManagedContainer(OBSERVED);

		Assert.assertEquals(List.of("succeeded " + OBSERVED, "disposed " + OBSERVED),
				StubContainerManagerViewComponentImpl.reports);
		Assert.assertEquals(List.of("connected", "disconnected"), this.connections);

		CovalentContainer recreated = Covalent.managedContainer(OBSERVED);

		this.await(() -> StubContainerManagerViewComponentImpl.reports.size() == 3);
		Assert.assertEquals(List.of("succeeded " + OBSERVED, "disposed " + OBSERVED, "succeeded " + OBSERVED),
				StubContainerManagerViewComponentImpl.reports);
		recreated.deployComponents(FlatParametersOfferorComponentImpl.class);
		recreated.start();
		Assert.assertTrue(this.observes(FlatParametersOfferorComponent.class));
	}

	private boolean observes(final Class<?> componentType) {
		return this.observedRegistry.getInstances().containsKey(componentType);
	}

	private void await(final BooleanSupplier condition) {
		long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
		while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}
}
