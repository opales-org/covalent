/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.interaction;

import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.interaction.impl.TrainingInteractionImpl;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.view.impl.TrainingViewImpl;

import org.opales.covalent.hmi.action.ActionKey;

/**
 * Action identifiers registered by {@code TrainingInteraction} with the
 * HMI's {@code ActionManager}, and executed from the training view's controls
 * (start/pause/step/reset buttons, batch-size and pace fields, and the
 * "freeze charts" checkbox).
 * <p>
 * An {@code ActionKey} is simply an enum constant that identifies one
 * user-triggerable action; implementing the marker interface
 * {@link ActionKey} is what lets it be passed to
 * {@code ActionProviderServices.registerActionExecution(...)} (by the
 * Interaction that defines what the action does) and to
 * {@code ActionConsumerServices.executeAction(...)} (by the View that
 * triggers it) - see {@link TrainingInteractionImpl} for the
 * registration side and {@link TrainingViewImpl} for the triggering side.
 * {@code SetBatchSize}, {@code SetEpochsPerSecond} and
 * {@code ToggleFreezeCharts} are registered with an expected argument type
 * ({@code Integer}/{@code Double}/{@code Boolean}); the other four take no
 * argument.
 */
public enum TrainingActionKey implements ActionKey {

	/** Start or resume continuous training. */
	Start,
	/** Pause continuous training. */
	Pause,
	/** Advance training by one epoch. */
	Step,
	/** Reset training to its initial state. */
	Reset,
	/** Set the mini-batch size (carries an {@code Integer}). */
	SetBatchSize,
	/** Set the training pace (carries a {@code Double}). */
	SetEpochsPerSecond,
	/** Freeze or unfreeze the charts (carries a {@code Boolean}, {@code true} to freeze). */
	ToggleFreezeCharts;
}
