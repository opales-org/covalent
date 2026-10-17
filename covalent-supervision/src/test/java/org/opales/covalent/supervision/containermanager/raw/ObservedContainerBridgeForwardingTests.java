/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.containermanager.raw;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.opales.covalent.supervision.SupervisionThreads;
import org.opales.covalent.container.async.AsyncParameters;
import org.opales.covalent.container.async.AsyncWorkersServices;
import org.opales.covalent.container.events.EventsManagerParameters;
import org.opales.covalent.container.events.EventsManagerServices;
import org.opales.covalent.container.events.EventsWorkersServices;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.lifecycle.DependencyGraph;
import org.opales.covalent.container.lifecycle.DependencyGraphServices;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.metamodel.MetamodelReadServices;
import org.opales.covalent.container.metamodel.metamodel.ReadOnlyComponentMetamodel;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.rr.parameters.ParametersManagerServices;
import org.opales.covalent.container.rr.services.ServicesManagerServices;
import org.opales.covalent.container.workers.DurationHistogram;
import org.opales.covalent.container.workers.WorkerPoolKind;
import org.opales.covalent.container.workers.WorkerPoolState;
import org.opales.covalent.supervision.testsupport.observed.SampleEvents;

/**
 * {@link ObservedContainerBridge} forwarding to hand-written stand-ins of the observed container's services: every
 * read answers what the observed service answers and falls back to the empty or absent answer once that service is
 * invalidated, every command reaches the observed service with its own arguments, every notification of the observed
 * container is republished unchanged, and the per-call notifications carry the calling thread and an order number
 * only while the call stream is enabled.
 */
public class ObservedContainerBridgeForwardingTests {

	/** One call received by a stand-in service. */
	private record Call(String name, List<Object> arguments) {
	}

	/** A stand-in for an observed service: records its calls, answers configured results, can be invalidated. */
	private static final class Fake implements InvocationHandler {

		private final List<Call> calls = Collections.synchronizedList(new ArrayList<>());

		private final Map<String, Object> results = new HashMap<>();

		private volatile boolean invalidated;

		@Override
		public Object invoke(final Object proxy, final Method called, final Object[] args) {
			if (called.getDeclaringClass() == Object.class) {
				return switch (called.getName()) {
					case "equals" -> proxy == args[0];
					case "hashCode" -> System.identityHashCode(proxy);
					default -> "fake";
				};
			}
			this.calls.add(new Call(called.getName(), args == null ? List.of() : Arrays.asList(args)));
			if (this.invalidated) {
				throw new IllegalStateException("invalidated");
			}
			if (this.results.containsKey(called.getName())) {
				return this.results.get(called.getName());
			}
			Class<?> type = called.getReturnType();
			if (type == boolean.class) {
				return false;
			}
			if (type == List.class) {
				return List.of();
			}
			if (type == Map.class) {
				return Map.of();
			}
			return null;
		}

		private Fake result(final String name, final Object value) {
			this.results.put(name, value);
			return this;
		}

		private Call only() {
			Assert.assertEquals(1, this.calls.size());
			return this.calls.get(0);
		}

		private List<String> names() {
			return this.calls.stream().map(Call::name).toList();
		}
	}

	/** One notification republished by the bridge. */
	private record Published(String name, Object[] arguments) {
	}

	private final List<Published> published = Collections.synchronizedList(new ArrayList<>());

	private final ObservedContainerBridge bridge = new ObservedContainerBridge();

