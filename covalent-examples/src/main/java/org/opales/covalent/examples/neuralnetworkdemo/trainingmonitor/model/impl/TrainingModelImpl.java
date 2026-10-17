/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.model.impl;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.model.impl.DatasetModelImpl;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.model.impl.NetworkModelImpl;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.model.TrainingEvents;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.model.TrainingModel;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.model.TrainingModelServices;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;

/**
 * The training run's Model: records the run's state (last epoch, last
 * held-out test-subset loss/accuracy) and fires
 * the matching {@link TrainingEvents} notification for every call. Carries
 * no training logic of its own.
 * <p>
 * Note what this class does <em>not</em> do: unlike
 * {@code DatasetModelImpl}/{@code NetworkModelImpl} elsewhere in this
 * example, it does not declare and implement a nested
 * {@code @EventsSubscribe} interface to replay its current state to a
 * subscriber that activates after training has already produced some
 * history. That replay exists there because a
 * dataset/network View activating late would otherwise render nothing (or
 * stale data) until the next event fires - an initially-blank training loss
 * chart, by contrast, is a legitimate, non-buggy starting state: there is
 * nothing meaningful to "catch up" on before the first training step has
 * happened, so no delegation is needed here.
 */
@Implementation
public class TrainingModelImpl implements TrainingModel, TrainingModelServices {

	@ProducedEvents
	private TrainingEvents produceTrainingEvents;

	private int lastEpoch = -1;

	private double lastTestLoss;

	private double lastTestAccuracy;

	@Override
	public void recordEpochStarted(final int epoch) {
		this.produceTrainingEvents.epochStarted(epoch);
	}

	@Override
	public void recordEpochCompleted(final int epoch, final double loss, final double accuracy) {
		this.lastEpoch = epoch;
		this.produceTrainingEvents.epochCompleted(epoch, loss, accuracy);
	}

	@Override
	public void recordTestEpochCompleted(final int epoch, final double testLoss, final double testAccuracy) {
		this.lastTestLoss = testLoss;
		this.lastTestAccuracy = testAccuracy;
		this.produceTrainingEvents.testEpochCompleted(epoch, testLoss, testAccuracy);
	}

	@Override
	public void recordConverged(final int epoch, final double finalLoss) {
		this.produceTrainingEvents.trainingConverged(epoch, finalLoss);
	}

	@Override
	public void recordReset() {
		this.lastEpoch = -1;
		this.lastTestLoss = 0.0;
		this.lastTestAccuracy = 0.0;
		this.produceTrainingEvents.trainingReset();
	}

	@Override
	public int getLastEpoch() {
		return this.lastEpoch;
	}

	@Override
	public double getLastTestLoss() {
		return this.lastTestLoss;
	}

	@Override
	public double getLastTestAccuracy() {
		return this.lastTestAccuracy;
	}
}
