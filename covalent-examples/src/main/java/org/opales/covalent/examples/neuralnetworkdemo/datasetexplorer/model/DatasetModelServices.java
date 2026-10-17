/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.model;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetController;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetServices;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.view.DatasetView;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingController;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Records the dataset's current samples, provided by {@code DatasetModel}
 * and used by {@code DatasetController}. The Model carries no logic of its
 * own: {@link #replaceSamples(TrainingSample[])} just stores the given
 * samples and fires {@link DatasetEvents#datasetChanged(TrainingSample[])}.
 * <p>
 * This pairing illustrates the deliberate split between {@code @Services}
 * and {@code @Events} in this pattern: {@code replaceSamples}/{@code getSamples}
 * are a synchronous, call-and-return contract used by the one component that
 * needs a return value or needs the write to have happened before it
 * proceeds ({@code DatasetController}); {@code datasetChanged} is the
 * fire-and-forget counterpart broadcast to any number of interested
 * consumers ({@code DatasetView}, with no guaranteed synchronous call and no
 * return value) without the Model needing to know who, if anyone, is
 * listening.
 * <p>
 * The train/test split ({@link #replaceTrainSamples(TrainingSample[])},
 * {@link #replaceTestSamples(TrainingSample[])}) is stored separately from
 * the full displayed set, and neither fires an event: nothing needs
 * push-based catch-up on them, since {@code TrainingController} pulls them
 * synchronously, once per epoch, through {@code DatasetServices}. Keeping the
 * held-out test subset in its own field (rather than deriving it on demand
 * from the full set) means the training pool cannot accidentally include a
 * test sample: the two are physically separate arrays, not a runtime
 * convention.
 */
@Services
public interface DatasetModelServices {

	/**
	 * Replaces the stored samples, and fires
	 * {@link DatasetEvents#datasetChanged(TrainingSample[])}.
	 *
	 * @param samples the new, full set of samples
	 */
	void replaceSamples(TrainingSample[] samples);

	/**
	 * Returns the full set of samples currently stored for display.
	 *
	 * @return the currently stored samples
	 */
	TrainingSample[] getSamples();

	/**
	 * Replaces the stored training subset. Does not fire any event: see this
	 * interface's javadoc for why.
	 *
	 * @param trainSamples the new training subset
	 */
	void replaceTrainSamples(TrainingSample[] trainSamples);

	/**
	 * Returns the stored training subset.
	 *
	 * @return the currently stored training subset
	 */
	TrainingSample[] getTrainSamples();

	/**
	 * Replaces the stored held-out test subset. Does not fire any event: see
	 * this interface's javadoc for why.
	 *
	 * @param testSamples the new test subset
	 */
	void replaceTestSamples(TrainingSample[] testSamples);

	/**
	 * Returns the stored held-out test subset.
	 *
	 * @return the currently stored held-out test subset
	 */
	TrainingSample[] getTestSamples();
}
