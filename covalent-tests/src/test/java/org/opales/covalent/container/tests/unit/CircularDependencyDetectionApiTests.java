/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters;
import org.opales.covalent.container.CovalentContainerParameters.VerificationLevel;
import org.opales.covalent.container.lifecycle.DependencyGraph;
import org.opales.covalent.container.lifecycle.DependencyGraphServices;
import org.opales.covalent.container.lifecycle.LifecycleServices;
import org.opales.covalent.container.tests.unit.complexcycle.AType;
import org.opales.covalent.container.tests.unit.complexcycle.BType;
import org.opales.covalent.container.tests.unit.complexcycle.SiblingType;
import org.opales.covalent.container.tests.unit.complexcycle.XType;
import org.opales.covalent.container.tests.unit.complexcycle.YType;
import org.opales.covalent.container.tests.unit.complexcycle.ZType;
import org.opales.covalent.container.tests.unit.complexcycle.impl.AImpl;
import org.opales.covalent.container.tests.unit.complexcycle.impl.BImpl;
import org.opales.covalent.container.tests.unit.complexcycle.impl.SiblingImpl;
import org.opales.covalent.container.tests.unit.complexcycle.impl.XImpl;
import org.opales.covalent.container.tests.unit.complexcycle.impl.YImpl;
import org.opales.covalent.container.tests.unit.complexcycle.impl.ZImpl;

/**
 * Circular dependency detection's public API:
 * {@link CovalentContainerParameters#setCircularDependencyChecking(boolean)}
 * / {@code isCircularDependencyChecking()} only turn the check on or off -
 * what happens on a found cycle is driven by the existing
 * {@link CovalentContainerParameters#getStructuralRulesChecking()}, the same
 * as every other structural rule - and
 * {@link DependencyGraphServices#getDependencyGraph()} - provided by the
 * lifecycle manager's dependency graph tracking item, resolved through
 * {@link org.opales.covalent.container.raw.RawComponentsServices#useServices(Class)} -
 * returning a {@link DependencyGraph}, or throwing before the container was
 * ever started.
 * <p>
 * The first group below (parameter default and roundtrip,
 * throws-before-first-start) exercises the API shape. The second group
 * exercises the actual detection behavior.
 */
public class CircularDependencyDetectionApiTests {

	private CovalentContainer container;

	@After
	public void tearDown() {
		if (this.container != null) {
			this.container.dispose();
		}
	}

	// --- API shape: parameter default, roundtrip, and throw-before-start. ---

	@Test
	public void circularDependencyCheckingDefaultsToDisabled() {
		this.container = Covalent.container();

		Assert.assertFalse(this.container.getParameters().isCircularDependencyChecking());
	}

	@Test
	public void circularDependencyCheckingFlagRoundTrips() {
		this.container = Covalent.container();
		CovalentContainerParameters parameters = this.container.getParameters();

		parameters.setCircularDependencyChecking(true);

		Assert.assertTrue(parameters.isCircularDependencyChecking());
	}

	@Test(expected = IllegalStateException.class)
	public void getDependencyGraphThrowsBeforeFirstStart() {
		this.container = Covalent.container();

		this.container.rcs().useServices(DependencyGraphServices.class).getDependencyGraph();
	}

	@Test
	public void hasDependencyGraphIsFalseBeforeFirstStart() {
		this.container = Covalent.container();
		this.container.rcs().registerComponent(this);

		Assert.assertFalse(this.container.rcs().useServices(DependencyGraphServices.class).hasDependencyGraph());
	}

	@Test(expected = IllegalStateException.class)
	public void getDependencyGraphThrowsAgainAfterStop() {
		this.container = Covalent.container();

		this.container.start();
		this.container.stop();

		this.container.rcs().useServices(DependencyGraphServices.class).getDependencyGraph();
	}

