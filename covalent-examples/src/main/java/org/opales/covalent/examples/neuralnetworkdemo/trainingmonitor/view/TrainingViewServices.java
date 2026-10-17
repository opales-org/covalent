/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.view;

import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingServices;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.interaction.TrainingInteraction;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.interaction.impl.TrainingInteractionImpl;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.model.TrainingEvents;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Piloting commands for the training view, provided by {@code TrainingView}
 * and used by {@code TrainingInteraction}. Distinct from the view's
 * direct, reactive consumption of {@link TrainingEvents}: these are
 * explicit interaction-driven commands rather than a reflection of the
 * Model's state.
 * <p>
 * See {@link TrainingServices} for what {@code @Services}/{@code @UsedServices}
 * means in general. This is the Interaction-to-View half of the
 * Interaction's two service links (the other being
 * {@link TrainingServices} for driving the Controller); see
 * {@link TrainingInteractionImpl} for why exactly one component ends
 * up depending on both.
 */
@Services
public interface TrainingViewServices {

	/**
	 * Enables or disables the Start and Step buttons: disabled once the
	 * training has converged, enabled again when it is reset.
	 *
	 * @param enabled {@code true} to enable the controls, {@code false} to disable them
	 */
	void setControlsEnabled(boolean enabled);

	/**
	 * Visually highlights that training has converged.
	 *
	 * @param atEpoch the epoch at which convergence was detected
	 */
	void highlightConvergence(int atEpoch);

	/**
	 * Freezes or unfreezes the view's live charts: while frozen, the view
	 * stops reacting to {@link TrainingEvents}'s epoch-level notifications
	 * (training keeps running underneath - only the display pauses), and
	 * resumes - with a visible jump across whatever was missed - once
	 * unfrozen. See {@code TrainingViewImpl}'s javadoc for the manual
	 * subscription this toggles.
	 *
	 * @param frozen {@code true} to freeze the charts, {@code false} to
	 *               unfreeze them
	 */
	void setChartsFrozen(boolean frozen);
}
