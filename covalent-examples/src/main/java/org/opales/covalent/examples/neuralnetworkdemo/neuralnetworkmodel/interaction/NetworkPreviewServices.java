/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.interaction;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.interaction.DatasetActionKey;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.interaction.impl.DatasetInteractionImpl;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.interaction.impl.NetworkInteractionImpl;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.view.NetworkViewServices;

import org.opales.covalent.container.component.annotations.Services;

/**
 * The one public, cross-function entry point into the {@code NeuralNetworkModel}
 * function's Interaction layer: lets {@code DatasetExplorer}'s pipette
 * ({@link DatasetActionKey#PickPredictionPoint}) push a canvas-picked point
 * into the network view's Predict fields, without reaching into
 * {@link NetworkViewServices} directly.
 * <p>
 * {@code NetworkViewServices} stays what it is everywhere else in this demo -
 * a View-level port strictly private to its own MVIC, used only by this
 * function's own {@code NetworkInteraction}. {@code NetworkInteractionImpl}
 * provides this interface and delegates {@link #previewInput(double, double)}
 * straight to its own {@code useNetworkViewServices.setPreviewInput(...)}
 * call; {@code DatasetInteractionImpl} is the sole external consumer,
 * exactly as {@code TrainingController} is the sole external consumer of
 * {@link org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkServices}.
 * <p>
 * A dedicated, single-method interface rather than folding this into a
 * broader contract: mirrors this project's existing granularity (e.g.
 * {@code DatasetControllerServices} vs {@code DatasetServices}) and keeps
 * this narrow, UI-only reach clearly named for what it does rather than
 * bundled into a general-purpose "Interaction services" grab-bag.
 */
@Services
public interface NetworkPreviewServices {

	/**
	 * Fills the Predict panel's x/y fields with a coordinate pair, without
	 * triggering a prediction - the user still presses "Predict" themselves
	 * afterward.
	 *
	 * @param x the x coordinate to display
	 * @param y the y coordinate to display
	 */
	void previewInput(double x, double y);
}
