/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.view;

import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkControllerServices;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.interaction.NetworkInteraction;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Piloting commands for the network view, provided by {@code NetworkView}
 * and used by {@code NetworkInteraction}. Strictly private to this
 * function, like every other {@code *ViewServices} in this demo - the
 * dataset function's pipette feature reaches {@link #setPreviewInput(double, double)}
 * only indirectly, through {@code NetworkInteraction}'s own public
 * {@code NetworkPreviewServices}, never through this interface directly.
 * <p>
 * See {@link NetworkControllerServices} for the general {@code @Services}/
 * {@code @UsedServices} mechanism this port relies on.
 */
@Services
public interface NetworkViewServices {

	/**
	 * Highlights one layer in the rendering, e.g. following user selection.
	 *
	 * @param layerIndex the layer index to highlight, 0-based
	 */
	void setHighlightedLayer(int layerIndex);

	/**
	 * Displays the result of an interactive "try it yourself" preview
	 * prediction, without affecting training.
	 *
	 * @param output the network's output vector for the previewed input
	 */
	void displayPrediction(double[] output);

	/**
	 * Fills the Predict panel's x/y fields with a coordinate pair, without
	 * triggering a prediction - the pipette's effect (see
	 * {@code DatasetActionKey#PickPredictionPoint}): the user still presses
	 * "Predict" themselves afterward.
	 *
	 * @param x the x coordinate to display
	 * @param y the y coordinate to display
	 */
	void setPreviewInput(double x, double y);
}
