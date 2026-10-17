/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.model;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetServices;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkServices;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingController;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingServices;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.interaction.TrainingInteraction;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.interaction.impl.TrainingInteractionImpl;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.model.impl.TrainingModelImpl;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.view.TrainingView;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.view.impl.TrainingViewImpl;

import org.opales.covalent.container.component.annotations.Events;

/**
 * Training progress notifications, produced by {@code TrainingModel} and
 * consumed by {@code TrainingView} and by {@code TrainingInteraction}
 * to drive the live visualization.
 * <p>
 * Fired at epoch granularity: one {@link #epochStarted(int)} /
 * {@link #epochCompleted(int, double, double)} pair per training epoch, not
 * per mini-batch, immediately followed by
 * {@link #testEpochCompleted(int, double, double)} with the same epoch's
 * held-out test-subset metrics.
 * <p>
 * {@code @Events} / {@code @ProducedEvents} is Covalent's fire-and-forget,
 * one-producer-to-many-consumers notification contract - contrast with
 * {@code @Services}/{@code @UsedServices} ({@link TrainingServices}), which
 * is a synchronous call-and-return between exactly one provider and one
 * consumer. The producer ({@code TrainingModelImpl}) annotates a field of
 * this interface type with {@code @ProducedEvents} and calls its methods to
 * broadcast. A consumer needs no special field or annotation to receive
 * events: simply <em>implementing</em> this interface directly on a
 * component's implementation class is enough for Covalent to auto-subscribe
 * that component when it activates, and auto-unsubscribe it when it
 * passivates - which is what {@code TrainingInteractionImpl} relies on.
 * {@code TrainingViewImpl} implements it the same way, but does not rely on
 * that automatic wiring: its "Freeze charts" checkbox needs to stop and
 * resume receiving these notifications on demand while training keeps
 * running, so it declares its own manual {@code GenericSubscribe}
 * subscription instead (see that class's javadoc) - allowed precisely
 * because this interface, unlike the dataset/network functions elsewhere in
 * this example, declares no nested {@code @EventsSubscribe} interface of its
 * own. That same absence means resubscribing after an unfreeze gets no
 * replay of whatever epochs completed while frozen (contrast
 * {@link TrainingModelImpl}'s javadoc, about the different case of a
 * consumer activating before training has produced any history at all) -
 * the charts simply resume with a visible gap, which this example accepts
 * as the deliberate cost of freezing them.
 */
@Events
public interface TrainingEvents {

	/**
	 * Fired when a new training epoch begins.
	 *
	 * @param epoch the epoch number, starting at 0
	 */
	void epochStarted(int epoch);

	/**
	 * Fired when a training epoch completes, with its resulting metrics.
	 *
	 * @param epoch    the epoch number that just completed
	 * @param loss     the average loss over the epoch
	 * @param accuracy the classification accuracy over the epoch, in [0, 1]
	 */
	void epochCompleted(int epoch, double loss, double accuracy);

	/**
	 * Fired right after {@link #epochCompleted(int, double, double)}, with
	 * the same epoch's metrics measured on the held-out test subset
	 * ({@code DatasetServices#getTestSamples()}) instead of the training
	 * subset - a pure forward pass per sample
	 * ({@code NetworkServices#predict(double[])}), never trained on. A
	 * dedicated event rather than extra parameters on
	 * {@link #epochCompleted(int, double, double)}: that method's signature,
	 * and everything already consuming it, stays untouched.
	 *
	 * @param epoch        the epoch number that just completed
	 * @param testLoss     the average loss over the held-out test subset
	 * @param testAccuracy the classification accuracy over the held-out test
	 *                     subset, in [0, 1]
	 */
	void testEpochCompleted(int epoch, double testLoss, double testAccuracy);

	/**
	 * Fired once when the training run is considered converged (a
	 * {@code TrainingController}-defined stopping criterion was reached).
	 *
	 * @param epoch     the epoch at which convergence was detected
	 * @param finalLoss the loss value at convergence
	 */
	void trainingConverged(int epoch, double finalLoss);

	/**
	 * Fired when the training run is reset back to its initial state (epoch
	 * counter back to 0).
	 */
	void trainingReset();
}
