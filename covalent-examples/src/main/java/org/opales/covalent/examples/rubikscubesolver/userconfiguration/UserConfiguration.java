/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.userconfiguration;

import org.opales.covalent.examples.rubikscubesolver.cube.CubeReadServices;
import org.opales.covalent.examples.rubikscubesolver.solver.kociemba.KociembaParameters;
import org.opales.covalent.examples.rubikscubesolver.solver.model.SolverPlaybackParameters;
import org.opales.covalent.examples.rubikscubesolver.userconfiguration.impl.UserConfigurationImpl;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the user-facing configuration composite. See
 * {@code DatasetController} in the Neural Network Trainer demo for what a
 * bare {@code @Component} tag interface means in general - here too it is a
 * pure type marker, fulfilled by exactly one {@code @Implementation} class,
 * {@link UserConfigurationImpl}.
 * <p>
 * Architecturally, like {@code UserConfiguration} in the Neural Network
 * Trainer demo, this composite is deliberately <b>not</b> a full MVIC
 * function: it owns no domain state of its own, so it has only a
 * View/Interaction pair. It is a thin, cross-cutting front-end that
 * gathers every other function's user-editable {@code @Parameters}
 * ({@link KociembaParameters}, {@link SolverPlaybackParameters}) and read-only
 * status ({@link CubeReadServices}) behind a single place the user can
 * reach them from.
 */
@Component
public interface UserConfiguration {

}
