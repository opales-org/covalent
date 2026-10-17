/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.concurrent.CountDownLatch;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.annotations.AccessedParameters;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.ComposedComponent;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsSubscribe;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.Parameters;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.events.EventsManagerServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Initialization;
import org.opales.covalent.container.lifecycle.LifecycleParameters;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.lifecycle.LifecycleServices.StartMode;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.rr.parameters.ParametersManagerServices;
import org.opales.covalent.container.rr.services.ServicesManagerServices;

public class AutoWiringTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	private final LifecycleServices lifecycleServices = Covalent.main.lcs();

	@Before
	public void setUp() {
		this.rcs.registerComponent(this);
		this.cm.getParameters().setSynchronousEvents(true);
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
		this.lifecycleParameters().setStartMode(StartMode.vertical);
	}

	@After
	public void tearDown() {
		this.lifecycleParameters().setStartMode(StartMode.vertical);
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.trace);
		this.cm.stop();
	}

	@Test
	public void verticalStartInjectsManagedServicesParametersEventsAndSubscriberBeforeActivation() {
		this.cm.deployComponents(ProviderImpl.class, PortConsumerImpl.class);
		this.cm.start();

		ProviderImpl provider = this.registryReadServices().getComponentInstance(ProviderComponent.class);
		PortConsumerImpl consumer = this.registryReadServices().getComponentInstance(PortConsumer.class);

		Assert.assertTrue(consumer.servicesInjectedDuringInitialization);
		Assert.assertTrue(consumer.parametersInjectedDuringInitialization);
		Assert.assertTrue(consumer.subscriberInjectedDuringInitialization);
		Assert.assertEquals("service:value", consumer.callService("value"));
		Assert.assertEquals("parameter", consumer.readParameter());

		provider.fire("event");

		Assert.assertEquals("event", consumer.lastEvent);
	}

	@Test
	public void fieldLevelPortAliasesAreAutowired() {
		this.cm.deployComponents(AliasProviderImpl.class, AliasConsumerImpl.class);
		this.cm.start();

		AliasProviderImpl provider = this.registryReadServices().getComponentInstance(AliasProvider.class);
		AliasConsumerImpl consumer = this.registryReadServices().getComponentInstance(AliasConsumer.class);

		Assert.assertTrue(provider.aliasEventsInjected());
		Assert.assertEquals("alias:service", consumer.callService("service"));
		Assert.assertEquals("alias-parameter", consumer.readParameter());
	}

	@Test
	public void consumerWithoutUsedSubscriberIsAutomaticallySubscribed() {
		this.cm.deployComponents(BroadcastSourceImpl.class, AutoSubscribedConsumerImpl.class);
		this.cm.start();

		BroadcastSourceImpl source = this.registryReadServices().getComponentInstance(BroadcastSource.class);
		AutoSubscribedConsumerImpl consumer = this.registryReadServices().getComponentInstance(AutoSubscribedConsumer.class);

		source.fire("broadcast");

		Assert.assertEquals("broadcast", consumer.lastBroadcast);
		Assert.assertEquals(1, consumer.broadcastCount);
	}

	@Test
	public void composedConsumerAutoSubscribeWaitsForSiblingSubscriberPublication() {
		this.cm.deployComponents(CompositeAutoSubscribeRootImpl.class);
		this.cm.start();

		CompositeAutoSubscribeConsumerImpl consumer = this.registryReadServices()
				.getComponentInstance(CompositeAutoSubscribeConsumer.class);

		Assert.assertEquals("snapshot", consumer.lastValue);
	}

	@Test(timeout = 10000)
	public void asynchronousSubscribeDelegateStateReachesConsumer() throws InterruptedException {
		this.cm.getParameters().setSynchronousEvents(false);

		this.cm.deployComponents(AsyncSubscribeProducerImpl.class, AsyncSubscribeConsumerImpl.class);
		this.cm.start();

		AsyncSubscribeConsumerImpl consumer = this.registryReadServices()
				.getComponentInstance(AsyncSubscribeConsumer.class);
		consumer.valueReceived.await();

		Assert.assertEquals("snapshot", consumer.lastValue);
	}

	@Test
	public void itemFieldsReceiveAutowiredServicesParametersAndSubscribers() {
		this.cm.deployComponents(ProviderImpl.class, ItemPortConsumerImpl.class);
		this.cm.start();

		ProviderImpl provider = this.registryReadServices().getComponentInstance(ProviderComponent.class);
		ItemPortConsumerImpl consumer = this.registryReadServices().getComponentInstance(ItemPortConsumer.class);

		Assert.assertEquals("service:item", consumer.callServiceFromItem("item"));
		Assert.assertEquals("parameter", consumer.readParameterFromItem());

		provider.fire("item-event");

		Assert.assertEquals("item-event", consumer.lastEventFromItem());
	}

	@Test
	public void undeployDeletesAutomaticallyCreatedPorts() {
		this.cm.deployComponents(ProviderImpl.class, PortConsumerImpl.class);
		this.cm.start();

		// Looked up after start(), not before: these raw lookups need the
		// container already running.
		ServicesManagerServices servicesManager = this.rcs.useServices(ServicesManagerServices.class);
		ParametersManagerServices parametersManager = this.rcs.useServices(ParametersManagerServices.class);
		EventsManagerServices eventsManager = this.rcs.useServices(EventsManagerServices.class);

		Assert.assertTrue(servicesManager.isServicesProvided(ProviderServices.class));
		Assert.assertTrue(parametersManager.isParametersOffered(ProviderParameters.class));
		Assert.assertTrue(eventsManager.isEventsProduced(ProviderEvents.class));

		this.lifecycleServices.undeployComponent(PortConsumerImpl.class);
		this.lifecycleServices.undeployComponent(ProviderImpl.class);

		Assert.assertFalse(servicesManager.isServicesProvided(ProviderServices.class));
		Assert.assertFalse(parametersManager.isParametersOffered(ProviderParameters.class));
		Assert.assertFalse(eventsManager.isEventsProduced(ProviderEvents.class));
	}

	@Test
	public void horizontalStartInjectsOnlyParametersBeforeInitializationAndOtherUsedPortsBeforeActivation() {
		this.lifecycleParameters().setStartMode(StartMode.horizontal);

		this.cm.deployComponents(ProviderImpl.class, TimingConsumerImpl.class);
		this.cm.start();

		TimingConsumerImpl consumer = this.registryReadServices().getComponentInstance(TimingConsumer.class);

		Assert.assertFalse(consumer.servicesInjectedDuringInitialization);
		Assert.assertTrue(consumer.parametersInjectedDuringInitialization);
		Assert.assertFalse(consumer.subscriberInjectedDuringInitialization);
		Assert.assertTrue(consumer.servicesInjectedDuringActivation);
		Assert.assertTrue(consumer.parametersInjectedDuringActivation);
		Assert.assertTrue(consumer.subscriberInjectedDuringActivation);
	}

	private LifecycleParameters lifecycleParameters() {
		// A cast on the direct lcs() facade, not this.rcs.accessParameters(...):
		// this is called from @Before/@After, before this.cm.start() has run for
		// the test, and only the direct facade works without the container
		// already running.
		return (LifecycleParameters) this.lifecycleServices;
	}

	// Looked up lazily, not as a field initializer: this class does not extend
	// CovalentContainerizedTests, so nothing starts the container before a
	// test's own body does; this raw lookup needs it already running, which is
	// only true from partway through each test method onward.
	private RegistryReadServices registryReadServices() {
		return this.rcs.useServices(RegistryReadServices.class);
	}

	@Component
	public interface CompositeAutoSubscribeRoot {
	}

	@Component
	public interface CompositeAutoSubscribeProducer {
	}

	@Component
	public interface CompositeAutoSubscribeConsumer {
	}

	@Events
	public interface CompositeAutoSubscribeEvents {
		void onValue(String value);

		@EventsSubscribe
		public interface CompositeAutoSubscribeEventsSubscribe {
			void subscribe(CompositeAutoSubscribeEvents observer);

			void unsubscribe(CompositeAutoSubscribeEvents observer);
		}
	}

	@Implementation
	public static class CompositeAutoSubscribeRootImpl implements CompositeAutoSubscribeRoot {
		@ComposedComponent
		private final CompositeAutoSubscribeProducerImpl producer = null;

		@ComposedComponent
		private final CompositeAutoSubscribeConsumerImpl consumer = null;
	}

	@Implementation
	public static class CompositeAutoSubscribeProducerImpl implements CompositeAutoSubscribeProducer,
			CompositeAutoSubscribeEvents.CompositeAutoSubscribeEventsSubscribe {
		@ProducedEvents
		private CompositeAutoSubscribeEvents events;

		@Override
		public void subscribe(final CompositeAutoSubscribeEvents observer) {
			observer.onValue("snapshot");
		}

		@Override
		public void unsubscribe(final CompositeAutoSubscribeEvents observer) {
		}
	}

	@Implementation
	public static class CompositeAutoSubscribeConsumerImpl implements CompositeAutoSubscribeConsumer,
			CompositeAutoSubscribeEvents {
		private String lastValue;

		@Override
		public void onValue(final String value) {
			this.lastValue = value;
		}
	}

	@Component
	public interface ProviderComponent {
	}

	@Component
	public interface AsyncSubscribeProducer {
	}

	@Component
	public interface AsyncSubscribeConsumer {
	}

	@Events
	public interface AsyncSubscribeEvents {
		void onValue(String value);

		@EventsSubscribe
		interface Subscribe {
			void subscribe(AsyncSubscribeEvents observer);

			void unsubscribe(AsyncSubscribeEvents observer);
		}
	}

	@Implementation
	public static class AsyncSubscribeProducerImpl implements AsyncSubscribeProducer,
			AsyncSubscribeEvents.Subscribe {
		@ProducedEvents
		private AsyncSubscribeEvents events;

		@Override
		public void subscribe(final AsyncSubscribeEvents observer) {
			observer.onValue("snapshot");
		}

		@Override
		public void unsubscribe(final AsyncSubscribeEvents observer) {
		}
	}

	@Implementation
	public static class AsyncSubscribeConsumerImpl implements AsyncSubscribeConsumer, AsyncSubscribeEvents {
		private final CountDownLatch valueReceived = new CountDownLatch(1);

		private volatile String lastValue;

		@Override
		public void onValue(final String value) {
			this.lastValue = value;
			this.valueReceived.countDown();
		}
	}

	@Services
	public interface ProviderServices {
		String format(String value);
	}

	@Parameters
	public interface ProviderParameters {
		String getName();
	}

	@Events
	public interface ProviderEvents {
		void onValue(String value);

		@EventsSubscribe
		public interface ProviderEventsSubscribe {
			void subscribe(ProviderEvents consumer);

			void unsubscribe(ProviderEvents consumer);
		}
	}

	@Implementation
	public static class ProviderImpl implements ProviderComponent, ProviderServices, ProviderParameters {
		@ProducedEvents
		private ProviderEvents providerEvents;

		@Override
		public String format(final String value) {
			return "service:" + value;
		}

		@Override
		public String getName() {
			return "parameter";
		}

		void fire(final String value) {
			this.providerEvents.onValue(value);
		}
	}

	@Component
	public interface PortConsumer {
	}

	@Implementation
	public static class PortConsumerImpl implements PortConsumer, ProviderEvents {
		@UsedServices
		private ProviderServices providerServices;

		@AccessedParameters
		private ProviderParameters providerParameters;

		@UsedEventsSubscribe
		private ProviderEvents.ProviderEventsSubscribe providerEventsSubscribe;

		private boolean servicesInjectedDuringInitialization;

		private boolean parametersInjectedDuringInitialization;

		private boolean subscriberInjectedDuringInitialization;

		private String lastEvent;

		@Initialization
		public void initialize() {
			this.servicesInjectedDuringInitialization = this.providerServices != null;
			this.parametersInjectedDuringInitialization = this.providerParameters != null;
			this.subscriberInjectedDuringInitialization = this.providerEventsSubscribe != null;
		}

		@Activation
		public void activate() {
			this.providerEventsSubscribe.subscribe(this);
		}

		@Passivation
		public void passivate() {
			this.providerEventsSubscribe.unsubscribe(this);
		}

		String callService(final String value) {
			return this.providerServices.format(value);
		}

		String readParameter() {
			return this.providerParameters.getName();
		}

		@Override
		public void onValue(final String value) {
			this.lastEvent = value;
		}
	}

	@Component
	public interface AliasProvider {
	}

	@Services
	public interface AliasServices {
		String formatAlias(String value);
	}

	@Parameters
	public interface AliasParameters {
		String getAliasName();
	}

	@Events
	public interface AliasEvents {
		void onAlias(String value);
	}

	@Implementation
	public static class AliasProviderImpl implements AliasProvider, AliasServices, AliasParameters {
		@Events
		private AliasEvents aliasEvents;

		@Override
		public String formatAlias(final String value) {
			return "alias:" + value;
		}

		@Override
		public String getAliasName() {
			return "alias-parameter";
		}

		boolean aliasEventsInjected() {
			return this.aliasEvents != null;
		}
	}

	@Component
	public interface AliasConsumer {
	}

	@Implementation
	public static class AliasConsumerImpl implements AliasConsumer {
		@Services
		private AliasServices aliasServices;

		@Parameters
		private AliasParameters aliasParameters;

		String callService(final String value) {
			return this.aliasServices.formatAlias(value);
		}

		String readParameter() {
			return this.aliasParameters.getAliasName();
		}
	}

	@Component
	public interface BroadcastSource {
	}

	@Events
	public interface BroadcastEvents {
		void onBroadcast(String value);
	}

	@Implementation
	public static class BroadcastSourceImpl implements BroadcastSource {
		@ProducedEvents
		private BroadcastEvents broadcastEvents;

		void fire(final String value) {
			this.broadcastEvents.onBroadcast(value);
		}
	}

	@Component
	public interface AutoSubscribedConsumer {
	}

	@Implementation
	public static class AutoSubscribedConsumerImpl implements AutoSubscribedConsumer, BroadcastEvents {
		private String lastBroadcast;

		private int broadcastCount;

		@Override
		public void onBroadcast(final String value) {
			this.lastBroadcast = value;
			this.broadcastCount++;
		}
	}

	@Component
	public interface ItemPortConsumer {
	}

	@Implementation
	public static class ItemPortConsumerImpl implements ItemPortConsumer {
		@Item
		private ItemPortConsumerItem item;

		@Activation
		public void activate() {
			this.item.subscribe();
		}

		@Passivation
		public void passivate() {
			this.item.unsubscribe();
		}

		String callServiceFromItem(final String value) {
			return this.item.callService(value);
		}

		String readParameterFromItem() {
			return this.item.readParameter();
		}

		String lastEventFromItem() {
			return this.item.lastEvent;
		}
	}

	@Item
	public static class ItemPortConsumerItem implements ProviderEvents {
		@UsedServices
		private ProviderServices providerServices;

		@AccessedParameters
		private ProviderParameters providerParameters;

		@UsedEventsSubscribe
		private ProviderEvents.ProviderEventsSubscribe providerEventsSubscribe;

		private String lastEvent;

		void subscribe() {
			this.providerEventsSubscribe.subscribe(this);
		}

		void unsubscribe() {
			this.providerEventsSubscribe.unsubscribe(this);
		}

		String callService(final String value) {
			return this.providerServices.format(value);
		}

		String readParameter() {
			return this.providerParameters.getName();
		}

		@Override
		public void onValue(final String value) {
			this.lastEvent = value;
		}
	}

	@Component
	public interface TimingConsumer {
	}

	@Implementation
	public static class TimingConsumerImpl implements TimingConsumer {
		@UsedServices
		private ProviderServices providerServices;

		@AccessedParameters
		private ProviderParameters providerParameters;

		@UsedEventsSubscribe
		private ProviderEvents.ProviderEventsSubscribe providerEventsSubscribe;

		private boolean servicesInjectedDuringInitialization;

		private boolean parametersInjectedDuringInitialization;

		private boolean subscriberInjectedDuringInitialization;

		private boolean servicesInjectedDuringActivation;

		private boolean parametersInjectedDuringActivation;

		private boolean subscriberInjectedDuringActivation;

		@Initialization
		public void initialize() {
			this.servicesInjectedDuringInitialization = this.providerServices != null;
			this.parametersInjectedDuringInitialization = this.providerParameters != null;
			this.subscriberInjectedDuringInitialization = this.providerEventsSubscribe != null;
		}

		@Activation
		public void activate() {
			this.servicesInjectedDuringActivation = this.providerServices != null;
			this.parametersInjectedDuringActivation = this.providerParameters != null;
			this.subscriberInjectedDuringActivation = this.providerEventsSubscribe != null;
		}
	}
}
