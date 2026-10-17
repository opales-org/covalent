/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.ManagedContainerRelay;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.internals.Internal;
import org.opales.covalent.container.lifecycle.LifecycleManager;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.CovalentTests;
import org.opales.covalent.container.tests.unit.internalreserved.impl.PosingInternalImpl;

/**
 * {@code @Internal} is reserved to Covalent's own components, kept across
 * {@code stop()}: neither a deployed nor a raw component can carry it, and no
 * raw port can be opened on behalf of one of them. The managed container
 * relay, Covalent's own raw component, can neither be unregistered nor lend
 * its identity to a raw port.
 */
public class InternalReservedTests extends CovalentTests {

	private static final String MANAGED = "internal-reserved-tests";

	/** A raw component posing as one of Covalent's own components. */
	@Internal
	public static class RawPosingInternal {
	}

	/** Events used for the raw ports. */
	@Events
	public interface ProbeEvents {
		void onProbe();
	}

	private CovalentContainer container;

	@After
	public void disposeContainer() {
		if (this.container != null) {
			this.container.dispose();
		}
		Covalent.disposeManagedContainer(MANAGED);
	}

	@Test
	public void anInternalComponentCannotBeDeployed() {
		this.container = Covalent.container();

		RuntimeException failure = Assert.assertThrows(RuntimeException.class,
				() -> this.container.deployComponents(PosingInternalImpl.class));

		Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("@Internal is reserved"));
	}

	@Test
	public void anInternalRawComponentCannotBeRegistered() {
		this.container = Covalent.container();

		IllegalArgumentException failure = Assert.assertThrows(IllegalArgumentException.class,
				() -> this.container.rcs().registerComponent(new RawPosingInternal()));

		Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("@Internal is reserved"));
	}

	@Test
	public void noRawPortOnBehalfOfAnInternalComponent() {
		this.container = Covalent.container();
		RawComponentsServices rcs = this.container.rcs();
		rcs.registerComponent(this);
		ProbeEvents consumer = () -> { };

		Assert.assertThrows(IllegalArgumentException.class,
				() -> rcs.subscribe(LifecycleManager.class, ProbeEvents.class, consumer));
		Assert.assertThrows(IllegalArgumentException.class,
				() -> rcs.produceEvents(LifecycleManager.class, ProbeEvents.class));
	}

	@Test
	public void noRawPortOnBehalfOfTheManagedContainerRelay() {
		RawComponentsServices rcs = Covalent.managedContainer(MANAGED).rcs();
		ProbeEvents consumer = () -> { };

		Assert.assertThrows(IllegalArgumentException.class,
				() -> rcs.subscribe(ManagedContainerRelay.class, ProbeEvents.class, consumer));
	}

	@Test
	public void theManagedContainerRelayCannotBeUnregistered() {
		RawComponentsServices rcs = Covalent.managedContainer(MANAGED).rcs();

		Assert.assertThrows(IllegalArgumentException.class, () -> rcs.unregisterComponent(ManagedContainerRelay.class));
	}
}
