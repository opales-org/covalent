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
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Parameters;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Initialization;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.tests.CovalentTests;

public class RawComponentsContractTests extends CovalentTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	private final LifecycleServices lifecycleServices = Covalent.main.lcs();

	@Before
	public void setUp() {
		this.rcs.registerComponent(this);
		this.cm.getParameters().setSynchronousEvents(true);
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
	}

	@After
	public void tearDown() {
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.trace);
		this.cm.stop();
	}

	@Test
	public void rawParametersAccessAcceptsManagedImplementationAsOfferorType() {
		this.cm.deployComponents(ManagedParameterOwnerImpl.class);
		this.cm.start();

		ManagedSettings parameters = this.rcs.accessParameters(ManagedParameterOwnerImpl.class, ManagedSettings.class);

		Assert.assertEquals("managed", parameters.getValue());
	}

	public interface RawSettings {
		String getValue();

		void setValue(String value);
	}

	public static class RawSettingsComponent implements RawSettings {
		private final RawComponentsServices rcs = Covalent.main.rcs();

		private String value = "initial";

		@Activation
		public void activate() {
			this.rcs.offerParameters(RawSettingsComponent.class, RawSettings.class);
		}

		@Passivation
		public void passivate() {
			this.rcs.unofferParameters(RawSettingsComponent.class, RawSettings.class);
		}

		@Override
		public String getValue() {
			return this.value;
		}

		@Override
		public void setValue(final String value) {
			this.value = value;
		}
	}

	@Component
	public interface ManagedParameterOwner {
	}

	@Parameters
	public interface ManagedSettings {
		String getValue();
	}

	@Implementation
	public static class ManagedParameterOwnerImpl implements ManagedParameterOwner, ManagedSettings {
		@Override
		public String getValue() {
			return "managed";
		}
	}
}
