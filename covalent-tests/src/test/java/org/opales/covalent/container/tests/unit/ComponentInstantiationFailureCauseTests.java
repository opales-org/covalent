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
 * When a component cannot be instantiated, the failure that caused it stays
 * reachable through the exception chain: an exception thrown by the
 * constructor, or the absence of a public default constructor.
 */
public class ComponentInstantiationFailureCauseTests {

	@Component
	public interface Failing {
	}

	@Implementation
	public static class FailingImpl implements Failing {
		public FailingImpl() {
			throw new IllegalStateException("constructor failure");
		}
	}

	@Component
	public interface NoDefaultConstructor {
	}

	@Implementation
	public static class NoDefaultConstructorImpl implements NoDefaultConstructor {
		public NoDefaultConstructorImpl(final String required) {
		}
	}

	private CovalentContainer container;

	@Before
	public void setUp() {
		this.container = Covalent.container();
	}

	private static boolean chainContains(final Throwable failure, final Class<? extends Throwable> type,
			final String messagePart) {
		for (Throwable current = failure; current != null; current = current.getCause()) {
			if (type.isInstance(current) && String.valueOf(current.getMessage()).contains(messagePart)) {
				return true;
			}
		}
		return false;
	}

	@Test
	public void anExceptionThrownByTheConstructorIsKeptAsCause() {
		this.container.deployComponents(FailingImpl.class);

		RuntimeException failure = Assert.assertThrows(RuntimeException.class,
				() -> this.container.lcs().instantiateComponent(Failing.class));

		Assert.assertTrue(failure.toString(), chainContains(failure, IllegalStateException.class, "constructor failure"));
	}

	@Test
	public void theAbsenceOfADefaultConstructorIsKeptAsCause() {
		this.container.deployComponents(NoDefaultConstructorImpl.class);

		RuntimeException failure = Assert.assertThrows(RuntimeException.class,
				() -> this.container.lcs().instantiateComponent(NoDefaultConstructor.class));

		Assert.assertTrue(failure.toString(), chainContains(failure, NoSuchMethodException.class, "<init>"));
	}
}
