/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.dependencymanagement.controller.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.supervision.containermanager.raw.ObservedContainerBridge;
import org.opales.covalent.supervision.dependencymanagement.controller.DependencyManagementControllerServices;
import org.opales.covalent.supervision.dependencymanagement.model.DependencyManagementEvents;
import org.opales.covalent.supervision.dependencymanagement.model.DependencyManagementGraph;
import org.opales.covalent.supervision.dependencymanagement.model.DependencyManagementModelReadServices;
import org.opales.covalent.supervision.dependencymanagement.model.impl.DependencyManagementModelImpl;
import org.opales.covalent.supervision.exclusionpolicy.controller.ExclusionPolicyControllerServices;
import org.opales.covalent.supervision.exclusionpolicy.controller.impl.ExclusionPolicyControllerImpl;
import org.opales.covalent.supervision.exclusionpolicy.model.impl.ExclusionPolicyModelImpl;
import org.opales.covalent.supervision.testsupport.observed.CompositionChildComponent;
import org.opales.covalent.supervision.testsupport.observed.CompositionParentComponent;
import org.opales.covalent.supervision.testsupport.observed.CyclicComponentX;
import org.opales.covalent.supervision.testsupport.observed.CyclicComponentY;
import org.opales.covalent.supervision.testsupport.observed.FlatParametersAccessorComponent;
import org.opales.covalent.supervision.testsupport.observed.FlatParametersOfferorComponent;
import org.opales.covalent.supervision.testsupport.observed.impl.AutoConnectorComponentImpl;
import org.opales.covalent.supervision.testsupport.observed.impl.CompositionParentComponentImpl;
import org.opales.covalent.supervision.testsupport.observed.impl.CyclicComponentXImpl;
import org.opales.covalent.supervision.testsupport.observed.impl.CyclicComponentYImpl;
import org.opales.covalent.supervision.testsupport.observed.impl.FlatParametersAccessorComponentImpl;
import org.opales.covalent.supervision.testsupport.observed.impl.FlatParametersOfferorComponentImpl;

/**
 * Covers {@code DependencyManagementControllerImpl.refreshGraph()} - a one-shot read at its own
 * {@code @Activation}, unlike {@code ComponentDiagramControllerImpl} (no {@code
 * ObservedContainerConnectionEvents} consumed here), so the observed container must already be
 * started AND connected before the supervision container's own {@code start()} - see {@link
 * org.opales.covalent.supervision.testsupport.observed.AutoConnectorComponent}'s javadoc for why
 * this needs a different sequencing than {@link
 * org.opales.covalent.supervision.testsupport.observed.ObservedComponentDiagramTestSupport}.
 */
public class DependencyManagementControllerImplTests {

	private CovalentContainer observed;

	private CovalentContainer supervision;

	/** Whether a graph was published. */
	private boolean graphChanged;

	private DependencyManagementModelReadServices graphReadServices;

	/** The observed container's name: only a managed container can be observed. */
	private static final String OBSERVED = "dependency-management-controller-tests";

	@Before
	public void setUp() {
		this.observed = Covalent.managedContainer(OBSERVED);
		this.observed.getParameters().setSynchronousEvents(true);
		this.observed.getParameters().setCircularDependencyChecking(true);

		this.supervision = Covalent.container();
		this.supervision.getParameters().setSynchronousEvents(true);
		RawComponentsServices supervisionRcs = this.supervision.rcs();
		supervisionRcs.registerComponent(DependencyManagementControllerImplTests.class, this);
		supervisionRcs.subscribe(DependencyManagementControllerImplTests.class, DependencyManagementEvents.class,
				() -> this.graphChanged = true);
	}

	@After
	public void tearDown() {
		AutoConnectorComponentImpl.target = null;
		if (this.supervision != null) {
			this.supervision.dispose();
		}
		if (this.observed != null) {
			this.observed.dispose();
		}
	}

	private void deployAndStartBoth(final Class<?>... observedFixtures) {
		if (observedFixtures.length > 0) {
			this.observed.deployComponents(observedFixtures);
		}
		this.observed.start();

		AutoConnectorComponentImpl.target = this.observed;
		this.supervision.deployComponents(ObservedContainerBridge.class, ExclusionPolicyModelImpl.class,
				ExclusionPolicyControllerImpl.class, DependencyManagementModelImpl.class,
				AutoConnectorComponentImpl.class, DependencyManagementControllerImpl.class);
		this.supervision.start();
	}

	/** The graph last published, read from the model, or {@code null} if none was published. */
	private DependencyManagementGraph lastGraph() {
		if (!this.graphChanged) {
			return null;
		}
		if (this.graphReadServices == null) {
			this.graphReadServices = this.supervision.rcs().useServices(DependencyManagementModelReadServices.class);
		}
		return this.graphReadServices.getGraph();
	}

	@Test
	public void aCompositeIsLinkedToItsChildrenApartFromTheDependencies() {
		this.deployAndStartBoth(CompositionParentComponentImpl.class);

		Assert.assertEquals(Map.of(CompositionParentComponent.class, Set.of(CompositionChildComponent.class)),
				this.lastGraph().getCompositions());
		Assert.assertTrue(this.lastGraph().getCircularDependencies().isEmpty());
	}

