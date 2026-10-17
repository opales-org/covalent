/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller;

import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.NeuralNetworkModel;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.impl.GradientDescentOptimizerImpl;

import org.opales.covalent.container.component.annotations.Parameters;

/**
 * Configures which {@link OptimizerStrategy} {@code NetworkController} uses
 * to update parameters during training. Offered by the
 * {@code GradientDescentOptimizer} item (an internal part of
 * {@code NetworkController}, not a component of its own - see
 * {@link GradientDescentOptimizerImpl} for the {@code @Item} rationale)
 * rather than by the Controller itself.
 * <p>
 * See {@link NetworkParameters} for the general {@code @Parameters}/
 * {@code @AccessedParameters} mechanism. Unlike {@code NetworkParameters},
 * this interface is intentionally kept internal to the
 * {@code NeuralNetworkModel} function rather than made public: swapping the
 * optimizer strategy is an assembly-time/internal concern, not something
 * other MVIC functions need to reach.
 * <p>
 * Nothing in this demo calls {@link #setOptimizerStrategy(OptimizerStrategy)}:
 * the optimizer item stays its own strategy (plain gradient descent) and no
 * UI control exposes the choice. The setter is the extension point for
 * plugging in another strategy without changing this contract.
 */
@Parameters
public interface OptimizerParameters {

	/**
	 * Selects the strategy used to turn gradients into weight updates.
	 *
	 * @param strategy the optimizer strategy to use from now on
	 */
	void setOptimizerStrategy(OptimizerStrategy strategy);
}
