/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller;

import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.NeuralNetworkModel;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.TrainingMonitor;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingController;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingParameters;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingServices;
import org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.UserConfiguration;

import org.opales.covalent.container.component.annotations.Parameters;

/**
 * User-editable hyperparameters of the network model itself, offered by
 * {@code NetworkController}:
 * <ul>
 *   <li>the learning rate, used by the active {@link OptimizerStrategy} -
 *       applied by a plain field access on the {@code GradientDescentOptimizer}
 *       item {@code NetworkController} already holds (no cross-component
 *       port needed);</li>
 *   <li>the hidden-layer structure ({@link #setHiddenLayerCount(int)} /
 *       {@link #setHiddenLayerSize(int)}), all hidden layers sharing the same
 *       size. Changing either rebuilds the network from scratch
 *       (fresh random weights) via {@code NetworkServices#resetWeights()} -
 *       the input layer (2, the dimension of the dataset points) and output layer
 *       (1, binary classification) stay fixed.</li>
 * </ul>
 * <p>
 * {@code @Parameters} marks configuration-like state - read (and sometimes
 * written) without necessarily triggering active computation - as opposed
 * to {@code @Services}, which represents an operation to invoke. The
 * providing component implements this interface, and a consumer declares a
 * field of the same type annotated {@code @AccessedParameters} to have the
 * framework inject a live proxy to it, exactly as {@code @UsedServices} does
 * for {@code @Services}
 * (see {@link NetworkControllerServices} for that mechanism).
 * <p>
 * These live here, not on {@code TrainingMonitor}'s {@link TrainingParameters},
 * because they are used inside {@code NeuralNetworkModel} - a parameter
 * belongs with the component that actually uses it, regardless of how the
 * HMI chooses to group controls for the user (see
 * {@code UserConfiguration}). This is also why this interface is public
 * (accessed externally by {@code UserConfiguration}), unlike
 * {@link OptimizerParameters}, whose scope is intentionally kept to this
 * function alone.
 * <p>
 * <b>Note:</b> rebuilding the network here does not reset
 * {@code TrainingMonitor}'s epoch counter (that stays {@code TrainingController}'s
 * own concern, reset only through {@code TrainingServices#reset()}), so a caller
 * that changes the network's structure also resets training to keep the
 * displayed epoch count meaningful.
 */
@Parameters
public interface NetworkParameters {

	/**
	 * Returns the learning rate applied to weight updates.
	 *
	 * @return the current learning rate
	 */
	double getLearningRate();

	/**
	 * Sets the learning rate applied to weight updates.
	 *
	 * @param learningRate the new learning rate to apply from the next batch on
	 */
	void setLearningRate(double learningRate);

	/**
	 * Returns the number of hidden layers.
	 *
	 * @return the current number of hidden layers
	 */
	int getHiddenLayerCount();

	/**
	 * Sets the number of hidden layers and rebuilds the network from
	 * scratch with fresh random weights.
	 *
	 * @param hiddenLayerCount the new number of hidden layers (0 or more)
	 */
	void setHiddenLayerCount(int hiddenLayerCount);

	/**
	 * Returns the number of neurons in each hidden layer.
	 *
	 * @return the current size (neuron count) shared by every hidden layer
	 */
	int getHiddenLayerSize();

	/**
	 * Sets the size (neuron count) of every hidden layer and rebuilds the
	 * network from scratch with fresh random weights.
	 *
	 * @param hiddenLayerSize the new hidden layer size (at least 1)
	 */
	void setHiddenLayerSize(int hiddenLayerSize);
}
