/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.raw.RawComponentsServices;

/**
 * Verifies that the exceptions raised by a provider reach the caller
 * unchanged when the first call goes through the surrogate that stands in
 * for a provider declared after the services were used: a declared checked
 * exception and an unchecked exception, exactly as on every later call.
 */
public class SurrogateServicesExceptionTests {

	public interface FailingServices {
		void failChecked() throws IOException;

		void failUnchecked();
	}

	public static class FailingServicesImpl implements FailingServices {
		@Override
		public void failChecked() throws IOException {
			throw new IOException("checked failure");
		}

		@Override
		public void failUnchecked() {
			throw new IllegalStateException("unchecked failure");
		}
	}

	private RawComponentsServices rcs;

	@Before
	public void setUp() {
		CovalentContainer container = Covalent.container();
		this.rcs = container.rcs();
		this.rcs.registerComponent(this);
		this.rcs.registerComponent(FailingServicesImpl.class, new FailingServicesImpl());
	}

	private FailingServices useBeforeProvidedThenProvide() {
		FailingServices used = this.rcs.useServices(FailingServicesImpl.class, FailingServices.class);
		this.rcs.provideServices(FailingServicesImpl.class, FailingServices.class);
		return used;
	}

	@Test
	public void aDeclaredCheckedExceptionReachesTheCallerThroughTheSurrogate() {
		FailingServices used = this.useBeforeProvidedThenProvide();

		IOException failure = Assert.assertThrows(IOException.class, used::failChecked);
		Assert.assertEquals("checked failure", failure.getMessage());
	}

	@Test
	public void anUncheckedExceptionReachesTheCallerThroughTheSurrogate() {
		FailingServices used = this.useBeforeProvidedThenProvide();

		IllegalStateException failure = Assert.assertThrows(IllegalStateException.class, used::failUnchecked);
		Assert.assertEquals("unchecked failure", failure.getMessage());
	}
}
