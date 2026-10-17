/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsSubscribe;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Parameters;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.component.annotations.AccessedParameters;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.registry.RegistryReadServices;

/**
 * A single end-to-end restart exercising every port type at once - managed
 * services/parameters/events AND raw services/parameters/events - in the
 * same container, on the same stop()/start() cycle.
 * <p>
 * Every other test class covers one dimension in isolation (raw services and
 * parameters cleanup: {@link RawComponentsStopCleanupContainerizedTests};
 * the events async worker pool: {@code EventsShutdownContainerizedTests};
 * shutdown reporting: {@link ContainerReportTests}), which is the right
 * granularity for p
 * i
 * n
 * n
 * i
 * n
 * g
 *  
 * d
 * o
 * w
 * n
 *  
 * a
 *  
 * f
 * a
 * i
 * l
 * u
 * r
 * e
 * ,
 *  
 * b
 * u
 * t none of them proves the
 * container-wide guarantee end to end: after stop()/start(), every port -
 * managed or raw, service, parameter or event - is torn down and rewired as
 * if the container had just been created, with nothing surviving from the
 * previous cycle and nothing preventing a clean second cycle.
 */
public class FullRestartAllPortTypesContainerizedTests {

	private CovalentContainer container;

	@After
	public void tearDown() {
		if (this.container != null) {
			// dispose(), not stop(): this is a throwaway container
			// (Covalent.container()), so it must release its internal
			// component tree for good rather than leaving it reactivated.
			this.container.dispose();
		}
	}

