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
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;

/**
 * Asking for a lifecycle transition that does not match the deployment state of
 * a component type is reported as an {@link IllegalStateException} with a message
 * naming the component, not as an illegal argument.
 */
public class ComponentDeploymentStateErrorsTests {

	@Component
	public interface Plain {
	}

	@Implementation
	public static class PlainImpl implements Plain {
	}

	private CovalentContainer container;

	@Before
	public void setUp() {
		this.container = Covalent.container();
	}

	private static Throwable findInChain(final Throwable failure, final Class<? extends Throwable> type) {
		for (Throwable current = failure; current != null; current = current.getCause()) {
			if (type.isInstance(current)) {
				return current;
			}
		}
		return null;
	}

	private static void assertStateError(final Throwable failure, final String messagePart) {
		Throwable state = findInChain(failure, IllegalStateException.class);
		Assert.assertNotNull(failure.toString(), state);
		Assert.assertTrue(state.getMessage(), state.getMessage().contains(messagePart));
		Assert.assertTrue(state.getMessage(), state.getMessage().contains("Plain"));
		Assert.assertNull(failure.toString(), findInChain(failure, IllegalArgumentException.class));
	}

	@Test
	public void deployingAnAlreadyDeployedComponentIsAStateError() {
		this.container.deployComponents(PlainImpl.class);

		RuntimeException failure = Assert.assertThrows(RuntimeException.class,
				() -> this.container.lcs().deployComponent(PlainImpl.class));

		assertStateError(failure, "already deployed");
	}

	@Test
	public void initializingAComponentThatIsNotDeployedIsAStateError() {
		RuntimeException failure = Assert.assertThrows(RuntimeException.class,
				() -> this.container.lcs().initializeComponent(Plain.class));

		assertStateError(failure, "not deployed");
	}

	@Test
	public void activatingAComponentThatIsNotDeployedIsAStateError() {
		RuntimeException failure = Assert.assertThrows(RuntimeException.class,
				() -> this.container.lcs().activateComponent(Plain.class));

		assertStateError(failure, "not deployed");
	}
}
