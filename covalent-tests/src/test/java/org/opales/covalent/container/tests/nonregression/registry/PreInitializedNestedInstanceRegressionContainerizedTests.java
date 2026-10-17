/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.nonregression.registry;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.registry.PortRegistryServices;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.container.tests.CovalentContainerizedTests;

public class PreInitializedNestedInstanceRegressionContainerizedTests extends CovalentContainerizedTests {

	private final CovalentContainer cm = Covalent.main;

	private final RawComponentsServices rcs = Covalent.main.rcs();

	private LifecycleServices lifecycleServices;

	private PortRegistryServices portRegistryServices;

	private RegistryReadServices registryReadServices;

	@Before
	public void setUp() {
		this.rcs.registerComponent(this);
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.silent);
		// Looked up here, not as field initializers: the base class's own
		// @Before (which runs first) is what starts the container, and these
		// raw lookups need it already running.
		this.lifecycleServices = this.rcs.useServices(LifecycleServices.class);
		this.portRegistryServices = this.rcs.useServices(PortRegistryServices.class);
		this.registryReadServices = this.rcs.useServices(RegistryReadServices.class);
	}

	@After
	public void tearDown() {
		this.cm.getParameters().setStructuralRulesChecking(VerificationLevel.trace);

	}

	@Test
	public void managedComponentShouldAdoptPreInitializedItemInstance() {
		this.cm.deployComponents(PreInitializedItemOwnerImpl.class);
		this.lifecycleServices.activateComponent(PreInitializedItemOwner.class);

		PreInitializedItemOwnerImpl owner = this.registryReadServices.getComponentInstance(PreInitializedItemOwner.class);
		PreInitializedItemServices services = this.rcs.useServices(PreInitializedItemServices.class);

		services.record("from-service");

		Assert.assertEquals("from-service", owner.readItemMessage());
		Assert.assertSame(owner.getPreInitializedItem(),
				this.portRegistryServices.getServicesProvider(PreInitializedItemServices.class));
	}

	@Test
	public void managedComponentShouldAdoptPreInitializedComposedComponentInstance() {
		this.cm.deployComponents(PreInitializedCompositeImpl.class);
		this.lifecycleServices.activateComponent(PreInitializedComposite.class);

		PreInitializedCompositeImpl composite = this.registryReadServices.getComponentInstance(PreInitializedComposite.class);
		PreInitializedComposedServices services = this.rcs.useServices(PreInitializedComposedServices.class);

		services.record("from-composed-service");

		Assert.assertEquals("from-composed-service", composite.readComposedMessage());
		Assert.assertSame(composite.getPreInitializedComposed(),
				this.portRegistryServices.getServicesProvider(PreInitializedComposedServices.class));
	}

	@Component
	public interface PreInitializedItemOwner {
	}

	@Services
	public interface PreInitializedItemServices {
		void record(String message);
	}

	@Implementation
	public static class PreInitializedItemOwnerImpl implements PreInitializedItemOwner {
		@Item
		private final PreInitializedItem item = new PreInitializedItem("pre-initialized-item");

		String readItemMessage() {
			return this.item.getMessage();
		}

		PreInitializedItem getPreInitializedItem() {
			return this.item;
		}
	}

	@Item
	public static class PreInitializedItem implements PreInitializedItemServices {
		private final String source;

		private String message;

		public PreInitializedItem(final String source) {
			this.source = source;
		}

		@Override
		public void record(final String message) {
			this.message = message;
		}

		String getMessage() {
			return this.message;
		}

		String getSource() {
			return this.source;
		}
	}

	@Component
	public interface PreInitializedComposite {
	}

	@Component
	public interface PreInitializedComposed {
	}

	@Services
	public interface PreInitializedComposedServices {
		void record(String message);
	}

	@Implementation
	public static class PreInitializedCompositeImpl implements PreInitializedComposite {
		@Implementation
		private final PreInitializedComposedImpl composed = new PreInitializedComposedImpl("pre-initialized-composed");

		String readComposedMessage() {
			return this.composed.getMessage();
		}

		PreInitializedComposedImpl getPreInitializedComposed() {
			return this.composed;
		}
	}

	@Implementation
	public static class PreInitializedComposedImpl implements PreInitializedComposed, PreInitializedComposedServices {
		private final String source;

		private String message;

		public PreInitializedComposedImpl(final String source) {
			this.source = source;
		}

		@Override
		public void record(final String message) {
			this.message = message;
		}

		String getMessage() {
			return this.message;
		}

		String getSource() {
			return this.source;
		}
	}
}