	@Test
	public void stopThenStartLeavesEveryPortTypeAsCleanAsAFreshContainer() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);

		this.runOneFullCycle("first cycle");

		// Nothing was re-deployed or re-registered yet: the restart itself
		// must not resurrect anything from the first cycle, nor leave the
		// container unable to start cleanly with zero application
		// components - exactly what a genuinely fresh container looks like.
		this.container.start();

		RawComponentsServices rcs = this.container.rcs();
		rcs.registerComponent(this);
		RegistryReadServices registry = rcs.useServices(RegistryReadServices.class);
		try {
			Assert.assertFalse("managed provider must not survive a stop()/start() with no redeploy",
					registry.isRegistered(ManagedProviderComponent.class));
			Assert.assertFalse("managed consumer must not survive a stop()/start() with no redeploy",
					registry.isRegistered(ManagedConsumerComponent.class));
			Assert.assertFalse("raw provider must not survive a stop()/start() with no re-registration",
					registry.isRegistered(RawProvider.class));
			Assert.assertFalse("raw consumer must not survive a stop()/start() with no re-registration",
					registry.isRegistered(RawConsumer.class));
		} finally {
			rcs.unuseServices(RegistryReadServices.class);
		}

		// deployComponents() is called with the container stopped, exactly
		// like the very first cycle - stop() again before redeploying.
		this.container.stop();

		// A second, independent cycle on the very same container instance:
		// re-deploying/re-registering the same types must succeed exactly as
		// it did the first time - no "already provided"/"already used"
		// leftover state, and no stale delivery to the first cycle's
		// consumer instances.
		this.runOneFullCycle("second cycle");
	}

	private void runOneFullCycle(final String label) {
		this.container.deployComponents(ManagedProviderImpl.class, ManagedConsumerImpl.class);
		this.container.start();

		RawComponentsServices rcs = this.container.rcs();
		rcs.registerComponent(this);
		RawProvider rawProvider = new RawProvider(rcs);
		rcs.registerComponent(rawProvider);
		RawConsumer rawConsumer = new RawConsumer(rcs);
		rcs.registerComponent(rawConsumer);

		RegistryReadServices registry = rcs.useServices(RegistryReadServices.class);
		ManagedProviderImpl managedProvider;
		ManagedConsumerImpl managedConsumer;
		try {
			managedProvider = registry.getComponentInstance(ManagedProviderComponent.class);
			managedConsumer = registry.getComponentInstance(ManagedConsumerComponent.class);
		} finally {
			rcs.unuseServices(RegistryReadServices.class);
		}

		Assert.assertEquals(label + ": managed service", "managed:hello", managedConsumer.callService("hello"));
		Assert.assertEquals(label + ": managed parameter", "managed-label", managedConsumer.readParameter());
		managedProvider.fire("managed-event-" + label);
		Assert.assertEquals(label + ": managed event", "managed-event-" + label, managedConsumer.lastEvent);
		Assert.assertEquals(label + ": managed event count", 1, managedConsumer.eventCount);

		Assert.assertEquals(label + ": raw service", "raw:hi", rawConsumer.callService("hi"));
		Assert.assertEquals(label + ": raw parameter", "raw-label", rawConsumer.readParameter());
		rawProvider.fire("raw-event-" + label);
		Assert.assertEquals(label + ": raw event count", 1, rawConsumer.eventCount);
		Assert.assertEquals(label + ": raw event", "raw-event-" + label, rawConsumer.lastEvent);

		this.container.stop();
	}

	// --- managed side -------------------------------------------------

	@Component
	public interface ManagedProviderComponent {
	}

	@Component
	public interface ManagedConsumerComponent {
	}

	@Services
	public interface ManagedServices {
		String echo(String value);
	}

	@Parameters
	public interface ManagedParameters {
		String getLabel();
	}

	@Events
	public interface ManagedEvents {
		void onPing(String value);

		@EventsSubscribe
		interface Subscribe {
			void subscribe(ManagedEvents observer);

			void unsubscribe(ManagedEvents observer);
		}
	}

	@Implementation
	public static class ManagedProviderImpl implements ManagedProviderComponent, ManagedServices, ManagedParameters {

		@ProducedEvents
		private ManagedEvents events;

		@Override
		public String echo(final String value) {
			return "managed:" + value;
		}

		@Override
		public String getLabel() {
			return "managed-label";
		}

		void fire(final String value) {
			this.events.onPing(value);
		}
	}

	@Implementation
	public static class ManagedConsumerImpl implements ManagedConsumerComponent, ManagedEvents {

		@UsedServices
		private ManagedServices services;

		@AccessedParameters
		private ManagedParameters parameters;

		@UsedEventsSubscribe
		private ManagedEvents.Subscribe subscribe;

		private String lastEvent;

		private int eventCount;

		@Activation
		public void activate() {
			this.subscribe.subscribe(this);
		}

		@Passivation
		public void passivate() {
			this.subscribe.unsubscribe(this);
		}

		@Override
		public void onPing(final String value) {
			this.lastEvent = value;
			this.eventCount++;
		}

		String callService(final String value) {
			return this.services.echo(value);
		}

		String readParameter() {
			return this.parameters.getLabel();
		}
	}

	// --- raw side -------------------------------------------------------

	public interface RawEchoServices {
		String echo(String value);
	}

	public interface RawLabelParameters {
		String getLabel();
	}

	public interface RawPingEvents {
		void onPing(String value);
	}

	public static class RawProvider implements RawEchoServices, RawLabelParameters {

		private final RawComponentsServices rcs;

		private RawPingEvents events;

		RawProvider(final RawComponentsServices rcs) {
			this.rcs = rcs;
		}

		@Activation
		public void activate() {
			this.rcs.provideServices(RawProvider.class, RawEchoServices.class);
			this.rcs.offerParameters(RawProvider.class, RawLabelParameters.class);
			this.events = this.rcs.produceEvents(RawProvider.class, RawPingEvents.class);
		}

		@Passivation
		public void passivate() {
			this.rcs.unprovideServices(RawProvider.class, RawEchoServices.class);
			this.rcs.unofferParameters(RawProvider.class, RawLabelParameters.class);
			this.rcs.unproduceEvents(RawProvider.class, RawPingEvents.class);
		}

		@Override
		public String echo(final String value) {
			return "raw:" + value;
		}

		@Override
		public String getLabel() {
			return "raw-label";
		}

		void fire(final String value) {
			this.events.onPing(value);
		}
	}

	public static class RawConsumer implements RawPingEvents {

		private final RawComponentsServices rcs;

		private RawEchoServices services;

		private RawLabelParameters parameters;

		private String lastEvent;

		private int eventCount;

		RawConsumer(final RawComponentsServices rcs) {
			this.rcs = rcs;
		}

		@Activation
		public void activate() {
			this.services = this.rcs.useServices(RawProvider.class, RawEchoServices.class);
			this.parameters = this.rcs.accessParameters(RawProvider.class, RawLabelParameters.class);
			this.rcs.subscribe(RawConsumer.class, RawPingEvents.class, this);
		}

		@Passivation
		public void passivate() {
			this.rcs.unuseServices(RawEchoServices.class);
			this.rcs.unaccessParameters(RawLabelParameters.class);
			this.rcs.unsubscribe(RawPingEvents.class, this);
		}

		@Override
		public void onPing(final String value) {
			this.lastEvent = value;
			this.eventCount++;
		}

		String callService(final String value) {
			return this.services.echo(value);
		}

		String readParameter() {
			return this.parameters.getLabel();
		}
	}

}
