/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
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
import org.opales.covalent.container.lifecycle.Activation;

/**
 * The fields of a component implementation that declare ports, items or
 * composed components are read on the whole class hierarchy of the
 * implementation, superclasses included, whatever their visibility.
 */
public class InheritedPortFieldsTests {

	@Component
	public interface Provider {
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
	public static class ProviderImpl implements Provider, ProviderServices, ProviderParameters {
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
	}

	@Events
	public interface HolderEvents {
		void onChange(String value);
	}

	@Item
	public static class HolderItem {
	}

	@Component
	public interface Child {
	}

	@Implementation
	public static class ChildImpl implements Child {
	}

	@Component
	public interface Holder {
	}

	public abstract static class HolderBase {
		@UsedServices
		private ProviderServices providerServices;

		@UsedEventsSubscribe
		private ProviderEvents.ProviderEventsSubscribe providerEventsSubscribe;

		@ProducedEvents
		private HolderEvents holderEvents;

		@Item
		private HolderItem holderItem;

		@ComposedComponent
		private final ChildImpl child = null;

		protected void recordBaseFields(final Map<String, Boolean> seen) {
			seen.put("services", this.providerServices != null);
			seen.put("subscribe", this.providerEventsSubscribe != null);
			seen.put("producedEvents", this.holderEvents != null);
			seen.put("item", this.holderItem != null);
			seen.put("composed", this.child != null);
		}
	}

	public abstract static class HolderMiddle extends HolderBase {
		@AccessedParameters
		private ProviderParameters providerParameters;

		protected void recordMiddleFields(final Map<String, Boolean> seen) {
			seen.put("parameters", this.providerParameters != null);
		}
	}

	@Implementation
	public static class HolderImpl extends HolderMiddle implements Holder {
		@Activation
		public void activate() {
			this.recordBaseFields(SEEN);
			this.recordMiddleFields(SEEN);
		}
	}

	@Component
	public interface Duplicating {
	}

	@Implementation
	public static class DuplicatingImpl extends HolderBase implements Duplicating {
		@UsedServices
		private ProviderServices otherProviderServices;
	}

	private static final Map<String, Boolean> SEEN = new ConcurrentHashMap<>();

	private CovalentContainer container;

	@Before
	public void setUp() {
		SEEN.clear();
		this.container = Covalent.container();
	}

	private void startHolder() {
		this.container.deployComponents(ProviderImpl.class, HolderImpl.class);
		this.container.start();
		Assert.assertFalse("the holder was not activated", SEEN.isEmpty());
	}

	@Test
	public void usedServicesDeclaredInASuperclassAreInjected() {
		this.startHolder();
		Assert.assertEquals(Boolean.TRUE, SEEN.get("services"));
	}

	@Test
	public void accessedParametersDeclaredInAnIntermediateSuperclassAreInjected() {
		this.startHolder();
		Assert.assertEquals(Boolean.TRUE, SEEN.get("parameters"));
	}

	@Test
	public void usedSubscribeDeclaredInASuperclassIsInjected() {
		this.startHolder();
		Assert.assertEquals(Boolean.TRUE, SEEN.get("subscribe"));
	}

	@Test
	public void producedEventsDeclaredInASuperclassAreInjected() {
		this.startHolder();
		Assert.assertEquals(Boolean.TRUE, SEEN.get("producedEvents"));
	}

	@Test
	public void anItemDeclaredInASuperclassIsInjected() {
		this.startHolder();
		Assert.assertEquals(Boolean.TRUE, SEEN.get("item"));
	}

	@Test
	public void aComposedComponentDeclaredInASuperclassIsInjected() {
		this.startHolder();
		Assert.assertEquals(Boolean.TRUE, SEEN.get("composed"));
	}

	@Test
	public void aPortDeclaredTwiceInTheHierarchyIsRefused() {
		RuntimeException failure = Assert.assertThrows(RuntimeException.class, () -> {
			this.container.deployComponents(ProviderImpl.class, DuplicatingImpl.class);
			this.container.start();
		});
		Throwable cause = failure;
		while (cause.getCause() != null) {
			cause = cause.getCause();
		}
		Assert.assertTrue(cause.getMessage(), cause.getMessage().contains("more than one @UsedServices field"));
	}
}