	@Test
	public void emptyObservedContainerYieldsAnEmptyGraph() {
		this.deployAndStartBoth();

		Assert.assertNotNull(this.lastGraph());
		Assert.assertTrue(this.lastGraph().getComponents().isEmpty());
		Assert.assertTrue(this.lastGraph().getDependencies().isEmpty());
		Assert.assertTrue(this.lastGraph().getCircularDependencies().isEmpty());
	}

	@Test
	public void parameterDependencyAppearsAsAnEdgeFromAccessorToOfferor() {
		this.deployAndStartBoth(FlatParametersOfferorComponentImpl.class, FlatParametersAccessorComponentImpl.class);

		Assert.assertEquals(Set.of(FlatParametersOfferorComponent.class, FlatParametersAccessorComponent.class),
				this.lastGraph().getComponents());
		Assert.assertEquals(Set.of(FlatParametersOfferorComponent.class),
				this.lastGraph().getDependencies().get(FlatParametersAccessorComponent.class));
	}

	@Test
	public void excludingTheOfferorDropsItFromComponentsAndFromTheAccessorsDependencySet() {
		this.observed.deployComponents(FlatParametersOfferorComponentImpl.class,
				FlatParametersAccessorComponentImpl.class);
		this.observed.start();

		AutoConnectorComponentImpl.target = this.observed;
		this.supervision.deployComponents(ObservedContainerBridge.class, ExclusionPolicyModelImpl.class,
				ExclusionPolicyControllerImpl.class, DependencyManagementModelImpl.class,
				AutoConnectorComponentImpl.class, DependencyManagementControllerImpl.class);

		// Exclude before start(), since there is no reactive rebuild for this Controller:
		// setExcludedComponents only takes effect for a rebuild that has not happened yet.
		RawComponentsServices supervisionRcs = this.supervision.rcs();
		this.supervision.start();
		ExclusionPolicyControllerServices exclusionPolicyControllerServices =
				supervisionRcs.useServices(ExclusionPolicyControllerServices.class);
		exclusionPolicyControllerServices.setExcludedComponents(List.of(FlatParametersOfferorComponent.class));

		// refreshGraph() is public on DependencyManagementControllerServices precisely so a
		// caller (a real View's refresh button, here this test) can re-invoke it - this
		// directly verifies the exclusion-filtering logic without needing a second
		// Controller-reactivation path that does not exist for this one-shot Controller.
		supervisionRcs.useServices(DependencyManagementControllerServices.class).refreshGraph();

		Assert.assertFalse(this.lastGraph().getComponents().contains(FlatParametersOfferorComponent.class));
		Assert.assertTrue(this.lastGraph().getComponents().contains(FlatParametersAccessorComponent.class));
		Assert.assertTrue("the accessor's own dependency set must no longer list the now-excluded offeror",
				this.lastGraph().getDependencies().get(FlatParametersAccessorComponent.class).isEmpty());
	}

	@Test
	public void theGraphIsComputedEvenWhenCircularDependencyCheckingIsDisabled() {
		this.observed.getParameters().setCircularDependencyChecking(false);
		this.deployAndStartBoth(FlatParametersOfferorComponentImpl.class, FlatParametersAccessorComponentImpl.class);

		Assert.assertNotNull(this.lastGraph());
		Assert.assertTrue(this.lastGraph().isAvailable());
		Assert.assertFalse(this.lastGraph().getComponents().isEmpty());
	}

	@Test
	public void aCircularDependencyIsReportedWhenNoMemberIsExcluded() {
		this.deployAndStartBoth(CyclicComponentXImpl.class, CyclicComponentYImpl.class);

		Assert.assertEquals(1, this.lastGraph().getCircularDependencies().size());
		List<Class<?>> cycle = this.lastGraph().getCircularDependencies().get(0);
		Assert.assertEquals(Set.of(CyclicComponentX.class, CyclicComponentY.class), Set.copyOf(cycle));
	}

	@Test
	public void aCircularDependencyWithAnExcludedMemberIsDroppedEntirely() {
		this.observed.deployComponents(CyclicComponentXImpl.class, CyclicComponentYImpl.class);
		this.observed.start();

		AutoConnectorComponentImpl.target = this.observed;
		this.supervision.deployComponents(ObservedContainerBridge.class, ExclusionPolicyModelImpl.class,
				ExclusionPolicyControllerImpl.class, DependencyManagementModelImpl.class,
				AutoConnectorComponentImpl.class, DependencyManagementControllerImpl.class);
		RawComponentsServices supervisionRcs = this.supervision.rcs();
		this.supervision.start();

		supervisionRcs.useServices(ExclusionPolicyControllerServices.class)
				.setExcludedComponents(List.of(CyclicComponentX.class));
		supervisionRcs.useServices(DependencyManagementControllerServices.class).refreshGraph();

		Assert.assertTrue("a cycle missing one of its members is no longer the cycle that was actually found, "
				+ "so it must be dropped entirely rather than reported with a gap",
				this.lastGraph().getCircularDependencies().isEmpty());
		Assert.assertFalse(this.lastGraph().getComponents().contains(CyclicComponentX.class));
		Assert.assertTrue(this.lastGraph().getComponents().contains(CyclicComponentY.class));
	}
}
