/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.cube.controller;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the Cube's Business Controller (CubeController), per
 * the MVIC pattern. See {@code DatasetController} in the Neural Network
 * Trainer demo for what a bare {@code @Component} tag interface means in
 * general.
 * <p>
 * {@code CubeController} is where the cube's two public contracts,
 * {@code CubeServices} and {@code CubeReadServices}, are provided from -
 * not {@code CubeModel} directly - exactly the split
 * {@code DatasetControllerImpl} documents between a function's internal
 * Controller Services and its public Services: only the Controller, never
 * the Model, is meant to be called across a function boundary.
 */
@Component
public interface CubeController {

}
