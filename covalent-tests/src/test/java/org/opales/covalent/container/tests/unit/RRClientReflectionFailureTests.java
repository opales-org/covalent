/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.UndeclaredThrowableException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;

/**
 * A call that cannot be performed by reflection on the services side of a proxy,
 * here because the services interface is not public, is reported to the caller as
 * a {@link RuntimeException} naming the call, not as an
 * {@link UndeclaredThrowableException}.
 */
public class RRClientReflectionFailureTests {

	@Services
	interface HiddenServices {
		String hello();
	}

	@Component
	public interface Provider {
	}

	@Implementation
	public static class ProviderImpl implements Provider, HiddenServices {
		@Override
		public String hello() {
			return "hi";
		}
	}

	@Component
	public interface User {
	}

	private static volatile Throwable callFailure;

	@Implementation
	public static class UserImpl implements User {
		@UsedServices
		private HiddenServices services;

		@Activation
		public void activate() {
			try {
				this.services.hello();
			} catch (Throwable failure) {
				callFailure = failure;
			}
		}
	}

	@Before
	public void setUp() {
		callFailure = null;
	}

	@Test
	public void aReflectionFailureOnTheClientSideIsReportedAsARuntimeException() {
		CovalentContainer container = Covalent.container();
		container.deployComponents(ProviderImpl.class, UserImpl.class);
		container.start();

		Assert.assertNotNull("the call should have failed", callFailure);
		Assert.assertFalse(callFailure.toString(), callFailure instanceof UndeclaredThrowableException);
		Assert.assertTrue(callFailure.toString(), callFailure.getMessage().startsWith("Exception while invoking"));
		Assert.assertTrue(callFailure.toString(), callFailure.getCause() instanceof IllegalAccessException);
	}
}
