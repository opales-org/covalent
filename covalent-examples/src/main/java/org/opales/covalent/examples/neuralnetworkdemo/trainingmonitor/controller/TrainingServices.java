/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller;

import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.TrainingMonitor;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.impl.TrainingControllerImpl;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.interaction.TrainingInteraction;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.model.TrainingEvents;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.model.TrainingModelServices;

import org.opales.covalent.container.component.annotations.Services;

/**
 * User-facing training control, provided by {@code TrainingController} and
 * used by {@code TrainingInteraction} to drive training from the UI
 * (start/pause/reset/step buttons). Internal to the {@code TrainingMonitor}
 * function - not shared with any other function.
 * <p>
 * {@code @Services} is Covalent's synchronous, call-and-return contract
 * between components: the provider (here, {@code TrainingControllerImpl})
 * implements this interface, a consumer declares a field of this type
 * annotated {@code @UsedServices}, and the container injects a live proxy
 * into that field that forwards calls straight to the provider - contrast
 * with {@code @Events}/{@code @ProducedEvents} ({@link TrainingEvents}),
 * which is fire-and-forget and one-to-many instead. This interface is public
 * because it is this function's outward-facing control surface, meant to be
 * usable from outside the {@code TrainingMonitor} function (e.g. an external
 * smoke test driving training directly) even though its only
 * in-function consumer is {@code TrainingInteraction} - contrast with
 * {@link TrainingModelServices}, which is only ever meant to be consumed
 * from inside this function (by {@code TrainingController}) and is
 * documented as such even though Java visibility does not enforce it.
 */
@Services
public interface TrainingServices {

	/** Starts (or resumes) continuous training. */
	void start();

	/** Pauses continuous training after the current epoch completes. */
	void pause();

	/** Advances training by exactly one epoch, regardless of run/pause state. */
	void step();

	/** Resets training to its initial state: fresh weights, epoch counter back to 0. */
	void reset();

	/**
	 * Reports whether continuous training is currently running.
	 *
	 * @return {@code true} if training is actively progressing, {@code false} if paused
	 */
	boolean isRunning();
}
