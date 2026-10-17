/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.hmibuilder;

import org.opales.covalent.examples.rubikscubesolver.hmibuilder.impl.HmiBuilderImpl;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the demo's pure HMI assembly component: builds the
 * JavaFX scene graph and embeds each function's view root, one per leaf
 * function. No business logic, and it consumes no domain event - it only
 * accesses each function's {@code *ViewParameters} to read its
 * already-built root node.
 * <p>
 * See {@code HmiBuilder} in the Neural Network Trainer demo for the fuller
 * rationale. Deployed last, directly, alongside {@link org.opales.covalent.examples.rubikscubesolver.cube.Cube},
 * {@link org.opales.covalent.examples.rubikscubesolver.solver.Solver} and
 * {@link org.opales.covalent.examples.rubikscubesolver.userconfiguration.UserConfiguration}
 * (see {@code LauncherApplication} for the exact order), so that every
 * function's View has already activated, and therefore already built its
 * root node, by the time {@link HmiBuilderImpl} reads it.
 */
@Component
public interface HmiBuilder {

}
