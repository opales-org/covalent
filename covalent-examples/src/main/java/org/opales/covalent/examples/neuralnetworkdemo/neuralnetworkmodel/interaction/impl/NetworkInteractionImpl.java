/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.interaction.impl;

import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkControllerServices;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.interaction.NetworkActionKey;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.interaction.NetworkInteraction;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.interaction.NetworkPreviewServices;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.view.NetworkViewServices;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionProviderServices;

/**
 * The network's Interaction Controller: registers its actions with the HMI's
 * {@code ActionManager} at activation - the view's layer-selection
 * and "try it yourself" controls trigger them through
 * {@code ActionConsumerServices.executeAction(...)}, not by calling this
 * class directly. The preview's result is pushed back to the view rather
 * than returned, since an executed action has no return value.
 * <p>
 * This is the MVIC Interaction's defining role: it is the only piece
 * that touches both a View's services (to command the view -
 * {@link NetworkViewServices}) and a Controller's services (to trigger
 * business logic - {@link NetworkControllerServices}), and it is where
 * {@link ActionProviderServices#registerActionExecution} /
 * {@code #unregisterActionExecution} calls live. It has no JavaFX code of
 * its own.
 * <p>
 * Also provides {@link NetworkPreviewServices}, this function's one public
 * entry point into its Interaction layer: {@code DatasetInteractionImpl}'s
 * pipette calls {@link #previewInput(double, double)} here instead of
 * reaching into {@link NetworkViewServices} directly, which stays strictly
 * private to this MVIC as a result.
 * <p>
 * {@code @Activation} and {@code @Passivation} are mirror lifecycle hooks:
 * the former runs once when the component becomes live, the latter when it
 * stops being live - registering the actions here and unregistering them
 * there keeps the ActionManager's registry consistent with which
 * components are actually active.
 */
@Implementation
public class NetworkInteractionImpl implements NetworkInteraction, NetworkPreviewServices {

	@UsedServices
	private ActionProviderServices actionProviderServices;

	@UsedServices
	private NetworkViewServices useNetworkViewServices;

	@UsedServices
	private NetworkControllerServices useNetworkControllerServices;

	/**
	 * Registers this component's two actions - {@link NetworkActionKey#SelectLayer}
	 * and {@link NetworkActionKey#PreviewPrediction} - with the
	 * {@code ActionManager}, each bound to the method that actually
	 * executes it and the argument type(s) it expects. From this point on,
	 * the view triggers them indirectly through
	 * {@code actionConsumerServices.executeAction(...)}.
	 */
	@Activation
	public void registerActions() {
		this.actionProviderServices.registerActionExecution(
				NetworkActionKey.SelectLayer, this::selectLayer, Integer.class);
		this.actionProviderServices.registerActionExecution(
				NetworkActionKey.PreviewPrediction, this::previewPrediction, double[].class);
	}

	/**
	 * Mirrors {@link #registerActions()}: undoes both registrations so the
	 * ActionManager holds no dangling reference to this component once it
	 * is passivated.
	 */
	@Passivation
	public void unregisterActions() {
		this.actionProviderServices.unregisterActionExecution(NetworkActionKey.SelectLayer);
		this.actionProviderServices.unregisterActionExecution(NetworkActionKey.PreviewPrediction);
	}

	/**
	 * Highlights a layer in the view, e.g. following user selection.
	 *
	 * @param layerIndex the layer index to highlight, 0-based
	 */
	private void selectLayer(final Integer layerIndex) {
		this.useNetworkViewServices.setHighlightedLayer(layerIndex);
	}

	/**
	 * Computes an interactive preview prediction, without affecting training,
	 * and pushes the result to the view.
	 *
	 * @param input the input feature vector
	 */
	private void previewPrediction(final double[] input) {
		double[] output = this.useNetworkControllerServices.previewPrediction(input);
		this.useNetworkViewServices.displayPrediction(output);
	}

	/**
	 * {@code NetworkPreviewServices}'s sole method: delegates straight to
	 * this function's own, otherwise strictly private, {@link NetworkViewServices}.
	 * The only external caller is {@code DatasetInteractionImpl}'s
	 * pipette - see {@link NetworkPreviewServices}'s javadoc.
	 *
	 * @param x the x coordinate to display
	 * @param y the y coordinate to display
	 */
	@Override
	public void previewInput(final double x, final double y) {
		this.useNetworkViewServices.setPreviewInput(x, y);
	}
}
