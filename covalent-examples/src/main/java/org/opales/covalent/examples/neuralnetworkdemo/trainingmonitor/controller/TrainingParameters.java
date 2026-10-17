/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller;

import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.NeuralNetworkModel;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkParameters;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.impl.TrainingControllerImpl;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.interaction.TrainingInteraction;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.interaction.impl.TrainingInteractionImpl;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.model.TrainingModelServices;
import org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.UserConfiguration;

import org.opales.covalent.container.component.annotations.Parameters;

/**
 * User-editable hyperparameters of the training run itself (pacing and
 * batching), offered by {@code TrainingController} and accessed by
 * {@code TrainingInteraction} so the UI can both display and let the
 * user change them live while training runs.
 * <p>
 * The learning rate is <b>not</b> here: it is used inside
 * {@code NeuralNetworkModel}, so it lives on that function's own
 * {@link NetworkParameters} instead - see {@code UserConfiguration} for how
 * the two are fronted together for the user despite living on different
 * functions' Parameters interfaces.
 * <p>
 * {@code @Parameters} / {@code @AccessedParameters} models configuration-like
 * state - read (and here, also written) without necessarily triggering
 * active computation - as distinct from {@code @Services}
 * ({@link TrainingServices}, {@link TrainingModelServices}), which models an
 * operation. The provider ({@code TrainingControllerImpl}) implements this
 * interface; a consumer ({@code TrainingInteractionImpl}) declares a field of
 * this type annotated {@code @AccessedParameters} to get a live proxy onto it,
 * symmetrical to how {@code @Services}/{@code @UsedServices} works.
 */
@Parameters
public interface TrainingParameters {

	/**
	 * Returns the number of samples drawn per training step.
	 *
	 * @return the current mini-batch size
	 */
	int getBatchSize();

	/**
	 * Sets the number of samples drawn per training step.
	 *
	 * @param batchSize the new mini-batch size to apply from the next epoch on; a value below 1 is raised to 1
	 */
	void setBatchSize(int batchSize);

	/**
	 * Returns the noise level used to generate the dataset.
	 *
	 * @return the current dataset noise level (0 = perfectly separable classes)
	 */
	double getDatasetNoise();

	/**
	 * Sets the noise level used to generate the dataset.
	 *
	 * @param noise the new dataset noise level; regenerating the dataset is a separate action
	 */
	void setDatasetNoise(double noise);

	/**
	 * Returns the pace of continuous training.
	 *
	 * @return the current training pace, in epochs per second, while running
	 */
	double getEpochsPerSecond();

	/**
	 * Sets how fast continuous training is allowed to progress, so the loss
	 * curve and decision boundary remain watchable instead of jumping straight
	 * to convergence.
	 *
	 * @param epochsPerSecond the new pace, in epochs per second; a value that is not a positive,
	 *        finite number is ignored
	 */
	void setEpochsPerSecond(double epochsPerSecond);
}
