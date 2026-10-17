/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Field;
import java.util.List;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.annotations.Events;
import org.opales.covalent.container.component.annotations.EventsSubscribe;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.exceptions.NamingException;
import org.opales.covalent.container.exceptions.ValidationException;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;
import org.opales.covalent.container.validation.impl.ComponentValidatorImpl;

/**
 * Verifies the field naming-convention rules of a stand-alone
 * {@link ComponentValidatorImpl}: a used-services, produced-events or
 * used-subscribe field whose name does not start with the expected prefix and
 * end with the interface name is reported as a {@link NamingException} under
 * the strict level, only logged under the warn level, and ignored when its
 * convention toggle is switched off. The message names the declaring class as
 * a component item or a component implementation, and every toggle and
 * verification level accessor reads back what was set.
 */
public class ComponentValidatorNamingConventionTests {

	private CapturingLogHandler componentValidatorLogs;

	@Services
	interface SampleServices {
	}

	@Events
	interface SampleEvents {
	}

	@Events
	interface BadlyNamedEvents {
		void fired();

		void second();

		void onGood();
	}

	@EventsSubscribe
	interface SampleSubscribe {
	}

	@Implementation
	static class SampleImpl {
		SampleServices useSampleServices;
		SampleServices wrongName;
		SampleEvents produceSampleEvents;
		SampleEvents wrongEvents;
		SampleSubscribe useSampleSubscribe;
		SampleSubscribe wrongSubscriber;
		SampleServices useOther;
		SampleEvents produceOther;
		SampleSubscribe useOther2;
	}

	@EventsSubscribe
	interface BadlySuffixed {
	}

	@Item
	static class SampleItem {
		SampleServices wrongName;
		SampleEvents wrongEvents;
		SampleSubscribe wrongSubscriber;
	}

	private ComponentValidatorImpl validator;

	@Before
	public void setUp() {
		this.componentValidatorLogs = CapturingLogHandler.install(ComponentValidatorImpl.class);
		this.validator = new ComponentValidatorImpl();
		this.validator.setStructuralRulesVerificationLevel(VerificationLevel.strict);
		this.validator.setDynamicRulesVerificationLevel(VerificationLevel.strict);
	}

	@After
	public void restoreTheLogs() {
		this.componentValidatorLogs.restore();
	}

	private static Field field(final Class<?> owner, final String name) throws NoSuchFieldException {
		return owner.getDeclaredField(name);
	}

	@Test
	public void wellNamedFieldsAreAcceptedUnderStrictLevel() throws Exception {
		this.validator.checkUsedServicesField(field(SampleImpl.class, "useSampleServices"), SampleServices.class,
				SampleImpl.class);
		this.validator.checkProducedEventsField(field(SampleImpl.class, "produceSampleEvents"), SampleEvents.class,
				SampleImpl.class);
		this.validator.checkUsedSubscribeField(field(SampleImpl.class, "useSampleSubscribe"), SampleSubscribe.class,
				SampleImpl.class);
	}

	@Test
	public void badlyNamedUsedServicesFieldIsRejectedWithNamingException() throws Exception {
		NamingException e = Assert.assertThrows(NamingException.class, () -> this.validator.checkUsedServicesField(
				field(SampleImpl.class, "wrongName"), SampleServices.class, SampleImpl.class));
		Assert.assertTrue(e.getMessage().contains("wrongName"));
		Assert.assertTrue(e.getMessage().contains("useSampleServices"));
		Assert.assertTrue(e.getMessage().contains("component implementation SampleImpl"));
	}

	@Test
	public void badlyNamedProducedEventsFieldIsRejectedWithNamingException() throws Exception {
		NamingException e = Assert.assertThrows(NamingException.class, () -> this.validator.checkProducedEventsField(
				field(SampleImpl.class, "wrongEvents"), SampleEvents.class, SampleImpl.class));
		Assert.assertTrue(e.getMessage().contains("wrongEvents"));
		Assert.assertTrue(e.getMessage().contains("produceSampleEvents"));
	}

