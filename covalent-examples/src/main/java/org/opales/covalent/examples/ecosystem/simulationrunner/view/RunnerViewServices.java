/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.simulationrunner.view;

import org.opales.covalent.examples.ecosystem.simulationrunner.interaction.RunnerInteraction;
import org.opales.covalent.examples.ecosystem.simulationrunner.model.RunnerEvents;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Piloting commands for the runner view, provided by {@code RunnerView} and
 * used by {@code RunnerInteraction}. Distinct from the view's direct,
 * reactive consumption of {@link RunnerEvents}: a manual single step and a
 * running tick both fire the same {@link RunnerEvents#onTick(double)}, so
 * this is the only way the view can tell them apart to give distinct visual
 * feedback.
 */
@Services
public interface RunnerViewServices {

	/** Briefly highlights the readout, to mark that a manual single step just ran. */
	void pulseStep();
}
