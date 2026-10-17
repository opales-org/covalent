/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.events.EventsManagerServices;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.CovalentContainerizedTests;
import org.opales.covalent.container.tests.unit.RawComponentsContractTests.RawSettings;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.FullyConnectedEvents;
import org.opales.covalent.container.tests.unit.annotatedcomponents.fullyconnected.FullyConnectedEvents.FullyConnectedEventsSubscribe;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.ComponentAServices;

/**
 * Verifies that invoking a client through a surrogate proxy, before any
 * provider is declared for the corresponding services, parameters or events
 * interface, fails with a clear, user-facing message rather than the
 * directory's internal "not bound" wording.
 */
public class SurrogateProxyContainerizedTests extends CovalentContainerizedTests {

	private final RawComponentsServices rcs = Covalent.main.rcs();

	@Before
	public void registerTestInstanceAsRawComponent() {
		this.rcs.registerComponent(this);
	}

	@Test
	public void usedServicesSurrogateFailsWithAClearMessageWhenNoProviderIsDeclared() {
		ComponentAServices services = this.rcs.useServices(ComponentAServices.class);

		IllegalStateException e = Assert.assertThrows("Calling through a services surrogate should fail while no provider is declared.",
				IllegalStateException.class,
				() -> services.doSomething());
		Assert.assertTrue(this.isExceptionIncludingText(e,
		"No provider is currently declared for the services interface ComponentAServices"));
	}

	@Test
	public void accessedParametersSurrogateFailsWithAClearMessageWhenNoOfferorIsDeclared() {
		RawSettings parameters = this.rcs.accessParameters(RawSettings.class);

		IllegalStateException e = Assert.assertThrows("Calling through a parameters surrogate should fail while no offeror is declared.",
				IllegalStateException.class,
				() -> parameters.setValue("value"));
		Assert.assertTrue(this.isExceptionIncludingText(e,
		"No provider is currently declared for the parameters interface RawSettings"));
	}

	@Test
	public void usedSubscriberSurrogateFailsWithAClearMessageWhenNoProducerIsDeclared() {
		EventsManagerServices eventsManagerServices = this.rcs.useServices(EventsManagerServices.class);
		FullyConnectedEventsSubscribe subscriber = eventsManagerServices.createUsedSubscriber(FullyConnectedEvents.class,
				FullyConnectedEventsSubscribe.class);

		IllegalStateException e = Assert.assertThrows("Calling through a used-subscriber surrogate should fail while no producer is declared.",
				IllegalStateException.class,
				() -> subscriber.subscribe(new FullyConnectedEventsNoOpMock()));
		Assert.assertTrue(this.isExceptionIncludingText(e,
		"No producer is currently declared for the events interface FullyConnectedEvents"));
	}

	private static class FullyConnectedEventsNoOpMock implements FullyConnectedEvents {

		@Override
		public void onEvent(final String string) {
		}

	}

}
