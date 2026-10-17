/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.model;

import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkController;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkControllerServices;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Records the network's layer structure and weights, provided by
 * {@code NetworkModel} and used by {@code NetworkController}. The Model
 * carries no computation of its own (no forward/backward pass): it only
 * stores what the Controller computes, and fires
 * {@link NetworkEvents#weightsChanged(int, double[][])} on every update.
 * <p>
 * See {@link NetworkControllerServices} for the general {@code @Services}/
 * {@code @UsedServices} mechanism this port relies on; here the direction is
 * reversed from that example - the Model provides, and the Controller
 * consumes via {@code @UsedServices}.
 */
@Services
public interface NetworkModelServices {

	/**
	 * (Re)initializes the layer structure, discarding any previous state.
	 *
	 * @param layerSizes each layer's size (neuron count), input to output
	 */
	void initializeLayers(int[] layerSizes);

	/**
	 * Returns the number of layers, input and output layers included.
	 *
	 * @return the number of layers currently stored
	 */
	int getLayerCount();

	/**
	 * Returns the size (neuron count) of every layer.
	 *
	 * @return each layer's size (neuron count), in order from input to output
	 */
	int[] getLayerSizes();

	/**
	 * Returns the weight matrix of a layer.
	 *
	 * @param layerIndex the layer index, 0-based
	 * @return that layer's current weight matrix
	 */
	double[][] getLayerWeights(int layerIndex);

	/**
	 * Returns the bias vector of a layer.
	 *
	 * @param layerIndex the layer index, 0-based
	 * @return that layer's current bias vector
	 */
	double[] getLayerBiases(int layerIndex);

	/**
	 * Stores a layer's new weight matrix, and fires
	 * {@link NetworkEvents#weightsChanged(int, double[][])}.
	 *
	 * @param layerIndex the layer index, 0-based
	 * @param weights    the layer's new weight matrix
	 */
	void setLayerWeights(int layerIndex, double[][] weights);

	/**
	 * Stores a layer's new bias vector.
	 *
	 * @param layerIndex the layer index, 0-based
	 * @param biases     the layer's new bias vector
	 */
	void setLayerBiases(int layerIndex, double[] biases);
}
