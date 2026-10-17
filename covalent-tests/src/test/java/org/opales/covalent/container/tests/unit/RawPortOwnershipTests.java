/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.raw.RawComponentsServices;

/**
 * Verifies that withdrawing a raw port on behalf of a component that does
 * not own it is refused and leaves the owner's port fully working: the
 * ownership check happens before anything of the port is torn down.
 */
public class RawPortOwnershipTests {

	public interface PingServices {
		String ping();
	}

	public interface SizeParameters {
		int size();
	}

	public interface ChangeEvents {
		void onChange(String what);
	}

	public static class Owner implements PingServices, SizeParameters {
		@Override
		public String ping() {
			return "pong";
		}

		@Override
		public int size() {
			return 42;
		}
	}

	public static class Intruder {
	}

	public static class Listener implements ChangeEvents {
		private final List<String> received = new ArrayList<>();

		@Override
		public void onChange(final String what) {
			this.received.add(what);
		}
	}

	private final Listener listener = new Listener();

	private RawComponentsServices rcs;

	@Before
	public void setUp() {
		CovalentContainer container = Covalent.container();
		container.getParameters().setSynchronousEvents(true);
		this.rcs = container.rcs();
		this.rcs.registerComponent(this);
		this.rcs.registerComponent(Owner.class, new Owner());
		this.rcs.registerComponent(Intruder.class, new Intruder());
		this.rcs.registerComponent(Listener.class, this.listener);
	}

	@Test
	public void unprovidingServicesOfAnotherComponentIsRefusedAndTheServicesKeepWorking() {
		this.rcs.provideServices(Owner.class, PingServices.class);
		PingServices used = this.rcs.useServices(Owner.class, PingServices.class);

		Assert.assertThrows(IllegalArgumentException.class,
				() -> this.rcs.unprovideServices(Intruder.class, PingServices.class));

		Assert.assertEquals("pong", used.ping());
		PingServices usedAgain = this.rcs.useServices(Owner.class, PingServices.class);
		Assert.assertEquals("pong", usedAgain.ping());
	}

	@Test
	public void unofferingParametersOfAnotherComponentIsRefusedAndTheParametersKeepWorking() {
		this.rcs.offerParameters(Owner.class, SizeParameters.class);
		SizeParameters accessed = this.rcs.accessParameters(Owner.class, SizeParameters.class);

		Assert.assertThrows(IllegalArgumentException.class,
				() -> this.rcs.unofferParameters(Intruder.class, SizeParameters.class));

		Assert.assertEquals(42, accessed.size());
		SizeParameters accessedAgain = this.rcs.accessParameters(Owner.class, SizeParameters.class);
		Assert.assertEquals(42, accessedAgain.size());
	}

	@Test
	public void unproducingEventsOfAnotherComponentIsRefusedAndTheEventsKeepFlowing() {
		ChangeEvents notifier = this.rcs.produceEvents(Owner.class, ChangeEvents.class);
		this.rcs.subscribe(Listener.class, ChangeEvents.class, this.listener);

		Assert.assertThrows(IllegalArgumentException.class,
				() -> this.rcs.unproduceEvents(Intruder.class, ChangeEvents.class));

		notifier.onChange("still flowing");
		Assert.assertEquals(List.of("still flowing"), this.listener.received);
	}

	@Test
	public void portsDeclaredFromOutsideTheComponentCanBeWithdrawnAndDeclaredAgain() {
		// The test class is neither the owner nor the component the ports belong to:
		// the owner is the type given explicitly, as for legacy code that cannot be changed.
		this.rcs.provideServices(Owner.class, PingServices.class);
		this.rcs.offerParameters(Owner.class, SizeParameters.class);
		ChangeEvents notifier = this.rcs.produceEvents(Owner.class, ChangeEvents.class);

		this.rcs.unprovideServices(Owner.class, PingServices.class);
		this.rcs.unofferParameters(Owner.class, SizeParameters.class);
		this.rcs.unproduceEvents(Owner.class, ChangeEvents.class);

		this.rcs.provideServices(Owner.class, PingServices.class);
		this.rcs.offerParameters(Owner.class, SizeParameters.class);
		notifier = this.rcs.produceEvents(Owner.class, ChangeEvents.class);
		this.rcs.subscribe(Listener.class, ChangeEvents.class, this.listener);

		Assert.assertEquals("pong", this.rcs.useServices(Owner.class, PingServices.class).ping());
		Assert.assertEquals(42, this.rcs.accessParameters(Owner.class, SizeParameters.class).size());
		notifier.onChange("declared again");
		Assert.assertEquals(List.of("declared again"), this.listener.received);
	}
}
