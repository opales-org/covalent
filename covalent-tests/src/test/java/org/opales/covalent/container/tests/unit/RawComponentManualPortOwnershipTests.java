/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.raw.RawComponentsServices;

/**
 * <p>
 * The three "manual" consumer-side ports of {@code RawComponentsImpl} -
 * {@code subscribe}, {@code useServices}, and {@code accessParameters} - track
 * their owner as the registered component type: the raw caller class returned by
 * {@code CallerFinder.getCaller()} is resolved back to its registered component
 * type through the metamodel (the shared {@code resolveManualPortOwner} helper),
 * like their producer-side counterparts ({@code produceEvents},
 * {@code provideServices}, {@code offerParameters}).
 * </p>
 * <p>
 * This matters for a raw component registered under an explicit component type
 * distinct from its own implementation class - a documented, supported usage of
 * {@code registerComponent(Class<?>, Object, ...)} - and using the implicit,
 * no-explicit-type overloads of these three methods from inside its own code (the
 * common style): its ports must be tracked under the type it was registered as,
 * not under its own class. {@code unregisterComponent} only cleans ports under
 * the registered type, so ports tracked under the class would be silently left
 * behind: the "unregistered" component would keep receiving events, its
 * used-services and accessed-parameters bookkeeping would never be released, and
 * a later re-registration of a component of the same implementation class would
 * fail with an "already used"/"already produced" validation error.
 * </p>
 */
public class RawComponentManualPortOwnershipTests {

	private CovalentContainer container;

	// No tearDown(): these tests never call start() on their fresh container
	// (exercising the pre-first-start bootstrap window), and stop()
	// requires having been started first; the throwaway container is simply
	// discarded after each test.

	@Test
	public void unregisterStopsASubscriberRegisteredUnderADistinctComponentType() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		RawComponentsServices rcs = this.container.rcs();

		// Producer registered with its own implementation class as component type
		// (the common single-arg registerComponent path), so its own bookkeeping is
		// not itself under test here.
		EventsProducer producer = new EventsProducer(rcs);
		rcs.registerComponent(producer);

		// Consumer registered under an explicit marker type distinct from its own
		// implementation class.
		EventsConsumer consumer = new EventsConsumer(rcs);
		rcs.registerComponent(EventsConsumerMarker.class, consumer);

		producer.events.ping("before-unregister");
		Assert.assertEquals(1, consumer.receivedCount);

		rcs.unregisterComponent(EventsConsumerMarker.class);

		producer.events.ping("after-unregister");
		Assert.assertEquals("consumer should not receive events after being unregistered", 1, consumer.receivedCount);
	}

	@Test
	public void unregisterFreesAUsedServicesPortRegisteredUnderADistinctComponentType() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		// Strict dynamic-rules checking turns the "already used" bookkeeping
		// conflict left by a stale entry into a hard failure instead of a silent trace
		// log, so a failure here is loud.
		this.container.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		RawComponentsServices rcs = this.container.rcs();

		rcs.registerComponent(ServicesUserMarker.class, new ServicesUser(rcs));
		rcs.unregisterComponent(ServicesUserMarker.class);

		// A second, unrelated registration of the same implementation class,
		// consuming the same services interface, must succeed exactly like the
		// first one: nothing should still be tracked under the stale key.
		rcs.registerComponent(ServicesUserMarker.class, new ServicesUser(rcs));
	}

	@Test
	public void unregisterFreesAnAccessedParametersPortRegisteredUnderADistinctComponentType() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		this.container.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		RawComponentsServices rcs = this.container.rcs();

		rcs.registerComponent(ParametersAccessorMarker.class, new ParametersAccessor(rcs));
		rcs.unregisterComponent(ParametersAccessorMarker.class);

		rcs.registerComponent(ParametersAccessorMarker.class, new ParametersAccessor(rcs));
	}

	// --- events fixtures ---

	public interface EventsConsumerMarker {
	}

	public interface OwnershipEvents {
		void ping(String data);
	}

	public static class EventsProducer {

		private final RawComponentsServices rcs;

		OwnershipEvents events;

		public EventsProducer(final RawComponentsServices rcs) {
			this.rcs = rcs;
		}

		@Activation
		public void activate() {
			this.events = this.rcs.produceEvents(EventsProducer.class, OwnershipEvents.class);
		}

	}

	public static class EventsConsumer implements EventsConsumerMarker, OwnershipEvents {

		private final RawComponentsServices rcs;

		int receivedCount;

		public EventsConsumer(final RawComponentsServices rcs) {
			this.rcs = rcs;
		}

		@Activation
		public void activate() {
			// Caller-inferred: no explicit consumerComponentType, so
			// RawComponentsImpl resolves the owner via CallerFinder.getCaller().
			// The tracking key must be EventsConsumerMarker, not that raw class
			// (EventsConsumer).
			this.rcs.subscribe(OwnershipEvents.class, this);
		}

		@Override
		public void ping(final String data) {
			this.receivedCount++;
		}

	}

	// --- services fixtures ---

	public interface ServicesUserMarker {
	}

	public interface OwnershipServices {
		String ping();
	}

	public static class ServicesUser implements ServicesUserMarker {

		private final RawComponentsServices rcs;

		public ServicesUser(final RawComponentsServices rcs) {
			this.rcs = rcs;
		}

		@Activation
		public void activate() {
			// Caller-inferred: no explicit servicesUser type.
			this.rcs.useServices(OwnershipServices.class);
		}

	}

	// --- parameters fixtures ---

	public interface ParametersAccessorMarker {
	}

	public interface OwnershipParameters {
		String getValue();
	}

	public static class ParametersAccessor implements ParametersAccessorMarker {

		private final RawComponentsServices rcs;

		public ParametersAccessor(final RawComponentsServices rcs) {
			this.rcs = rcs;
		}

		@Activation
		public void activate() {
			// Caller-inferred: no explicit accessor type.
			this.rcs.accessParameters(OwnershipParameters.class);
		}

	}

}
