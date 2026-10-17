/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.interaction;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.interaction.DatasetActionKey;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.interaction.NetworkActionKey;
import org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.interaction.impl.UserConfigurationInteractionImpl;
import org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.view.impl.UserConfigurationViewImpl;

import org.opales.covalent.hmi.action.ActionKey;

/**
 * Action identifiers registered by {@code UserConfigurationInteraction}
 * with the HMI's {@code ActionManager}, and executed from the view (e.g. the
 * preferences panel's controls) instead of calling the
 * Interaction Controller directly. See {@link DatasetActionKey}'s javadoc for
 * what an {@code ActionKey} enum constant is and why this indirection
 * (View asks {@code ActionConsumerServices} to execute a key; Interaction
 * Controller registers the handler at {@code @Activation}) is used instead
 * of a direct method call.
 * <p>
 * Unlike {@link DatasetActionKey} (three keys) or
 * {@link NetworkActionKey} (two keys), one key is declared per
 * user-editable parameter this composite fronts, each carrying its own
 * argument type ({@code Double} or {@code Integer}) when registered in
 * {@code UserConfigurationInteractionImpl} - a plain reflection of
 * there being one independent control (a slider or a spinner) per
 * parameter in {@code UserConfigurationViewImpl}, rather than a single
 * generic "parameter changed" action.
 */
public enum UserConfigurationActionKey implements ActionKey {

	/** Set the learning rate (carries a {@code Double}). */
	SetLearningRate,
	/** Set the number of hidden layers (carries an {@code Integer}). */
	SetHiddenLayerCount,
	/** Set the hidden layer size (carries an {@code Integer}). */
	SetHiddenLayerSize,
	/** Set the mini-batch size (carries an {@code Integer}). */
	SetBatchSize,
	/** Set the dataset noise level (carries a {@code Double}). */
	SetDatasetNoise,
	/** Set the training pace in epochs per second (carries a {@code Double}). */
	SetEpochsPerSecond;
}