	/**
	 * A {@code DependencyGraphServices} reference obtained before {@code stop()}
	 * is a client proxy like any other raw service reference: {@code stop()}
	 * invalidates it, the same as every other service or parameter proxy
	 * obtained before the stop (see the troubleshooting guide). Calling it
	 * afterwards throws rather than returning a safe {@code false}, so this
	 * asserts the invalidation instead of a graph-less state.
	 */
	@Test(expected = IllegalStateException.class)
	public void hasDependencyGraphThrowsOnAProxyObtainedBeforeStop() {
		this.container = Covalent.container();
		this.container.getParameters().setCircularDependencyChecking(true);

		this.container.start();
		DependencyGraphServices dependencyGraphServices = this.container.rcs().useServices(DependencyGraphServices.class);
		Assert.assertTrue("expected a graph while started", dependencyGraphServices.hasDependencyGraph());

		this.container.stop();

		dependencyGraphServices.hasDependencyGraph();
	}

	// --- Detection behavior. ---

	@Test
	public void checkingDisabledStillAnalyzesTheLiveGraphAfterStart() {
		this.container = Covalent.container();
		this.container.rcs().registerComponent(this);
		// circularDependencyChecking stays at its default (false).

		this.container.start();

		DependencyGraphServices dependencyGraphServices = this.container.rcs().useServices(DependencyGraphServices.class);
		Assert.assertTrue("start() ran, so a graph is available", dependencyGraphServices.hasDependencyGraph());
		DependencyGraph graph = dependencyGraphServices.getDependencyGraph();
		Assert.assertNotNull("the live graph is analyzed on demand, whether or not checking is enabled", graph);
		Assert.assertFalse(graph.hasCircularDependencies());
	}

	/**
	 * The graph analyzes the container's current structure, which includes the
	 * raw component this test registers to look the services up: the start
	 * order is read without it.
	 */
	private List<Class<?>> deployedStartOrder(final DependencyGraph graph) {
		return graph.getStartOrder().stream().filter(type -> type != this.getClass()).toList();
	}

	@Test
	public void traceLevelComputesGraphReportingTheCycleWithoutFailingStart() {
		this.container = Covalent.container();
		this.container.rcs().registerComponent(this);
		this.container.getParameters().setCircularDependencyChecking(true);
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.trace);
		LifecycleServices lifecycleServices = this.container.lcs();
		lifecycleServices.deployComponents(
				org.opales.covalent.container.tests.unit.managedcycle.impl.AImpl.class,
				org.opales.covalent.container.tests.unit.managedcycle.impl.BImpl.class);

		this.container.start();

