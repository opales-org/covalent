/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.interaction;

import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.interaction.impl.NetworkInteractionImpl;

import org.opales.covalent.hmi.action.ActionKey;

/**
 * Action identifiers registered by {@code NetworkInteraction} with the
 * HMI's {@code ActionManager}, and executed from the network view's controls.
 * <p>
 * An {@code ActionKey} is a plain identifier type, not a Covalent port: it
 * names one runnable/consumable action across the
 * {@code ActionProviderServices.registerActionExecution}/{@code ActionConsumerServices.executeAction}
 * pair (see {@code NetworkInteractionImpl}). A View never calls its
 * Interaction directly for a user-triggered action; it calls
 * {@code actionConsumerServices.executeAction(SomeActionKey, args...)}, which
 * routes to whichever consumer/runnable the Interaction registered
 * for that key at activation.
 */
public enum NetworkActionKey implements ActionKey {

	/** Highlight a layer of the network diagram (carries the {@code Integer} layer index). */
	SelectLayer,
	/** Preview the network output for an input point (carries a {@code double[]} of {@code {x, y}}). */
	PreviewPrediction;
}
