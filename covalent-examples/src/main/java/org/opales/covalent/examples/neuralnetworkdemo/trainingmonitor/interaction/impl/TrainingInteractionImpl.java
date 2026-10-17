/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.interaction.impl;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.interaction.DatasetInteraction;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.interaction.NetworkInteraction;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingParameters;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingServices;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.interaction.TrainingActionKey;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.interaction.TrainingInteraction;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.model.TrainingEvents;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.view.TrainingViewServices;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.view.impl.TrainingViewImpl;

import org.opales.covalent.container.component.annotations.AccessedParameters;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionProviderServices;

/**
 * The training run's Interaction Controller: registers its actions with the
 * HMI's {@code ActionManager} at activation - the view's
 * start/pause/step/reset buttons and batch-size/pace fields trigger
 * them through {@code ActionConsumerServices.executeAction(...)}, not by
 * calling this class directly. Also consumes {@link TrainingEvents} directly
 * (unlike {@code DatasetInteraction} / {@code NetworkInteraction},
 * which don't): it reacts to convergence
 * by disabling the run controls, and to a reset by re-enabling them.
 * <p>
 * This is the Interaction's defining role in MVIC: it is the only
 * piece of the function that touches both the View's services
 * ({@link TrainingViewServices}, to command the View) and the Controller's
 * services ({@link TrainingServices}, to trigger business logic) - it has no
 * JavaFX code of its own, and instead sits between the two, translating
 * user-triggered actions into Controller calls and Model-driven events into
 * View commands. {@link ActionProviderServices#registerActionExecution} /
 * {@link ActionProviderServices#unregisterActionExecution} is where a
 * Interaction's action wiring always lives: each call in
 * {@link #registerActions()} pairs one {@link TrainingActionKey} constant
 * with the private method that actually executes it, and each pair is
 * undone symmetrically in {@link #unregisterActions()} at passivation, so a
 * torn-down Interaction leaves no dangling action registration behind.
 * A View never calls an Interaction's methods directly for a
 * user-triggered action; it instead calls
 * {@code actionConsumerServices.executeAction(SomeActionKey, args...)}, which
 * the {@code ActionManager} routes to whatever was registered here - see
 * {@link TrainingViewImpl#buildRoots()} for the View side of this pattern.
 */
@Implementation
public class TrainingInteractionImpl implements TrainingInteraction, TrainingEvents {

	@UsedServices
	private ActionProviderServices actionProviderServices;

	@UsedServices
	private TrainingServices useTrainingServices;

	@UsedServices
	private TrainingViewServices useTrainingViewServices;

	@AccessedParameters
	private TrainingParameters accessTrainingParameters;

	/**
	 * {@code @Activation}: runs once this component becomes live. Registers
	 * every {@link TrainingActionKey} this function defines with the
	 * {@code ActionManager}, each bound to the private method below that
	 * performs it - some (like {@code SetBatchSize}) declare the argument
	 * type(s) the action expects, so the {@code ActionManager} can validate
	 * and route calls made with that argument shape.
	 */
	@Activation
	public void registerActions() {
		this.actionProviderServices.registerActionExecution(TrainingActionKey.Start, this::startTraining);
		this.actionProviderServices.registerActionExecution(TrainingActionKey.Pause, this::pauseTraining);
		this.actionProviderServices.registerActionExecution(TrainingActionKey.Step, this::stepTraining);
		this.actionProviderServices.registerActionExecution(TrainingActionKey.Reset, this::resetTraining);
		this.actionProviderServices.registerActionExecution(
				TrainingActionKey.SetBatchSize, this::setBatchSize, Integer.class);
		this.actionProviderServices.registerActionExecution(
				TrainingActionKey.SetEpochsPerSecond, this::setEpochsPerSecond, Double.class);
		this.actionProviderServices.registerActionExecution(
				TrainingActionKey.ToggleFreezeCharts, this::toggleFreezeCharts, Boolean.class);
	}

	/**
	 * {@code @Passivation}: the mirror of {@link #registerActions()}, run
	 * when this component is torn down. Unregisters every action key
	 * registered there, so the {@code ActionManager} holds no reference to a
	 * no-longer-live component.
	 */
	@Passivation
	public void unregisterActions() {
		this.actionProviderServices.unregisterActionExecution(TrainingActionKey.Start);
		this.actionProviderServices.unregisterActionExecution(TrainingActionKey.Pause);
		this.actionProviderServices.unregisterActionExecution(TrainingActionKey.Step);
		this.actionProviderServices.unregisterActionExecution(TrainingActionKey.Reset);
		this.actionProviderServices.unregisterActionExecution(TrainingActionKey.SetBatchSize);
		this.actionProviderServices.unregisterActionExecution(TrainingActionKey.SetEpochsPerSecond);
		this.actionProviderServices.unregisterActionExecution(TrainingActionKey.ToggleFreezeCharts);
	}

	/** Starts (or resumes) continuous training. */
	private void startTraining() {
		this.useTrainingServices.start();
	}

	/** Pauses continuous training after the current epoch completes. */
	private void pauseTraining() {
		this.useTrainingServices.pause();
	}

	/** Advances training by exactly one epoch. */
	private void stepTraining() {
		this.useTrainingServices.step();
	}

	/**
	 * Resets training to its initial state. Unfreezes the view's charts
	 * first, if frozen: {@link TrainingEvents#trainingReset()} is the only
	 * notification that clears the view's chart data (see
	 * {@code TrainingViewImpl#trainingReset()}), so a still-frozen view
	 * would otherwise keep showing a stale, now-meaningless curve after a
	 * reset it never received.
	 */
	private void resetTraining() {
		this.useTrainingViewServices.setChartsFrozen(false);
		this.useTrainingServices.reset();
	}

	/**
	 * @param frozen {@code true} to freeze the training charts, {@code
	 *               false} to unfreeze them - see
	 *               {@code TrainingViewImpl}'s javadoc for what this
	 *               actually toggles
	 */
	private void toggleFreezeCharts(final Boolean frozen) {
		this.useTrainingViewServices.setChartsFrozen(frozen);
	}

	/** @param batchSize the new mini-batch size to apply */
	private void setBatchSize(final Integer batchSize) {
		this.accessTrainingParameters.setBatchSize(batchSize);
	}

	/** @param epochsPerSecond the new training pace to apply */
	private void setEpochsPerSecond(final Double epochsPerSecond) {
		this.accessTrainingParameters.setEpochsPerSecond(epochsPerSecond);
	}

	@Override
	public void epochStarted(final int epoch) {
		// No interaction-controller-specific reaction needed: the View already
		// tracks this for rendering purposes.
	}

	@Override
	public void epochCompleted(final int epoch, final double loss, final double accuracy) {
		// See epochStarted.
	}

	@Override
	public void testEpochCompleted(final int epoch, final double testLoss, final double testAccuracy) {
		// See epochStarted.
	}

	@Override
	public void trainingConverged(final int epoch, final double finalLoss) {
		this.useTrainingViewServices.setControlsEnabled(false);
		this.useTrainingViewServices.highlightConvergence(epoch);
	}

	@Override
	public void trainingReset() {
		this.useTrainingViewServices.setControlsEnabled(true);
	}
}
