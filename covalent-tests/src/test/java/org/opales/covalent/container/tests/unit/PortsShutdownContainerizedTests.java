/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.CovalentContainerizedTests;
import org.opales.covalent.container.tests.unit.RawComponentsContractTests.RawSettings;
import org.opales.covalent.container.tests.unit.RawComponentsContractTests.RawSettingsComponent;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.ComponentAServices;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.impl.ComponentA;

/**
 * Coverage of {@code stop()}, which clears the registry (see
 * {@link RawComponentsStopCleanupContainerizedTests}), so no port -
 * services, parameters or otherwise - a component offered before the stop
 * may keep being used once the container is stopped, whether the port was
 * obtained before or after the stop.
 */
public class PortsShutdownContainerizedTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	@Before
	public void registerTestInstanceAsRawComponent() {
		this.rcs.registerComponent(this);
	}

	@Test
	public void servicesPortObtainedBeforeStopRejectsUseAfterStop() {
		ComponentA provider = new ComponentA();
		this.rcs.registerComponent(provider);

		ComponentAServices services = this.rcs.useServices(ComponentA.class, ComponentAServices.class);
		services.doSomething();
		Assert.assertTrue("sanity: the port must work while the container is running", provider.doSomething);

		this.cm.stop();

		IllegalStateException e = Assert.assertThrows("A services port obtained before stop() must not be usable once the container is stopped.",
				IllegalStateException.class,
				() -> services.doSomething());
		Assert.assertTrue(this.isInvalidatedException(e));

		this.cm.start();
	}

	@Test
	public void servicesPortRequestedAfterStopRejectsUse() {
		ComponentA provider = new ComponentA();
		this.rcs.registerComponent(provider);

		this.cm.stop();
		this.rcs.registerComponent(this);

		// stop() always leaves internal (technical) components reactivated, so
		// requesting the port itself succeeds - it only hands back a surrogate
		// proxy, since stop()'s business cascade undeployed ComponentA and no
		// provider is bound. The failure surfaces on first invocation.
		ComponentAServices services = this.rcs.useServices(ComponentA.class, ComponentAServices.class);
		IllegalStateException e = Assert.assertThrows("A services port requested while the container is stopped must not be usable.",
				IllegalStateException.class,
				() -> services.doSomething());
		Assert.assertTrue(this.isSurrogateException(e));

		this.cm.start();
	}

	@Test
	public void parametersPortObtainedBeforeStopRejectsAccessAfterStop() {
		RawSettingsComponent offeror = new RawSettingsComponent();
		this.rcs.registerComponent(offeror);

		RawSettings parameters = this.rcs.accessParameters(RawSettingsComponent.class, RawSettings.class);
		parameters.setValue("running");
		Assert.assertEquals("sanity: the port must work while the container is running", "running",
				offeror.getValue());

		this.cm.stop();

		IllegalStateException e = Assert.assertThrows("A parameters port obtained before stop() must not be usable once the container is stopped.",
				IllegalStateException.class,
				() -> parameters.setValue("stopped"));
		Assert.assertTrue(this.isInvalidatedException(e));

		this.cm.start();
	}

	@Test
	public void parametersPortRequestedAfterStopRejectsAccess() {
		RawSettingsComponent offeror = new RawSettingsComponent();
		this.rcs.registerComponent(offeror);

		this.cm.stop();
		this.rcs.registerComponent(this);

		// Same reasoning as servicesPortRequestedAfterStopRejectsUse(): the
		// request itself succeeds against the reactivated technical machinery,
		// handing back a surrogate proxy since stop()'s business cascade left
		// no offeror bound; the failure surfaces on first invocation.
		RawSettings parameters = this.rcs.accessParameters(RawSettingsComponent.class, RawSettings.class);
		IllegalStateException e = Assert.assertThrows("A parameters port requested while the container is stopped must not be usable.",
				IllegalStateException.class,
				() -> parameters.setValue("stopped"));
		Assert.assertTrue(this.isSurrogateException(e));

		this.cm.start();
	}

}
