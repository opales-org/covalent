/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.solver.kociemba;

import org.opales.covalent.examples.rubikscubesolver.solver.controller.SolverController;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the Kociemba two-phase solving strategy provider. Not
 * part of the Solver's MVIC quartet: a plain provider component, composed
 * into {@code SolverImpl} alongside {@link SolverController}'s Model, View
 * and Interaction, and used only by {@code SolverController} through
 * {@link KociembaSolvingServices}.
 */
@Component
public interface KociembaSolver {

}
