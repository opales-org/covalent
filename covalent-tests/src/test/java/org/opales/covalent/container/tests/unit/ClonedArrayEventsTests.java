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
import org.opales.covalent.container.component.annotations.Cloned;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.Services;

/**
 * An array given to an events method annotated with {@code @Cloned} is
 * cloned before delivery: a consumer that modifies the array it receives
 * leaves the producer's array untouched.
 */
public class ClonedArrayEventsTests {

	@Events
	@Cloned
	public interface ArrayEvents {
		void onPrimitiveArray(int[] data);

		void onObjectArray(String[] data);
	}

	@Services
	public interface ArrayProducerServices {
		void firePrimitiveArray(int[] data);

		void fireObjectArray(String[] data);
	}

	@Component
	public interface ArrayProducer {
	}

	@Implementation
	public static class ArrayProducerImpl implements ArrayProducer, ArrayProducerServices {

		@ProducedEvents
		private ArrayEvents produceArrayEvents;

		@Override
		public void firePrimitiveArray(final int[] data) {
			this.produceArrayEvents.onPrimitiveArray(data);
		}

		@Override
		public void fireObjectArray(final String[] data) {
			this.produceArrayEvents.onObjectArray(data);
		}
	}

	public static class MutatingListener implements ArrayEvents {

		int received;

		@Override
		public void onPrimitiveArray(final int[] data) {
			data[0] = 99;
			this.received++;
		}

		@Override
		public void onObjectArray(final String[] data) {
			data[0] = "mutated";
			this.received++;
		}
	}

	private CovalentContainer container;

	private MutatingListener listener;

	private ArrayProducerServices producer;

	@Before
	public void setUp() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		this.container.deployComponents(ArrayProducerImpl.class);
		this.container.rcs().registerComponent(this);
		this.listener = new MutatingListener();
		this.container.rcs().registerComponent(this.listener);
		this.container.start();
		this.container.rcs().subscribe(MutatingListener.class, ArrayEvents.class, this.listener);
		this.producer = this.container.rcs().useServices(ArrayProducer.class, ArrayProducerServices.class);
	}

	@After
	public void tearDown() {
		this.container.dispose();
	}

	@Test
	public void aPrimitiveArrayIsClonedBeforeDelivery() {
		int[] data = { 1, 2, 3 };

		this.producer.firePrimitiveArray(data);

		Assert.assertEquals(1, this.listener.received);
		Assert.assertArrayEquals(new int[] { 1, 2, 3 }, data);
	}

	@Test
	public void anObjectArrayIsClonedBeforeDelivery() {
		String[] data = { "a", "b" };

		this.producer.fireObjectArray(data);

		Assert.assertEquals(1, this.listener.received);
		Assert.assertArrayEquals(new String[] { "a", "b" }, data);
	}
}
