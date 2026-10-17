/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller;

import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.NeuralNetworkModel;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.impl.NetworkControllerImpl;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.interaction.NetworkInteraction;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.interaction.impl.NetworkInteractionImpl;
import org.opales.covalent.examples.neuralnetworkdemo.trainingmonitor.controller.TrainingController;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Interactive preview operations, provided by {@code NetworkController} and
 * used by {@code NetworkInteraction}. Internal to the
 * {@code NeuralNetworkModel} function - distinct from the public
 * {@link NetworkServices}, used externally by {@code TrainingController}.
 * <p>
 * {@code @Services} marks a synchronous, call-and-return contract: the
 * providing component implements this interface ({@code NetworkControllerImpl}),
 * and a consumer declares a field of the same interface type annotated
 * {@code @UsedServices} to have the framework inject a live proxy to the
 * provider automatically ({@code NetworkInteractionImpl}). This contrasts with a
 * fire-and-forget notification sent via {@code @Events}, and with passive
 * configuration state read through {@code @Parameters}.
 */
@Services
public interface NetworkControllerServices {

	/**
	 * Computes the network's output for a single, arbitrary input, for an
	 * interactive "try it yourself" preview in the view - without affecting
	 * training in any way.
	 *
	 * @param input the input feature vector
	 * @return the network's output vector
	 */
	double[] previewPrediction(double[] input);
}
