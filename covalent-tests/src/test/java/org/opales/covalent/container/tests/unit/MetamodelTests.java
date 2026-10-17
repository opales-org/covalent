/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.tests.CovalentTests;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsSubscribe;
import org.opales.covalent.container.deployment.Deployer;
import org.opales.covalent.container.deployment.impl.DeployerImpl;
import org.opales.covalent.container.metamodel.MetamodelWriteServices;
import org.opales.covalent.container.metamodel.MetamodelEvents;
import org.opales.covalent.container.metamodel.metamodel.ComponentMetamodel;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.simpleproducer.impl.SimpleProducerImpl;

public class MetamodelTests extends CovalentTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	@Before
	public void setUp() {
		this.rcs.registerComponent(this);
		this.cm.getParameters().setSynchronousEvents(true);
	}

	@After
	public void tearDown() {
		this.cm.stop();
	}

	@Test
	public void testMetamodelEvents() throws InterruptedException {
		// Internal (technical) components are only activated by the container's
		// own start() (outside the pre-first-start bootstrap window); subscribing
		// to a technical events interface below needs them activated.
		this.cm.start();

		MetamodelEventsConsumer metamodelEventsConsumer = new MetamodelEventsConsumer();
		this.rcs.subscribe(MetamodelEvents.class, metamodelEventsConsumer);

		MetamodelWriteServices metamodelWriteServices = this.rcs.useServices(MetamodelWriteServices.class);

		CountDownLatch latch = new CountDownLatch(1);
		metamodelEventsConsumer.setLatch(latch);
		this.cm.deployComponents(SimpleProducerImpl.class);
		// The container is already started (see above); activate the
		// component just deployed instead of calling start() again.
		this.cm.lcs().activate();
		Assert.assertTrue("Timed out waiting for the expected events.", latch.await(5, TimeUnit.SECONDS));

		Assert.assertTrue(metamodelEventsConsumer.created);
		Assert.assertEquals(1, metamodelEventsConsumer.createdCount);
		Assert.assertEquals(SimpleProducerImpl.class, metamodelEventsConsumer.createdMetamodel.getImplementation());
		metamodelEventsConsumer.reset();

		// Note: testing updateMetamodel in real situation is quite tricky as this
		// method is used only on very specific cases.
		// The choice I made here is to simply call it manually.
		latch = new CountDownLatch(1);
		metamodelEventsConsumer.setLatch(latch);
		ComponentMetamodel metamodel = metamodelWriteServices.readMetamodel(Deployer.class);
		metamodelWriteServices.updateMetamodel(metamodel);
		Assert.assertTrue("Timed out waiting for the expected events.", latch.await(5, TimeUnit.SECONDS));

		Assert.assertTrue(metamodelEventsConsumer.updated);
		Assert.assertEquals(1, metamodelEventsConsumer.updatedCount);
		Assert.assertEquals(DeployerImpl.class, metamodelEventsConsumer.updatedMetamodel.getImplementation());
		metamodelEventsConsumer.reset();

		latch = new CountDownLatch(1);
		metamodelEventsConsumer.setLatch(latch);
		this.cm.stop();
		Assert.assertTrue("Timed out waiting for the expected events.", latch.await(5, TimeUnit.SECONDS));
		Assert.assertTrue(metamodelEventsConsumer.deleted);
		Assert.assertEquals(1, metamodelEventsConsumer.deletedCount);
		Assert.assertEquals(SimpleProducerImpl.class, metamodelEventsConsumer.deletedMetamodel.getImplementation());

		// This test exercises stop() itself; restart so tearDown()'s own,
		// unconditional stop() below does not run on an already-stopped
		// container.
		this.cm.start();
	}

	class MetamodelEventsConsumer implements MetamodelEvents {
		CountDownLatch latch;

		void setLatch(final CountDownLatch latch) {
			this.latch = latch;
		}

		public void reset() {
			this.created = false;
			this.updated = false;
			this.deleted = false;
			this.createdCount = 0;
			this.updatedCount = 0;
			this.deletedCount = 0;
			this.createdMetamodel = null;
			this.updatedMetamodel = null;
			this.deletedMetamodel = null;
		}

		private boolean isOwnRegistration(final ComponentMetamodel metamodel) {
			return MetamodelTests.class.equals(metamodel.getComponentType());
		}

		@Override
		public void onMetamodelCreated(final ComponentMetamodel metamodel) {
			if (this.isOwnRegistration(metamodel)) {
				return;
			}
			this.created = true;
			this.createdCount++;
			this.createdMetamodel = metamodel;
			this.latch.countDown();
		}

		@Override
		public void onMetamodelUpdated(final ComponentMetamodel metamodel) {
			if (this.isOwnRegistration(metamodel)) {
				return;
			}
			this.updated = true;
			this.updatedCount++;
			this.updatedMetamodel = metamodel;
			if (this.latch != null) {
				this.latch.countDown();
			}
		}

		@Override
		public void onMetamodelDeleted(final ComponentMetamodel metamodel) {
			if (this.isOwnRegistration(metamodel)) {
				return;
			}
			this.deleted = true;
			this.deletedCount++;
			this.deletedMetamodel = metamodel;
			this.latch.countDown();
		}

		boolean created;
		boolean updated;
		boolean deleted;

		int createdCount;
		int updatedCount;
		int deletedCount;

		ComponentMetamodel createdMetamodel;
		ComponentMetamodel updatedMetamodel;
		ComponentMetamodel deletedMetamodel;

	}

}
