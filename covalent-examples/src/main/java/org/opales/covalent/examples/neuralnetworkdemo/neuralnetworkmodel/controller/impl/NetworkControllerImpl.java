/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.impl;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.model.TrainingSample;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkController;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkControllerServices;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkParameters;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkServices;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.OptimizerStrategy;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.model.NetworkEvents;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.model.NetworkModelServices;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.model.impl.NetworkModelImpl;

import java.util.Random;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.Item;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Initialization;

/**
 * The network's Controller: owns the actual feed-forward / backpropagation
 * math. The Model only stores state; all computation (forward pass, loss,
 * gradients, and delegating weight updates to the active
 * {@link OptimizerStrategy}) happens here.
 * <p>
 * Hidden layers use {@code tanh}; the output layer uses a sigmoid, matching
 * the binary classification target of every dataset shape (labels in {@code {0, 1}}).
 * <p>
 * This is the {@code @Implementation} fulfilling {@link NetworkController};
 * it additionally implements three further ports directly on the same
 * class - {@link NetworkControllerServices}, {@link NetworkServices} and
 * {@link NetworkParameters} - the common Covalent pattern of one
 * implementation backing several contracts of the same component. It
 * consumes {@link NetworkModelServices} through the {@code @UsedServices}
 * field below (a live proxy to whatever component provides that service,
 * injected automatically by the container at deployment), and owns the
 * default optimizer as an {@code @Item} - see {@link GradientDescentOptimizerImpl}
 * for what distinguishes an internal item from a component. The
 * {@code @Initialization} hook runs once, early in the component's setup,
 * to seed the network's initial random weights before it is activated.
 */
@Implementation
public class NetworkControllerImpl implements NetworkController, NetworkControllerServices, NetworkServices, NetworkParameters {

	/** Fixed input size: the 2D points of every dataset shape. */
	private static final int INPUT_SIZE = 2;

	/** Fixed output size: a single binary-classification output. */
	private static final int OUTPUT_SIZE = 1;

	/** Default number of hidden layers used at startup. */
	private static final int DEFAULT_HIDDEN_LAYER_COUNT = 2;

	/** Default size (neuron count) of each hidden layer, used at startup. */
	private static final int DEFAULT_HIDDEN_LAYER_SIZE = 8;

	/**
	 * Live proxy to whatever component provides {@link NetworkModelServices}
	 * (here, {@code NetworkModelImpl}), injected by the container because
	 * this field is annotated {@code @UsedServices}.
	 */
	@UsedServices
	private NetworkModelServices useNetworkModelServices;

	/**
	 * The default (and, until reconfigured, only) {@link OptimizerStrategy},
	 * held as an {@code @Item}: an internal sub-object owned and wired
	 * directly by this {@code @Implementation}, with no component identity
	 * of its own. See {@link GradientDescentOptimizerImpl} for the full
	 * rationale.
	 */
	@Item
	private GradientDescentOptimizerImpl optimizer;

	private final Random weightInitRandom = new Random();

	/**
	 * Held by every operation that reads or replaces the layer structure and the weights: a
	 * training step runs on the training thread, whereas a change of structure, a reset or a
	 * preview comes from the user's thread, and a step run over a structure replaced halfway
	 * would mix layers of different sizes.
	 */
	private final Object networkLock = new Object();

	private volatile int hiddenLayerCount = DEFAULT_HIDDEN_LAYER_COUNT;

	private volatile int hiddenLayerSize = DEFAULT_HIDDEN_LAYER_SIZE;

	/**
	 * Runs once, early in the component's setup (before activation), to
	 * give the network its first random layer structure and weights.
	 */
	@Initialization
	public void initializeNetwork() {
		this.resetWeights(this.computeLayerSizes());
	}

	@Override
	public int getHiddenLayerCount() {
		return this.hiddenLayerCount;
	}

	@Override
	public void setHiddenLayerCount(final int hiddenLayerCount) {
		if (hiddenLayerCount < 0) {
			throw new IllegalArgumentException("hiddenLayerCount must be >= 0, got " + hiddenLayerCount);
		}
		synchronized (this.networkLock) {
			this.hiddenLayerCount = hiddenLayerCount;
			this.resetWeights(this.computeLayerSizes());
		}
	}

	@Override
	public int getHiddenLayerSize() {
		return this.hiddenLayerSize;
	}

	@Override
	public void setHiddenLayerSize(final int hiddenLayerSize) {
		if (hiddenLayerSize < 1) {
			throw new IllegalArgumentException("hiddenLayerSize must be >= 1, got " + hiddenLayerSize);
		}
		synchronized (this.networkLock) {
			this.hiddenLayerSize = hiddenLayerSize;
			this.resetWeights(this.computeLayerSizes());
		}
	}

