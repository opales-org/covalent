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
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsFilter;
import org.opales.covalent.container.component.annotations.EventsSubscribe;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.tests.unit.SubscribeAmbiguousMethodTests.AmbiguousEvents.AmbiguousSubscribe;
import org.opales.covalent.container.tests.unit.SubscribeAmbiguousMethodTests.AmbiguousEvents.FilterA;
import org.opales.covalent.container.tests.unit.SubscribeAmbiguousMethodTests.AmbiguousEvents.FilterB;

/**
 * A subscribe call whose arguments fit several subscribe methods of the subscribe
 * interface (a {@code null} filter fits every overload taking a filter) fails with
 * an {@link IllegalStateException} that names the call's argument types, instead of
 * being mistaken for a call that fits no method.
 */
public class SubscribeAmbiguousMethodTests {

	private CovalentContainer container;

	@Before
	public void setUp() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
	}

	@After
	public void tearDown() {
		this.container.dispose();
	}

	@Test
	public void aNullFilterFittingTwoSubscribeMethodsIsRejectedAsAmbiguous() {
		this.container.deployComponents(AmbiguousProducerImpl.class, AmbiguousConsumerImpl.class);
		this.container.rcs().registerComponent(this);
		this.container.start();
		AmbiguousConsumerImpl consumer = this.container.rcs().useServices(RegistryReadServices.class)
				.getComponentInstance(AmbiguousConsumer.class);

		IllegalStateException failure = Assert.assertThrows(IllegalStateException.class,
				consumer::subscribeWithNullFilter);

		Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("More than one"));
	}

	@Events
	public interface AmbiguousEvents {
		void onEvent(String message);

		@EventsFilter
		interface FilterA {
			boolean filterEvent(String message);
		}

		@EventsFilter
		interface FilterB {
			boolean filterEvent(String message);
		}

		@EventsSubscribe
		interface AmbiguousSubscribe {

			void subscribe(AmbiguousEvents observer, FilterA filter);

			void subscribe(AmbiguousEvents observer, FilterB filter);

			void unsubscribe(AmbiguousEvents observer);
		}
	}

	@Component
	public interface AmbiguousProducer {
	}

	@Implementation
	public static class AmbiguousProducerImpl implements AmbiguousProducer, AmbiguousSubscribe {

		@ProducedEvents
		private AmbiguousEvents produced;

		@Override
		public void subscribe(final AmbiguousEvents observer, final FilterA filter) {
		}

		@Override
		public void subscribe(final AmbiguousEvents observer, final FilterB filter) {
		}

		@Override
		public void unsubscribe(final AmbiguousEvents observer) {
		}
	}

	@Component
	public interface AmbiguousConsumer {
	}

	@Implementation
	public static class AmbiguousConsumerImpl implements AmbiguousConsumer, AmbiguousEvents {

		@UsedEventsSubscribe
		private AmbiguousSubscribe producerSubscribe;

		void subscribeWithNullFilter() {
			this.producerSubscribe.subscribe(this, (FilterA) null);
		}

		@Override
		public void onEvent(final String message) {
		}
	}
}
