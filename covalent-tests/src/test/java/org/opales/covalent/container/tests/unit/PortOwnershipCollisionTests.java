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
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Parameters;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.exceptions.ValidationException;

/**
 * Each produced-events, provided-services, or offered-parameters interface
 * must have exactly one owner among the deployed components: a second
 * component that also claims one is rejected, and the first-registered
 * owner is kept rather than silently overwritten.
 * <p>
 * Three isolated fixture pairs - one per port kind - are used instead of a
 * single component colliding on all three at once, because
 * {@link org.opales.covalent.container.metamodel.impl.MetamodelManagerImpl#registerMetamodel}
 * checks events, then services, then parameters in that order and reports
 * the first violation it finds: a component colliding on every port kind at
 * once would only ever surface the events violation at the {@code strict}
 * level, leaving the services and parameters checks unverified.
 */
public class PortOwnershipCollisionTests {

	private CovalentContainer container;

	@After
	public void tearDown() {
		if (this.container != null) {
			this.container.dispose();
		}
	}

	// --- Produced events: one owner per events interface. ---

	@Test
	public void producedEventsCollisionIsRejectedInStrictMode() {
		this.container = Covalent.container();
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.strict);

		RuntimeException thrown = Assert.assertThrows(RuntimeException.class,
				() -> this.container.deployComponents(EventsFirstImpl.class, EventsSecondImpl.class));

		Assert.assertTrue("expected the ValidationException to be the reported cause",
				thrown.getCause() instanceof ValidationException);
		Assert.assertTrue("expected the message to name the contested events interface",
				thrown.getMessage().contains(PingEvents.class.getSimpleName()));
		Assert.assertTrue("expected the message to explain the single-owner rule",
				thrown.getMessage().contains("already produced"));
	}

	@Test
	public void producedEventsCollisionDoesNotCrashAtDefaultVerificationLevel() {
		this.container = Covalent.container();

		this.container.deployComponents(EventsFirstImpl.class, EventsSecondImpl.class);
		this.container.start();
		this.container.stop();
	}

	@Test
	public void unregisteringTheLosingComponentKeepsTheWinnersOwnership() {
		this.container = Covalent.container();

		this.container.deployComponents(EventsFirstImpl.class, EventsSecondImpl.class);
		this.container.lcs().undeployComponent(EventsSecondImpl.class);

		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.strict);
		RuntimeException thrown = Assert.assertThrows(RuntimeException.class,
				() -> this.container.deployComponents(EventsThirdImpl.class));

		Assert.assertTrue("expected the first owner's registration to still be in effect "
				+ "after the losing component was undeployed", thrown.getCause() instanceof ValidationException);
		Assert.assertTrue(thrown.getMessage().contains(EventsFirst.class.getSimpleName()));
	}

	@Component
	public interface EventsFirst {
	}

	@Component
	public interface EventsSecond {
	}

	@Component
	public interface EventsThird {
	}

	@Events
	public interface PingEvents {
		void onPing();
	}

	@Implementation
	public static class EventsFirstImpl implements EventsFirst {
		@ProducedEvents
		private PingEvents producePingEvents;
	}

	@Implementation
	public static class EventsSecondImpl implements EventsSecond {
		@ProducedEvents
		private PingEvents producePingEvents;
	}

	@Implementation
	public static class EventsThirdImpl implements EventsThird {
		@ProducedEvents
		private PingEvents producePingEvents;
	}

	// --- Provided services: one owner per services interface. ---

	@Test
	public void providedServicesCollisionIsRejectedInStrictMode() {
		this.container = Covalent.container();
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.strict);

		RuntimeException thrown = Assert.assertThrows(RuntimeException.class,
				() -> this.container.deployComponents(ServicesFirstImpl.class, ServicesSecondImpl.class));

		Assert.assertTrue(thrown.getCause() instanceof ValidationException);
		Assert.assertTrue(thrown.getMessage().contains(PingServices.class.getSimpleName()));
		Assert.assertTrue(thrown.getMessage().contains("already provided"));
	}

	@Test
	public void providedServicesCollisionDoesNotCrashAtDefaultVerificationLevel() {
		this.container = Covalent.container();

		this.container.deployComponents(ServicesFirstImpl.class, ServicesSecondImpl.class);
		this.container.start();
		this.container.stop();
	}

	@Component
	public interface ServicesFirst {
	}

	@Component
	public interface ServicesSecond {
	}

	@Services
	public interface PingServices {
		void ping();
	}

	@Implementation
	public static class ServicesFirstImpl implements ServicesFirst, PingServices {
		@Override
		public void ping() {
		}
	}

	@Implementation
	public static class ServicesSecondImpl implements ServicesSecond, PingServices {
		@Override
		public void ping() {
		}
	}

	// --- Offered parameters: one owner per parameters interface. ---

	@Test
	public void offeredParametersCollisionIsRejectedInStrictMode() {
		this.container = Covalent.container();
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.strict);

		RuntimeException thrown = Assert.assertThrows(RuntimeException.class,
				() -> this.container.deployComponents(ParametersFirstImpl.class, ParametersSecondImpl.class));

		Assert.assertTrue(thrown.getCause() instanceof ValidationException);
		Assert.assertTrue(thrown.getMessage().contains(PingParameters.class.getSimpleName()));
		Assert.assertTrue(thrown.getMessage().contains("already offered"));
	}

	@Test
	public void offeredParametersCollisionDoesNotCrashAtDefaultVerificationLevel() {
		this.container = Covalent.container();

		this.container.deployComponents(ParametersFirstImpl.class, ParametersSecondImpl.class);
		this.container.start();
		this.container.stop();
	}

	@Component
	public interface ParametersFirst {
	}

	@Component
	public interface ParametersSecond {
	}

	@Parameters
	public interface PingParameters {
		void setPing(String value);

		String getPing();
	}

	@Implementation
	public static class ParametersFirstImpl implements ParametersFirst, PingParameters {
		private String ping;

		@Override
		public void setPing(final String value) {
			this.ping = value;
		}

		@Override
		public String getPing() {
			return this.ping;
		}
	}

	@Implementation
	public static class ParametersSecondImpl implements ParametersSecond, PingParameters {
		private String ping;

		@Override
		public void setPing(final String value) {
			this.ping = value;
		}

		@Override
		public String getPing() {
			return this.ping;
		}
	}

}