	/**
	 * Builds the full layer-size array from the current hidden-layer
	 * structure: fixed input size, {@link #hiddenLayerCount} hidden layers of
	 * {@link #hiddenLayerSize} each, fixed output size.
	 */
	private int[] computeLayerSizes() {
		int[] layerSizes = new int[this.hiddenLayerCount + 2];
		layerSizes[0] = INPUT_SIZE;
		for (int i = 0; i < this.hiddenLayerCount; i++) {
			layerSizes[i + 1] = this.hiddenLayerSize;
		}
		layerSizes[layerSizes.length - 1] = OUTPUT_SIZE;
		return layerSizes;
	}

	/**
	 * Reinitializes the network with a new layer structure (layer count and
	 * per-layer size are both configurable here). Pushes the new structure
	 * and fresh weights/biases to the Model through {@link NetworkModelServices},
	 * which is what causes {@code NetworkEvents#networkReset(int[])} and the
	 * subsequent {@code NetworkEvents#weightsChanged(int, double[][])} calls
	 * to fire towards the View.
	 *
	 * @param layerSizes each layer's size (neuron count), input to output
	 */
	public void resetWeights(final int[] layerSizes) {
		synchronized (this.networkLock) {
			this.resetWeightsLocked(layerSizes);
		}
	}

	private void resetWeightsLocked(final int[] layerSizes) {
		this.useNetworkModelServices.initializeLayers(layerSizes);
		int transitions = layerSizes.length - 1;
		for (int l = 0; l < transitions; l++) {
			int outSize = layerSizes[l + 1];
			int inSize = layerSizes[l];
			double[][] w = new double[outSize][inSize];
			// Xavier/Glorot scale (sqrt(1/inSize)), not He's sqrt(2/inSize):
			// He assumes a ReLU output, but every transition here feeds a
			// saturating activation (tanh for every hidden layer, sigmoid
			// for the output layer - see forwardPass). He's larger variance
			// pushes pre-activations too wide for those, saturating a
			// meaningful share of units right from initialization; on a
			// deep, wide configuration (e.g. 4 hidden layers of 27 neurons)
			// that saturation compounds layer over layer until backprop's
			// tanh'(z) = 1 - tanh(z)^2 term collapses to ~0 almost
			// everywhere, vanishing the gradient reaching every weight
			// before the last layer or two. Once that happens, training
			// keeps running (the loss plateaus above
			// TrainingControllerImpl's convergence loss threshold rather than
			// reaching it, so it never auto-pauses) but no longer meaningfully updates most of
			// the network, leaving prediction dominated by whichever
			// near-constant bias path survived - seen as the same
			// prediction (0 or 1) for virtually every input.
			double scale = Math.sqrt(1.0 / inSize);
			for (int o = 0; o < outSize; o++) {
				for (int i = 0; i < inSize; i++) {
					w[o][i] = this.weightInitRandom.nextGaussian() * scale;
				}
			}
			this.useNetworkModelServices.setLayerWeights(l, w);
			this.useNetworkModelServices.setLayerBiases(l, new double[outSize]);
		}
	}

	@Override
	public void resetWeights() {
		synchronized (this.networkLock) {
			this.resetWeights(this.computeLayerSizes());
		}
	}

	@Override
	public int getLayerCount() {
		return this.useNetworkModelServices.getLayerCount();
	}

	@Override
	public int[] getLayerSizes() {
		return this.useNetworkModelServices.getLayerSizes();
	}

	@Override
	public double[][] getLayerWeightsSnapshot(final int layerIndex) {
		return this.useNetworkModelServices.getLayerWeights(layerIndex);
	}

	@Override
	public double[] predict(final double[] input) {
		synchronized (this.networkLock) {
			double[][] activations = this.forwardPass(input);
			return activations[activations.length - 1];
		}
	}

	@Override
	public double[] previewPrediction(final double[] input) {
		return this.predict(input);
	}

	@Override
	public double getLearningRate() {
		return this.optimizer.getLearningRate();
	}

	@Override
	public void setLearningRate(final double learningRate) {
		this.optimizer.setLearningRate(learningRate);
	}

	@Override
	public double trainOnBatch(final TrainingSample[] batch) {
		synchronized (this.networkLock) {
			return this.trainOnBatchLocked(batch);
		}
	}

