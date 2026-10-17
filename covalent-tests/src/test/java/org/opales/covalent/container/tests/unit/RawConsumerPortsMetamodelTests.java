/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.lifecycle.DependencyGraph;
import org.opales.covalent.container.lifecycle.DependencyGraphServices;
import org.opales.covalent.container.metamodel.MetamodelPortReadServices;
import org.opales.covalent.container.raw.RawComponentsServices;

/**
 * A raw component's consumer-side services and parameters ports are recorded
 * in its own metamodel, like its consumed events: {@code useServices} and
 * {@code accessParameters} add the interface to the user's used services and
 * accessed parameters, {@code unuseServices} and {@code unaccessParameters}
 * remove it. The dependency graph is built from these metamodel ports, so a
 * raw component that uses another component's services depends on that
 * component in the graph.
 */
public class RawConsumerPortsMetamodelTests {

	public interface ExportServices {
		String export();
	}

	public interface ExportParameters {
		String getFormat();
	}

	public static class Provider implements ExportServices, ExportParameters {

		private final RawComponentsServices rcs;

		public Provider(final RawComponentsServices rcs) {
			this.rcs = rcs;
		}

		void publish() {
			this.rcs.provideServices(Provider.class, ExportServices.class);
			this.rcs.offerParameters(Provider.class, ExportParameters.class);
		}

		@Override
		public String export() {
			return "exported";
		}

		@Override
		public String getFormat() {
			return "csv";
		}

	}

	public static class Consumer {

		private final RawComponentsServices rcs;

		public Consumer(final RawComponentsServices rcs) {
			this.rcs = rcs;
		}

		void useServices() {
			this.rcs.useServices(ExportServices.class);
		}

		void unuseServices() {
			this.rcs.unuseServices(ExportServices.class);
		}

		void accessParameters() {
			this.rcs.accessParameters(ExportParameters.class);
		}

		void unaccessParameters() {
			this.rcs.unaccessParameters(ExportParameters.class);
		}

	}

	private CovalentContainer container;

	private RawComponentsServices rcs;

	private MetamodelPortReadServices portRead;

	private Provider provider;

	private Consumer consumer;

	@Before
	public void setUp() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);
		this.container.getParameters().setCircularDependencyChecking(true);
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.trace);
		this.container.start();
		this.rcs = this.container.rcs();
		this.rcs.registerComponent(this);
		this.portRead = this.rcs.useServices(MetamodelPortReadServices.class);
		this.provider = new Provider(this.rcs);
		this.rcs.registerComponent(this.provider);
		this.provider.publish();
		this.consumer = new Consumer(this.rcs);
		this.rcs.registerComponent(this.consumer);
	}

	@After
	public void tearDown() {
		if (this.container != null) {
			this.container.dispose();
		}
	}

	@Test
	public void useServicesRecordsTheUsedServicesInTheUserMetamodel() {
		this.consumer.useServices();

		Assert.assertTrue(this.portRead.getUsedServices(Consumer.class).contains(ExportServices.class));
	}

	@Test
	public void unuseServicesRemovesTheUsedServicesFromTheUserMetamodel() {
		this.consumer.useServices();

		this.consumer.unuseServices();

		Assert.assertFalse(this.portRead.getUsedServices(Consumer.class).contains(ExportServices.class));
	}

	@Test
	public void accessParametersRecordsTheAccessedParametersInTheAccessorMetamodel() {
		this.consumer.accessParameters();

		Assert.assertTrue(this.portRead.getAccessedParameters(Consumer.class).contains(ExportParameters.class));
	}

	@Test
	public void unaccessParametersRemovesTheAccessedParametersFromTheAccessorMetamodel() {
		this.consumer.accessParameters();

		this.consumer.unaccessParameters();

		Assert.assertFalse(this.portRead.getAccessedParameters(Consumer.class).contains(ExportParameters.class));
	}

	@Test
	public void aRawServicesUserDependsOnTheProviderInTheDependencyGraph() {
		this.consumer.useServices();

		Assert.assertTrue(this.dependenciesOf(Consumer.class).contains(Provider.class));
	}

	@Test
	public void aRawParametersAccessorDependsOnTheOfferorInTheDependencyGraph() {
		this.consumer.accessParameters();

		Assert.assertTrue(this.dependenciesOf(Consumer.class).contains(Provider.class));
	}

	@Test
	public void aReleasedRawPortCreatesNoDependency() {
		this.consumer.useServices();
		this.consumer.accessParameters();

		this.consumer.unuseServices();
		this.consumer.unaccessParameters();

		Assert.assertFalse(this.dependenciesOf(Consumer.class).contains(Provider.class));
	}

	private Set<Class<?>> dependenciesOf(final Class<?> componentType) {
		DependencyGraph graph = this.rcs.useServices(DependencyGraphServices.class).getDependencyGraph();
		return graph.getDependencies().getOrDefault(componentType, Set.of());
	}

}
