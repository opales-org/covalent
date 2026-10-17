/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.cube.impl;

import org.opales.covalent.examples.rubikscubesolver.cube.Cube;
import org.opales.covalent.examples.rubikscubesolver.cube.controller.impl.CubeControllerImpl;
import org.opales.covalent.examples.rubikscubesolver.cube.interaction.impl.CubeInteractionImpl;
import org.opales.covalent.examples.rubikscubesolver.cube.model.impl.CubeModelImpl;
import org.opales.covalent.examples.rubikscubesolver.cube.view.impl.CubeViewImpl;

import org.opales.covalent.container.component.annotations.Implementation;

/**
 * The {@code Cube} composite: deploys its four MVIC parts together. Only
 * this class needs to be passed to {@code container.deployComponents(...)}
 * (see {@code LauncherApplication}, where it is passed directly, with no
 * wrapping root composite - see {@link Cube}'s javadoc for why).
 * <p>
 * See {@code DatasetExplorerImpl} in the Neural Network Trainer demo for
 * the full rationale behind this composed-sub-component mechanism: each
 * field below is itself a sub-component (own type, ports, lifecycle state),
 * resolved and wired by the container when it instantiates this class, in
 * the Model/Controller/Interaction/View order their dependencies
 * require.
 */
@Implementation
public class CubeImpl implements Cube {

	@Implementation
	private CubeModelImpl model;

	@Implementation
	private CubeControllerImpl controller;

	@Implementation
	private CubeViewImpl view;

	@Implementation
	private CubeInteractionImpl interaction;
}
