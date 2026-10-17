/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.view;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.view.DatasetViewServices;
import org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.interaction.UserConfigurationInteraction;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Piloting commands for the configuration view, provided by
 * {@code UserConfigurationView} and used by
 * {@code UserConfigurationInteraction} to keep the displayed values in
 * sync with what was actually applied (initial values at activation, and
 * confirmed values after every change).
 * <p>
 * Modeled as {@code @Services} rather than {@code @Parameters}, the same
 * split documented on {@link DatasetViewServices}: each of these calls is
 * an actual operation on the View (push a value into its control), not a
 * settled reference to read.
 */
@Services
public interface UserConfigurationViewServices {

	/**
	 * Shows the learning rate in its control.
	 *
	 * @param learningRate the learning rate to display
	 */
	void displayLearningRate(double learningRate);

	/**
	 * Shows the number of hidden layers in its control.
	 *
	 * @param hiddenLayerCount the number of hidden layers to display
	 */
	void displayHiddenLayerCount(int hiddenLayerCount);

	/**
	 * Shows the hidden layer size in its control.
	 *
	 * @param hiddenLayerSize the hidden layer size to display
	 */
	void displayHiddenLayerSize(int hiddenLayerSize);

	/**
	 * Shows the mini-batch size in its control.
	 *
	 * @param batchSize the mini-batch size to display
	 */
	void displayBatchSize(int batchSize);

	/**
	 * Shows the dataset noise level in its control.
	 *
	 * @param noise the dataset noise level to display
	 */
	void displayDatasetNoise(double noise);

	/**
	 * Shows the training pace in its control.
	 *
	 * @param epochsPerSecond the training pace to display
	 */
	void displayEpochsPerSecond(double epochsPerSecond);
}
