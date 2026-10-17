/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.model;

import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.TrainingMonitor;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingController;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingServices;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.impl.TrainingControllerImpl;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Records the training run's state, provided by {@code TrainingModel} and
 * used by {@code TrainingController}. The Model carries no logic of its
 * own: each method here just stores the given state and fires the matching
 * {@link TrainingEvents} notification, letting the Controller drive both
 * from a single call.
 * <p>
 * See {@link TrainingServices} for what {@code @Services}/{@code @UsedServices}
 * means in general. This interface is this function's internal
 * Controller-to-Model wiring: unlike {@link TrainingServices}, it is not
 * meant to be consumed from outside the {@code TrainingMonitor} function, and
 * has exactly one consumer ({@code TrainingControllerImpl}).
 */
@Services
public interface TrainingModelServices {

	/**
	 * Records that a new epoch has started, and fires {@link TrainingEvents#epochStarted(int)}.
	 *
	 * @param epoch the epoch number, starting at 0
	 */
	void recordEpochStarted(int epoch);

	/**
	 * Records an epoch's resulting metrics, and fires
	 * {@link TrainingEvents#epochCompleted(int, double, double)}.
	 *
	 * @param epoch    the epoch number that just completed
	 * @param loss     the average loss over the epoch
	 * @param accuracy the classification accuracy over the epoch, in [0, 1]
	 */
	void recordEpochCompleted(int epoch, double loss, double accuracy);

	/**
	 * Records an epoch's held-out test-subset metrics, and fires
	 * {@link TrainingEvents#testEpochCompleted(int, double, double)}.
	 *
	 * @param epoch        the epoch number that just completed
	 * @param testLoss     the average loss over the held-out test subset
	 * @param testAccuracy the classification accuracy over the held-out test
	 *                     subset, in [0, 1]
	 */
	void recordTestEpochCompleted(int epoch, double testLoss, double testAccuracy);

	/**
	 * Records that training has converged, and fires
	 * {@link TrainingEvents#trainingConverged(int, double)}.
	 *
	 * @param epoch     the epoch at which convergence was detected
	 * @param finalLoss the loss value at convergence
	 */
	void recordConverged(int epoch, double finalLoss);

	/**
	 * Resets the recorded training state, and fires {@link TrainingEvents#trainingReset()}.
	 */
	void recordReset();

	/**
	 * Returns the number of the last epoch recorded.
	 *
	 * @return the last epoch number recorded, or -1 if none yet
	 */
	int getLastEpoch();

	/**
	 * Returns the held-out test-subset loss of the last recorded epoch.
	 *
	 * @return the last recorded held-out test-subset loss
	 */
	double getLastTestLoss();

	/**
	 * Returns the held-out test-subset accuracy of the last recorded epoch.
	 *
	 * @return the last recorded held-out test-subset accuracy, in [0, 1]
	 */
	double getLastTestAccuracy();
}
