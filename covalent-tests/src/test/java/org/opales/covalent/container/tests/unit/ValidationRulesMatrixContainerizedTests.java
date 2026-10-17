/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.tests.CovalentContainerizedTests;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.exceptions.ValidationException;
import org.opales.covalent.container.metamodel.MetamodelPortReadServices;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.unit.RawComponentsContractTests.RawSettings;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.ComponentAEvents;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.ComponentAServices;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.impl.ComponentA;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;
import org.opales.covalent.container.validation.ValidationServices;
import org.opales.covalent.container.validation.impl.ComponentValidatorImpl;

public class ValidationRulesMatrixContainerizedTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	@Before
	public void setUp() {
		this.rcs.registerComponent(this);
		this.cm.getParameters().setSynchronousEvents(true);
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
		this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
	}

	@After
	public void tearDown() {
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.trace);
		this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.trace);

	}

	@Test
	public void structuralVerificationOnlyThrowsInStrictMode() {
		ValidationServices validationServices = this.rcs.useServices(ValidationServices.class);
		CapturingLogHandler logHandler = CapturingLogHandler.install(ComponentValidatorImpl.class);

		try {
			for (VerificationLevel verificationLevel : VerificationLevel.values()) {
				this.cm.getParameters().setStructuralRulesChecking(verificationLevel);

				if (VerificationLevel.strict.equals(verificationLevel)) {
					Assert.assertThrows(ValidationException.class,
							() -> validationServices.handleStructuralVerification("expected structural failure"));
				} else {
					validationServices.handleStructuralVerification("expected structural failure");
				}
			}
			Assert.assertTrue(logHandler.containsWarning("expected structural failure"));
		} finally {
			logHandler.restore();
		}
	}

	@Test
	public void dynamicVerificationOnlyThrowsInStrictMode() {
		ValidationServices validationServices = this.rcs.useServices(ValidationServices.class);
		CapturingLogHandler logHandler = CapturingLogHandler.install(ComponentValidatorImpl.class);

		try {
			for (VerificationLevel verificationLevel : VerificationLevel.values()) {
				this.cm.getParameters().setDynamicRulesChecking(verificationLevel);

				if (VerificationLevel.strict.equals(verificationLevel)) {
					Assert.assertThrows(ValidationException.class,
							() -> validationServices.handleDynamicVerification("expected dynamic failure"));
				} else {
					validationServices.handleDynamicVerification("expected dynamic failure");
				}
			}
			Assert.assertTrue(logHandler.containsWarning("expected dynamic failure"));
		} finally {
			logHandler.restore();
		}
	}

	@Test
	public void duplicatePortOperationsFollowDynamicVerificationLevels() {
		this.assertDuplicateDynamicRuleBehavior(new DuplicateRuleScenario() {
			@Override
			public void prepare() {
				ComponentA provider = new ComponentA();
				ValidationRulesMatrixContainerizedTests.this.rcs.registerComponent(provider);
			}

			@Override
			public void duplicate() {
				ValidationRulesMatrixContainerizedTests.this.rcs.provideServices(ComponentA.class, ComponentAServices.class);
			}

			@Override
			public void cleanup() {
				ValidationRulesMatrixContainerizedTests.this.rcs.unregisterComponent(ComponentA.class);
			}
		});

		this.assertDuplicateDynamicRuleBehavior(new DuplicateRuleScenario() {
			@Override
			public void prepare() {
				PassiveParameterOwner offeror = new PassiveParameterOwner();
				ValidationRulesMatrixContainerizedTests.this.rcs.registerComponent(offeror);
				ValidationRulesMatrixContainerizedTests.this.rcs.offerParameters(PassiveParameterOwner.class, RawSettings.class);
			}

			@Override
			public void duplicate() {
				ValidationRulesMatrixContainerizedTests.this.rcs.offerParameters(PassiveParameterOwner.class, RawSettings.class);
			}

			@Override
			public void cleanup() {
				ValidationRulesMatrixContainerizedTests.this.rcs.unregisterComponent(PassiveParameterOwner.class);
			}
		});

		this.assertDuplicateDynamicRuleBehavior(new DuplicateRuleScenario() {
			@Override
			public void prepare() {
				ComponentA producer = new ComponentA();
				ValidationRulesMatrixContainerizedTests.this.rcs.registerComponent(producer);
			}

			@Override
			public void duplicate() {
				ValidationRulesMatrixContainerizedTests.this.rcs.produceEvents(ComponentA.class, ComponentAEvents.class);
			}

			@Override
			public void cleanup() {
				ValidationRulesMatrixContainerizedTests.this.rcs.unregisterComponent(ComponentA.class);
			}
		});

		this.assertDuplicateDynamicRuleBehavior(new DuplicateRuleScenario() {
			private PassiveComponentAEventsConsumer consumer;

			@Override
			public void prepare() {
				ComponentA producer = new ComponentA();
				this.consumer = new PassiveComponentAEventsConsumer();
				ValidationRulesMatrixContainerizedTests.this.rcs.registerComponent(producer);
				ValidationRulesMatrixContainerizedTests.this.rcs.subscribe(ValidationRulesMatrixContainerizedTests.class,
						ComponentAEvents.class, this.consumer);
			}

			@Override
			public void duplicate() {
				ValidationRulesMatrixContainerizedTests.this.rcs.subscribe(ValidationRulesMatrixContainerizedTests.class,
						ComponentAEvents.class, this.consumer);
			}

			@Override
			public void cleanup() {
				ValidationRulesMatrixContainerizedTests.this.rcs.unsubscribe(ValidationRulesMatrixContainerizedTests.class,
						ComponentAEvents.class, this.consumer);
				ValidationRulesMatrixContainerizedTests.this.rcs.unregisterComponent(ComponentA.class);
			}
		});
	}

	@Test
	public void duplicateServiceProvideFailsInStrictMode() {
		ComponentA provider = new ComponentA();
		this.rcs.registerComponent(provider);

		Assert.assertThrows("Duplicate service provide should fail in strict mode.", ValidationException.class,
				() -> this.rcs.provideServices(ComponentA.class, ComponentAServices.class));
	}

	/**
	 * Two distinct raw components cannot provide the same services interface:
	 * {@code MetamodelManagerImpl.addProvidedServices} keeps a global reverse index
	 * ({@code servicesToType}) and rejects the second owner through
	 * {@link ValidationServices#handleDynamicVerification} before
	 * {@code RRManagerImpl.createProvidedRR} is reached. See
	 * {@link #rejectedDuplicateServiceProviderLeavesItsOwnMetamodelEntryClean()} for the state the
	 * rejected component is left in.
	 */
	@Test
	public void duplicateServiceProvideAcrossDifferentComponentTypesFailsInStrictMode() {
		// Two componentTypes used by no other test of this class, so the outcome does not
		// depend on the order in which the tests run.
		FirstDistinctServicesProvider firstProvider = new FirstDistinctServicesProvider();
		this.rcs.registerComponent(firstProvider);
		this.rcs.provideServices(FirstDistinctServicesProvider.class, ComponentAServices.class);

		SecondDistinctServicesProvider secondProvider = new SecondDistinctServicesProvider();
		this.rcs.registerComponent(secondProvider);

		try {
			Assert.assertThrows("A second, distinct componentType providing an interface already provided by "
					+ "another componentType should fail in strict mode, the same as a same-componentType redeclare.",
					ValidationException.class,
					() -> this.rcs.provideServices(SecondDistinctServicesProvider.class, ComponentAServices.class));
		} finally {
			this.rcs.unregisterComponent(SecondDistinctServicesProvider.class);
			this.rcs.unregisterComponent(FirstDistinctServicesProvider.class);
		}
	}

	/**
	 * A raw component whose provide call was rejected because another component owns the interface
	 * does not keep that interface in its own metamodel entry:
	 * {@link MetamodelPortReadServices#getProvidedServices} does not list it for the rejected
	 * component, and unregistering that component afterwards is a clean no-op.
	 */
	@Test
	public void rejectedDuplicateServiceProviderLeavesItsOwnMetamodelEntryClean() {
		FirstDistinctServicesProvider firstProvider = new FirstDistinctServicesProvider();
		this.rcs.registerComponent(firstProvider);
		this.rcs.provideServices(FirstDistinctServicesProvider.class, ComponentAServices.class);

		SecondDistinctServicesProvider secondProvider = new SecondDistinctServicesProvider();
		this.rcs.registerComponent(secondProvider);

		Assert.assertThrows(ValidationException.class,
				() -> this.rcs.provideServices(SecondDistinctServicesProvider.class, ComponentAServices.class));

		MetamodelPortReadServices metamodelPortReadServices = this.rcs.useServices(MetamodelPortReadServices.class);
		Assert.assertFalse(
				"a rejected provider should not locally claim to provide the interface it was rejected for",
				metamodelPortReadServices.getProvidedServices(SecondDistinctServicesProvider.class)
						.contains(ComponentAServices.class));

		// Unregistering a component that was never granted what it attempted to declare is a
		// clean operation: it does not throw.
		try {
			this.rcs.unregisterComponent(SecondDistinctServicesProvider.class);
		} finally {
			this.rcs.unregisterComponent(FirstDistinctServicesProvider.class);
		}
	}

	@Test
	public void duplicateParameterOfferFailsInStrictMode() {
		PassiveParameterOwner offeror = new PassiveParameterOwner();
		this.rcs.registerComponent(offeror);
		this.rcs.offerParameters(PassiveParameterOwner.class, RawSettings.class);

		Assert.assertThrows("Duplicate parameter offer should fail in strict mode.", ValidationException.class,
				() -> this.rcs.offerParameters(PassiveParameterOwner.class, RawSettings.class));
	}

	/**
	 * Same expectation as {@link #rejectedDuplicateServiceProviderLeavesItsOwnMetamodelEntryClean()},
	 * for {@code MetamodelManagerImpl.addOfferedParameters}: a rejected second offeror does not keep
	 * the parameters interface in its own metamodel entry.
	 */
	@Test
	public void rejectedDuplicateParameterOfferorLeavesItsOwnMetamodelEntryClean() {
		FirstDistinctParametersOfferor firstOfferor = new FirstDistinctParametersOfferor();
		this.rcs.registerComponent(firstOfferor);
		this.rcs.offerParameters(FirstDistinctParametersOfferor.class, RawSettings.class);

		SecondDistinctParametersOfferor secondOfferor = new SecondDistinctParametersOfferor();
		this.rcs.registerComponent(secondOfferor);

		Assert.assertThrows(ValidationException.class,
				() -> this.rcs.offerParameters(SecondDistinctParametersOfferor.class, RawSettings.class));

		MetamodelPortReadServices metamodelPortReadServices = this.rcs.useServices(MetamodelPortReadServices.class);
		Assert.assertFalse(
				"a rejected offeror should not locally claim to offer the parameters interface it was rejected for",
				metamodelPortReadServices.getOfferedParameters(SecondDistinctParametersOfferor.class)
						.contains(RawSettings.class));

		// Unregistering a component that was never granted what it attempted to declare is a
		// clean operation: it does not throw.
		try {
			this.rcs.unregisterComponent(SecondDistinctParametersOfferor.class);
		} finally {
			this.rcs.unregisterComponent(FirstDistinctParametersOfferor.class);
		}
	}

	@Test
	public void duplicateEventsProductionFailsInStrictMode() {
		ComponentA producer = new ComponentA();
		this.rcs.registerComponent(producer);

		Assert.assertThrows("Duplicate events production should fail in strict mode.", ValidationException.class,
				() -> this.rcs.produceEvents(ComponentA.class, ComponentAEvents.class));
	}

	/**
	 * Same expectation as {@link #rejectedDuplicateServiceProviderLeavesItsOwnMetamodelEntryClean()},
	 * for {@code MetamodelManagerImpl.addProducedEvents}: a rejected second producer does not keep
	 * the events interface in its own metamodel entry.
	 */
	@Test
	public void rejectedDuplicateEventsProducerLeavesItsOwnMetamodelEntryClean() {
		FirstDistinctEventsProducer firstProducer = new FirstDistinctEventsProducer();
		this.rcs.registerComponent(firstProducer);
		this.rcs.produceEvents(FirstDistinctEventsProducer.class, ComponentAEvents.class);

		SecondDistinctEventsProducer secondProducer = new SecondDistinctEventsProducer();
		this.rcs.registerComponent(secondProducer);

		Assert.assertThrows(ValidationException.class,
				() -> this.rcs.produceEvents(SecondDistinctEventsProducer.class, ComponentAEvents.class));

		MetamodelPortReadServices metamodelPortReadServices = this.rcs.useServices(MetamodelPortReadServices.class);
		Assert.assertFalse(
				"a rejected producer should not locally claim to produce the events interface it was rejected for",
				metamodelPortReadServices.getProducedEvents(SecondDistinctEventsProducer.class)
						.contains(ComponentAEvents.class));

		// Unregistering a component that was never granted what it attempted to declare is a
		// clean operation: it does not throw.
		try {
			this.rcs.unregisterComponent(SecondDistinctEventsProducer.class);
		} finally {
			this.rcs.unregisterComponent(FirstDistinctEventsProducer.class);
		}
	}

	public static class PassiveParameterOwner {
	}

	/**
	 * Two componentTypes, both implementing {@link ComponentAServices}, used
	 * only by {@link #duplicateServiceProvideAcrossDifferentComponentTypesFailsInStrictMode()}
	 * to check the rejection of a provider overlap across componentTypes: two
	 * genuinely distinct providers of the same interface. Kept separate from
	 * {@link ComponentA} (used pervasively elsewhere in this file) so this
	 * test is not order-dependent on other tests' use of that type.
	 */
	public static class FirstDistinctServicesProvider implements ComponentAServices {
		@Override
		public boolean doSomethingWithBoolean(final boolean aValue) {
			return aValue;
		}

		@Override
		public void doSomething() {
		}

		@Override
		public String getData() {
			return "first-provider";
		}
	}

	public static class SecondDistinctServicesProvider implements ComponentAServices {
		@Override
		public boolean doSomethingWithBoolean(final boolean aValue) {
			return aValue;
		}

		@Override
		public void doSomething() {
		}

		@Override
		public String getData() {
			return "second-provider";
		}
	}

	/**
	 * Two componentTypes used only by
	 * {@link #rejectedDuplicateParameterOfferorLeavesItsOwnMetamodelEntryClean()}
	 * to check the parameters flavor of the rejection of a second offeror.
	 * Kept separate from {@link PassiveParameterOwner} (used by the
	 * same-componentType test above) so this test is order-independent.
	 */
	public static class FirstDistinctParametersOfferor {
	}

	public static class SecondDistinctParametersOfferor {
	}

	/**
	 * Two componentTypes used only by
	 * {@link #rejectedDuplicateEventsProducerLeavesItsOwnMetamodelEntryClean()}
	 * to check the events flavor of the rejection of a second producer. Kept
	 * separate from {@link ComponentA} (used by the same-componentType test
	 * above) so this test is order-independent.
	 */
	public static class FirstDistinctEventsProducer {
	}

	public static class SecondDistinctEventsProducer {
	}

	public static class PassiveComponentAEventsConsumer implements ComponentAEvents {
		@Override
		public void onCreate(final String data) {
		}

		@Override
		public void onUpdate(final String data) {
		}

		@Override
		public void onDelete(final String data) {
		}

		@Override
		public void onEmpty() {
		}
	}

	private void assertDuplicateDynamicRuleBehavior(final DuplicateRuleScenario scenario) {
		CapturingLogHandler logHandler = CapturingLogHandler.install(ComponentValidatorImpl.class);
		try {
			for (VerificationLevel verificationLevel : VerificationLevel.values()) {
				this.cm.getParameters().setDynamicRulesChecking(verificationLevel);
				scenario.prepare();
				try {
					if (VerificationLevel.strict.equals(verificationLevel)) {
						Assert.assertThrows(ValidationException.class, scenario::duplicate);
					} else {
						scenario.duplicate();
					}
				} finally {
					scenario.cleanup();
				}
			}
			this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		} finally {
			logHandler.restore();
		}
	}

	private interface DuplicateRuleScenario {
		void prepare();

		void duplicate();

		void cleanup();
	}

}
