/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.interaction;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.controller.DatasetShape;
import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.interaction.impl.DatasetInteractionImpl;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.interaction.NetworkInteraction;

import org.opales.covalent.hmi.action.ActionKey;

/**
 * Action identifiers registered by {@code DatasetInteraction} with the
 * HMI's {@code ActionManager}, and executed from the dataset view's controls.
 * <p>
 * An {@code ActionKey} enum constant is the stable name the View and the
 * Interaction Controller agree on for one user-triggerable action: the View
 * only ever asks {@code ActionConsumerServices} to execute one of these keys
 * (with the arguments the registered handler expects), and never invokes
 * the Interaction Controller's implementation directly. See
 * {@code DatasetInteractionImpl}'s javadoc for how the two ends of
 * this indirection - registration at {@code @Activation}/{@code @Passivation}
 * and execution from the View - fit together, and why it is kept indirect.
 */
public enum DatasetActionKey implements ActionKey {

	/** Regenerate the dataset (carries a {@code Long} random seed and a {@code Double} noise level). */
	Regenerate,

	/** Switch the generated shape (carries a {@link DatasetShape}). */
	SelectShape,

	/**
	 * The "pipette": the user clicked directly on the dataset canvas to set
	 * the network's preview-prediction input coordinates (carries a
	 * {@code double[]} of {@code {x, y}}), instead of typing them into the
	 * Predict panel's fields - see {@code DatasetInteractionImpl} for
	 * why this Interaction Controller, rather than {@code NetworkInteraction},
	 * handles it.
	 */
	PickPredictionPoint;
}
