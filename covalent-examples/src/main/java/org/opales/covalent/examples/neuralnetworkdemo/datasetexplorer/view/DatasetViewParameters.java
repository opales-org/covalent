/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.view;

import org.opales.covalent.examples.neuralnetworkdemo.hmibuilder.HmiBuilder;

import org.opales.covalent.container.component.annotations.Parameters;

import javafx.scene.Node;

/**
 * Exposes the dataset view's root JavaFX node, offered by {@code DatasetView}
 * and accessed by {@code HmiBuilder} to embed it in the application window.
 * <p>
 * Modeled as {@code @Parameters} rather than {@code @Services}: a stable
 * JavaFX node with no computation of its own is configuration-like state,
 * not a piloting operation. More precisely (see {@code doc/managed-components.md}'s
 * Parameters section): a service call is made for the caller's benefit, a
 * parameter call is made for the provider's benefit, and parameters are
 * wired earlier in the component lifecycle than services - reachable from
 * {@code @Initialization}, before activation. Here it is {@code HmiBuilder}
 * that reads the node built by {@code DatasetView} at its own activation, so
 * the caller/provider direction is admittedly the reverse of the usual
 * "configure the provider" case; what still makes {@code @Parameters} the
 * right fit is the nature of the value itself - a settled reference, not an
 * operation to invoke - rather than who benefits from the call.
 */
@Parameters
public interface DatasetViewParameters {

	/**
	 * Returns the root node of the dataset view, for the HMI builder to graft into the scene graph.
	 *
	 * @return the dataset view's root node, built once at activation
	 */
	Node getRoot();
}
