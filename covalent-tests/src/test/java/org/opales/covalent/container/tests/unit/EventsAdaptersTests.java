/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Method;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.container.events.EventsManagerEvents.EventsManagerEventsAdapter;
import org.opales.covalent.container.rr.parameters.ParametersManagerEvents.ParametersManagerEventsAdapter;
import org.opales.covalent.container.rr.services.ServicesManagerEvents.ServicesManagerEventsAdapter;

/**
 * <p>
 * The *Events interfaces each expose an empty adapter meant to be
 * subclassed by consumers that only care about a subset of notifications.
 * These tests instantiate the adapters directly and invoke every method to
 * confirm they remain harmless no-ops.
 * </p>
 */
public class EventsAdaptersTests {

	@Test
	public void eventsManagerEventsAdapterIgnoresAllNotifications() {
		EventsManagerEventsAdapter adapter = new EventsManagerEventsAdapter();
		Method method = Object.class.getMethods()[0];

		adapter.onEventsProduced(Object.class, Runnable.class);
		adapter.onEventsUnproduced(Object.class, Runnable.class);
		adapter.onEventsSubscribed(Runnable.class, Runnable.class);
		adapter.onEventsUnsubscribed(Runnable.class, Runnable.class);
		adapter.onEventNotificationStarted(Runnable.class, method, Object.class, Object.class, Thread.class, true, 0L,
				"arg");
		adapter.onEventNotificationFinished(Runnable.class, method, Object.class, Object.class, Thread.class, 1L,
				new RuntimeException("failure"), "arg");

		Assert.assertNotNull(adapter);
	}

	@Test
	public void parametersManagerEventsAdapterIgnoresAllNotifications() {
		ParametersManagerEventsAdapter adapter = new ParametersManagerEventsAdapter();
		Method method = Object.class.getMethods()[0];

		adapter.onParametersOffered(Object.class, Runnable.class);
		adapter.onParametersUnoffered(Object.class, Runnable.class);
		adapter.onParametersAccessed(Object.class, Runnable.class);
		adapter.onParametersUnaccessed(Object.class, Runnable.class);
		adapter.onAccessedParametersCallStarted(Runnable.class, method, Object.class, "arg");
		adapter.onAccessedParametersCallFinished(Runnable.class, method, Object.class, 1L,
				new RuntimeException("failure"), null, "arg");
		adapter.onOfferedParametersCallStarted(Runnable.class, method, Object.class, "arg");
		adapter.onOfferedParametersCallFinished(Runnable.class, method, Object.class, 1L,
				new RuntimeException("failure"), null, "arg");

		Assert.assertNotNull(adapter);
	}

	@Test
	public void servicesManagerEventsAdapterIgnoresAllNotifications() {
		ServicesManagerEventsAdapter adapter = new ServicesManagerEventsAdapter();
		Method method = Object.class.getMethods()[0];

		adapter.onServicesProvided(Object.class, Runnable.class);
		adapter.onServicesUnprovided(Object.class, Runnable.class);
		adapter.onServicesUsed(Object.class, Runnable.class);
		adapter.onServicesUnused(Object.class, Runnable.class);
		adapter.onUsedServiceCallStarted(Runnable.class, method, Object.class, "arg");
		adapter.onUsedServiceCallFinished(Runnable.class, method, Object.class, 1L, new RuntimeException("failure"),
				null, "arg");
		adapter.onProvidedServiceCallStarted(Runnable.class, method, Object.class, "arg");
		adapter.onProvidedServiceCallFinished(Runnable.class, method, Object.class, 1L,
				new RuntimeException("failure"), null, "arg");

		Assert.assertNotNull(adapter);
	}
}
