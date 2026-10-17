/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.containermanager.raw;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;

import org.opales.covalent.supervision.SupervisionThreads;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.workers.WorkerPoolKind;
import org.opales.covalent.container.workers.WorkerPoolState;
import org.opales.covalent.supervision.observed.CallOutcome;
import org.opales.covalent.supervision.observed.ObservedCallingThread;
import org.opales.covalent.supervision.observed.ObservedPortKind;
import org.opales.covalent.supervision.testsupport.observed.FlatParametersOfferorComponent;
import org.opales.covalent.supervision.testsupport.observed.impl.FlatParametersOfferorComponentImpl;
import org.opales.covalent.supervision.testsupport.observed.SampleEvents;

/**
 * {@link ObservedContainerBridge} outside a container: its own produced events are recording proxies, the
 * observed container (when one is needed) is a real managed container. Without a container observed every read
 * answers empty or absent; the per-call notifications, once connected, feed the port activity and the calling
 * threads whatever the call stream does, and are republished only while the call stream is enabled.
 */
public class ObservedContainerBridgeTests {

	private static final String OBSERVED = "observed-container-bridge-tests";

	private static final long TIMEOUT_MILLIS = 5000;

	/** One notification republished by the bridge. */
	private record Published(String name, Object[] arguments) {
	}

	private final List<Published> published = Collections.synchronizedList(new ArrayList<>());

	private final ObservedContainerBridge bridge = new ObservedContainerBridge();

	/** Objects receiving the events of {@link SampleEvents}. */
	private static class ReceiverA implements SampleEvents {
		@Override
		public void fired() {
		}
	}

	private static class ReceiverB implements SampleEvents {
		@Override
		public void fired() {
		}
	}

	private Method method;

	private Method eventMethod;

	@Before
	public void setUp() throws Exception {
		this.method = Runnable.class.getMethod("run");
		this.eventMethod = SampleEvents.class.getMethod("fired");
		for (Field field : ObservedContainerBridge.class.getDeclaredFields()) {
			if (field.getName().startsWith("produceObserved")) {
				field.setAccessible(true);
				field.set(this.bridge, Proxy.newProxyInstance(field.getType().getClassLoader(),
						new Class<?>[] { field.getType() }, (proxy, called, args) -> {
							this.published.add(new Published(called.getName(), args));
							return null;
						}));
			}
		}
	}

	@After
	public void tearDown() {
		this.bridge.disconnect();
		this.bridge.stopPortActivity();
		Covalent.disposeManagedContainer(OBSERVED);
	}

	private List<Published> published(final String name) {
		synchronized (this.published) {
			return this.published.stream().filter(event -> event.name().equals(name)).toList();
		}
	}

	private CovalentContainer startObserved() {
		CovalentContainer observed = Covalent.managedContainer(OBSERVED);
		observed.deployComponents(FlatParametersOfferorComponentImpl.class);
		observed.start();
		return observed;
	}

