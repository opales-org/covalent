/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.impl.DatasetControllerImpl;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.model.TrainingSample;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingController;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.impl.TrainingControllerImpl;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Read-only access to the toy 2D classification dataset, provided by
 * {@code DatasetController} and used externally by {@code TrainingController}.
 * Public interface: distinct from {@link DatasetControllerServices}, which
 * offers regeneration and is used only by this function's own Interaction
 * Controller.
 * <p>
 * See {@link DatasetControllerServices} for why this project keeps a
 * function's public {@code @Services} contract and its internal one as two
 * separate interfaces, even when (as here) one implementation class,
 * {@link DatasetControllerImpl}, provides both: it lets
 * {@code TrainingController} depend on exactly the read-only surface it
 * needs, with no visibility into - or accidental coupling to - the dataset's
 * own regeneration mechanics.
 * <p>
 * {@link #getTrainSamples()} and {@link #getTestSamples()} are a held-out
 * split of the same generation ({@code DatasetController} draws both at
 * once, at {@code @Initialization} and on every regeneration): the test
 * subset is never handed to {@link #getBatch(int)} or included in
 * {@link #getTrainSamples()}, so {@code TrainingController} can evaluate on
 * it each epoch as a genuine, never-trained-on measure of generalization -
 * see {@code TrainingControllerImpl#runOneEpoch()}.
 */
@Services
public interface DatasetServices {

	/**
	 * Draws a mini-batch of training samples.
	 *
	 * @param batchSize the number of samples to draw
	 * @return the drawn samples, drawn only from {@link #getTrainSamples()}
	 */
	TrainingSample[] getBatch(int batchSize);

	/**
	 * Returns the training subset of the current generation, excluding the held-out test samples.
	 *
	 * @return every sample in the training subset
	 */
	TrainingSample[] getTrainSamples();

	/**
	 * Returns the held-out test subset of the current generation, which is never used for training.
	 *
	 * @return every sample in the held-out test subset, never trained on
	 */
	TrainingSample[] getTestSamples();
}
