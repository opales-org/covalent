/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.tests.CovalentTests;
import org.opales.covalent.container.tests.unit.rawcomponents.componenta.impl.ComponentA;

public class ManagedContainersTests extends CovalentTests {

	private static final String FIRST_TEST_CONTAINER = "managed-containers-tests-first";

	private static final String SECOND_TEST_CONTAINER = "managed-containers-tests-second";

	@After
	public void tearDown() {
		Covalent.disposeManagedContainer(FIRST_TEST_CONTAINER);
		Covalent.disposeManagedContainer(SECOND_TEST_CONTAINER);
	}

	@Test
	public void aManagedContainerKnowsItsName() {
		Assert.assertEquals(FIRST_TEST_CONTAINER, Covalent.managedContainer(FIRST_TEST_CONTAINER).getName());
	}

	@Test
	public void theMainContainerIsNamedMain() {
		Assert.assertEquals("main", Covalent.main.getName());
	}

	@Test
	public void anUnmanagedContainerHasNoName() {
		CovalentContainer container = Covalent.container();
		try {
			Assert.assertNull(container.getName());
		} finally {
			container.dispose();
		}
	}

	@Test
	public void testManagedContainerNamesExposeMainFirst() {
		Covalent.managedContainer(FIRST_TEST_CONTAINER);
		Covalent.managedContainer(SECOND_TEST_CONTAINER);

		List<String> containerNames = new ArrayList<>(Covalent.getManagedContainerNames());
		Assert.assertTrue(containerNames.size() >= 3);
		Assert.assertEquals("main", containerNames.get(0));
		Assert.assertTrue(containerNames.contains(FIRST_TEST_CONTAINER));
		Assert.assertTrue(containerNames.contains(SECOND_TEST_CONTAINER));
	}

	@Test
	public void testGetManagedContainerReturnsExistingContainerWithoutCreatingIt() {
		CovalentContainer managedContainer = Covalent.managedContainer(FIRST_TEST_CONTAINER);

		Assert.assertSame(Covalent.main, Covalent.getManagedContainer("main"));
		Assert.assertSame(managedContainer, Covalent.getManagedContainer(FIRST_TEST_CONTAINER));
		Assert.assertNull(Covalent.getManagedContainer("missing-managed-container"));
	}

	@Test
	public void testDisposeDoesNotDisposeMainContainer() {
		Assert.assertFalse(Covalent.disposeManagedContainer("main"));
		Assert.assertSame(Covalent.main, Covalent.getManagedContainer("main"));
	}

	@Test
	public void testUnmanagedContainerIsNotAddedToManagedContainerNames() {
		List<String> before = new ArrayList<>(Covalent.getManagedContainerNames());

		CovalentContainer unmanagedContainer = Covalent.container();

		Assert.assertNotNull(unmanagedContainer);
		Assert.assertEquals(before, new ArrayList<>(Covalent.getManagedContainerNames()));
		Assert.assertNotSame(Covalent.main, unmanagedContainer);
	}

	@Test
	public void testDisposeRemovesManagedContainerAndRecreatedContainerStartsClean() {
		CovalentContainer firstContainer = Covalent.managedContainer(FIRST_TEST_CONTAINER);
		RawComponentsServices rawComponentsServices = firstContainer.rcs();
		rawComponentsServices.registerComponent(this);
		RegistryReadServices registryReadServices = rawComponentsServices.useServices(RegistryReadServices.class);
		rawComponentsServices.registerComponent(new ComponentA(rawComponentsServices));

		Assert.assertTrue(registryReadServices.isRegistered(ComponentA.class));
		Assert.assertTrue(Covalent.disposeManagedContainer(FIRST_TEST_CONTAINER));
		Assert.assertNull(Covalent.getManagedContainer(FIRST_TEST_CONTAINER));

		CovalentContainer recreatedContainer = Covalent.managedContainer(FIRST_TEST_CONTAINER);
		recreatedContainer.rcs().registerComponent(this);
		RegistryReadServices recreatedRegistryServices = recreatedContainer.rcs().useServices(RegistryReadServices.class);

		Assert.assertNotSame(firstContainer, recreatedContainer);
		Assert.assertFalse(recreatedRegistryServices.isRegistered(ComponentA.class));
	}

	@Test
	public void testDisposedContainerRejectsFurtherCallsWithDedicatedMessage() {
		CovalentContainer firstContainer = Covalent.managedContainer(FIRST_TEST_CONTAINER);
		RawComponentsServices rawComponentsServices = firstContainer.rcs();

		firstContainer.dispose();

		Assert.assertTrue(firstContainer.isDisposed());
		IllegalStateException e = Assert.assertThrows("Expected IllegalStateException after dispose()",
				IllegalStateException.class,
				() -> rawComponentsServices.registerComponent(new ComponentA(rawComponentsServices)));
		Assert.assertEquals("Container is disposed and no longer available.", e.getMessage());
	}

	@Test
	public void testDisposeManagedContainerDisposesRatherThanJustStops() {
		CovalentContainer firstContainer = Covalent.managedContainer(FIRST_TEST_CONTAINER);
		RawComponentsServices rawComponentsServices = firstContainer.rcs();

		Assert.assertTrue(Covalent.disposeManagedContainer(FIRST_TEST_CONTAINER));

		// disposeManagedContainer() disposes rather than merely stopping:
		// the container's internal component tree is permanently
		// passivated, not just its business components torn down - a proxy
		// call into it must be rejected with dispose()'s own message.
		Assert.assertTrue(firstContainer.isDisposed());
		IllegalStateException e = Assert.assertThrows("Expected IllegalStateException after dispose",
				IllegalStateException.class,
				() -> rawComponentsServices.registerComponent(new ComponentA(rawComponentsServices)));
		Assert.assertEquals("Container is disposed and no longer available.", e.getMessage());
	}

	@Test
	public void testGetManagedContainersReturnsSnapshotIncludingMain() {
		CovalentContainer firstContainer = Covalent.managedContainer(FIRST_TEST_CONTAINER);
		CovalentContainer secondContainer = Covalent.managedContainer(SECOND_TEST_CONTAINER);

		Map<String, CovalentContainer> managedContainers = Covalent.getManagedContainers();

		Assert.assertSame(Covalent.main, managedContainers.get("main"));
		Assert.assertSame(firstContainer, managedContainers.get(FIRST_TEST_CONTAINER));
		Assert.assertSame(secondContainer, managedContainers.get(SECOND_TEST_CONTAINER));
	}

	@Test
	public void testDisposeManagedContainersKeepsMainAndDisposesNamedContainers() {
		Covalent.managedContainer(FIRST_TEST_CONTAINER);
		Covalent.managedContainer(SECOND_TEST_CONTAINER);

		Covalent.disposeManagedContainers();

		Assert.assertSame(Covalent.main, Covalent.getManagedContainer("main"));
		Assert.assertNull(Covalent.getManagedContainer(FIRST_TEST_CONTAINER));
		Assert.assertNull(Covalent.getManagedContainer(SECOND_TEST_CONTAINER));
		Assert.assertEquals(List.of("main"), new ArrayList<>(Covalent.getManagedContainerNames()));
	}

}