	private double trainOnBatchLocked(final TrainingSample[] batch) {
		if (batch.length == 0) {
			return 0.0;
		}
		int transitions = this.useNetworkModelServices.getLayerCount() - 1;

		double[][][] currentWeights = new double[transitions][][];
		double[][][] weightGradientSum = new double[transitions][][];
		double[][] biasGradientSum = new double[transitions][];
		for (int l = 0; l < transitions; l++) {
			currentWeights[l] = this.useNetworkModelServices.getLayerWeights(l);
			weightGradientSum[l] = new double[currentWeights[l].length][currentWeights[l][0].length];
			biasGradientSum[l] = new double[currentWeights[l].length];
		}

		double totalLoss = 0.0;
		for (TrainingSample sample : batch) {
			double[][] activations = this.forwardPass(sample.input());
			double[] output = activations[transitions];
			double[] label = sample.label();

			double[][] deltas = new double[transitions][];
			double[] outputDelta = new double[output.length];
			for (int o = 0; o < output.length; o++) {
				double error = output[o] - label[o];
				outputDelta[o] = error * output[o] * (1.0 - output[o]);
				totalLoss += 0.5 * error * error;
			}
			deltas[transitions - 1] = outputDelta;

			for (int l = transitions - 2; l >= 0; l--) {
				double[][] nextWeights = this.useNetworkModelServices.getLayerWeights(l + 1);
				double[] activation = activations[l + 1];
				double[] delta = new double[activation.length];
				for (int o = 0; o < activation.length; o++) {
					double sum = 0.0;
					for (int nextOutput = 0; nextOutput < nextWeights.length; nextOutput++) {
						sum += nextWeights[nextOutput][o] * deltas[l + 1][nextOutput];
					}
					delta[o] = sum * (1.0 - activation[o] * activation[o]);
				}
				deltas[l] = delta;
			}

			for (int l = 0; l < transitions; l++) {
				double[] delta = deltas[l];
				double[] previousActivation = activations[l];
				for (int o = 0; o < delta.length; o++) {
					biasGradientSum[l][o] += delta[o];
					for (int i = 0; i < previousActivation.length; i++) {
						weightGradientSum[l][o][i] += delta[o] * previousActivation[i];
					}
				}
			}
		}

		double averagingFactor = 1.0 / batch.length;
		for (int l = 0; l < transitions; l++) {
			double[][] weights = currentWeights[l];
			int outSize = weights.length;
			int inSize = weights[0].length;

			double[] flatGradient = new double[outSize * inSize];
			double[] flatCurrent = new double[outSize * inSize];
			for (int o = 0; o < outSize; o++) {
				for (int i = 0; i < inSize; i++) {
					int index = o * inSize + i;
					flatGradient[index] = weightGradientSum[l][o][i] * averagingFactor;
					flatCurrent[index] = weights[o][i];
				}
			}
			double[] updatedFlat = this.optimizer.computeUpdate("layer-" + l + "-weights", flatGradient, flatCurrent);
			double[][] updatedWeights = new double[outSize][inSize];
			for (int o = 0; o < outSize; o++) {
				System.arraycopy(updatedFlat, o * inSize, updatedWeights[o], 0, inSize);
			}
			this.useNetworkModelServices.setLayerWeights(l, updatedWeights);

			double[] biasGradient = new double[outSize];
			for (int o = 0; o < outSize; o++) {
				biasGradient[o] = biasGradientSum[l][o] * averagingFactor;
			}
			double[] currentBiases = this.useNetworkModelServices.getLayerBiases(l);
			double[] updatedBiases = this.optimizer.computeUpdate("layer-" + l + "-biases", biasGradient, currentBiases);
			this.useNetworkModelServices.setLayerBiases(l, updatedBiases);
		}

		return totalLoss / batch.length;
	}

	/**
	 * Runs a pure forward pass, returning every layer's activations
	 * (index 0 = the raw input, index n = the network's output).
	 */
	private double[][] forwardPass(final double[] input) {
		int transitions = this.useNetworkModelServices.getLayerCount() - 1;
		double[][] activations = new double[transitions + 1][];
		activations[0] = input;
		for (int l = 0; l < transitions; l++) {
			double[][] weights = this.useNetworkModelServices.getLayerWeights(l);
			double[] biases = this.useNetworkModelServices.getLayerBiases(l);
			double[] z = new double[weights.length];
			for (int o = 0; o < weights.length; o++) {
				double sum = biases[o];
				for (int i = 0; i < weights[o].length; i++) {
					sum += weights[o][i] * activations[l][i];
				}
				z[o] = sum;
			}
			activations[l + 1] = (l == transitions - 1) ? sigmoid(z) : tanh(z);
		}
		return activations;
	}

	private static double[] sigmoid(final double[] z) {
		double[] result = new double[z.length];
		for (int i = 0; i < z.length; i++) {
			result[i] = 1.0 / (1.0 + Math.exp(-z[i]));
		}
		return result;
	}

	private static double[] tanh(final double[] z) {
		double[] result = new double[z.length];
		for (int i = 0; i < z.length; i++) {
			result[i] = Math.tanh(z[i]);
		}
		return result;
	}
}
