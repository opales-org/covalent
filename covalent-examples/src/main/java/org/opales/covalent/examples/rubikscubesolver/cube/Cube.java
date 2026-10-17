/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.cube;

import org.opales.covalent.examples.rubikscubesolver.cube.controller.CubeController;
import org.opales.covalent.examples.rubikscubesolver.cube.impl.CubeImpl;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the Cube MVIC (Model/Controller/Interaction/View).
 * See {@code DatasetExplorer} in the Neural Network Trainer demo for what a
 * bare {@code @Component} tag interface (no methods, no body) means in
 * general; as there, this type is never used to look up a service or event
 * contract - it exists only as the deployment root, see {@link CubeImpl}.
 * <p>
 * Unlike every other demo in this project, {@code Cube} is deployed with no
 * wrapping root composite of its own: it, {@link org.opales.covalent.examples.rubikscubesolver.solver.Solver},
 * {@link org.opales.covalent.examples.rubikscubesolver.userconfiguration.UserConfiguration}
 * and {@link org.opales.covalent.examples.rubikscubesolver.hmibuilder.HmiBuilder}
 * are all passed directly to {@code deployComponents(...)}, to demonstrate
 * that Covalent accepts several independent top-level composites at once -
 * see {@code LauncherApplication} for the exact deployment order, which
 * matches these composites' dependency order.
 * <p>
 * This function owns the cube's state and the mechanics of applying moves
 * to it ({@link CubeController}, through the public {@link CubeServices}/
 * {@link CubeReadServices}), and renders it. See {@link CubeController}'s
 * own javadoc for why it, rather than {@code CubeModel}, provides those two
 * public contracts.
 */
@Component
public interface Cube {

}
