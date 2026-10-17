/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.view;

import org.opales.covalent.examples.neuralnetworkdemo.hmibuilder.HmiBuilder;
import org.opales.covalent.examples.neuralnetworkdemo.neuralnetworkmodel.controller.NetworkParameters;

import org.opales.covalent.container.component.annotations.Parameters;

import javafx.scene.Node;

/**
 * Exposes the network view's root JavaFX node, offered by {@code NetworkView}
 * and accessed by {@code HmiBuilder} to embed it in the application window.
 * <p>
 * Modeled as {@code Parameters} rather than {@code Services}: a stable
 * JavaFX node with no computation of its own is configuration-like state,
 * not a piloting operation. See {@link NetworkParameters} for the general
 * {@code @Parameters}/{@code @AccessedParameters} mechanism this port
 * relies on.
 */
@Parameters
public interface NetworkViewParameters {

	/**
	 * Returns the root node of the network view, for the HMI builder to graft into the scene graph.
	 *
	 * @return the network view's root node, built once at activation
	 */
	Node getRoot();
}
