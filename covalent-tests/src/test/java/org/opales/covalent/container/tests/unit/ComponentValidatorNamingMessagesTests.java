/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Field;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsSubscribe;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.Parameters;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.exceptions.NamingException;
import org.opales.covalent.container.exceptions.ValidationException;
import org.opales.covalent.container.validation.impl.ComponentValidatorImpl;

/**
 * Pins the full text of the messages of {@link ComponentValidatorImpl} for a
 * badly named used services, accessed parameters, produced events or used
 * subscribe field, declared by a component implementation or by an item, and
 * the message for a used services field whose type is not a services
 * interface.
 */
public class ComponentValidatorNamingMessagesTests {

	@Services
	public interface MsgServices {
	}

	@Parameters
	public interface MsgParameters {
	}

	@Events
	public interface MsgEvents {
	}

	@EventsSubscribe
	public interface MsgSubscribe {
	}

	@Implementation
	static class MsgImpl {
		MsgServices wrongServices;
		MsgParameters wrongParameters;
		MsgEvents wrongEvents;
		MsgSubscribe wrongSubscribe;
		Runnable useRunnable;
	}

	@Item
	static class MsgItem {
		MsgServices wrongServices;
		MsgParameters wrongParameters;
		MsgEvents wrongEvents;
		MsgSubscribe wrongSubscribe;
	}

	private ComponentValidatorImpl validator;

	@Before
	public void setUp() {
		this.validator = new ComponentValidatorImpl();
		this.validator.setStructuralRulesVerificationLevel(VerificationLevel.strict);
	}

	private static Field field(final Class<?> owner, final String name) throws NoSuchFieldException {
		return owner.getDeclaredField(name);
	}

	private static String expected(final String declaringKind, final String declaringClass, final String article,
			final String fieldKind, final String fieldName, final String interfaceKind, final String interfaceName,
			final String prefix) {
		return "Naming Error: In " + declaringKind + " " + declaringClass + ", the " + fieldKind + " field "
				+ fieldName + " for " + interfaceKind + " interface " + interfaceName
				+ "\ndoes not respect the naming convention. " + article + " " + fieldKind
				+ " field should start with '" + prefix + "'\nand end with the name of the " + interfaceKind
				+ " interface. For instance: " + prefix + interfaceName;
	}

	private void assertMessages(final Class<?> owner, final String declaringKind) throws Exception {
		String name = owner.getSimpleName();
		NamingException services = Assert.assertThrows(NamingException.class, () -> this.validator
				.checkUsedServicesField(field(owner, "wrongServices"), MsgServices.class, owner));
		Assert.assertEquals(expected(declaringKind, name, "A", "used services", "wrongServices", "services",
				"MsgServices", "use"), services.getMessage());

		NamingException parameters = Assert.assertThrows(NamingException.class, () -> this.validator
				.checkAccessedParametersField(field(owner, "wrongParameters"), MsgParameters.class, owner));
		Assert.assertEquals(expected(declaringKind, name, "An", "accessed parameters", "wrongParameters",
				"parameters", "MsgParameters", "access"), parameters.getMessage());

		NamingException events = Assert.assertThrows(NamingException.class, () -> this.validator
				.checkProducedEventsField(field(owner, "wrongEvents"), MsgEvents.class, owner));
		Assert.assertEquals(expected(declaringKind, name, "A", "produced events", "wrongEvents", "events",
				"MsgEvents", "produce"), events.getMessage());

		NamingException subscribe = Assert.assertThrows(NamingException.class, () -> this.validator
				.checkUsedSubscribeField(field(owner, "wrongSubscribe"), MsgSubscribe.class, owner));
		Assert.assertEquals(expected(declaringKind, name, "An", "events subscriber", "wrongSubscribe", "subscribe",
				"MsgSubscribe", "use"), subscribe.getMessage());
	}

	@Test
	public void badlyNamedFieldsOfAComponentImplementationAreReportedWithTheirFullMessage() throws Exception {
		this.assertMessages(MsgImpl.class, "component implementation");
	}

	@Test
	public void badlyNamedFieldsOfAnItemAreReportedWithTheirFullMessage() throws Exception {
		this.assertMessages(MsgItem.class, "component item");
	}

	@Test
	public void aUsedServicesFieldWhoseTypeIsNotAServicesInterfaceIsReportedWithAReadableMessage() throws Exception {
		ValidationException failure = Assert.assertThrows(ValidationException.class, () -> this.validator
				.checkUsedServicesField(field(MsgImpl.class, "useRunnable"), Runnable.class, MsgImpl.class));

		Assert.assertEquals("Interface Runnable is expected to be a Services interface, as it is referred to as such"
				+ " in used services field:\n    MsgImpl.useRunnable, however it is not annotated with @Services",
				failure.getMessage());
	}
}
