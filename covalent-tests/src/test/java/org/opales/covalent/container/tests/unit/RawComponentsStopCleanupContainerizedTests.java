/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.registry.DescriptorRegistryReadServices;
import org.opales.covalent.container.registry.ComponentDescriptor;
import org.opales.covalent.container.rr.parameters.ParametersManagerServices;
import org.opales.covalent.container.rr.services.ServicesManagerServices;
import org.opales.covalent.container.tests.CovalentContainerizedTests;

public class RawComponentsStopCleanupContainerizedTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	@Before
	public void setUp() {
		this.rcs.registerComponent(this);
		this.cm.getParameters().setSynchronousEvents(true);
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
		this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.trace);
	}

	@After
	public void tearDown() {
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.trace);
		this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.trace);

	}

	@Test
	public void rawStopRemovesAllUsedServicesForOneRawCaller() {
		FirstRawServiceProvider firstProvider = new FirstRawServiceProvider();
		SecondRawServiceProvider secondProvider = new SecondRawServiceProvider();
		MultiServiceRawCaller caller = new MultiServiceRawCaller();

		this.rcs.registerComponent(firstProvider);
		this.rcs.registerComponent(secondProvider);
		this.rcs.registerComponent(caller);

		Assert.assertEquals("first", caller.callFirstService());
		Assert.assertEquals("second", caller.callSecondService());
		Assert.assertEquals(1, firstProvider.invocationCount);
		Assert.assertEquals(1, secondProvider.invocationCount);

		this.assertServiceUsedBy(FirstRawService.class, MultiServiceRawCaller.class);
		this.assertServiceUsedBy(SecondRawService.class, MultiServiceRawCaller.class);

		this.cm.stop();
		// stop() itself leaves the internal (technical) component tree
		// freshly re-activated (see LifecycleManagerImpl#stop()), so the
		// technical services used to inspect/re-register below would
		// already work right here, without this start() - see
		// rawRegistrationSucceedsImmediatelyAfterStopWithoutRestart() for
		// that exact case, verified in isolation. Kept here as a second,
		// symmetric stop()/start() cycle: this also exercises a normal
		// restart, not just the window right after stop().
		this.cm.start();

		this.assertServiceNotUsedBy(FirstRawService.class, MultiServiceRawCaller.class);
		this.assertServiceNotUsedBy(SecondRawService.class, MultiServiceRawCaller.class);

		this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		try {
			FirstRawServiceProvider restartedFirstProvider = new FirstRawServiceProvider();
			SecondRawServiceProvider restartedSecondProvider = new SecondRawServiceProvider();
			MultiServiceRawCaller restartedCaller = new MultiServiceRawCaller();

			this.rcs.registerComponent(restartedFirstProvider);
			this.rcs.registerComponent(restartedSecondProvider);
			this.rcs.registerComponent(restartedCaller);

			Assert.assertEquals("first", restartedCaller.callFirstService());
			Assert.assertEquals("second", restartedCaller.callSecondService());
			Assert.assertEquals(1, restartedFirstProvider.invocationCount);
			Assert.assertEquals(1, restartedSecondProvider.invocationCount);
		} finally {
			this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.trace);
		}
	}

	@Test
	public void rawRegistrationSucceedsImmediatelyAfterStopWithoutRestart() {
		this.cm.stop();
		// Raw registration must not throw IllegalStateException("... Component is
		// passivated.") in this exact window (between stop() and the next start()):
		// stop() re-activates the internal (technical) component tree - including
		// whichever component provides LifecycleServices - immediately (see
		// LifecycleManagerImpl#stop()), so registration succeeds here without any
		// start() at all.
		this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		try {
			FirstRawServiceProvider provider = new FirstRawServiceProvider();

			this.rcs.registerComponent(provider);
			this.rcs.registerComponent(this);

			FirstRawService usedService = this.rcs.useServices(FirstRawServiceProvider.class, FirstRawService.class);
			Assert.assertEquals("first", usedService.readFirstValue());
			Assert.assertEquals(1, provider.invocationCount);
		} finally {
			this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.trace);
			// Leave the container started again: the @After teardown below
			// calls stop() unconditionally, and stop() throws if the
			// container is not currently started.
			this.cm.start();
		}
	}

	@Test
	public void rawStopRemovesAllAccessedParametersForOneRawCaller() {
		FirstRawParametersOfferor firstOfferor = new FirstRawParametersOfferor();
		SecondRawParametersOfferor secondOfferor = new SecondRawParametersOfferor();
		MultiParametersRawCaller caller = new MultiParametersRawCaller();

		this.rcs.registerComponent(firstOfferor);
		this.rcs.registerComponent(secondOfferor);
		this.rcs.registerComponent(caller);

		caller.writeValues("first-value", "second-value");
		Assert.assertEquals("first-value", firstOfferor.getValue());
		Assert.assertEquals("second-value", secondOfferor.getValue());

		this.assertParametersAccessedBy(FirstRawParameters.class, MultiParametersRawCaller.class);
		this.assertParametersAccessedBy(SecondRawParameters.class, MultiParametersRawCaller.class);

		this.cm.stop();
		// stop() itself leaves the internal (technical) component tree
		// freshly re-activated (see LifecycleManagerImpl#stop()), so the
		// technical services used to inspect/re-register below would
		// already work right here, without this start() - see
		// rawRegistrationSucceedsImmediatelyAfterStopWithoutRestart() for
		// that exact case, verified in isolation. Kept here as a second,
		// symmetric stop()/start() cycle: this also exercises a normal
		// restart, not just the window right after stop().
		this.cm.start();

		this.assertParametersNotAccessedBy(FirstRawParameters.class, MultiParametersRawCaller.class);
		this.assertParametersNotAccessedBy(SecondRawParameters.class, MultiParametersRawCaller.class);

		this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		try {
			FirstRawParametersOfferor restartedFirstOfferor = new FirstRawParametersOfferor();
			SecondRawParametersOfferor restartedSecondOfferor = new SecondRawParametersOfferor();
			MultiParametersRawCaller restartedCaller = new MultiParametersRawCaller();

			this.rcs.registerComponent(restartedFirstOfferor);
			this.rcs.registerComponent(restartedSecondOfferor);
			this.rcs.registerComponent(restartedCaller);

			restartedCaller.writeValues("first-restarted", "second-restarted");
			Assert.assertEquals("first-restarted", restartedFirstOfferor.getValue());
			Assert.assertEquals("second-restarted", restartedSecondOfferor.getValue());
		} finally {
			this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.trace);
		}
	}

	@Test
	public void rawStopRemovesAllManualPortsForOneRawCaller() {
		FirstRawServiceProvider firstProvider = new FirstRawServiceProvider();
		SecondRawServiceProvider secondProvider = new SecondRawServiceProvider();
		FirstRawParametersOfferor firstOfferor = new FirstRawParametersOfferor();
		SecondRawParametersOfferor secondOfferor = new SecondRawParametersOfferor();
		MultiPortRawCaller caller = new MultiPortRawCaller();

		this.rcs.registerComponent(firstProvider);
		this.rcs.registerComponent(secondProvider);
		this.rcs.registerComponent(firstOfferor);
		this.rcs.registerComponent(secondOfferor);
		this.rcs.registerComponent(caller);

		Assert.assertEquals("first", caller.callFirstService());
		Assert.assertEquals("second", caller.callSecondService());
		caller.writeValues("combined-first", "combined-second");
		Assert.assertEquals("combined-first", firstOfferor.getValue());
		Assert.assertEquals("combined-second", secondOfferor.getValue());

		this.assertServiceUsedBy(FirstRawService.class, MultiPortRawCaller.class);
		this.assertServiceUsedBy(SecondRawService.class, MultiPortRawCaller.class);
		this.assertParametersAccessedBy(FirstRawParameters.class, MultiPortRawCaller.class);
		this.assertParametersAccessedBy(SecondRawParameters.class, MultiPortRawCaller.class);

		this.cm.stop();
		// stop() itself leaves the internal (technical) component tree
		// freshly re-activated (see LifecycleManagerImpl#stop()), so the
		// technical services used to inspect/re-register below would
		// already work right here, without this start() - see
		// rawRegistrationSucceedsImmediatelyAfterStopWithoutRestart() for
		// that exact case, verified in isolation. Kept here as a second,
		// symmetric stop()/start() cycle: this also exercises a normal
		// restart, not just the window right after stop().
		this.cm.start();

		this.assertServiceNotUsedBy(FirstRawService.class, MultiPortRawCaller.class);
		this.assertServiceNotUsedBy(SecondRawService.class, MultiPortRawCaller.class);
		this.assertParametersNotAccessedBy(FirstRawParameters.class, MultiPortRawCaller.class);
		this.assertParametersNotAccessedBy(SecondRawParameters.class, MultiPortRawCaller.class);
	}

	@Test
	public void rawStopRemovesOfferedParametersForRawOfferor() {
		FirstRawParametersOfferor firstOfferor = new FirstRawParametersOfferor();

		this.rcs.registerComponent(firstOfferor);

		this.assertParametersOfferedBy(FirstRawParametersOfferor.class, FirstRawParameters.class);

		this.cm.stop();
		// stop() itself leaves the internal (technical) component tree
		// freshly re-activated (see LifecycleManagerImpl#stop()), so the
		// technical services used to inspect/re-register below would
		// already work right here, without this start() - see
		// rawRegistrationSucceedsImmediatelyAfterStopWithoutRestart() for
		// that exact case, verified in isolation. Kept here as a second,
		// symmetric stop()/start() cycle: this also exercises a normal
		// restart, not just the window right after stop().
		this.cm.start();

		this.assertParametersNotOfferedBy(FirstRawParametersOfferor.class, FirstRawParameters.class);
	}

	@Test
	public void rawStopAllowsReofferingParametersAfterRestart() {
		FirstRawParametersOfferor firstOfferor = new FirstRawParametersOfferor();
		MultiParametersRawCaller caller = new MultiParametersRawCaller();

		this.rcs.registerComponent(firstOfferor);
		this.rcs.registerComponent(new SecondRawParametersOfferor());
		this.rcs.registerComponent(caller);

		this.cm.stop();
		// stop() itself leaves the internal (technical) component tree
		// freshly re-activated (see LifecycleManagerImpl#stop()), so the
		// technical services used to inspect/re-register below would
		// already work right here, without this start() - see
		// rawRegistrationSucceedsImmediatelyAfterStopWithoutRestart() for
		// that exact case, verified in isolation. Kept here as a second,
		// symmetric stop()/start() cycle: this also exercises a normal
		// restart, not just the window right after stop().
		this.cm.start();

		this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		try {
			FirstRawParametersOfferor restartedOfferor = new FirstRawParametersOfferor();
			this.rcs.registerComponent(restartedOfferor);
			Assert.assertEquals("initial-first", restartedOfferor.getValue());
		} finally {
			this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.trace);
		}
	}

	@Test
	public void rawStopAllowsReprovidingServicesAfterRestart() {
		FirstRawServiceProvider firstProvider = new FirstRawServiceProvider();
		MultiServiceRawCaller caller = new MultiServiceRawCaller();

		this.rcs.registerComponent(firstProvider);
		this.rcs.registerComponent(new SecondRawServiceProvider());
		this.rcs.registerComponent(caller);

		this.cm.stop();
		// stop() itself leaves the internal (technical) component tree
		// freshly re-activated (see LifecycleManagerImpl#stop()), so the
		// technical services used to inspect/re-register below would
		// already work right here, without this start() - see
		// rawRegistrationSucceedsImmediatelyAfterStopWithoutRestart() for
		// that exact case, verified in isolation. Kept here as a second,
		// symmetric stop()/start() cycle: this also exercises a normal
		// restart, not just the window right after stop().
		this.cm.start();

		this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.strict);
		try {
			FirstRawServiceProvider restartedProvider = new FirstRawServiceProvider();
			this.rcs.registerComponent(restartedProvider);
			Assert.assertEquals("first", restartedProvider.readFirstValue());
			Assert.assertEquals(1, restartedProvider.invocationCount);
		} finally {
			this.cm.getParameters().setDynamicRulesChecking(VerificationLevel.trace);
		}
	}

	@Test
	public void rawDescriptorUpdateRemovesUnprovidedServices() {
		DescriptorRawServiceProvider firstProvider = new DescriptorRawServiceProvider();
		this.rcs.registerComponent(firstProvider);
		DescriptorRegistryReadServices descriptorRegistry = this.rcs.useServices(DescriptorRegistryReadServices.class);
		ComponentDescriptor descriptor = descriptorRegistry.readComponentDescriptor(DescriptorRawServiceProvider.class);

		Assert.assertSame(firstProvider, descriptor.getServicesProvider(FirstRawService.class));

		this.rcs.unprovideServices(DescriptorRawServiceProvider.class, FirstRawService.class);

		Assert.assertNull(descriptor.getServicesProvider(FirstRawService.class));
	}

	@Test
	public void rawDescriptorUpdateRemovesUnofferedParameters() {
		DescriptorRawParametersOfferor firstOfferor = new DescriptorRawParametersOfferor();
		this.rcs.registerComponent(firstOfferor);
		DescriptorRegistryReadServices descriptorRegistry = this.rcs.useServices(DescriptorRegistryReadServices.class);
		ComponentDescriptor descriptor = descriptorRegistry.readComponentDescriptor(DescriptorRawParametersOfferor.class);

		Assert.assertSame(firstOfferor, descriptor.getParametersOfferor(FirstRawParameters.class));

		this.rcs.unofferParameters(DescriptorRawParametersOfferor.class, FirstRawParameters.class);

		Assert.assertNull(descriptor.getParametersOfferor(FirstRawParameters.class));
	}

	@Test
	public void unusingAServicesInterfaceLeavesNoEmptyEntryAmongTheServicesToUsers() {
		this.rcs.registerComponent(new FirstRawServiceProvider());
		this.rcs.useServices(FirstRawServiceProvider.class, FirstRawService.class);
		Assert.assertTrue(RawManagerInspector.useServicesManager().getServiceToUsers()
				.containsKey(FirstRawService.class));

		this.rcs.unuseServices(FirstRawService.class);

		Assert.assertFalse("an interface without any user must not be listed",
				RawManagerInspector.useServicesManager().getServiceToUsers().containsKey(FirstRawService.class));
	}

	private void assertServiceUsedBy(final Class<?> servicesInterface, final Class<?> componentType) {
		ServicesManagerServices servicesManagerServices = RawManagerInspector.useServicesManager();
		Map<Class<?>, Set<Class<?>>> servicesToUsers = servicesManagerServices.getServiceToUsers();
		Assert.assertTrue("Expected " + servicesInterface.getSimpleName() + " to be used by "
				+ componentType.getSimpleName(), servicesToUsers.containsKey(servicesInterface)
				&& servicesToUsers.get(servicesInterface).contains(componentType));
	}

	private void assertServiceNotUsedBy(final Class<?> servicesInterface, final Class<?> componentType) {
		ServicesManagerServices servicesManagerServices = RawManagerInspector.useServicesManager();
		Map<Class<?>, Set<Class<?>>> servicesToUsers = servicesManagerServices.getServiceToUsers();
		Set<Class<?>> users = servicesToUsers.get(servicesInterface);
		Assert.assertTrue("Expected no remaining user for " + servicesInterface.getSimpleName(),
				users == null || !users.contains(componentType));
	}

	private void assertParametersAccessedBy(final Class<?> parametersInterface, final Class<?> componentType) {
		ParametersManagerServices parametersManagerServices = RawManagerInspector.useParametersManager();
		Map<Class<?>, Set<Class<?>>> parametersToAccessor = parametersManagerServices.getParametersToAccessor();
		Assert.assertTrue("Expected " + parametersInterface.getSimpleName() + " to be accessed by "
				+ componentType.getSimpleName(), parametersToAccessor.containsKey(parametersInterface)
				&& parametersToAccessor.get(parametersInterface).contains(componentType));
	}

	private void assertParametersOfferedBy(final Class<?> offerorComponentType, final Class<?> parametersInterface) {
		ParametersManagerServices parametersManagerServices = RawManagerInspector.useParametersManager();
		Map<Class<?>, Set<Class<?>>> offerorToParameters = parametersManagerServices.getOfferorToParameters();
		Assert.assertTrue("Expected " + parametersInterface.getSimpleName() + " to be offered by "
				+ offerorComponentType.getSimpleName(), offerorToParameters.containsKey(offerorComponentType)
				&& offerorToParameters.get(offerorComponentType).contains(parametersInterface));
	}

	private void assertParametersNotAccessedBy(final Class<?> parametersInterface, final Class<?> componentType) {
		ParametersManagerServices parametersManagerServices = RawManagerInspector.useParametersManager();
		Map<Class<?>, Set<Class<?>>> parametersToAccessor = parametersManagerServices.getParametersToAccessor();
		Set<Class<?>> accessors = parametersToAccessor.get(parametersInterface);
		Assert.assertTrue("Expected no remaining accessor for " + parametersInterface.getSimpleName(),
				accessors == null || !accessors.contains(componentType));
	}

	private void assertParametersNotOfferedBy(final Class<?> offerorComponentType, final Class<?> parametersInterface) {
		ParametersManagerServices parametersManagerServices = RawManagerInspector.useParametersManager();
		Map<Class<?>, Set<Class<?>>> offerorToParameters = parametersManagerServices.getOfferorToParameters();
		Set<Class<?>> offeredParameters = offerorToParameters.get(offerorComponentType);
		Assert.assertTrue("Expected no remaining offered parameters for " + offerorComponentType.getSimpleName(),
				offeredParameters == null || !offeredParameters.contains(parametersInterface));
	}

	public static class RawManagerInspector {
		private static final RawComponentsServices rcs = Covalent.main.rcs();

		private static final RawManagerInspector INSTANCE = new RawManagerInspector();

		public static ServicesManagerServices useServicesManager() {
			rcs.registerComponent(RawManagerInspector.class, INSTANCE);
			return rcs.useServices(ServicesManagerServices.class);
		}

		public static ParametersManagerServices useParametersManager() {
			rcs.registerComponent(RawManagerInspector.class, INSTANCE);
			return rcs.useServices(ParametersManagerServices.class);
		}
	}

	public interface FirstRawService {
		String readFirstValue();
	}

	public interface SecondRawService {
		String readSecondValue();
	}

	public static class FirstRawServiceProvider implements FirstRawService {
		private final RawComponentsServices rcs = Covalent.main.rcs();

		private int invocationCount;

		@Activation
		public void activate() {
			this.rcs.provideServices(FirstRawServiceProvider.class, FirstRawService.class);
		}

		@Passivation
		public void passivate() {
			this.rcs.unprovideServices(FirstRawServiceProvider.class, FirstRawService.class);
		}

		@Override
		public String readFirstValue() {
			this.invocationCount++;
			return "first";
		}
	}

	public static class SecondRawServiceProvider implements SecondRawService {
		private final RawComponentsServices rcs = Covalent.main.rcs();

		private int invocationCount;

		@Activation
		public void activate() {
			this.rcs.provideServices(SecondRawServiceProvider.class, SecondRawService.class);
		}

		@Passivation
		public void passivate() {
			this.rcs.unprovideServices(SecondRawServiceProvider.class, SecondRawService.class);
		}

		@Override
		public String readSecondValue() {
			this.invocationCount++;
			return "second";
		}
	}

	public static class DescriptorRawServiceProvider implements FirstRawService {
		private final RawComponentsServices rcs = Covalent.main.rcs();

		@Activation
		public void activate() {
			this.rcs.provideServices(DescriptorRawServiceProvider.class, FirstRawService.class);
		}

		@Override
		public String readFirstValue() {
			return "descriptor";
		}
	}

	public interface FirstRawParameters {
		void setFirstValue(String value);

		String getFirstValue();
	}

	public interface SecondRawParameters {
		void setSecondValue(String value);

		String getSecondValue();
	}

	public static class FirstRawParametersOfferor implements FirstRawParameters {
		private final RawComponentsServices rcs = Covalent.main.rcs();

		private String value = "initial-first";

		@Activation
		public void activate() {
			this.rcs.offerParameters(FirstRawParametersOfferor.class, FirstRawParameters.class);
		}

		@Passivation
		public void passivate() {
			this.rcs.unofferParameters(FirstRawParametersOfferor.class, FirstRawParameters.class);
		}

		@Override
		public void setFirstValue(final String value) {
			this.value = value;
		}

		@Override
		public String getFirstValue() {
			return this.value;
		}

		public String getValue() {
			return this.value;
		}
	}

	public static class SecondRawParametersOfferor implements SecondRawParameters {
		private final RawComponentsServices rcs = Covalent.main.rcs();

		private String value = "initial-second";

		@Activation
		public void activate() {
			this.rcs.offerParameters(SecondRawParametersOfferor.class, SecondRawParameters.class);
		}

		@Passivation
		public void passivate() {
			this.rcs.unofferParameters(SecondRawParametersOfferor.class, SecondRawParameters.class);
		}

		@Override
		public void setSecondValue(final String value) {
			this.value = value;
		}

		@Override
		public String getSecondValue() {
			return this.value;
		}

		public String getValue() {
			return this.value;
		}
	}

	public static class DescriptorRawParametersOfferor implements FirstRawParameters {
		private final RawComponentsServices rcs = Covalent.main.rcs();

		private String value;

		@Activation
		public void activate() {
			this.rcs.offerParameters(DescriptorRawParametersOfferor.class, FirstRawParameters.class);
		}

		@Override
		public void setFirstValue(final String value) {
			this.value = value;
		}

		@Override
		public String getFirstValue() {
			return this.value;
		}
	}

	public static class MultiServiceRawCaller {
		private final RawComponentsServices rcs = Covalent.main.rcs();

		private FirstRawService firstRawService;

		private SecondRawService secondRawService;

		@Activation
		public void activate() {
			this.firstRawService = this.rcs.useServices(FirstRawServiceProvider.class, FirstRawService.class);
			this.secondRawService = this.rcs.useServices(SecondRawServiceProvider.class, SecondRawService.class);
		}

		public String callFirstService() {
			return this.firstRawService.readFirstValue();
		}

		public String callSecondService() {
			return this.secondRawService.readSecondValue();
		}
	}

	public static class MultiParametersRawCaller {
		private final RawComponentsServices rcs = Covalent.main.rcs();

		private FirstRawParameters firstRawParameters;

		private SecondRawParameters secondRawParameters;

		@Activation
		public void activate() {
			this.firstRawParameters = this.rcs.accessParameters(FirstRawParametersOfferor.class, FirstRawParameters.class);
			this.secondRawParameters = this.rcs.accessParameters(SecondRawParametersOfferor.class,
					SecondRawParameters.class);
		}

		public void writeValues(final String firstValue, final String secondValue) {
			this.firstRawParameters.setFirstValue(firstValue);
			this.secondRawParameters.setSecondValue(secondValue);
		}
	}

	public static class MultiPortRawCaller {
		private final RawComponentsServices rcs = Covalent.main.rcs();

		private FirstRawService firstRawService;

		private SecondRawService secondRawService;

		private FirstRawParameters firstRawParameters;

		private SecondRawParameters secondRawParameters;

		@Activation
		public void activate() {
			this.firstRawService = this.rcs.useServices(FirstRawServiceProvider.class, FirstRawService.class);
			this.secondRawService = this.rcs.useServices(SecondRawServiceProvider.class, SecondRawService.class);
			this.firstRawParameters = this.rcs.accessParameters(FirstRawParametersOfferor.class, FirstRawParameters.class);
			this.secondRawParameters = this.rcs.accessParameters(SecondRawParametersOfferor.class,
					SecondRawParameters.class);
		}

		public String callFirstService() {
			return this.firstRawService.readFirstValue();
		}

		public String callSecondService() {
			return this.secondRawService.readSecondValue();
		}

		public void writeValues(final String firstValue, final String secondValue) {
			this.firstRawParameters.setFirstValue(firstValue);
			this.secondRawParameters.setSecondValue(secondValue);
		}
	}
}