	private ObservedContainerDelegate delegate;

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
		Field delegateField = ObservedContainerBridge.class.getDeclaredField("delegate");
		delegateField.setAccessible(true);
		this.delegate = (ObservedContainerDelegate) delegateField.get(this.bridge);
	}

	private <T> Fake install(final String field, final Class<T> type) throws Exception {
		Fake fake = new Fake();
		Field target = ObservedContainerDelegate.class.getDeclaredField(field);
		target.setAccessible(true);
		target.set(this.delegate, Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, fake));
		return fake;
	}

	private static <T> T sentinel(final Class<T> type) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, new Fake()));
	}

	private List<Published> published(final String name) {
		synchronized (this.published) {
			return this.published.stream().filter(event -> event.name().equals(name)).toList();
		}
	}

	private static WorkerPoolState pool(final String name, final WorkerPoolKind kind) {
		return new WorkerPoolState(name, kind, 1, 0, 0, 0, false, 0, DurationHistogram.EMPTY, DurationHistogram.EMPTY, Map.of(),
				List.of());
	}

	// --- Workers ---

	@Test
	public void theWorkerPoolsAreTheEventsPoolsThenTheGeneralOnesAndSurviveAnInvalidatedPool() throws Exception {
		WorkerPoolState events = pool("events", WorkerPoolKind.EVENTS);
		WorkerPoolState general = pool("general", WorkerPoolKind.GENERAL);
		Fake eventsWorkers = this.install("observedEventsWorkersServices", EventsWorkersServices.class)
				.result("readWorkerPools", List.of(events));
		this.install("observedAsyncWorkersServices", AsyncWorkersServices.class).result("readWorkerPools",
				List.of(general));

		Assert.assertEquals(List.of(events, general), this.bridge.readWorkerPools());

		eventsWorkers.invalidated = true;
		Assert.assertEquals(List.of(general), this.bridge.readWorkerPools());
	}

	@Test
	public void taskTrackingIsSwitchedOnBothPoolsAndReadOnTheGeneralOne() throws Exception {
		Fake events = this.install("observedEventsManagerParameters", EventsManagerParameters.class);
		Fake async = this.install("observedAsyncParameters", AsyncParameters.class)
				.result("isTaskTracking", true);

		this.bridge.setTaskTracking(true);

		Assert.assertEquals(List.of(true), events.only().arguments());
		Assert.assertEquals("setTaskTracking", events.only().name());
		Assert.assertEquals(List.of("setTaskTracking", "setTaskTracking"),
				List.of(events.only().name(), async.calls.get(0).name()));
		Assert.assertEquals(List.of(true), async.calls.get(0).arguments());
		Assert.assertTrue(this.bridge.isTaskTracking());

		async.invalidated = true;
		Assert.assertFalse(this.bridge.isTaskTracking());
		this.bridge.setTaskTracking(false);
		Assert.assertEquals(List.of(true, false), events.calls.stream().map(call -> call.arguments().get(0)).toList());
	}

	@Test
	public void theWorkerCountAndTheQueueCapacityReachTheParametersOfTheirPool() throws Exception {
		Fake events = this.install("observedEventsManagerParameters", EventsManagerParameters.class);
		Fake async = this.install("observedAsyncParameters", AsyncParameters.class);

		this.bridge.setWorkerCount(WorkerPoolKind.EVENTS, 3);
		this.bridge.setQueueCapacity(WorkerPoolKind.EVENTS, 30);
		this.bridge.setWorkerCount(WorkerPoolKind.GENERAL, 4);
		this.bridge.setQueueCapacity(WorkerPoolKind.GENERAL, 40);

		Assert.assertEquals(List.of(new Call("setWorkerCount", List.of(3)), new Call("setWorkerQueueCapacity", List.of(30))),
				events.calls);
		Assert.assertEquals(
				List.of(new Call("setGeneralWorkerCount", List.of(4)), new Call("setWorkerQueueCapacity", List.of(40))),
				async.calls);
	}

	@Test
	public void aTuningOnAnInvalidatedPoolParametersIsAbsorbed() throws Exception {
		Fake events = this.install("observedEventsManagerParameters", EventsManagerParameters.class);
		Fake async = this.install("observedAsyncParameters", AsyncParameters.class);
		events.invalidated = true;
		async.invalidated = true;

		this.bridge.setWorkerCount(WorkerPoolKind.EVENTS, 3);
		this.bridge.setQueueCapacity(WorkerPoolKind.EVENTS, 30);
		this.bridge.setWorkerCount(WorkerPoolKind.GENERAL, 4);
		this.bridge.setQueueCapacity(WorkerPoolKind.GENERAL, 40);

		Assert.assertEquals(2, events.calls.size());
		Assert.assertEquals(2, async.calls.size());
	}

	// --- Lifecycle ---

	@Test
	public void theLifecycleCommandsReachTheObservedLifecycleWithTheComponentType() throws Exception {
		Fake lifecycle = this.install("observedLifecycleServices", LifecycleServices.class);

		this.bridge.instantiateComponent(String.class);
		this.bridge.initializeComponent(String.class);
		this.bridge.activateComponent(String.class);
		this.bridge.passivateComponent(String.class);
		this.bridge.removeComponent(String.class);
		this.bridge.resetFailedComponent(String.class);

		Assert.assertEquals(List.of("instantiateComponent", "initializeComponent", "activateComponent",
				"passivateComponent", "removeComponent", "resetFailedComponent"), lifecycle.names());
		Assert.assertTrue(lifecycle.calls.stream().allMatch(call -> call.arguments().equals(List.of(String.class))));
	}

	@Test
	public void aComponentIsUndeployedByItsImplementationClass() throws Exception {
		Fake lifecycle = this.install("observedLifecycleServices", LifecycleServices.class);
		Fake metamodel = this.install("observedMetamodelReadServices", MetamodelReadServices.class);
		Fake component = new Fake().result("getImplementation", Integer.class);
		metamodel.result("readMetamodel", Proxy.newProxyInstance(ReadOnlyComponentMetamodel.class.getClassLoader(),
				new Class<?>[] { ReadOnlyComponentMetamodel.class }, component));

		this.bridge.undeployComponent(Number.class);

		Assert.assertEquals(new Call("undeployComponent", List.of(Integer.class)), lifecycle.only());
		Assert.assertEquals(new Call("readMetamodel", List.of(Number.class)), metamodel.only());
	}

	@Test
	public void undeployingWithoutMetamodelIsRefusedAndTheLifecycleIsLeftAlone() throws Exception {
		Fake lifecycle = this.install("observedLifecycleServices", LifecycleServices.class);

		Assert.assertThrows(IllegalStateException.class, () -> this.bridge.undeployComponent(Number.class));

		Assert.assertTrue(lifecycle.calls.isEmpty());
	}

	@Test
	public void aComponentStateIsReadFromTheObservedLifecycleAndAbsentOnceItIsInvalidated() throws Exception {
		Fake lifecycle = this.install("observedLifecycleServices", LifecycleServices.class).result("readComponentState",
				ComponentState.activated);

		Assert.assertEquals(ComponentState.activated, this.bridge.readComponentState(String.class));

		lifecycle.invalidated = true;
		Assert.assertNull(this.bridge.readComponentState(String.class));
	}

	// --- Registry, metamodel, dependency graph ---

	@Test
	public void theRegistryAndTheMetamodelAreReadThroughAndFallBackWhenInvalidated() throws Exception {
		Map<Class<?>, Object> instances = Map.of(String.class, "instance");
		ReadOnlyComponentMetamodel metamodelValue = sentinel(ReadOnlyComponentMetamodel.class);
		Fake registry = this.install("observedRegistryReadServices", RegistryReadServices.class)
				.result("getComponentInstance", "found").result("getInstances", instances)
				.result("isRegistered", true);
		Fake metamodel = this.install("observedMetamodelReadServices", MetamodelReadServices.class)
				.result("readMetamodel", metamodelValue).result("getType", String.class)
				.result("getImplementation", Integer.class);

		Assert.assertEquals("found", this.bridge.<String, String>getComponentInstance(String.class));
		Assert.assertSame(instances, this.bridge.getInstances());
		Assert.assertTrue(this.bridge.isRegistered(String.class));
		Assert.assertSame(metamodelValue, this.bridge.readMetamodel(String.class));
		Assert.assertEquals(String.class, this.bridge.getType(Integer.class));
		Assert.assertEquals(Integer.class, this.bridge.getImplementation(Number.class));
		Assert.assertEquals(List.of(String.class), registry.calls.get(0).arguments());
		Assert.assertEquals(List.of(Integer.class), metamodel.calls.get(1).arguments());

		registry.invalidated = true;
		metamodel.invalidated = true;
		Assert.assertNull(this.bridge.getComponentInstance(String.class));
		Assert.assertTrue(this.bridge.getInstances().isEmpty());
		Assert.assertFalse(this.bridge.isRegistered(String.class));
		Assert.assertNull(this.bridge.readMetamodel(String.class));
		Assert.assertNull(this.bridge.getType(Integer.class));
		Assert.assertNull(this.bridge.getImplementation(Number.class));
	}

	@Test
	public void theDependencyGraphIsReadThroughAndAbsentWhenInvalidated() throws Exception {
		DependencyGraph graph = sentinel(DependencyGraph.class);
		Fake graphs = this.install("observedDependencyGraphServices", DependencyGraphServices.class)
				.result("getDependencyGraph", graph).result("hasDependencyGraph", true);

		Assert.assertSame(graph, this.bridge.getDependencyGraph());
		Assert.assertTrue(this.bridge.hasDependencyGraph());

		graphs.invalidated = true;
		Assert.assertNull(this.bridge.getDependencyGraph());
		Assert.assertFalse(this.bridge.hasDependencyGraph());
	}

	// --- Services ---

	@Test
	public void theServicesAreReadAndCommandedThroughTheObservedServicesManager() throws Exception {
		Runnable provider = () -> {
		};
		Runnable user = () -> {
		};
		Map<Class<?>, Set<Class<?>>> relations = Map.of(String.class, Set.of(Runnable.class));
		Fake services = this.install("observedServicesManagerServices", ServicesManagerServices.class)
				.result("isServicesUsed", true).result("getServicesProvider", provider)
				.result("createUsedServices", user).result("reconnectUsedServices", user)
				.result("isServicesProvided", true).result("getProviderToServices", relations)
				.result("getServiceToUsers", relations).result("isActivated", true);

		Assert.assertTrue(this.bridge.isServicesUsed(String.class, Runnable.class));
		Assert.assertSame(provider, this.bridge.getServicesProvider(Runnable.class));
		Assert.assertSame(user, this.bridge.createUsedServices(String.class, Runnable.class));
		Assert.assertSame(user, this.bridge.reconnectUsedServices(String.class, Runnable.class));
		Assert.assertTrue(this.bridge.isServicesProvided(Runnable.class));
		Assert.assertSame(relations, this.bridge.getProviderToServices());
		Assert.assertSame(relations, this.bridge.getServiceToUsers());
		Assert.assertTrue(this.bridge.isActivated(Runnable.class));
		this.bridge.createProvidedServices(Runnable.class);
		this.bridge.createProvidedServices(String.class, Runnable.class);
		this.bridge.deleteUsedServices(String.class, Runnable.class);
		this.bridge.deleteProvidedServices(Runnable.class);
		this.bridge.activateServices(Runnable.class);
		this.bridge.passivateServices(Runnable.class);

		Assert.assertEquals(List.of(new Call("isServicesUsed", List.of(String.class, Runnable.class)),
				new Call("getServicesProvider", List.of(Runnable.class)),
				new Call("createUsedServices", List.of(String.class, Runnable.class)),
				new Call("reconnectUsedServices", List.of(String.class, Runnable.class)),
				new Call("isServicesProvided", List.of(Runnable.class)), new Call("getProviderToServices", List.of()),
				new Call("getServiceToUsers", List.of()), new Call("isActivated", List.of(Runnable.class)),
				new Call("createProvidedServices", List.of(Runnable.class)),
				new Call("createProvidedServices", List.of(String.class, Runnable.class)),
				new Call("deleteUsedServices", List.of(String.class, Runnable.class)),
				new Call("deleteProvidedServices", List.of(Runnable.class)),
				new Call("activateServices", List.of(Runnable.class)),
				new Call("passivateServices", List.of(Runnable.class))), services.calls);

		services.invalidated = true;
		Assert.assertFalse(this.bridge.isServicesUsed(String.class, Runnable.class));
		Assert.assertNull(this.bridge.getServicesProvider(Runnable.class));
		Assert.assertFalse(this.bridge.isServicesProvided(Runnable.class));
		Assert.assertTrue(this.bridge.getProviderToServices().isEmpty());
		Assert.assertTrue(this.bridge.getServiceToUsers().isEmpty());
		Assert.assertFalse(this.bridge.isActivated(Runnable.class));
	}

	// --- Parameters ---

	@Test
	public void theParametersAreReadAndCommandedThroughTheObservedParametersManager() throws Exception {
		Runnable offeror = () -> {
		};
		Runnable accessed = () -> {
		};
		Map<Class<?>, Set<Class<?>>> relations = Map.of(String.class, Set.of(Runnable.class));
		Fake parameters = this.install("observedParametersManagerServices", ParametersManagerServices.class)
				.result("isParametersAccessed", true).result("getParametersOfferor", offeror)
				.result("createAccessedParameters", accessed).result("isParametersOffered", true)
				.result("getOfferorToParameters", relations).result("getParametersToAccessor", relations);

		Assert.assertTrue(this.bridge.isParametersAccessed(String.class, Runnable.class));
		Assert.assertSame(offeror, this.bridge.getParametersOfferor(Runnable.class));
		Assert.assertSame(accessed, this.bridge.createAccessedParameters(String.class, Runnable.class));
		Assert.assertTrue(this.bridge.isParametersOffered(Runnable.class));
		Assert.assertSame(relations, this.bridge.getOfferorToParameters());
		Assert.assertSame(relations, this.bridge.getParametersToAccessor());
		this.bridge.createOfferedParameters(Runnable.class);
		this.bridge.createOfferedParameters(String.class, Runnable.class);
		this.bridge.deleteAccessedParameters(String.class, Runnable.class);
		this.bridge.deleteOfferedParameters(Runnable.class);
		this.bridge.activateParameters(Runnable.class);
		this.bridge.passivateParameters(Runnable.class);

		Assert.assertEquals(List.of("isParametersAccessed", "getParametersOfferor", "createAccessedParameters",
				"isParametersOffered", "getOfferorToParameters", "getParametersToAccessor", "createOfferedParameters",
				"createOfferedParameters", "deleteAccessedParameters", "deleteOfferedParameters", "activateParameters",
				"passivateParameters"), parameters.names());
		Assert.assertEquals(List.of(String.class, Runnable.class), parameters.calls.get(7).arguments());
		Assert.assertEquals(List.of(String.class, Runnable.class), parameters.calls.get(8).arguments());
		Assert.assertEquals(List.of(Runnable.class), parameters.calls.get(11).arguments());

		parameters.invalidated = true;
		Assert.assertFalse(this.bridge.isParametersAccessed(String.class, Runnable.class));
		Assert.assertNull(this.bridge.getParametersOfferor(Runnable.class));
		Assert.assertFalse(this.bridge.isParametersOffered(Runnable.class));
		Assert.assertTrue(this.bridge.getOfferorToParameters().isEmpty());
		Assert.assertTrue(this.bridge.getParametersToAccessor().isEmpty());
	}

	// --- Events ---

	@Test
	public void theEventsAreReadAndCommandedThroughTheObservedEventsManager() throws Exception {
		Runnable producer = () -> {
		};
		Runnable subscriber = () -> {
		};
		Runnable provided = () -> {
		};
		Runnable consumer = () -> {
		};
		Map<Class<?>, Set<Class<?>>> producers = Map.of(String.class, Set.of(Runnable.class));
		Fake events = this.install("observedEventsManagerServices", EventsManagerServices.class)
				.result("getSubscribers", Map.of()).result("getProducerToEvent", producers)
				.result("createProducer", producer).result("isEventsProduced", true)
				.result("createUsedSubscriber", subscriber).result("getProvidedSubscriber", provided)
				.result("isSubscribed", true);

		Assert.assertTrue(this.bridge.getSubscribers().isEmpty());
		Assert.assertSame(producers, this.bridge.getProducerToEvent());
		Assert.assertSame(producer, this.bridge.<Runnable>createProducer(Runnable.class, String.class, null, null));
		Assert.assertTrue(this.bridge.isEventsProduced(Runnable.class));
		Assert.assertSame(subscriber, this.bridge.<Runnable, Runnable>createUsedSubscriber(Runnable.class, Runnable.class));
		Assert.assertSame(provided, this.bridge.<Runnable, Runnable>getProvidedSubscriber(Runnable.class));
		Assert.assertTrue(this.bridge.isSubscribed(Runnable.class, consumer));
		this.bridge.<Runnable>deleteProducer(String.class, Runnable.class);
		this.bridge.activateProducer(Runnable.class);
		this.bridge.passivateProducer(Runnable.class);
		this.bridge.<Runnable>subscribe(Runnable.class, consumer, null);
		this.bridge.<Runnable>unsubscribe(Runnable.class, consumer);

		Assert.assertEquals(List.of("getSubscribers", "getProducerToEvent", "createProducer", "isEventsProduced",
				"createUsedSubscriber", "getProvidedSubscriber", "isSubscribed", "deleteProducer", "activateProducer",
				"passivateProducer", "subscribe", "unsubscribe"), events.names());
		Assert.assertEquals(Arrays.asList(Runnable.class, String.class, null, null), events.calls.get(2).arguments());
		Assert.assertEquals(List.of(String.class, Runnable.class), events.calls.get(7).arguments());
		Assert.assertEquals(Arrays.asList(Runnable.class, consumer, null), events.calls.get(10).arguments());
		Assert.assertEquals(List.of(Runnable.class, consumer), events.calls.get(11).arguments());

		events.invalidated = true;
		Assert.assertTrue(this.bridge.getSubscribers().isEmpty());
		Assert.assertTrue(this.bridge.getProducerToEvent().isEmpty());
		Assert.assertFalse(this.bridge.isEventsProduced(Runnable.class));
		Assert.assertNull(this.bridge.getProvidedSubscriber(Runnable.class));
		Assert.assertFalse(this.bridge.isSubscribed(Runnable.class, consumer));
	}

	@Test
	public void aCommandOnAnInvalidatedServiceIsNotAbsorbed() throws Exception {
		Fake services = this.install("observedServicesManagerServices", ServicesManagerServices.class);
		services.invalidated = true;

		Assert.assertThrows(IllegalStateException.class, () -> this.bridge.activateServices(Runnable.class));
	}

	// --- Republished notifications ---

	@Test
	public void everyStructureAndLifecycleNotificationIsRepublishedUnchanged() {
		Object instance = "instance";

		this.bridge.onComponentRegistered(String.class, instance);
		this.bridge.onComponentUnregistered(String.class, instance);
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
		this.bridge.onComponentStateChanged(String.class, ComponentState.failed);

		Assert.assertEquals(List.of("onComponentRegistered", "onComponentUnregistered", "onServicesProvided",
				"onServicesUnprovided", "onServicesUsed", "onServicesUnused", "onParametersOffered",
				"onParametersUnoffered", "onParametersAccessed", "onParametersUnaccessed", "onEventsProduced",
				"onEventsUnproduced", "onEventsSubscribed", "onEventsUnsubscribed", "onComponentStateChanged"),
				this.published.stream().map(Published::name).toList());
		Assert.assertArrayEquals(new Object[] { String.class, instance }, this.published("onComponentRegistered").get(0).arguments());
		Assert.assertArrayEquals(new Object[] { String.class, Runnable.class }, this.published("onServicesUsed").get(0).arguments());
		Assert.assertArrayEquals(new Object[] { String.class, Runnable.class }, this.published("onParametersAccessed").get(0).arguments());
		Assert.assertArrayEquals(new Object[] { String.class, Runnable.class }, this.published("onEventsProduced").get(0).arguments());
		Assert.assertArrayEquals(new Object[] { Runnable.class, Runnable.class }, this.published("onEventsSubscribed").get(0).arguments());
		Assert.assertArrayEquals(new Object[] { String.class, ComponentState.failed },
				this.published("onComponentStateChanged").get(0).arguments());
	}

	@Test
	public void noPerCallNotificationIsRepublishedWhileTheCallStreamIsDisabled() {
		this.bridge.onUsedServiceCallStarted(Runnable.class, this.method, String.class);
		this.bridge.onUsedServiceCallFinished(Runnable.class, this.method, String.class, 5, null, null);
		this.bridge.onProvidedServiceCallStarted(Runnable.class, this.method, String.class);
		this.bridge.onProvidedServiceCallFinished(Runnable.class, this.method, String.class, 5, null, null);
		this.bridge.onAccessedParametersCallStarted(Runnable.class, this.method, String.class);
		this.bridge.onAccessedParametersCallFinished(Runnable.class, this.method, String.class, 5, null, null);
		this.bridge.onOfferedParametersCallStarted(Runnable.class, this.method, String.class);
		this.bridge.onOfferedParametersCallFinished(Runnable.class, this.method, String.class, 5, null, null);
		this.bridge.onEventNotificationStarted(SampleEvents.class, this.eventMethod, String.class, Integer.class, ReceiverA.class,
				false, 0);
		this.bridge.onEventNotificationFinished(SampleEvents.class, this.eventMethod, String.class, Integer.class, ReceiverA.class, 5,
				null);

		Assert.assertTrue(this.published.stream().noneMatch(event -> event.name().contains("Call")
				|| event.name().contains("Notification")));
	}

	@Test
	public void everyPerCallNotificationCarriesTheCallingThreadAndAnIncreasingOrderNumber() {
		this.bridge.setCallStreamEnabled(true);
		RuntimeException failure = new RuntimeException("boom");

		this.bridge.onUsedServiceCallStarted(Runnable.class, this.method, String.class, "argument");
		this.bridge.onUsedServiceCallFinished(Runnable.class, this.method, String.class, 7, failure, null);
		this.bridge.onProvidedServiceCallStarted(Runnable.class, this.method, String.class);
		this.bridge.onProvidedServiceCallFinished(Runnable.class, this.method, String.class, 8, null, null);
		this.bridge.onAccessedParametersCallStarted(Runnable.class, this.method, String.class);
		this.bridge.onAccessedParametersCallFinished(Runnable.class, this.method, String.class, 9, null, null);
		this.bridge.onOfferedParametersCallStarted(Runnable.class, this.method, String.class);
		this.bridge.onOfferedParametersCallFinished(Runnable.class, this.method, String.class, 10, null, null);
		this.bridge.onEventNotificationStarted(SampleEvents.class, this.eventMethod, String.class, Integer.class, ReceiverB.class, true,
				11);
		this.bridge.onEventNotificationFinished(SampleEvents.class, this.eventMethod, String.class, Integer.class, ReceiverB.class, 12,
				failure);

		List<Published> calls = this.published.stream().filter(event -> event.name().contains("Call")
				|| event.name().contains("Notification")).toList();
		Assert.assertEquals(List.of("onUsedServiceCallStarted", "onUsedServiceCallFinished",
				"onProvidedServiceCallStarted", "onProvidedServiceCallFinished", "onAccessedParametersCallStarted",
				"onAccessedParametersCallFinished", "onOfferedParametersCallStarted", "onOfferedParametersCallFinished",
				"onEventNotificationStarted", "onEventNotificationFinished"),
				calls.stream().map(Published::name).toList());
		for (int index = 0; index < calls.size(); index++) {
			Object[] arguments = calls.get(index).arguments();
			Assert.assertSame(Thread.currentThread(), arguments[0]);
			Assert.assertTrue(arguments[1] instanceof Long);
			Assert.assertEquals(index + 1L, arguments[2]);
			boolean isEvent = index >= 8;
			Assert.assertEquals(isEvent ? SampleEvents.class : Runnable.class, arguments[3]);
			Assert.assertEquals(isEvent ? this.eventMethod : this.method, arguments[4]);
		}
		Assert.assertTrue(calls.get(0).arguments()[6].toString().contains("argument"));
		Assert.assertEquals(7L, calls.get(1).arguments()[6]);
		Assert.assertSame(failure, calls.get(1).arguments()[7]);
		Assert.assertEquals(Integer.class, calls.get(8).arguments()[6]);
		Assert.assertEquals(ReceiverB.class, calls.get(8).arguments()[7]);
		Assert.assertEquals(true, calls.get(8).arguments()[8]);
		Assert.assertEquals(11L, calls.get(8).arguments()[9]);
		Assert.assertEquals(12L, calls.get(9).arguments()[8]);
		Assert.assertSame(failure, calls.get(9).arguments()[9]);
	}

	@Test
	public void withOnlyOneOfTheTwoPoolsObservedThereIsNothingToReadOrSwitch() throws Exception {
		Fake events = this.install("observedEventsWorkersServices", EventsWorkersServices.class)
				.result("readWorkerPools", List.of(pool("events", WorkerPoolKind.EVENTS)));
		Fake eventsParameters = this.install("observedEventsManagerParameters", EventsManagerParameters.class);

		Assert.assertTrue(this.bridge.readWorkerPools().isEmpty());
		this.bridge.setTaskTracking(true);

		Assert.assertTrue(events.calls.isEmpty());
		Assert.assertTrue(eventsParameters.calls.isEmpty());
	}

	@Test
	public void callsRunningOnTheSupervisorsOwnThreadsAreNeitherCountedNorRepublished() throws Exception {
		this.bridge.setCallStreamEnabled(true);
		for (String prefix : List.of(SupervisionThreads.PREFIX)) {
			Thread thread = new Thread(() -> {
				this.bridge.onUsedServiceCallStarted(Runnable.class, this.method, String.class);
				this.bridge.onUsedServiceCallFinished(Runnable.class, this.method, String.class, 1, null, null);
				this.bridge.onProvidedServiceCallStarted(Runnable.class, this.method, String.class);
				this.bridge.onProvidedServiceCallFinished(Runnable.class, this.method, String.class, 1, null, null);
				this.bridge.onAccessedParametersCallStarted(Runnable.class, this.method, String.class);
				this.bridge.onAccessedParametersCallFinished(Runnable.class, this.method, String.class, 1, null, null);
				this.bridge.onOfferedParametersCallStarted(Runnable.class, this.method, String.class);
				this.bridge.onOfferedParametersCallFinished(Runnable.class, this.method, String.class, 1, null, null);
				this.bridge.onEventNotificationStarted(SampleEvents.class, this.eventMethod, String.class, Integer.class,
						ReceiverA.class, false, 0);
				this.bridge.onEventNotificationFinished(SampleEvents.class, this.eventMethod, String.class, Integer.class,
						ReceiverA.class, 1, null);
			}, prefix + "worker");
			thread.start();
			thread.join();
		}

		Assert.assertTrue(this.published.isEmpty());
	}
}
