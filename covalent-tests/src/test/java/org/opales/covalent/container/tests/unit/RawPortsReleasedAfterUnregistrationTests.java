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
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Parameters;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.raw.RawComponentsServices;

/**
 * Raw services and parameters released by a caller that was unregistered meanwhile (the test class registers
 * itself as a raw component, uses the services, then unregisters): releasing them is a no-op, as there is no
 * metamodel left to update, and nothing fails.
 */
public class RawPortsReleasedAfterUnregistrationTests {

	private CovalentContainer container;

	private RawComponentsServices rcs;

	@Before
	public void setUp() {
		this.container = Covalent.container();
		this.container.deployComponents(ProviderImpl.class);
		this.container.start();
		this.rcs = this.container.rcs();
		this.rcs.registerComponent(this);
	}

	@After
	public void tearDown() {
		this.container.dispose();
	}

	@Test
	public void servicesReleasedAfterTheCallerWasUnregisteredAreIgnored() {
		EchoServices echo = this.rcs.useServices(Provider.class, EchoServices.class);
		Assert.assertEquals("echo:hello", echo.echo("hello"));
		this.rcs.unregisterComponent(RawPortsReleasedAfterUnregistrationTests.class);

		this.rcs.unuseServices(EchoServices.class);
	}

	@Test
	public void parametersReleasedAfterTheCallerWasUnregisteredAreIgnored() {
		LabelParameters label = this.rcs.accessParameters(Provider.class, LabelParameters.class);
		label.setLabel("changed");
		Assert.assertEquals("changed", label.getLabel());
		this.rcs.unregisterComponent(RawPortsReleasedAfterUnregistrationTests.class);

		this.rcs.unaccessParameters(LabelParameters.class);
	}

	// --- Fixtures ---

	@Component
	public interface Provider {
	}

	@Services
	public interface EchoServices {
		String echo(String text);
	}

	@Parameters
	public interface LabelParameters {
		String getLabel();

		void setLabel(String label);
	}

	@Implementation
	public static class ProviderImpl implements Provider, EchoServices, LabelParameters {

		private String label = "default";

		@Override
		public String echo(final String text) {
			return "echo:" + text;
		}

		@Override
		public String getLabel() {
			return this.label;
		}

		@Override
		public void setLabel(final String label) {
			this.label = label;
		}
	}
}
