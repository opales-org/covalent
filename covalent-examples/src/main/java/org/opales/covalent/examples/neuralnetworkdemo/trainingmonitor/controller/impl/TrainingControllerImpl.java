/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.impl;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetServices;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.model.TrainingSample;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.NeuralNetworkModel;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkController;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkParameters;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkServices;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingController;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingParameters;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingServices;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.interaction.TrainingInteraction;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.model.TrainingEvents;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.model.TrainingModelServices;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.model.impl.TrainingModelImpl;

import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Initialization;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.lifecycle.Removal;

/**
 * The training run's Controller: orchestrates continuous or step-by-step
 * training over the dataset, at a user-controlled pace, using the network's
 * real forward/backward pass ({@link NetworkServices#trainOnBatch(TrainingSample[])}).
 * <p>
 * The learning rate is not configured here: it is {@code NeuralNetworkModel}'s
 * own concern, exposed through {@link NetworkParameters} and applied directly
 * by {@code NetworkController} to the {@code GradientDescentOptimizer} item it
 * owns. This class only owns pacing/batching ({@link TrainingParameters}).
 * <p>
 * This is the single {@code @Implementation} class that fulfils three
 * component-facing interfaces at once: the empty {@link TrainingController}
 * marker (see its javadoc for what that means), the {@link TrainingServices}
 * operations it provides to {@code TrainingInteraction}, and the
 * {@link TrainingParameters} configuration state it also provides to it - a
 * single class can implement several such contracts, and the container wires
 * each one independently to whichever other component declares a matching
 * {@code @UsedServices}/{@code @AccessedParameters} field. It also
 * <em>consumes</em> two services of its own, {@link TrainingModelServices}
 * (to report progress into the Model) and the dataset/network functions'
 * {@code DatasetServices}/{@code NetworkServices} (to actually run training) -
 * a Controller both provides and uses services, in different directions, in
 * the same class.
 * <p>
 * <b>Lifecycle and background thread.</b> The epochs run on a thread of their own, outside
 * the container's control, so this class ties that thread to its lifecycle explicitly:
 * <ul>
 * <li>{@code @Initialization} creates the scheduler, once: initialization runs once per
 * instance, whereas activation and passivation may alternate any number of times.</li>
 * <li>Activation has nothing to do: training starts only on request.</li>
 * <li>{@code @Passivation} stops training and waits for the epoch in progress to end.
 * Passivation goes from the users to their providers: this function's own Model, then
 * {@code NeuralNetworkModel} and {@code DatasetExplorer}, are passivated after this component,
 * and an epoch still running at that point would call a passivated provider and fail. The
 * wait lasts at most one epoch, which keeps passivation quick.</li>
 * <li>{@code @Removal} shuts the scheduler down: the instance is never activated again.</li>
 * </ul>
 * Keeping the scheduler across passivation is what lets training run again once reactivated;
 * shutting it down there would leave the component unable to schedule anything.
 */
@Implementation
public class TrainingControllerImpl implements TrainingController, TrainingServices, TrainingParameters {

	private static final Logger log = Logger.getLogger(TrainingControllerImpl.class.getName());

	private static final int DEFAULT_BATCH_SIZE = 32;

	private static final double DEFAULT_DATASET_NOISE = 0.15;

	private static final double DEFAULT_EPOCHS_PER_SECOND = 5.0;

	/** Average-loss threshold below which the run is considered converged. */
	private static final double CONVERGENCE_LOSS_THRESHOLD = 0.02;

	@UsedServices
	private TrainingModelServices useTrainingModelServices;

	@UsedServices
	private DatasetServices useDatasetServices;

	@UsedServices
	private NetworkServices useNetworkServices;

	private ScheduledExecutorService executor;

	/**
	 * Held for the whole of each epoch. Taking it, as passivation does, waits for the epoch in
	 * progress, if any, to end - without stopping the scheduler.
	 */
	private final Object epochLock = new Object();

	private volatile ScheduledFuture<?> scheduledEpoch;

	private volatile boolean running;

	private volatile int epoch;

	private volatile int batchSize = DEFAULT_BATCH_SIZE;

