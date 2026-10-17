/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.events.SubscribeParameters;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.raw.RawComponentsServices;

/**
 * {@code RawComponentsServices} exposes both an explicit-component-type and a caller-inferred
 * ("implicit-type") overload for every producer-side port method - {@code produceEvents},
 * {@code provideServices}, {@code offerParameters} - and for the {@code SubscribeParameters}
 * variant of {@code subscribe}. The implicit-type overloads resolve their owner from the calling
 * class, like their consumer-side counterparts ({@code useServices(Class)},
 * {@code accessParameters(Class)}, {@code subscribe(Class, EVENTS)}, see
 * {@link RawComponentManualPortOwnershipTests}): these tests check that each of them works end to
 * end. They also cover the explicit-type {@code produceEvents} overload that takes both delegates
 * ({@code produceEvents(Class<?>, Class<EVENTS>, Consumer, Consumer)}).
 */
public class RawComponentsImplicitOwnerOverloadTests {

	private CovalentContainer container;

	// No tearDown(): these tests never call start() on their fresh container
	// (exercising the pre-first-start bootstrap window), and stop()
	// requires having been started first; the throwaway container is simply
	// discarded after each test.

	@Test
	public void produceEventsWithNoExplicitTypeAndNoDelegatesWorks() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		RawComponentsServices rcs = this.container.rcs();

		OneArgProducer producer = new OneArgProducer(rcs);
		rcs.registerComponent(producer);
		RecordingConsumer consumer = new RecordingConsumer(rcs);
		rcs.registerComponent(consumer);

		producer.events.onPing("data");
		Assert.assertEquals(1, consumer.received.size());
		Assert.assertEquals("data", consumer.received.get(0));

