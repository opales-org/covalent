/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.List;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.annotations.AccessedParameters;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsSubscribe;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.Parameters;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.component.annotations.UsedEventsSubscribe;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;
import org.opales.covalent.container.validation.impl.ComponentValidatorImpl;

/**
 * Verifies that the descriptor factory validates the used services, accessed
 * parameters, produced events and used subscribe fields it indexes, on the
 * component itself and on its items: a badly named field of each kind is
 * reported once per declaring class, naming the declaring class.
 */
public class DescriptorFactoryFieldChecksTests {

	@Services
	public interface FieldServices {
		void ping();
	}

	@Parameters
	public interface FieldParameters {
		String value();
	}

	@Events
	public interface FieldEvents {
		void onField(String value);

		@EventsSubscribe
		interface Subscribe {
			void subscribe(FieldEvents observer);

			void unsubscribe(FieldEvents observer);
		}
	}

	@Component
	public interface FieldHost {
	}

	@Implementation
	public static class FieldHostImpl implements FieldHost {
		@Item
		private FieldItem item;

		@UsedServices
		private FieldServices wrongComponentServices;

		@AccessedParameters
		private FieldParameters wrongComponentParameters;

		@ProducedEvents
		private FieldEvents wrongComponentEvents;

		@UsedEventsSubscribe
		private FieldEvents.Subscribe wrongComponentSubscribe;
	}

	@Item
	public static class FieldItem {
		@UsedServices
		private FieldServices wrongItemServices;

		@AccessedParameters
		private FieldParameters wrongItemParameters;

		@ProducedEvents
		private FieldEvents wrongItemEvents;

		@UsedEventsSubscribe
		private FieldEvents.Subscribe wrongItemSubscribe;
	}

	private CapturingLogHandler componentValidatorLogs;
	private CovalentContainer container;

	@Before
	public void setUp() {
		this.componentValidatorLogs = CapturingLogHandler.install(ComponentValidatorImpl.class);
		this.container = Covalent.container();
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.warn);
	}

	@After
	public void tearDown() {
		this.container.dispose();
		this.componentValidatorLogs.restore();
	}

	private void assertReported(final List<String> warnings, final String expectedStart) {
		boolean found = warnings.stream().anyMatch(message -> message.startsWith(expectedStart));
		Assert.assertTrue("No warning starting with: " + expectedStart + "\nwarnings: " + warnings, found);
	}

	@Test
	public void badlyNamedFieldsOfEveryKindAreReportedForTheComponentAndForItsItem() {
		this.container.deployComponents(FieldHostImpl.class);
		this.container.start();

		List<String> warnings = this.componentValidatorLogs.messages(Level.WARNING);
		String host = "Naming Error: In component implementation FieldHostImpl, the ";
		String item = "Naming Error: In component item FieldItem, the ";
		this.assertReported(warnings,
				host + "used services field wrongComponentServices for services interface FieldServices");
		this.assertReported(warnings,
				host + "accessed parameters field wrongComponentParameters for parameters interface FieldParameters");
		this.assertReported(warnings, host + "produced events field wrongComponentEvents for events interface FieldEvents");
		this.assertReported(warnings, host + "events subscriber field wrongComponentSubscribe for subscribe interface Subscribe");
		this.assertReported(warnings, item + "used services field wrongItemServices for services interface FieldServices");
		this.assertReported(warnings,
				item + "accessed parameters field wrongItemParameters for parameters interface FieldParameters");
		this.assertReported(warnings, item + "produced events field wrongItemEvents for events interface FieldEvents");
		this.assertReported(warnings, item + "events subscriber field wrongItemSubscribe for subscribe interface Subscribe");
	}
}
