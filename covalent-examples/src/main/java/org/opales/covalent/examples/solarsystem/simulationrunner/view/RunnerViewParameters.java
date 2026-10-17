/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.simulationrunner.view;

import org.opales.covalent.examples.solarsystem.hmibuilder.HmiBuilder;

import org.opales.covalent.container.component.annotations.Parameters;

import javafx.scene.Node;

/**
 * Exposes the runner view's two root JavaFX nodes (the run controls and the
 * elapsed-time readout, kept separate so {@code HmiBuilder} can lay them out
 * in different places - the same reason
 * {@code TrainingViewParameters} keeps its chart and controls roots
 * separate), offered by {@code RunnerView} and accessed by
 * {@code HmiBuilder}.
 */
@Parameters
public interface RunnerViewParameters {

	/**
	 * Returns the root node holding the start/pause, step, reset and time scale controls.
	 *
	 * @return the runner view's run-controls root node, built once at activation
	 */
	Node getControlsRoot();

	/**
	 * Returns the root node showing the elapsed simulation time.
	 *
	 * @return the runner view's elapsed-time readout root node, built once at activation
	 */
	Node getReadoutRoot();
}