		rcs.unregisterComponent(RecordingConsumer.class);
		rcs.unregisterComponent(OneArgProducer.class);
	}

	@Test
	public void produceEventsWithNoExplicitTypeAndSubscribeDelegateWorks() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		RawComponentsServices rcs = this.container.rcs();

		TwoArgProducer producer = new TwoArgProducer(rcs);
		rcs.registerComponent(producer);
		RecordingConsumer consumer = new RecordingConsumer(rcs);
		rcs.registerComponent(consumer);

		// The subscribe delegate consumer fires as soon as the new subscriber joins,
		// before any explicit onPing call.
		Assert.assertEquals(1, producer.subscribeDelegateCount);
		Assert.assertEquals(1, consumer.received.size());
		Assert.assertEquals("subscribed", consumer.received.get(0));

		rcs.unregisterComponent(RecordingConsumer.class);
		rcs.unregisterComponent(TwoArgProducer.class);
	}

	@Test
	public void produceEventsWithNoExplicitTypeAndBothDelegatesWorks() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		RawComponentsServices rcs = this.container.rcs();

		ThreeArgProducer producer = new ThreeArgProducer(rcs);
		rcs.registerComponent(producer);
		RecordingConsumer consumer = new RecordingConsumer(rcs);
		rcs.registerComponent(consumer);
		Assert.assertEquals(1, producer.subscribeDelegateCount);

		rcs.unregisterComponent(RecordingConsumer.class);
		Assert.assertEquals(1, producer.unsubscribeDelegateCount);

		rcs.unregisterComponent(ThreeArgProducer.class);
	}

	@Test
	public void produceEventsWithExplicitTypeAndBothDelegatesWorks() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		RawComponentsServices rcs = this.container.rcs();

		FourArgProducer producer = new FourArgProducer(rcs);
		rcs.registerComponent(producer);
		RecordingConsumer consumer = new RecordingConsumer(rcs);
		rcs.registerComponent(consumer);
		Assert.assertEquals(1, producer.subscribeDelegateCount);

		rcs.unregisterComponent(RecordingConsumer.class);
		Assert.assertEquals(1, producer.unsubscribeDelegateCount);

		rcs.unregisterComponent(FourArgProducer.class);
	}

	@Test
	public void subscribeWithNoExplicitTypeAndSubscribeParametersAppliesTheFilter() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		RawComponentsServices rcs = this.container.rcs();

		OneArgProducer producer = new OneArgProducer(rcs);
		rcs.registerComponent(producer);

		BlockingFilter filter = new BlockingFilter("blocked");
		SubscribeParameters subscribeParameters = this.container.getParameters().newSubscribeParameters();
		subscribeParameters.setEventFilter(filter);
		FilteredConsumer consumer = new FilteredConsumer(rcs, subscribeParameters);
		rcs.registerComponent(consumer);

		producer.events.onPing("blocked");
		producer.events.onPing("allowed");

		Assert.assertEquals(2, filter.checkedCount);
		Assert.assertEquals(1, consumer.received.size());
		Assert.assertEquals("allowed", consumer.received.get(0));

		rcs.unregisterComponent(FilteredConsumer.class);
		rcs.unregisterComponent(OneArgProducer.class);
	}

	@Test
	public void provideServicesAndUnprovideServicesWithNoExplicitTypeWork() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		// Strict dynamic-rules checking turns an "already provided" bookkeeping
		// conflict left by a stale entry into a hard failure instead of a silent
		// trace log, so a failure in unprovideServices(no-type) is loud.
		this.container.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		RawComponentsServices rcs = this.container.rcs();
		rcs.registerComponent(this);

		NoTypeServicesProvider provider = new NoTypeServicesProvider(rcs);
		rcs.registerComponent(provider);
		NoTypeServices services = rcs.useServices(NoTypeServicesProvider.class, NoTypeServices.class);
		Assert.assertEquals("pong", services.ping());
		rcs.unregisterComponent(NoTypeServicesProvider.class);

		// A second, unrelated registration providing the same services interface
		// must succeed cleanly: nothing should still be tracked under the stale key
		// if unprovideServices(no-type) actually released it.
		NoTypeServicesProvider secondProvider = new NoTypeServicesProvider(rcs);
		rcs.registerComponent(secondProvider);
		rcs.unregisterComponent(NoTypeServicesProvider.class);
	}

	@Test
	public void offerParametersAndUnofferParametersWithNoExplicitTypeWork() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		this.container.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		RawComponentsServices rcs = this.container.rcs();
		rcs.registerComponent(this);

		NoTypeParametersOfferor offeror = new NoTypeParametersOfferor(rcs);
		rcs.registerComponent(offeror);
		NoTypeParameters parameters = rcs.accessParameters(NoTypeParametersOfferor.class, NoTypeParameters.class);
		Assert.assertEquals("value", parameters.getValue());
		rcs.unregisterComponent(NoTypeParametersOfferor.class);

		NoTypeParametersOfferor secondOfferor = new NoTypeParametersOfferor(rcs);
		rcs.registerComponent(secondOfferor);
		rcs.unregisterComponent(NoTypeParametersOfferor.class);
	}

	// --- events fixtures ---

	public interface NoTypeEvents {
		void onPing(String data);
	}

	public static class RecordingConsumer implements NoTypeEvents {

		private final RawComponentsServices rcs;

		final List<String> received = new ArrayList<>();

		public RecordingConsumer(final RawComponentsServices rcs) {
			this.rcs = rcs;
		}

		@Activation
		public void activate() {
			this.rcs.subscribe(NoTypeEvents.class, this);
		}

		@Passivation
		public void passivate() {
			this.rcs.unsubscribe(NoTypeEvents.class, this);
		}

		@Override
		public void onPing(final String data) {
			this.received.add(data);
		}

	}

	public static class OneArgProducer {

		private final RawComponentsServices rcs;

		NoTypeEvents events;

		public OneArgProducer(final RawComponentsServices rcs) {
			this.rcs = rcs;
		}

		@Activation
		public void activate() {
			// No explicit producerComponentType, no delegates.
			this.events = this.rcs.produceEvents(NoTypeEvents.class);
		}

		@Passivation
		public void passivate() {
			this.rcs.unproduceEvents(NoTypeEvents.class);
		}

	}

	public static class TwoArgProducer {

		private final RawComponentsServices rcs;

		NoTypeEvents events;

		int subscribeDelegateCount;

		public TwoArgProducer(final RawComponentsServices rcs) {
			this.rcs = rcs;
		}

		@Activation
		public void activate() {
			// No explicit producerComponentType, subscribeDelegateConsumer only.
			this.events = this.rcs.produceEvents(NoTypeEvents.class, this::onSubscribe);
		}

		private void onSubscribe(final NoTypeEvents subscriber) {
			this.subscribeDelegateCount++;
			subscriber.onPing("subscribed");
		}

		@Passivation
		public void passivate() {
			this.rcs.unproduceEvents(NoTypeEvents.class);
		}

	}

	public static class ThreeArgProducer {

		private final RawComponentsServices rcs;

		NoTypeEvents events;

		int subscribeDelegateCount;

		int unsubscribeDelegateCount;

		public ThreeArgProducer(final RawComponentsServices rcs) {
			this.rcs = rcs;
		}

		@Activation
		public void activate() {
			// No explicit producerComponentType, both delegates.
			this.events = this.rcs.produceEvents(NoTypeEvents.class, this::onSubscribe, this::onUnsubscribe);
		}

		private void onSubscribe(final NoTypeEvents subscriber) {
			this.subscribeDelegateCount++;
		}

		private void onUnsubscribe(final NoTypeEvents subscriber) {
			this.unsubscribeDelegateCount++;
		}

		@Passivation
		public void passivate() {
			this.rcs.unproduceEvents(NoTypeEvents.class);
		}

	}

	public static class FourArgProducer {

		private final RawComponentsServices rcs;

		NoTypeEvents events;

		int subscribeDelegateCount;

		int unsubscribeDelegateCount;

		public FourArgProducer(final RawComponentsServices rcs) {
			this.rcs = rcs;
		}

		@Activation
		public void activate() {
			// Explicit producerComponentType, both delegates.
			this.events = this.rcs.produceEvents(FourArgProducer.class, NoTypeEvents.class, this::onSubscribe,
					this::onUnsubscribe);
		}

		private void onSubscribe(final NoTypeEvents subscriber) {
			this.subscribeDelegateCount++;
		}

		private void onUnsubscribe(final NoTypeEvents subscriber) {
			this.unsubscribeDelegateCount++;
		}

		@Passivation
		public void passivate() {
			this.rcs.unproduceEvents(FourArgProducer.class, NoTypeEvents.class);
		}

	}

	public static class BlockingFilter {

		private final String blockedValue;

		int checkedCount;

		BlockingFilter(final String blockedValue) {
			this.blockedValue = blockedValue;
		}

		/** Found by name through reflection: the filter method of {@code NoTypeEvents.onPing}. */
		public boolean filterPing(final String data) {
			this.checkedCount++;
			return this.blockedValue.equals(data);
		}

	}

	public static class FilteredConsumer implements NoTypeEvents {

		private final RawComponentsServices rcs;

		private final SubscribeParameters subscribeParameters;

		final List<String> received = new ArrayList<>();

		public FilteredConsumer(final RawComponentsServices rcs, final SubscribeParameters subscribeParameters) {
			this.rcs = rcs;
			this.subscribeParameters = subscribeParameters;
		}

		@Activation
		public void activate() {
			// No explicit consumerComponentType, with SubscribeParameters.
			this.rcs.subscribe(NoTypeEvents.class, this, this.subscribeParameters);
		}

		@Passivation
		public void passivate() {
			this.rcs.unsubscribe(NoTypeEvents.class, this);
		}

		@Override
		public void onPing(final String data) {
			this.received.add(data);
		}

	}

	// --- services fixtures ---

	public interface NoTypeServices {
		String ping();
	}

	public static class NoTypeServicesProvider implements NoTypeServices {

		private final RawComponentsServices rcs;

		public NoTypeServicesProvider(final RawComponentsServices rcs) {
			this.rcs = rcs;
		}

		@Activation
		public void activate() {
			// No explicit providerComponentType.
			this.rcs.provideServices(NoTypeServices.class);
		}

		@Passivation
		public void passivate() {
			this.rcs.unprovideServices(NoTypeServices.class);
		}

		@Override
		public String ping() {
			return "pong";
		}

	}

	// --- parameters fixtures ---

	public interface NoTypeParameters {
		String getValue();
	}

	public static class NoTypeParametersOfferor implements NoTypeParameters {

		private final RawComponentsServices rcs;

		public NoTypeParametersOfferor(final RawComponentsServices rcs) {
			this.rcs = rcs;
		}

		@Activation
		public void activate() {
			// No explicit offerorComponentType.
			this.rcs.offerParameters(NoTypeParameters.class);
		}

		@Passivation
		public void passivate() {
			this.rcs.unofferParameters(NoTypeParameters.class);
		}

		@Override
		public String getValue() {
			return "value";
		}

	}

}
