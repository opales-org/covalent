/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerServices;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents.ProducerEventsSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.ProducerWithSubscribeEvents.ProducerWithSubscribeEventsFilter;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithsubscribe.ProducerWithSubscribe;
import org.opales.covalent.container.tests.unit.annotatedcomponents.producer.producerwithsubscribe.impl.ProducerWithSubscribeImpl;

/**
 * An object given to a subscribe method as a filter is recognized when the
 * {@code @EventsFilter}-annotated interface is implemented directly by its
 * class, by a super-interface of an interface it implements, or by one of its
 * superclasses. In each case the events it blocks are not delivered.
 */
public class SubscribeFilterHierarchyTests {

	private static final String BLOCKED = "blocked";

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

	private List<String> deliveredWith(final ProducerWithSubscribeEventsFilter filter) {
		FilteredConsumerImpl.filter = filter;
		this.container.deployComponents(ProducerWithSubscribeImpl.class, FilteredConsumerImpl.class);
		this.container.rcs().registerComponent(this);
		this.container.start();
		ProducerServices producer = this.container.rcs().useServices(ProducerWithSubscribe.class,
				ProducerServices.class);
		producer.fireEvent("first");
		producer.fireEvent(BLOCKED);
		producer.fireEvent("second");
		return FilteredConsumerImpl.received;
	}

	@Test
	public void aFilterImplementingTheAnnotatedInterfaceDirectlyBlocksItsEvents() {
		Assert.assertEquals(List.of("first", "second"), this.deliveredWith(new DirectFilter()));
	}

	@Test
	public void aFilterImplementingASubInterfaceOfTheAnnotatedInterfaceBlocksItsEvents() {
		Assert.assertEquals(List.of("first", "second"), this.deliveredWith(new SubInterfaceFilter()));
	}

	@Test
	public void aFilterWhoseSuperclassImplementsTheAnnotatedInterfaceBlocksItsEvents() {
		Assert.assertEquals(List.of("first", "second"), this.deliveredWith(new ChildFilter()));
	}

	public static class DirectFilter implements ProducerWithSubscribeEventsFilter {

		@Override
		public boolean filterEvent(final String message) {
			return BLOCKED.equals(message);
		}
	}

	public interface SubFilter extends ProducerWithSubscribeEventsFilter {
	}

	public static class SubInterfaceFilter implements SubFilter {

		@Override
		public boolean filterEvent(final String message) {
			return BLOCKED.equals(message);
		}
	}

	public static class ParentFilter implements ProducerWithSubscribeEventsFilter {

		@Override
		public boolean filterEvent(final String message) {
			return BLOCKED.equals(message);
		}
	}

	public static class ChildFilter extends ParentFilter {
	}

	@Component
	public interface FilteredConsumer {
	}

	@Implementation
	public static class FilteredConsumerImpl implements FilteredConsumer, ProducerWithSubscribeEvents {

		static volatile ProducerWithSubscribeEventsFilter filter;

		static final List<String> received = new CopyOnWriteArrayList<>();

		@UsedEventsSubscribe
		private ProducerEventsSubscribe producerEventsSubscribe;

		@Activation
		public void activate() {
			received.clear();
			this.producerEventsSubscribe.subscribe(this, filter);
		}

		@Override
		public void onEvent(final String message) {
			received.add(message);
		}
	}
}
