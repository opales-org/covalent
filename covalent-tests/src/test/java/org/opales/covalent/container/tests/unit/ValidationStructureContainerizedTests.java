/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Field;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.tests.CovalentContainerizedTests;
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
import org.opales.covalent.container.events.GenericSubscribe;
import org.opales.covalent.container.exceptions.ValidationException;
import org.opales.covalent.container.exceptions.NamingException;
import org.opales.covalent.container.lifecycle.LifecycleParameters;
import org.opales.covalent.container.lifecycle.LifecycleServices.StartMode;
import org.opales.covalent.container.lifecycle.impl.LifecycleManagerImpl;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;
import org.opales.covalent.container.validation.ValidationParameters;
import org.opales.covalent.container.validation.ValidationServices;
import org.opales.covalent.container.validation.impl.ComponentValidatorImpl;

public class ValidationStructureContainerizedTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	private ValidationServices validationServices;

	@Before
	public void setUp() {
		this.rcs.registerComponent(this);
		this.cm.getParameters().setSynchronousEvents(true);
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
		this.lifecycleParameters().setStartMode(StartMode.vertical);
		// Looked up here, not as a field initializer: the base class's own
		// @Before (which runs first) is what starts the container, and this raw
		// lookup needs it already running.
		this.validationServices = this.rcs.useServices(ValidationServices.class);
	}

	@After
	public void tearDown() {
		this.lifecycleParameters().setStartMode(StartMode.vertical);
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.trace);

	}

	@Test
	public void invalidPortDeclarationsFollowStructuralVerificationLevels() throws Exception {
		this.assertStructuralRuleBehavior(() -> this.validationServices.checkServices(PlainServices.class));
		this.assertStructuralRuleBehavior(() -> this.validationServices.checkParameters(PlainParameters.class));
		this.assertStructuralRuleBehavior(() -> this.validationServices.checkEvents(PlainEvents.class));
		this.assertStructuralRuleBehavior(() -> this.validationServices.checkSubscribe(PlainEventsSubscribe.class));
		this.assertStructuralRuleBehavior(() -> this.validationServices.checkUsedServicesField(
				this.field(InvalidPortFields.class, "usePlainServices"), PlainServices.class, InvalidPortFields.class));
		this.assertStructuralRuleBehavior(() -> this.validationServices.checkAccessedParametersField(
				this.field(InvalidPortFields.class, "accessPlainParameters"), PlainParameters.class,
				InvalidPortFields.class));
		this.assertStructuralRuleBehavior(() -> this.validationServices.checkProducedEventsField(
				this.field(InvalidPortFields.class, "producePlainEvents"), PlainEvents.class, InvalidPortFields.class));
		this.assertStructuralRuleBehavior(() -> this.validationServices.checkUsedSubscribeField(
				this.field(InvalidPortFields.class, "usePlainEventsSubscribe"), PlainEventsSubscribe.class,
				InvalidPortFields.class));
	}

	@Test
	public void accessedParametersFieldNamingConventionCanBeToggled() throws Exception {
		ValidationParameters validationParameters = this.rcs.accessParameters(ValidationParameters.class);
		Field badlyNamedField = this.field(BadlyNamedPortFields.class, "wrongNamedGoodParameters");
		try {
			// Enabled by default: a field whose type is properly annotated
			// @Parameters, but whose name does not respect the convention,
			// still follows the structural verification levels like every
			// other structural rule.
			validationParameters.setCheckAccessedParametersFieldNamingConvention(true);
			// NamingException, not the generic ValidationException: the naming-convention
			// sub-check wraps its violation into that more specific type on purpose - see
			// ComponentValidatorImpl#checkAccessedParametersFieldNamingConvention's own javadoc.
			this.assertStructuralRuleBehavior(() -> this.validationServices.checkAccessedParametersField(
					badlyNamedField, GoodParameters.class, BadlyNamedPortFields.class), NamingException.class);

			// Disabled: the same badly named field is accepted, even under strict.
			validationParameters.setCheckAccessedParametersFieldNamingConvention(false);
			this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.strict);
			this.validationServices.checkAccessedParametersField(badlyNamedField, GoodParameters.class,
					BadlyNamedPortFields.class);
		} finally {
			validationParameters.setCheckAccessedParametersFieldNamingConvention(true);
			this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
		}
	}

	@Test
	public void invalidManagedTypeDeclarationsFollowStructuralVerificationLevels() {
		this.assertStructuralRuleBehavior(() -> this.validationServices.checkImplementation(PlainImplementation.class));
		this.assertStructuralRuleBehavior(
				() -> this.validationServices.checkImplementation(ComponentAnnotatedImplementation.class));
		this.assertStructuralRuleBehavior(() -> this.validationServices.checkItem(PlainItem.class));
		this.assertStructuralRuleBehavior(() -> this.validationServices.checkComposedComponent(ItemComposedComponent.class));
	}

	@Test
	public void aComponentTypeWithMethodsFollowsStructuralVerificationLevels() {
		this.assertStructuralRuleBehavior(
				() -> this.validationServices.checkComponentType(ComponentTypeWithMethods.class));
		this.assertStructuralRuleBehavior(
				() -> this.validationServices.checkComponentType(ComponentTypeExtendingServices.class));
	}

	@Test
	public void aTagInterfaceAndTheContainerTypeAreValidComponentTypes() {
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.strict);
		try {
			this.validationServices.checkComponentType(TagComponentType.class);
			// a single possible implementation: the container's type is part of its implementation
			this.validationServices.checkComponentType(CovalentContainer.class);
		} finally {
			this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
		}
	}

	@Test
	public void aComponentTypeWithMethodsIsRejectedAtDeploymentUnderStrictWithoutPartialDeployment() {
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.strict);
		try {
			RuntimeException failure = Assert.assertThrows(RuntimeException.class,
					() -> Covalent.main.lcs().deployComponent(ComponentWithMethodsImpl.class));
			Assert.assertTrue(this.exceptionContains(failure, "tag interface"));
			Assert.assertFalse(Covalent.main.lcs().isDeployed(ComponentTypeWithMethods.class));
		} finally {
			this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
		}
	}

	@Test
	public void duplicatePortFieldsInsideOneComponentAreHardModelInvariants() {
		this.assertDeploymentInvariant(DuplicateUsedServicesImpl.class);
		this.assertDeploymentInvariant(DuplicateAccessedParametersImpl.class);
		this.assertDeploymentInvariant(DuplicateProducedEventsImpl.class);
		this.assertDeploymentInvariant(DuplicateUsedSubscriberImpl.class);
	}

	@Test
	public void duplicatePortFieldsInsideOneItemAreHardModelInvariants() {
		this.assertDeploymentInvariant(DuplicateItemUsedServicesImpl.class);
		this.assertDeploymentInvariant(DuplicateItemAccessedParametersImpl.class);
		this.assertDeploymentInvariant(DuplicateItemProducedEventsImpl.class);
		this.assertDeploymentInvariant(DuplicateItemUsedSubscriberImpl.class);
	}

	@Test
	public void genericSubscribeFailsWhenConsumerDeclaresSeveralConsumedEvents() {
		this.cm.deployComponents(MultiGenericSubscriberImpl.class);

		RuntimeException failure = this.assertLifecycleFailure(
				() -> Covalent.main.lcs().initializeComponent(MultiGenericSubscriber.class));

		Assert.assertTrue(this.exceptionContains(failure, "cannot use generic subscribe"));
		Assert.assertTrue(this.exceptionContains(failure, "more than one kind of events"));
	}

	@Test
	public void genericSubscribeFailsWhenEventsProvideSpecificSubscribeInterface() {
		this.cm.deployComponents(SpecificSubscribeProducerImpl.class, SpecificSubscribeGenericConsumerImpl.class);

		RuntimeException failure = this.assertLifecycleFailure(
				() -> Covalent.main.lcs().initializeComponent(SpecificSubscribeGenericConsumer.class));

		Assert.assertTrue(this.exceptionContains(failure, "cannot use generic subscribe"));
		Assert.assertTrue(this.exceptionContains(failure, "provides its own subscribe interface"));
	}

	private LifecycleParameters lifecycleParameters() {
		// A cast on the direct lcs() facade, not this.rcs.accessParameters(...):
		// the latter infers its caller from the call stack, which only works
		// reliably from inside a real component, not from test code.
		return (LifecycleParameters) this.cm.lcs();
	}

	private Field field(final Class<?> declaringClass, final String fieldName) throws NoSuchFieldException {
		return declaringClass.getDeclaredField(fieldName);
	}

	private void assertStructuralRuleBehavior(final CheckedRunnable rule) {
		this.assertStructuralRuleBehavior(rule, ValidationException.class);
	}

	/**
	 * Same as {@link #assertStructuralRuleBehavior(CheckedRunnable)}, but for a structural rule
	 * that deliberately throws a more specific {@code ValidationException} subtype-sibling (for
	 * instance {@link NamingException}) under {@code strict}, rather than the plain {@code
	 * ValidationException} every other structural rule throws.
	 */
	private void assertStructuralRuleBehavior(final CheckedRunnable rule,
			final Class<? extends RuntimeException> expectedStrictException) {
		CapturingLogHandler logHandler = CapturingLogHandler.install(ComponentValidatorImpl.class);
		try {
			for (VerificationLevel verificationLevel : VerificationLevel.values()) {
				this.cm.getParameters().setStructuralRulesChecking(verificationLevel);
				if (VerificationLevel.strict.equals(verificationLevel)) {
					Assert.assertThrows(expectedStrictException, rule::run);
				} else {
					try {
						rule.run();
					} catch (Exception e) {
						throw new AssertionError(e);
					}
				}
			}
			this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
		} finally {
			logHandler.restore();
		}
	}

	private void assertDeploymentInvariant(final Class<?> implementation) {
		for (VerificationLevel verificationLevel : VerificationLevel.values()) {
			this.cm.getParameters().setStructuralRulesChecking(verificationLevel);
			RuntimeException failure = Assert.assertThrows(RuntimeException.class,
					() -> Covalent.main.lcs().deployComponent(implementation));
			Assert.assertTrue(this.exceptionContains(failure, "more than one"));
			this.cm.stop();
			// Restart before the next verification level's attempt: this
			// per-iteration stop() is part of the assertion, not the test's own
			// teardown, so it must not leave the container stopped for the rest
			// of the loop (or for the base class's own automatic teardown).
			this.cm.start();
		}
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
	}

	private RuntimeException assertLifecycleFailure(final Runnable lifecycleOperation) {
		CapturingLogHandler logHandler = CapturingLogHandler.install(LifecycleManagerImpl.class);
		try {
			return Assert.assertThrows(RuntimeException.class, lifecycleOperation::run);
		} finally {
			logHandler.restore();
		}
	}

	private boolean exceptionContains(final Throwable failure, final String expectedText) {
		Throwable current = failure;
		while (current != null) {
			if (current.getMessage() != null && current.getMessage().contains(expectedText)) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private interface CheckedRunnable {
		void run() throws Exception;
	}

	public interface PlainServices {
	}

	public interface PlainParameters {
	}

	public interface PlainEvents {
	}

	public interface PlainEventsSubscribe {
	}

	public static class InvalidPortFields {
		@UsedServices
		private PlainServices usePlainServices;

		@AccessedParameters
		private PlainParameters accessPlainParameters;

		@ProducedEvents
		private PlainEvents producePlainEvents;

		@UsedEventsSubscribe
		private PlainEventsSubscribe usePlainEventsSubscribe;
	}

	public static class BadlyNamedPortFields {
		// Properly annotated @Parameters type (GoodParameters, declared
		// below), but a field name that respects neither the required
		// "access" prefix nor the parameters interface name suffix.
		@AccessedParameters
		private GoodParameters wrongNamedGoodParameters;
	}

	public static class PlainImplementation {
	}

	@Component
	public static class ComponentAnnotatedImplementation {
	}

	@Component
	public interface TagComponentType {
	}

	@Component
	public interface ComponentTypeWithMethods {
		void doSomething();
	}

	@Services
	public interface ServicesWithMethodServices {
		void serve();
	}

	@Component
	public interface ComponentTypeExtendingServices extends ServicesWithMethodServices {
	}

	@Implementation
	public static class ComponentWithMethodsImpl implements ComponentTypeWithMethods {
		@Override
		public void doSomething() {
		}
	}

	public static class PlainItem {
	}

	@Item
	public static class ItemComposedComponent {
	}

	@Component
	public interface DuplicateUsedServicesComponent {
	}

	@Implementation
	public static class DuplicateUsedServicesImpl implements DuplicateUsedServicesComponent {
		@UsedServices
		private GoodServices useFirstGoodServices;

		@UsedServices
		private GoodServices useSecondGoodServices;
	}

	@Component
	public interface DuplicateAccessedParametersComponent {
	}

	@Implementation
	public static class DuplicateAccessedParametersImpl implements DuplicateAccessedParametersComponent {
		@AccessedParameters
		private GoodParameters accessFirstGoodParameters;

		@AccessedParameters
		private GoodParameters accessSecondGoodParameters;
	}

	@Component
	public interface DuplicateProducedEventsComponent {
	}

	@Implementation
	public static class DuplicateProducedEventsImpl implements DuplicateProducedEventsComponent {
		@ProducedEvents
		private GoodEvents produceFirstGoodEvents;

		@ProducedEvents
		private GoodEvents produceSecondGoodEvents;
	}

	@Component
	public interface DuplicateUsedSubscriberComponent {
	}

	@Implementation
	public static class DuplicateUsedSubscriberImpl implements DuplicateUsedSubscriberComponent {
		@UsedEventsSubscribe
		private GoodEvents.GoodEventsSubscribe useFirstGoodEventsSubscribe;

		@UsedEventsSubscribe
		private GoodEvents.GoodEventsSubscribe useSecondGoodEventsSubscribe;
	}

	@Component
	public interface DuplicateItemUsedServicesComponent {
	}

	@Implementation
	public static class DuplicateItemUsedServicesImpl implements DuplicateItemUsedServicesComponent {
		@Item
		private DuplicateItemUsedServices item;
	}

	@Item
	public static class DuplicateItemUsedServices {
		@UsedServices
		private GoodServices useFirstGoodServices;

		@UsedServices
		private GoodServices useSecondGoodServices;
	}

	@Component
	public interface DuplicateItemAccessedParametersComponent {
	}

	@Implementation
	public static class DuplicateItemAccessedParametersImpl implements DuplicateItemAccessedParametersComponent {
		@Item
		private DuplicateItemAccessedParameters item;
	}

	@Item
	public static class DuplicateItemAccessedParameters {
		@AccessedParameters
		private GoodParameters accessFirstGoodParameters;

		@AccessedParameters
		private GoodParameters accessSecondGoodParameters;
	}

	@Component
	public interface DuplicateItemProducedEventsComponent {
	}

	@Implementation
	public static class DuplicateItemProducedEventsImpl implements DuplicateItemProducedEventsComponent {
		@Item
		private DuplicateItemProducedEvents item;
	}

	@Item
	public static class DuplicateItemProducedEvents {
		@ProducedEvents
		private GoodEvents produceFirstGoodEvents;

		@ProducedEvents
		private GoodEvents produceSecondGoodEvents;
	}

	@Component
	public interface DuplicateItemUsedSubscriberComponent {
	}

	@Implementation
	public static class DuplicateItemUsedSubscriberImpl implements DuplicateItemUsedSubscriberComponent {
		@Item
		private DuplicateItemUsedSubscriber item;
	}

	@Item
	public static class DuplicateItemUsedSubscriber {
		@UsedEventsSubscribe
		private GoodEvents.GoodEventsSubscribe useFirstGoodEventsSubscribe;

		@UsedEventsSubscribe
		private GoodEvents.GoodEventsSubscribe useSecondGoodEventsSubscribe;
	}

	@Services
	public interface GoodServices {
	}

	@Parameters
	public interface GoodParameters {
	}

	@Events
	public interface GoodEvents {
		void onGood();

		@EventsSubscribe
		public interface GoodEventsSubscribe {
			void subscribe(GoodEvents consumer);

			void unsubscribe(GoodEvents consumer);
		}
	}

	@Events
	public interface OtherEvents {
		void onOther();
	}

	@Component
	public interface MultiGenericSubscriber {
	}

	@Implementation
	public static class MultiGenericSubscriberImpl implements MultiGenericSubscriber, GoodEvents, OtherEvents {
		@UsedEventsSubscribe
		private GenericSubscribe useGenericSubscribe;

		@Override
		public void onGood() {
		}

		@Override
		public void onOther() {
		}
	}

	@Component
	public interface SpecificSubscribeProducer {
	}

	@Implementation
	public static class SpecificSubscribeProducerImpl implements SpecificSubscribeProducer {
		@ProducedEvents
		private GoodEvents produceGoodEvents;
	}

	@Component
	public interface SpecificSubscribeGenericConsumer {
	}

	@Implementation
	public static class SpecificSubscribeGenericConsumerImpl implements SpecificSubscribeGenericConsumer, GoodEvents {
		@UsedEventsSubscribe
		private GenericSubscribe useGenericSubscribe;

		@Override
		public void onGood() {
		}
	}
}