	private static void await(final BooleanSupplier condition) {
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

	// --- Nothing observed ---

	@Test
	public void withNothingObservedEveryReadAnswersEmptyOrAbsent() {
		ObservedContainerBridge b = this.bridge;
		Runnable consumer = () -> {
		};

		Assert.assertNull(b.getObservedContainerName());
		Assert.assertFalse(b.isInstrumented());
		Assert.assertTrue(b.getInstances().isEmpty());
		Assert.assertNull(b.getComponentInstance(String.class));
		Assert.assertFalse(b.isRegistered(String.class));
		Assert.assertNull(b.readMetamodel(String.class));
		Assert.assertNull(b.getType(String.class));
		Assert.assertNull(b.getImplementation(String.class));
		Assert.assertNull(b.readComponentState(String.class));
		Assert.assertNull(b.getDependencyGraph());
		Assert.assertFalse(b.hasDependencyGraph());
		Assert.assertTrue(b.readWorkerPools().isEmpty());
		Assert.assertFalse(b.isTaskTracking());
		Assert.assertTrue(b.readCallingThreads().isEmpty());
		Assert.assertFalse(b.isServicesUsed(String.class, Runnable.class));
		Assert.assertNull(b.getServicesProvider(Runnable.class));
		Assert.assertTrue(b.getProviderToServices().isEmpty());
		Assert.assertTrue(b.getServiceToUsers().isEmpty());
		Assert.assertFalse(b.isServicesProvided(Runnable.class));
		Assert.assertFalse(b.isActivated(Runnable.class));
		Assert.assertFalse(b.isParametersAccessed(String.class, Runnable.class));
		Assert.assertNull(b.getParametersOfferor(Runnable.class));
		Assert.assertFalse(b.isParametersOffered(Runnable.class));
		Assert.assertTrue(b.getOfferorToParameters().isEmpty());
		Assert.assertTrue(b.getParametersToAccessor().isEmpty());
		Assert.assertTrue(b.getSubscribers().isEmpty());
		Assert.assertTrue(b.getProducerToEvent().isEmpty());
		Assert.assertFalse(b.isEventsProduced(Runnable.class));
		Assert.assertNull(b.getProvidedSubscriber(Runnable.class));
		Assert.assertFalse(b.isSubscribed(Runnable.class, consumer));
	}

	@Test
	public void withNothingObservedTheCommandsAreRefused() {
		ObservedContainerBridge b = this.bridge;
		List<ThrowingRunnable> commands = List.of(
				() -> b.instantiateComponent(String.class), () -> b.initializeComponent(String.class),
				() -> b.activateComponent(String.class), () -> b.passivateComponent(String.class),
				() -> b.removeComponent(String.class), () -> b.resetFailedComponent(String.class),
				() -> b.undeployComponent(String.class), () -> b.createProvidedServices(Runnable.class),
				() -> b.activateServices(Runnable.class), () -> b.passivateServices(Runnable.class),
				() -> b.deleteProvidedServices(Runnable.class),
				() -> b.createOfferedParameters(Runnable.class), () -> b.activateParameters(Runnable.class),
				() -> b.passivateParameters(Runnable.class), () -> b.deleteOfferedParameters(Runnable.class),
				() -> b.activateProducer(Runnable.class), () -> b.passivateProducer(Runnable.class),
				() -> b.createUsedSubscriber(Runnable.class, Runnable.class));

		for (ThrowingRunnable command : commands) {
			Assert.assertThrows(IllegalStateException.class, command);
		}
	}

	@Test
	public void withNothingObservedTheWorkerSettingsDoNothing() {
		this.bridge.setTaskTracking(true);
		this.bridge.setWorkerCount(WorkerPoolKind.GENERAL, 3);
		this.bridge.setWorkerCount(WorkerPoolKind.EVENTS, 3);
		this.bridge.setQueueCapacity(WorkerPoolKind.GENERAL, 10);
		this.bridge.setQueueCapacity(WorkerPoolKind.EVENTS, 10);

		Assert.assertFalse(this.bridge.isTaskTracking());
	}

	@Test
	public void aDedicatedPoolKeepsItsSingleWorkerAndItsCapacity() {
		Assert.assertThrows(IllegalArgumentException.class,
				() -> this.bridge.setWorkerCount(WorkerPoolKind.DEDICATED, 2));
		Assert.assertThrows(IllegalArgumentException.class,
				() -> this.bridge.setQueueCapacity(WorkerPoolKind.DEDICATED, 2));
	}

	// --- Connection ---

	@Test
	public void anUnmanagedContainerCannotBeObservedAndNothingIsPublished() {
		CovalentContainer unmanaged = Covalent.container();
		try {
			Assert.assertThrows(IllegalArgumentException.class, () -> this.bridge.connect(unmanaged));
		} finally {
			unmanaged.dispose();
		}

		Assert.assertTrue(this.published("onContainerConnected").isEmpty());
		Assert.assertNull(this.bridge.getObservedContainerName());
	}

	@Test
	public void connectingAndDisconnectingArePublishedAndShownByTheObservedName() {
		CovalentContainer observed = this.startObserved();

		this.bridge.connect(observed);

		Assert.assertEquals(OBSERVED, this.bridge.getObservedContainerName());
		Assert.assertEquals(1, this.published("onContainerConnected").size());

		this.bridge.disconnect();

		Assert.assertNull(this.bridge.getObservedContainerName());
		Assert.assertEquals(1, this.published("onContainerDisconnected").size());
		Assert.assertTrue(this.bridge.getInstances().isEmpty());
	}

	@Test
	public void theObservedContainerIsReadThroughTheBridge() {
		this.bridge.connect(this.startObserved());

		Assert.assertTrue(this.bridge.getInstances().containsKey(FlatParametersOfferorComponent.class));
		Assert.assertTrue(this.bridge.isRegistered(FlatParametersOfferorComponent.class));
		Assert.assertNotNull(this.bridge.getComponentInstance(FlatParametersOfferorComponent.class));
		Assert.assertNotNull(this.bridge.readMetamodel(FlatParametersOfferorComponent.class));
		Assert.assertEquals(FlatParametersOfferorComponentImpl.class,
				this.bridge.getImplementation(FlatParametersOfferorComponent.class));
		Assert.assertEquals(FlatParametersOfferorComponent.class,
				this.bridge.getType(FlatParametersOfferorComponentImpl.class));
		Assert.assertEquals(ComponentState.activated,
				this.bridge.readComponentState(FlatParametersOfferorComponent.class));
		Assert.assertFalse(this.bridge.isInstrumented());
	}

	@Test
	public void theWorkerPoolsAreReadAndTunedOnTheObservedContainer() {
		this.bridge.connect(this.startObserved());

		this.bridge.setWorkerCount(WorkerPoolKind.GENERAL, 3);
		this.bridge.setQueueCapacity(WorkerPoolKind.GENERAL, 77);

		WorkerPoolState general = this.bridge.readWorkerPools().stream()
				.filter(pool -> pool.kind() == WorkerPoolKind.GENERAL).findFirst().orElseThrow();
		Assert.assertEquals(3, general.targetWorkerCount());
		Assert.assertEquals(77, general.queueCapacity());
	}

	@Test
	public void taskTrackingIsSwitchedOnBothPoolsOfTheObservedContainer() {
		this.bridge.connect(this.startObserved());

		this.bridge.setTaskTracking(true);
		Assert.assertTrue(this.bridge.isTaskTracking());
		this.bridge.setTaskTracking(false);

		Assert.assertFalse(this.bridge.isTaskTracking());
	}

	@Test
	public void undeployingAnUnknownComponentTypeIsRefusedWithAClearReason() {
		this.bridge.connect(this.startObserved());

		Assert.assertThrows(IllegalArgumentException.class, () -> this.bridge.undeployComponent(String.class));
	}

	/** A read through the observed container's proxies, which a switch of container has just invalidated. */
	@Test
	public void everyReadAnswersAsIfNothingWereObservedWhileTheProxiesAreInvalidated() throws Exception {
		Field delegateField = ObservedContainerBridge.class.getDeclaredField("delegate");
		delegateField.setAccessible(true);
		ObservedContainerDelegate delegate = (ObservedContainerDelegate) delegateField.get(this.bridge);
		delegate.observedRegistryReadServices = invalidated(
				org.opales.covalent.container.registry.RegistryReadServices.class);
		delegate.observedMetamodelReadServices = invalidated(
				org.opales.covalent.container.metamodel.MetamodelReadServices.class);
		delegate.observedServicesManagerServices = invalidated(
				org.opales.covalent.container.rr.services.ServicesManagerServices.class);
		delegate.observedParametersManagerServices = invalidated(
				org.opales.covalent.container.rr.parameters.ParametersManagerServices.class);
		delegate.observedEventsManagerServices = invalidated(
				org.opales.covalent.container.events.EventsManagerServices.class);
		Runnable consumer = () -> {
		};

		Assert.assertNull(this.bridge.getComponentInstance(String.class));
		Assert.assertFalse(this.bridge.isRegistered(String.class));
		Assert.assertNull(this.bridge.getImplementation(String.class));
		Assert.assertFalse(this.bridge.isServicesUsed(String.class, Runnable.class));
		Assert.assertNull(this.bridge.getServicesProvider(Runnable.class));
		Assert.assertFalse(this.bridge.isServicesProvided(Runnable.class));
		Assert.assertFalse(this.bridge.isActivated(Runnable.class));
		Assert.assertFalse(this.bridge.isParametersAccessed(String.class, Runnable.class));
		Assert.assertNull(this.bridge.getParametersOfferor(Runnable.class));
		Assert.assertFalse(this.bridge.isParametersOffered(Runnable.class));
		Assert.assertFalse(this.bridge.isEventsProduced(Runnable.class));
		Assert.assertNull(this.bridge.getProvidedSubscriber(Runnable.class));
		Assert.assertFalse(this.bridge.isSubscribed(Runnable.class, consumer));
	}

	@SuppressWarnings("unchecked")
	private static <T> T invalidated(final Class<T> type) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, called, args) -> {
			throw new IllegalStateException("Proxy is invalidated");
		});
	}

	// --- Per-call notifications ---

	@Test
	public void theCallStreamIsRepublishedOnlyWhileEnabled() {
		this.bridge.onProvidedServiceCallStarted(Runnable.class, this.method, String.class, "a");
		Assert.assertTrue(this.published("onProvidedServiceCallStarted").isEmpty());

		this.bridge.setCallStreamEnabled(true);
		this.bridge.onProvidedServiceCallStarted(Runnable.class, this.method, String.class, "a", 3);
		this.bridge.onProvidedServiceCallFinished(Runnable.class, this.method, String.class, 5L, null, null);

		List<Published> started = this.published("onProvidedServiceCallStarted");
		Assert.assertEquals(1, started.size());
		Assert.assertSame(Thread.currentThread(), started.get(0).arguments()[0]);
		Assert.assertEquals(Runnable.class, started.get(0).arguments()[3]);
		Assert.assertEquals(this.method, started.get(0).arguments()[4]);
		Assert.assertEquals(String.class, started.get(0).arguments()[5]);
		Assert.assertEquals("String : a\nInteger : 3", started.get(0).arguments()[6]);
		List<Published> finished = this.published("onProvidedServiceCallFinished");
		Assert.assertEquals(1, finished.size());
		Assert.assertEquals(5L, finished.get(0).arguments()[6]);
		Assert.assertEquals(CallOutcome.VOID, finished.get(0).arguments()[8]);

		this.bridge.setCallStreamEnabled(false);
		this.bridge.onProvidedServiceCallStarted(Runnable.class, this.method, String.class);
		Assert.assertEquals(1, this.published("onProvidedServiceCallStarted").size());
	}

	@Test
	public void theOrderNumbersOfAThreadsCallsRestartWhenTheCallStreamIsEnabledAgain() {
		this.bridge.setCallStreamEnabled(true);
		this.bridge.onProvidedServiceCallStarted(Runnable.class, this.method, String.class);
		this.bridge.onProvidedServiceCallFinished(Runnable.class, this.method, String.class, 1L, null, null);
		this.bridge.onUsedServiceCallStarted(Runnable.class, this.method, String.class);

		this.bridge.setCallStreamEnabled(true);
		this.bridge.onProvidedServiceCallStarted(Runnable.class, this.method, String.class);

		List<Long> sequences = new ArrayList<>();
		synchronized (this.published) {
			this.published.stream().filter(event -> event.name().contains("ServiceCall"))
					.forEach(event -> sequences.add((Long) event.arguments()[2]));
		}
		Assert.assertEquals(List.of(1L, 2L, 3L, 1L), sequences);
	}

	@Test
	public void everyKindOfCallIsRepublishedWithItsOwnNotification() {
		this.bridge.setCallStreamEnabled(true);

		this.bridge.onUsedServiceCallStarted(Runnable.class, this.method, String.class);
		this.bridge.onUsedServiceCallFinished(Runnable.class, this.method, String.class, 1L, null, "r");
		this.bridge.onAccessedParametersCallStarted(Runnable.class, this.method, String.class);
		this.bridge.onAccessedParametersCallFinished(Runnable.class, this.method, String.class, 1L, null, null);
		this.bridge.onOfferedParametersCallStarted(Runnable.class, this.method, String.class);
		this.bridge.onOfferedParametersCallFinished(Runnable.class, this.method, String.class, 1L, null, null);
		this.bridge.onEventNotificationStarted(SampleEvents.class, this.eventMethod, String.class, Integer.class, ReceiverB.class,
				true, 7L, "x");
		this.bridge.onEventNotificationFinished(SampleEvents.class, this.eventMethod, String.class, Integer.class, ReceiverB.class,
				1L, null);

		for (String name : List.of("onUsedServiceCallStarted", "onUsedServiceCallFinished",
				"onAccessedParametersCallStarted", "onAccessedParametersCallFinished",
				"onOfferedParametersCallStarted", "onOfferedParametersCallFinished",
				"onEventNotificationStarted", "onEventNotificationFinished")) {
			Assert.assertEquals(name, 1, this.published(name).size());
		}
		Assert.assertEquals("String : r", ((CallOutcome) this.published("onUsedServiceCallFinished").get(0)
				.arguments()[8]).toText().replace("void", "String : r"));
	}

	@Test
	public void aFailedCallIsRepublishedWithItsFailure() {
		this.bridge.setCallStreamEnabled(true);
		IllegalStateException failure = new IllegalStateException("boom");

		this.bridge.onProvidedServiceCallFinished(Runnable.class, this.method, String.class, 1L, failure, null);

		Published finished = this.published("onProvidedServiceCallFinished").get(0);
		Assert.assertSame(failure, finished.arguments()[7]);
		Assert.assertEquals(CallOutcome.Kind.FAILED, ((CallOutcome) finished.arguments()[8]).kind());
	}

	@Test
	public void theSupervisorsOwnThreadsLeaveNoTrace() throws InterruptedException {
		this.bridge.connect(this.startObserved());
		this.bridge.setCallStreamEnabled(true);

		for (String prefix : List.of(SupervisionThreads.PREFIX)) {
			Thread thread = new Thread(() -> {
				this.bridge.onProvidedServiceCallStarted(Runnable.class, this.method, String.class);
				this.bridge.onProvidedServiceCallFinished(Runnable.class, this.method, String.class, 1L, null, null);
				this.bridge.onUsedServiceCallStarted(Runnable.class, this.method, String.class);
				this.bridge.onEventNotificationStarted(SampleEvents.class, this.eventMethod, String.class, Integer.class,
						ReceiverB.class, false, 0L);
			}, prefix + "test");
			thread.start();
			thread.join();
		}

		Assert.assertTrue(this.published("onProvidedServiceCallStarted").isEmpty());
		Assert.assertTrue(this.published("onProvidedServiceCallFinished").isEmpty());
		Assert.assertTrue(this.published("onUsedServiceCallStarted").isEmpty());
		Assert.assertTrue(this.published("onEventNotificationStarted").isEmpty());
		Assert.assertTrue(this.published("onPortActivityChanged").isEmpty());
		Assert.assertTrue(this.bridge.readCallingThreads().isEmpty());
	}

	@Test
	public void nothingIsRepublishedAnyMoreOnceTheBridgeIsPassivated() {
		this.bridge.setCallStreamEnabled(true);
		this.bridge.stopPortActivity();

		this.bridge.onProvidedServiceCallStarted(Runnable.class, this.method, String.class);
		this.bridge.onComponentStateChanged(String.class, ComponentState.activated);

		Assert.assertTrue(this.published.isEmpty());
	}

	// --- Port activity and calling threads, whatever the call stream does ---

	@Test
	public void aPortIsActiveWhileACallRunsAndStaysSoBrieflyAfterwards() {
		this.bridge.onProvidedServiceCallStarted(Runnable.class, this.method, String.class);

		List<Published> changes = this.published("onPortActivityChanged");
		Assert.assertEquals(1, changes.size());
		Assert.assertArrayEquals(new Object[] { String.class, Runnable.class, ObservedPortKind.SERVICE, true },
				changes.get(0).arguments());

		this.bridge.onProvidedServiceCallFinished(Runnable.class, this.method, String.class, 1L, null, null);

		await(() -> this.published("onPortActivityChanged").size() == 2);
		Assert.assertArrayEquals(new Object[] { String.class, Runnable.class, ObservedPortKind.SERVICE, false },
				this.published("onPortActivityChanged").get(1).arguments());
	}

	@Test
	public void everyKindOfPortReportsItsActivityWithItsKind() {
		this.bridge.onUsedServiceCallStarted(Runnable.class, this.method, String.class);
		this.bridge.onAccessedParametersCallStarted(Runnable.class, this.method, String.class);
		this.bridge.onOfferedParametersCallStarted(Runnable.class, this.method, Integer.class);
		this.bridge.onEventNotificationStarted(SampleEvents.class, this.eventMethod, Long.class, Short.class, ReceiverA.class, false,
				0L);

		List<Object> kinds = new ArrayList<>();
		for (Published change : this.published("onPortActivityChanged")) {
			kinds.add(change.arguments()[2]);
		}
		Assert.assertEquals(List.of(ObservedPortKind.SERVICE, ObservedPortKind.PARAMETER, ObservedPortKind.PARAMETER,
				ObservedPortKind.EVENT, ObservedPortKind.EVENT), kinds);
	}

	@Test
	public void theCallingThreadsAreReadWhileObserved() {
		this.bridge.connect(this.startObserved());

		this.bridge.onProvidedServiceCallStarted(Runnable.class, this.method, String.class);

		List<ObservedCallingThread> inCall = this.bridge.readCallingThreads();
		Assert.assertEquals(1, inCall.size());
		Assert.assertTrue(inCall.get(0).inCall());
		Assert.assertEquals("String.run", inCall.get(0).call());

		this.bridge.onProvidedServiceCallFinished(Runnable.class, this.method, String.class, 1L, null, null);

		Assert.assertFalse(this.bridge.readCallingThreads().get(0).inCall());
	}

	@Test
	public void theCallingThreadsAreForgottenWhenAnotherContainerIsObserved() {
		this.bridge.connect(this.startObserved());
		this.bridge.onProvidedServiceCallStarted(Runnable.class, this.method, String.class);

		this.bridge.disconnect();

		Assert.assertTrue(this.bridge.readCallingThreads().isEmpty());
		this.bridge.connect(Covalent.getManagedContainer(OBSERVED));
		Assert.assertTrue(this.bridge.readCallingThreads().isEmpty());
	}

	@Test
	public void thePortRatesAreSampledWhileObserved() {
		this.bridge.connect(this.startObserved());

		await(() -> !this.published("onPortRatesSampled").isEmpty());

		Assert.assertFalse(this.published("onPortRatesSampled").isEmpty());
	}

	// --- The rest of the notifications ---

	@Test
	public void theLifecycleAndStructureNotificationsAreRepublished() {
		this.bridge.onComponentStateChanged(String.class, ComponentState.activated);
		this.bridge.onComponentRegistered(String.class, "i");
		this.bridge.onComponentUnregistered(String.class, "i");
		this.bridge.onServicesProvided(String.class, Runnable.class);
		this.bridge.onServicesUnprovided(String.class, Runnable.class);
		this.bridge.onServicesUsed(String.class, Runnable.class);
		this.bridge.onServicesUnused(String.class, Runnable.class);
		this.bridge.onParametersOffered(String.class, Runnable.class);
		this.bridge.onParametersUnoffered(String.class, Runnable.class);
		this.bridge.onParametersAccessed(String.class, Runnable.class);
		this.bridge.onParametersUnaccessed(String.class, Runnable.class);
		this.bridge.onEventsProduced(String.class, Runnable.class);
		this.bridge.onEventsUnproduced(String.class, Runnable.class);
		this.bridge.onEventsSubscribed(Runnable.class, Runnable.class);
		this.bridge.onEventsUnsubscribed(Runnable.class, Runnable.class);

		for (String name : List.of("onComponentStateChanged", "onComponentRegistered", "onComponentUnregistered",
				"onServicesProvided", "onServicesUnprovided", "onServicesUsed", "onServicesUnused",
				"onParametersOffered", "onParametersUnoffered", "onParametersAccessed", "onParametersUnaccessed",
				"onEventsProduced", "onEventsUnproduced", "onEventsSubscribed", "onEventsUnsubscribed")) {
			Assert.assertEquals(name, 1, this.published(name).size());
		}
	}
}
