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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.async.AsyncParameters;
import org.opales.covalent.container.events.EventsManagerEvents;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.supervision.testsupport.observed.SampleEvents;

/**
 * {@link ObservedContainerDelegate} as the observed container sees it: each notification it receives reaches the
 * owning bridge as the matching republished notification with its own arguments, the deliveries made to the delegate
 * itself are dropped before the bridge, the observed container's instrumentation and name are read through it, and
 * leaving a container whose pools already refuse their settings still completes.
 */
public class ObservedContainerDelegateForwardingTests {

	private static final String OBSERVED = "observed-container-delegate-forwarding-tests";

	/** One notification republished by the bridge. */
	private record Published(String name, Object[] arguments) {
	}

	private final List<Published> published = Collections.synchronizedList(new ArrayList<>());

	private final ObservedContainerBridge bridge = new ObservedContainerBridge();

	private final ObservedContainerDelegate delegate = new ObservedContainerDelegate(this.bridge);

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
		this.delegate.disconnect();
		Covalent.disposeManagedContainer(OBSERVED);
	}

	private List<Published> published(final String name) {
		synchronized (this.published) {
			return this.published.stream().filter(event -> event.name().equals(name)).toList();
		}
	}

	@Test
	public void nothingObservedMeansNoNameAndNoInstrumentation() {
		Assert.assertNull(this.delegate.observedContainerName());
		Assert.assertFalse(this.delegate.isInstrumented());
	}

	@Test
	public void theObservedContainersNameAndInstrumentationAreReadThroughTheDelegate() {
		CovalentContainer observed = Covalent.managedContainer(OBSERVED);
		observed.start();
		this.delegate.connect(observed);

		Assert.assertEquals(OBSERVED, this.delegate.observedContainerName());
		Assert.assertFalse(this.delegate.isInstrumented());
		observed.getParameters().setInstrumented(true);
		Assert.assertTrue(this.delegate.isInstrumented());
	}

	@Test
	public void leavingAContainerClearsEverythingEvenWhenItsPoolsRefuseTheirSettings() throws Exception {
		CovalentContainer observed = Covalent.managedContainer(OBSERVED);
		observed.start();
		this.delegate.connect(observed);
		this.delegate.observedAsyncParameters = (AsyncParameters) Proxy.newProxyInstance(
				AsyncParameters.class.getClassLoader(), new Class<?>[] { AsyncParameters.class },
				(proxy, called, args) -> {
					throw new IllegalStateException("stopping");
				});

		this.delegate.disconnect();

		Assert.assertNull(this.delegate.observedContainerName());
		Assert.assertNull(this.delegate.observedAsyncWorkersServices);
		Assert.assertNull(this.delegate.observedEventsWorkersServices);
		Assert.assertNull(this.delegate.observedRegistryReadServices);
		Assert.assertNull(this.delegate.observedAsyncParameters);
		Assert.assertNull(this.delegate.observedEventsManagerParameters);
		Assert.assertFalse(observed.lcs().isActivated(ObservedContainerDelegate.class));
	}

	@Test
	public void structureNotificationsReachTheBridgeWithTheirArguments() {
		Object instance = "instance";

		this.delegate.onComponentRegistered(String.class, instance);
		this.delegate.onComponentUnregistered(String.class, instance);
		this.delegate.onServicesProvided(String.class, Runnable.class);
		this.delegate.onServicesUnprovided(String.class, Runnable.class);
		this.delegate.onServicesUsed(String.class, Runnable.class);
		this.delegate.onServicesUnused(String.class, Runnable.class);
		this.delegate.onParametersOffered(String.class, Runnable.class);
		this.delegate.onParametersUnoffered(String.class, Runnable.class);
		this.delegate.onParametersAccessed(String.class, Runnable.class);
		this.delegate.onParametersUnaccessed(String.class, Runnable.class);
		this.delegate.onEventsProduced(String.class, Runnable.class);
		this.delegate.onEventsUnproduced(String.class, Runnable.class);
		this.delegate.onEventsSubscribed(Runnable.class, Runnable.class);
		this.delegate.onEventsUnsubscribed(Runnable.class, Runnable.class);

		Assert.assertEquals(List.of("onComponentRegistered", "onComponentUnregistered", "onServicesProvided",
				"onServicesUnprovided", "onServicesUsed", "onServicesUnused", "onParametersOffered",
				"onParametersUnoffered", "onParametersAccessed", "onParametersUnaccessed", "onEventsProduced",
				"onEventsUnproduced", "onEventsSubscribed", "onEventsUnsubscribed"),
				this.published.stream().map(Published::name).toList());
		Assert.assertArrayEquals(new Object[] { String.class, instance }, this.published.get(0).arguments());
		Assert.assertArrayEquals(new Object[] { String.class, Runnable.class }, this.published.get(4).arguments());
		Assert.assertArrayEquals(new Object[] { Runnable.class, Runnable.class }, this.published.get(12).arguments());
	}

	@Test
	public void lifecycleNotificationsReachTheBridgeAsStateChanges() {
		this.delegate.onComponentInstantiated(String.class);
		this.delegate.onComponentInitialized(String.class);
		this.delegate.onComponentActivated(String.class);
		this.delegate.onComponentPassivated(String.class);
		this.delegate.onComponentRemoved(String.class);
		this.delegate.onComponentFailed(String.class);

		Assert.assertEquals(
				List.of(ComponentState.instantiated, ComponentState.initialized, ComponentState.activated,
						ComponentState.passivated, ComponentState.removed, ComponentState.failed),
				this.published("onComponentStateChanged").stream().map(event -> event.arguments()[1]).toList());
		Assert.assertTrue(this.published("onComponentStateChanged").stream()
				.allMatch(event -> event.arguments()[0] == String.class));
	}

	@Test
	public void callNotificationsReachTheBridgeWithTheirDurationAndOutcome() {
		this.bridge.setCallStreamEnabled(true);
		RuntimeException failure = new RuntimeException("boom");

		this.delegate.onUsedServiceCallStarted(Runnable.class, this.method, String.class);
		this.delegate.onUsedServiceCallFinished(Runnable.class, this.method, String.class, 5, failure, null);
		this.delegate.onProvidedServiceCallStarted(Runnable.class, this.method, Integer.class);
		this.delegate.onProvidedServiceCallFinished(Runnable.class, this.method, Integer.class, 6, null, null);
		this.delegate.onAccessedParametersCallStarted(Runnable.class, this.method, String.class);
		this.delegate.onAccessedParametersCallFinished(Runnable.class, this.method, String.class, 7, failure, null);
		this.delegate.onOfferedParametersCallStarted(Runnable.class, this.method, Integer.class);
		this.delegate.onOfferedParametersCallFinished(Runnable.class, this.method, Integer.class, 8, null, null);

		Assert.assertEquals(List.of("onUsedServiceCallStarted", "onUsedServiceCallFinished",
				"onProvidedServiceCallStarted", "onProvidedServiceCallFinished", "onAccessedParametersCallStarted",
				"onAccessedParametersCallFinished", "onOfferedParametersCallStarted", "onOfferedParametersCallFinished"),
				this.published.stream().map(Published::name).filter(name -> name.contains("Call")).toList());
		Assert.assertEquals(String.class, this.published("onUsedServiceCallStarted").get(0).arguments()[5]);
		Assert.assertEquals(5L, this.published("onUsedServiceCallFinished").get(0).arguments()[6]);
		Assert.assertSame(failure, this.published("onUsedServiceCallFinished").get(0).arguments()[7]);
		Assert.assertEquals(Integer.class, this.published("onProvidedServiceCallFinished").get(0).arguments()[5]);
		Assert.assertEquals(6L, this.published("onProvidedServiceCallFinished").get(0).arguments()[6]);
		Assert.assertEquals(7L, this.published("onAccessedParametersCallFinished").get(0).arguments()[6]);
		Assert.assertSame(failure, this.published("onAccessedParametersCallFinished").get(0).arguments()[7]);
		Assert.assertEquals(8L, this.published("onOfferedParametersCallFinished").get(0).arguments()[6]);
	}

	@Test
	public void eventDeliveriesToAnotherConsumerReachTheBridge() {
		this.bridge.setCallStreamEnabled(true);

		this.delegate.onEventNotificationStarted(SampleEvents.class, this.eventMethod, String.class, Integer.class, ReceiverB.class,
				true, 9);
		this.delegate.onEventNotificationFinished(SampleEvents.class, this.eventMethod, String.class, Integer.class, ReceiverB.class,
				10, null);

		Assert.assertEquals(1, this.published("onEventNotificationStarted").size());
		Assert.assertEquals(9L, this.published("onEventNotificationStarted").get(0).arguments()[9]);
		Assert.assertEquals(1, this.published("onEventNotificationFinished").size());
		Assert.assertEquals(10L, this.published("onEventNotificationFinished").get(0).arguments()[8]);
	}

	@Test
	public void eventDeliveriesToTheDelegateItselfNeverReachTheBridge() throws Exception {
		this.bridge.setCallStreamEnabled(true);
		Method delegateEvent = EventsManagerEvents.class.getMethod("onEventsProduced", Class.class, Class.class);

		this.delegate.onEventNotificationStarted(SampleEvents.class, this.eventMethod, String.class,
				ObservedContainerDelegate.class, ReceiverB.class, false, 0);
		this.delegate.onEventNotificationStarted(EventsManagerEvents.class, delegateEvent, String.class,
				Integer.class, ObservedContainerDelegate.class, false, 0);
		this.delegate.onEventNotificationFinished(SampleEvents.class, this.eventMethod, String.class,
				ObservedContainerDelegate.class, ReceiverB.class, 1, null);
		this.delegate.onEventNotificationFinished(EventsManagerEvents.class, delegateEvent, String.class,
				Integer.class, ObservedContainerDelegate.class, 1, null);

		Assert.assertTrue(this.published.isEmpty());
	}
}