	@Test
	public void badlyNamedUsedSubscribeFieldIsRejectedWithNamingException() throws Exception {
		NamingException e = Assert.assertThrows(NamingException.class, () -> this.validator.checkUsedSubscribeField(
				field(SampleImpl.class, "wrongSubscriber"), SampleSubscribe.class, SampleImpl.class));
		Assert.assertTrue(e.getMessage().contains("wrongSubscriber"));
		Assert.assertTrue(e.getMessage().contains("useSampleSubscribe"));
	}

	@Test
	public void rightPrefixWithWrongSuffixIsStillANamingViolation() throws Exception {
		Assert.assertThrows(NamingException.class, () -> this.validator.checkUsedServicesField(
				field(SampleImpl.class, "useOther"), SampleServices.class, SampleImpl.class));
		Assert.assertThrows(NamingException.class, () -> this.validator.checkProducedEventsField(
				field(SampleImpl.class, "produceOther"), SampleEvents.class, SampleImpl.class));
		Assert.assertThrows(NamingException.class, () -> this.validator.checkUsedSubscribeField(
				field(SampleImpl.class, "useOther2"), SampleSubscribe.class, SampleImpl.class));
	}

	@Test
	public void subscribeInterfaceNotSuffixedWithSubscribeIsRejectedUnderStrictLevel() {
		ValidationException e = Assert.assertThrows(ValidationException.class,
				() -> this.validator.checkSubscribe(BadlySuffixed.class));

		Assert.assertTrue(e.getMessage(), e.getMessage().contains("suffixed with 'Subscribe'"));
		this.validator.checkSubscribe(SampleSubscribe.class);
	}

	@Test
	public void violationsInAnItemAreReportedAsComponentItem() throws Exception {
		NamingException services = Assert.assertThrows(NamingException.class, () -> this.validator
				.checkUsedServicesField(field(SampleItem.class, "wrongName"), SampleServices.class, SampleItem.class));
		NamingException events = Assert.assertThrows(NamingException.class, () -> this.validator
				.checkProducedEventsField(field(SampleItem.class, "wrongEvents"), SampleEvents.class, SampleItem.class));
		NamingException subscribe = Assert.assertThrows(NamingException.class,
				() -> this.validator.checkUsedSubscribeField(field(SampleItem.class, "wrongSubscriber"),
						SampleSubscribe.class, SampleItem.class));
		Assert.assertTrue(services.getMessage().contains("component item SampleItem"));
		Assert.assertTrue(events.getMessage().contains("component item SampleItem"));
		Assert.assertTrue(subscribe.getMessage().contains("component item SampleItem"));
	}

	@Test
	public void warnLevelDoesNotRejectBadlyNamedFields() throws Exception {
		this.validator.setStructuralRulesVerificationLevel(VerificationLevel.warn);
		this.validator.checkUsedServicesField(field(SampleImpl.class, "wrongName"), SampleServices.class,
				SampleImpl.class);
		this.validator.checkProducedEventsField(field(SampleImpl.class, "wrongEvents"), SampleEvents.class,
				SampleImpl.class);
		this.validator.checkUsedSubscribeField(field(SampleImpl.class, "wrongSubscriber"), SampleSubscribe.class,
				SampleImpl.class);
		List<String> warnings = this.componentValidatorLogs.messages(Level.WARNING);
		Assert.assertEquals(3, warnings.size());
		Assert.assertTrue(warnings.get(0), warnings.get(0).startsWith(
				"Naming Error: In component implementation SampleImpl, the used services field wrongName for services interface"));
		Assert.assertTrue(warnings.get(1), warnings.get(1).startsWith(
				"Naming Error: In component implementation SampleImpl, the produced events field wrongEvents for events interface"));
		Assert.assertTrue(warnings.get(2), warnings.get(2).startsWith(
				"Naming Error: In component implementation SampleImpl, the events subscriber field wrongSubscriber for subscribe"));
	}

