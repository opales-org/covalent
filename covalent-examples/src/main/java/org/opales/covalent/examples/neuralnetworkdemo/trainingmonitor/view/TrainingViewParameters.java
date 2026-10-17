/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.view;

import org.opales.covalent.examples.neuralnetworkdemo.hmibuilder.HmiBuilder;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingParameters;

import org.opales.covalent.container.component.annotations.Parameters;

import javafx.scene.Node;

/**
 * Exposes the training view's two root JavaFX nodes (the loss and accuracy
 * charts and the run controls, kept separate so {@code HmiBuilder} can lay them out in
 * different places), offered by {@code TrainingView} and accessed by
 * {@code HmiBuilder}.
 * <p>
 * Modeled as {@code Parameters} rather than {@code Services}: stable
 * JavaFX nodes with no computation of their own are configuration-like
 * state, not a piloting operation. See {@link TrainingParameters} for what
 * {@code @Parameters}/{@code @AccessedParameters} means in general; unlike
 * that interface, this one is read-only (no setters) - {@code HmiBuilder}
 * only ever reads the nodes once, right after activation, to graft them into
 * the surrounding scene graph.
 */
@Parameters
public interface TrainingViewParameters {

	/**
	 * Returns the root node of the loss and accuracy charts, for the HMI builder to graft into the scene graph.
	 *
	 * @return the training view's charts root node, built once at activation
	 */
	Node getChartRoot();

	/**
	 * Returns the root node of the run controls, for the HMI builder to graft into the scene graph.
	 *
	 * @return the training view's run-controls root node, built once at activation
	 */
	Node getControlsRoot();
}
