/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.model.TrainingSample;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.impl.NetworkControllerImpl;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingController;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Query and training operations on the network model, provided by
 * {@code NetworkController} and used externally by {@code TrainingController}.
 * Public interface: distinct from {@link NetworkControllerServices}, used
 * only by this function's own Interaction Controller. Layer-internal detail
 * (how forward/backward propagation and weight updates actually happen)
 * stays inside the composite and is intentionally not part of this
 * contract.
 * <p>
 * See {@link NetworkControllerServices} for the general {@code @Services}/
 * {@code @UsedServices} mechanism; the two interfaces exist side by side on
 * the same implementation class ({@code NetworkControllerImpl}) precisely
 * to keep this public, cross-function surface separate from the
 * function-internal one.
 */
@Services
public interface NetworkServices {

	/**
	 * Returns the total number of layers, input and output layers included.
	 *
	 * @return the number of layers currently in the network
	 */
	int getLayerCount();

	/**
	 * Returns the size (neuron count) of every layer.
	 *
	 * @return each layer's size (neuron count), in order from input to output
	 */
	int[] getLayerSizes();

	/**
	 * Computes the network's output for a given input, without altering any
	 * weights (a pure forward pass).
	 *
	 * @param input the input feature vector
	 * @return the network's output vector
	 */
	double[] predict(double[] input);

	/**
	 * Returns a read-only snapshot of one layer's current weight matrix, for
	 * rendering purposes.
	 *
	 * @param layerIndex the layer index, 0-based
	 * @return the layer's weight matrix at the time of the call
	 */
	double[][] getLayerWeightsSnapshot(int layerIndex);

	/**
	 * Runs one full training step (forward pass, loss computation, backward
	 * pass, and a weight update delegated to whatever {@code OptimizerStrategy}
	 * is in use) on a single mini-batch.
	 *
	 * @param batch the mini-batch to train on
	 * @return the average loss over this batch
	 */
	double trainOnBatch(TrainingSample[] batch);

	/** Reinitializes every layer's weights and biases to fresh random values. */
	void resetWeights();
}