	@Test
	public void disabledConventionTogglesAcceptBadlyNamedFields() throws Exception {
		this.validator.setCheckUseServicesFieldNamingConvention(false);
		this.validator.setCheckProduceEventsFieldNamingConvention(false);
		this.validator.setCheckUseSubscriberFieldNamingConvention(false);
		this.validator.checkUsedServicesField(field(SampleImpl.class, "wrongName"), SampleServices.class,
				SampleImpl.class);
		this.validator.checkProducedEventsField(field(SampleImpl.class, "wrongEvents"), SampleEvents.class,
				SampleImpl.class);
		this.validator.checkUsedSubscribeField(field(SampleImpl.class, "wrongSubscriber"), SampleSubscribe.class,
				SampleImpl.class);
	}

	@Test
	public void togglesAndLevelsReadBackWhatWasSet() {
		Assert.assertTrue(this.validator.isCheckUseServicesFieldNamingConvention());
		Assert.assertTrue(this.validator.isCheckAccessedParametersFieldNamingConvention());
		Assert.assertTrue(this.validator.isCheckEventsMethodNamingConvention());
		Assert.assertTrue(this.validator.isCheckProduceEventsFieldNamingConvention());
		Assert.assertTrue(this.validator.isCheckUseSubscriberFieldNamingConvention());

		this.validator.setCheckUseServicesFieldNamingConvention(false);
		this.validator.setCheckAccessedParametersFieldNamingConvention(false);
		this.validator.setCheckEventsMethodNamingConvention(false);
		this.validator.setCheckProduceEventsFieldNamingConvention(false);
		this.validator.setCheckUseSubscriberFieldNamingConvention(false);
		this.validator.setStructuralRulesVerificationLevel(VerificationLevel.trace);
		this.validator.setDynamicRulesVerificationLevel(VerificationLevel.warn);

		Assert.assertFalse(this.validator.isCheckUseServicesFieldNamingConvention());
		Assert.assertFalse(this.validator.isCheckAccessedParametersFieldNamingConvention());
		Assert.assertFalse(this.validator.isCheckEventsMethodNamingConvention());
		Assert.assertFalse(this.validator.isCheckProduceEventsFieldNamingConvention());
		Assert.assertFalse(this.validator.isCheckUseSubscriberFieldNamingConvention());
		Assert.assertEquals(VerificationLevel.trace, this.validator.getStructuralRulesVerificationLevel());
		Assert.assertEquals(VerificationLevel.warn, this.validator.getDynamicRulesVerificationLevel());
	}

	@Test
	public void dynamicVerificationFollowsItsOwnLevel() {
		this.validator.setStructuralRulesVerificationLevel(VerificationLevel.silent);
		Assert.assertThrows(ValidationException.class, () -> this.validator.handleDynamicVerification("boom"));
		this.validator.handleStructuralVerification("ignored");
		this.validator.setDynamicRulesVerificationLevel(VerificationLevel.silent);
		this.validator.handleDynamicVerification("ignored");
	}

	@Test
	public void badlyNamedEventMethodsAreListedInTheNamingExceptionMessage() {
		NamingException e = Assert.assertThrows(NamingException.class,
				() -> this.validator.checkEventMethodNamingConvention(BadlyNamedEvents.class));

		String message = e.getMessage();
		Assert.assertTrue(message, message.contains("The events interface BadlyNamedEvents"));
		Assert.assertTrue(message, message.contains("The EventsManager should be unable to filter this event"));
		Assert.assertTrue(message, message.contains("Methods that don't respect the naming convention:\n"));
		Assert.assertTrue(message, message.contains("\t- fired() -> should be named onFired\n"));
		Assert.assertTrue(message, message.contains("\t- second() -> should be named onSecond\n"));
		Assert.assertFalse(message, message.contains("onGood()"));
	}
}