		DependencyGraphServices dependencyGraphServices = this.container.rcs().useServices(DependencyGraphServices.class);
		Assert.assertTrue("expected hasDependencyGraph() to report a graph is available",
				dependencyGraphServices.hasDependencyGraph());
		DependencyGraph graph = dependencyGraphServices.getDependencyGraph();
		Assert.assertNotNull("expected a graph once checking is enabled", graph);
		Assert.assertTrue("expected the A <-> B cycle to be reported", graph.hasCircularDependencies());
		Assert.assertEquals(1, graph.getCircularDependencies().size());
	}

	@Test(expected = RuntimeException.class)
	public void strictStructuralRulesFailsStartBeforeStartingAnythingWhenACycleExists() {
		this.container = Covalent.container();
		this.container.getParameters().setCircularDependencyChecking(true);
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.strict);
		this.container.lcs().deployComponents(
				org.opales.covalent.container.tests.unit.managedcycle.impl.AImpl.class,
				org.opales.covalent.container.tests.unit.managedcycle.impl.BImpl.class);

		this.container.start();
	}

	@Test
	public void acyclicGraphExposesAFullDependencyOrderedStartOrder() {
		this.container = Covalent.container();
		this.container.getParameters().setCircularDependencyChecking(true);
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.trace);
		LifecycleServices lifecycleServices = this.container.lcs();
		// AImpl alone: its @UsedServices port on BServices resolves to a surrogate,
		// not a real edge, since BImpl is not deployed - so no cycle here, and the
		// start order is just [AType].
		lifecycleServices.deployComponent(org.opales.covalent.container.tests.unit.managedcycle.impl.AImpl.class);

		this.container.start();

		this.container.rcs().registerComponent(this);
		DependencyGraph graph = this.container.rcs().useServices(DependencyGraphServices.class).getDependencyGraph();
		Assert.assertNotNull(graph);
		Assert.assertFalse(graph.hasCircularDependencies());
		Assert.assertEquals(1, this.deployedStartOrder(graph).size());
	}

	/**
	 * The case the user's original "already-seen node means a cycle" sketch
	 * gets wrong: a long ancestor (gray) path A -&gt; B -&gt; X, then a real
	 * cycle several hops in, X -&gt; Y -&gt; Z -&gt; X, plus an unrelated
	 * component (Sibling) that revisits B <em>after</em> B has already been
	 * fully processed (black) through A's branch. A correct three-color DFS
	 * must report exactly one 3-node cycle (X, Y, Z) and must not flag
	 * Sibling's revisit of B as a second cycle; a naive "seen before = cycle"
	 * implementation would false-positive on that revisit.
	 */
	@Test
	public void complexGraphWithLongGrayPathAndBlackRevisitFindsOnlyTheRealCycle() {
		this.container = Covalent.container();
		this.container.getParameters().setCircularDependencyChecking(true);
		this.container.getParameters().setStructuralRulesChecking(VerificationLevel.trace);
		LifecycleServices lifecycleServices = this.container.lcs();
		// A -> B -> X -> Y -> Z -> X (cycle), and Sibling -> B (revisits an
		// already-black node, not a cycle).
		lifecycleServices.deployComponents(AImpl.class, BImpl.class, XImpl.class, YImpl.class, ZImpl.class,
				SiblingImpl.class);

		this.container.start();

		this.container.rcs().registerComponent(this);
		DependencyGraph graph = this.container.rcs().useServices(DependencyGraphServices.class).getDependencyGraph();
		Assert.assertNotNull(graph);
		Assert.assertTrue("expected the X -> Y -> Z -> X cycle to be found", graph.hasCircularDependencies());

		List<List<Class<?>>> cycles = graph.getCircularDependencies();
		Assert.assertEquals("expected exactly one cycle, not one per revisited node", 1, cycles.size());
		Assert.assertEquals("expected the cycle to be exactly {X, Y, Z}", 3, cycles.get(0).size());
		Assert.assertTrue(cycles.get(0).contains(XType.class));
		Assert.assertTrue(cycles.get(0).contains(YType.class));
		Assert.assertTrue(cycles.get(0).contains(ZType.class));

		// Every deployed component still gets a position in the start order,
		// cyclic or not: Covalent reports a circular dependency, it does not
		// drop the components involved from the order it hands back.
		List<Class<?>> startOrder = this.deployedStartOrder(graph);
		Assert.assertEquals(6, startOrder.size());
		Assert.assertTrue(startOrder.contains(AType.class));
		Assert.assertTrue(startOrder.contains(BType.class));
		Assert.assertTrue(startOrder.contains(SiblingType.class));
		Assert.assertTrue(startOrder.contains(XType.class));
		Assert.assertTrue(startOrder.contains(YType.class));
		Assert.assertTrue(startOrder.contains(ZType.class));
		// A dependency-order guarantee still holds for every component outside
		// the cycle: A must start before B, and B before X (the cycle's entry
		// point), regardless of where exactly X, Y, and Z land relative to each
		// other.
		Assert.assertTrue(startOrder.indexOf(BType.class) < startOrder.indexOf(AType.class));
		Assert.assertTrue(startOrder.indexOf(XType.class) < startOrder.indexOf(BType.class));
	}

}
