/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.lifecycle.impl;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.container.lifecycle.DependencyGraph;

/**
 * Unit tests of {@link DependencyGraphChecker}, in its own package so the
 * package-private {@code computeIfEnabled}/{@code stopped} can be called
 * directly, without a {@code CovalentContainer}. After {@code stopped()},
 * {@link DependencyGraphChecker#getDependencyGraph()} throws again until the
 * next {@code computeIfEnabled} call: it must not keep answering as if the
 * container were still started. The integration-level
 * {@link org.opales.covalent.container.tests.unit.CircularDependencyDetectionApiTests}
 * covers the same services through a container.
 */
public class DependencyGraphCheckerTests {

	/** What the live graph source analyzes: the container's current structure. */
	private static final DependencyGraph LIVE_GRAPH = new DependencyGraphAnalyzer().analyze(List.of(), null);

	@Test
	public void getDependencyGraphThrowsBeforeAnyComputation() {
		DependencyGraphChecker checker = new DependencyGraphChecker();

		Assert.assertFalse(checker.hasDependencyGraph());
		IllegalStateException expected = Assert.assertThrows("expected getDependencyGraph() to throw before computeIfEnabled ever ran",
				IllegalStateException.class,
				() -> checker.getDependencyGraph());
	}

	@Test
	public void computeIfEnabledMarksTheCheckerAsComputedEvenWhenDisabled() {
		DependencyGraphChecker checker = new DependencyGraphChecker();
		checker.setLiveGraphSource(() -> LIVE_GRAPH);
		// enabled stays at its default (false); the disabled branch of
		// computeIfEnabled never touches metamodelReadServices/validationServices,
		// so null is fine here.
		checker.computeIfEnabled(List.of(), null, null);

		// computed: getDependencyGraph() must not throw once computeIfEnabled
		// has run, and answers with the live graph whether or not checking is
		// enabled. Only the start graph, which drives ordered activation, is
		// absent when neither checking nor ordered activation is enabled.
		Assert.assertTrue(checker.hasDependencyGraph());
		Assert.assertSame(LIVE_GRAPH, checker.getDependencyGraph());
		Assert.assertFalse(checker.hasStartGraph());
		Assert.assertNull(checker.getStartGraph());
	}

	@Test
	public void getDependencyGraphAnalyzesTheLiveStructureOnEveryCall() {
		DependencyGraphChecker checker = new DependencyGraphChecker();
		int[] analyses = { 0 };
		checker.setLiveGraphSource(() -> {
			analyses[0]++;
			return LIVE_GRAPH;
		});
		checker.computeIfEnabled(List.of(), null, null);
		Assert.assertEquals("computeIfEnabled alone does not analyze the live structure", 0, analyses[0]);

		checker.getDependencyGraph();
		checker.getDependencyGraph();

		Assert.assertEquals(2, analyses[0]);
	}

	@Test
	public void stoppedClearsTheComputedFlagSoGetDependencyGraphThrowsAgain() {
		DependencyGraphChecker checker = new DependencyGraphChecker();
		checker.setLiveGraphSource(() -> LIVE_GRAPH);
		checker.computeIfEnabled(List.of(), null, null);
		Assert.assertSame("sanity check: computeIfEnabled ran once, as the next assertions assume", LIVE_GRAPH,
				checker.getDependencyGraph());

		checker.stopped();

		Assert.assertFalse(checker.hasDependencyGraph());
		Assert.assertThrows("getDependencyGraph() must throw again after stopped()", IllegalStateException.class,
				checker::getDependencyGraph);
	}

}
