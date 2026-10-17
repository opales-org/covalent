/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.model.TrainingSample;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.interaction.NetworkActionKey;

import org.opales.covalent.container.component.annotations.Item;

/**
 * Pluggable parameter update rule, implemented by the
 * {@code GradientDescentOptimizer} {@link Item @Item} and used internally by
 * {@code NetworkController} during
 * {@link NetworkServices#trainOnBatch(TrainingSample[])}.
 * <p>
 * A plain behavioral interface, not a Covalent port: it carries no
 * {@code @Services}/{@code @Parameters}/{@code @Events} annotation of its
 * own. It is the value type held and set through {@link OptimizerParameters},
 * not itself something a component provides or uses as a service - the
 * same distinction {@link org.opales.covalent.hmi.action.ActionKey} makes
 * for {@code NetworkActionKey}.
 * <p>
 * Deliberately generic (identified parameter groups, flat vectors) rather
 * than tied to any specific layer/weight-matrix shape, so an
 * alternative implementation (momentum, Adam, ...) only has to implement
 * this one interface.
 */
public interface OptimizerStrategy {

	/**
	 * Computes the updated values for one named group of parameters (e.g. one
	 * layer's weights, or its biases), given its current values and the
	 * gradient computed for them.
	 *
	 * @param parameterId   an identifier for which parameter group this is
	 *                      (e.g. {@code "layer-1-weights"}), allowing a
	 *                      stateful strategy to keep per-parameter state
	 *                      (such as momentum) across calls
	 * @param gradient      the gradient computed for these parameters, same
	 *                      shape as {@code currentValues}
	 * @param currentValues the parameters' current values
	 * @return the updated parameter values, same shape as {@code currentValues}
	 */
	double[] computeUpdate(String parameterId, double[] gradient, double[] currentValues);
}
