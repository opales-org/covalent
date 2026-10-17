/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.solver.impl;

import org.opales.covalent.examples.rubikscubesolver.solver.Solver;
import org.opales.covalent.examples.rubikscubesolver.solver.controller.impl.SolverControllerImpl;
import org.opales.covalent.examples.rubikscubesolver.solver.human.impl.HumanSolverImpl;
import org.opales.covalent.examples.rubikscubesolver.solver.interaction.impl.SolverInteractionImpl;
import org.opales.covalent.examples.rubikscubesolver.solver.kociemba.impl.KociembaSolverImpl;
import org.opales.covalent.examples.rubikscubesolver.solver.model.impl.SolverModelImpl;
import org.opales.covalent.examples.rubikscubesolver.solver.view.impl.SolverViewImpl;

import org.opales.covalent.container.component.annotations.Implementation;

/**
 * The {@code Solver} composite: deploys its four MVIC parts together with
 * its two solving-strategy providers ({@link KociembaSolverImpl},
 * {@link HumanSolverImpl}). Only this class needs to be passed to
 * {@code container.deployComponents(...)} (see {@code LauncherApplication},
 * where it is passed directly, with no wrapping root composite - see
 * {@link Solver}'s javadoc for why).
 * <p>
 * See {@code DatasetExplorerImpl} in the Neural Network Trainer demo for
 * the full rationale behind this composed-sub-component mechanism. The two
 * strategy providers are not part of the MVIC quartet (they carry no View
 * or Interaction of their own), but are composed here through the
 * exact same mechanism: plain {@code @Implementation} fields, resolved and
 * wired by the container alongside the MVIC parts.
 */
@Implementation
public class SolverImpl implements Solver {

	@Implementation
	private SolverModelImpl model;

	@Implementation
	private SolverControllerImpl controller;

	@Implementation
	private SolverViewImpl view;

	@Implementation
	private SolverInteractionImpl interaction;

	@Implementation
	private KociembaSolverImpl kociembaSolver;

	@Implementation
	private HumanSolverImpl humanSolver;
}
