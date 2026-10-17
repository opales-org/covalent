/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.model;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetServices;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkServices;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingServices;

/**
 * A single labeled training example for the toy 2D classification problem:
 * an input point and its target output (a one-hot or single-value label,
 * depending on the chosen network output shape).
 * <p>
 * Plain data carrier, not a Covalent-managed type: unlike the interfaces
 * elsewhere in this package, it is not annotated {@code @Parameters},
 * {@code @Services} or {@code @Events}, and is simply passed as a regular
 * method argument/return value (e.g. by
 * {@link TrainingServices}/{@code DatasetServices}/{@code NetworkServices})
 * between components that are managed.
 *
 * @param input the input feature vector
 * @param label the target output vector
 */
public record TrainingSample(double[] input, double[] label) {
}