	private volatile double datasetNoise = DEFAULT_DATASET_NOISE;

	private volatile double epochsPerSecond = DEFAULT_EPOCHS_PER_SECOND;

	/**
	 * Creates the scheduler running the epochs: once per instance, since initialization is not
	 * repeated when the component is passivated and activated again. A single thread keeps the
	 * epochs in sequence; it is a daemon so that it never holds the JVM open.
	 */
	@Initialization
	public void createScheduler() {
		this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "training-monitor");
			thread.setDaemon(true);
			return thread;
		});
	}

	/**
	 * Stops training, then waits for the epoch in progress to end. Clearing {@code running}
	 * first, through {@link #pause()}, keeps any further epoch from starting; taking
	 * {@link #epochLock} then waits for one already started. Once this returns, no epoch can
	 * call a provider, all of which are passivated after this component. The scheduler itself
	 * is kept for a later reactivation.
	 */
	@Passivation
	public void stopTraining() {
		this.pause();
		synchronized (this.epochLock) {
			// Entered once the epoch in progress, if any, is over.
		}
	}

	/**
	 * Shuts the scheduler down and ends its thread: removal is final, unlike passivation.
	 * Training is already stopped, by the passivation that always precedes removal.
	 */
	@Removal
	public void shutdownScheduler() {
		this.executor.shutdownNow();
	}

	// --- TrainingServices -----------------------------------------------

	@Override
	public void start() {
		if (this.running) {
			return;
		}
		this.running = true;
		this.scheduleNextEpoch();
	}

	@Override
	public void pause() {
		this.running = false;
		ScheduledFuture<?> pending = this.scheduledEpoch;
		if (pending != null) {
			pending.cancel(false);
			this.scheduledEpoch = null;
		}
	}

	@Override
	public void step() {
		synchronized (this.epochLock) {
			this.runOneEpoch();
		}
	}

	@Override
	public void reset() {
		this.pause();
		// Under the lock, so that an epoch already started cannot write its results over the reset.
		synchronized (this.epochLock) {
			this.useNetworkServices.resetWeights();
			this.epoch = 0;
			this.useTrainingModelServices.recordReset();
		}
	}

	@Override
	public boolean isRunning() {
		return this.running;
	}

	// --- TrainingParameters -----------------------------------------------

	@Override
	public int getBatchSize() {
		return this.batchSize;
	}

	@Override
	public void setBatchSize(final int batchSize) {
		// A batch of fewer than one sample would make the epoch loop never advance.
		this.batchSize = Math.max(1, batchSize);
	}

	@Override
	public double getDatasetNoise() {
		return this.datasetNoise;
	}

	@Override
	public void setDatasetNoise(final double noise) {
		this.datasetNoise = noise;
	}

	@Override
	public double getEpochsPerSecond() {
		return this.epochsPerSecond;
	}

	@Override
	public void setEpochsPerSecond(final double epochsPerSecond) {
		// A pace that is not a positive finite number cannot be scheduled: the previous pace is kept.
		if (epochsPerSecond > 0.0 && !Double.isInfinite(epochsPerSecond)) {
			this.epochsPerSecond = epochsPerSecond;
		}
	}

	// --- Internal training loop -----------------------------------------------

	private void scheduleNextEpoch() {
		if (!this.running) {
			return;
		}
		long delayMillis = Math.max(1L, Math.round(1000.0 / this.epochsPerSecond));
		this.scheduledEpoch = this.executor.schedule(this::tick, delayMillis, TimeUnit.MILLISECONDS);
	}

	private void tick() {
		if (!this.running) {
			return;
		}
		try {
			synchronized (this.epochLock) {
				// Checked again under the lock: passivation may have stopped training while
				// this epoch was waiting for it.
				if (!this.running) {
					return;
				}
				this.runOneEpoch();
			}
		} catch (RuntimeException e) {
			log.log(Level.SEVERE, "Training epoch failed, stopping.", e);
			this.pause();
			return;
		}
		if (this.running) {
			this.scheduleNextEpoch();
		}
	}

	/**
	 * Runs exactly one training epoch: reports the start through
	 * {@link TrainingModelServices#recordEpochStarted(int)}, trains the
	 * network batch-by-batch via {@link NetworkServices#trainOnBatch(TrainingSample[])}
	 * over the training subset, reports the resulting metrics through
	 * {@link TrainingModelServices#recordEpochCompleted(int, double, double)},
	 * then evaluates the same network - unchanged since, no weight update in
	 * between - on the held-out test subset and reports that through
	 * {@link TrainingModelServices#recordTestEpochCompleted(int, double, double)} -
	 * each of those calls is itself what causes {@link TrainingModelImpl} to
	 * fire the matching {@link TrainingEvents} notification, so this method
	 * never touches {@code TrainingEvents} directly.
	 */
	private void runOneEpoch() {
		int currentEpoch = this.epoch;
		this.useTrainingModelServices.recordEpochStarted(currentEpoch);

		TrainingSample[] train = this.useDatasetServices.getTrainSamples();
		double totalLoss = 0.0;
		int batchCount = 0;
		int size = this.batchSize;
		for (int start = 0; start < train.length; start += size) {
			int end = Math.min(start + size, train.length);
			TrainingSample[] batch = Arrays.copyOfRange(train, start, end);
			totalLoss += this.useNetworkServices.trainOnBatch(batch);
			batchCount++;
		}
		double averageLoss = batchCount > 0 ? totalLoss / batchCount : 0.0;
		double accuracy = computeAccuracy(this.useNetworkServices, train);

		this.epoch = currentEpoch + 1;
		this.useTrainingModelServices.recordEpochCompleted(currentEpoch, averageLoss, accuracy);

		// Evaluated on the held-out test subset - never touched by the
		// training loop above - through a pure forward pass
		// (NetworkServices#predict, no weight update), the same way
		// computeAccuracy already does. This is what lets the test-loss
		// curve genuinely reveal overfitting: unlike the training loss just
		// recorded above, nothing here has ever adjusted a weight to reduce
		// this particular loss value.
		TrainingSample[] test = this.useDatasetServices.getTestSamples();
		double testLoss = computeLoss(this.useNetworkServices, test);
		double testAccuracy = computeAccuracy(this.useNetworkServices, test);
		this.useTrainingModelServices.recordTestEpochCompleted(currentEpoch, testLoss, testAccuracy);

		if (averageLoss < CONVERGENCE_LOSS_THRESHOLD) {
			this.pause();
			this.useTrainingModelServices.recordConverged(currentEpoch, averageLoss);
		}
	}

	/**
	 * Binary-classification accuracy (threshold 0.5 on the single output),
	 * matching the binary-labeled datasets this demo trains on.
	 */
	private static double computeAccuracy(final NetworkServices networkServices, final TrainingSample[] samples) {
		if (samples.length == 0) {
			return 0.0;
		}
		int correct = 0;
		for (TrainingSample sample : samples) {
			double[] output = networkServices.predict(sample.input());
			int predictedLabel = output[0] >= 0.5 ? 1 : 0;
			int actualLabel = (int) sample.label()[0];
			if (predictedLabel == actualLabel) {
				correct++;
			}
		}
		return (double) correct / samples.length;
	}

	/**
	 * Average loss over {@code samples}, using the same per-output
	 * {@code 0.5 * error^2} formula {@code NetworkServices#trainOnBatch(...)}
	 * accumulates internally while training - so a value returned here is
	 * directly comparable to the training loss {@link #runOneEpoch()}
	 * records, point for point on the same chart. Computed via
	 * {@link NetworkServices#predict(double[])} alone: a pure forward pass,
	 * no weight update, so evaluating on {@code samples} never trains on
	 * them.
	 */
	private static double computeLoss(final NetworkServices networkServices, final TrainingSample[] samples) {
		if (samples.length == 0) {
			return 0.0;
		}
		double totalLoss = 0.0;
		for (TrainingSample sample : samples) {
			double[] output = networkServices.predict(sample.input());
			double[] label = sample.label();
			for (int o = 0; o < output.length; o++) {
				double error = output[o] - label[o];
				totalLoss += 0.5 * error * error;
			}
		}
		return totalLoss / samples.length;
	}
}
