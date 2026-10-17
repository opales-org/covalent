/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.neuralnetworkdemo.userconfiguration.view;

import org.opales.covalent.examples.neuralnetworkdemo.datasetexplorer.view.DatasetViewParameters;
import org.opales.covalent.examples.neuralnetworkdemo.hmibuilder.HmiBuilder;

import org.opales.covalent.container.component.annotations.Parameters;

import javafx.scene.Node;

/**
 * Exposes the configuration view's root JavaFX node, offered by
 * {@code UserConfigurationView} and accessed by {@code HmiBuilder} to embed
 * it in the application window.
 * <p>
 * Modeled as {@code @Parameters} rather than {@code @Services}: a stable
 * JavaFX node with no computation of its own is configuration-like state,
 * not a piloting operation. See {@link DatasetViewParameters}'s javadoc for
 * the fuller rationale (including the caller/provider-benefit distinction
 * from {@code doc/managed-components.md}) - the same reasoning applies here,
 * {@code HmiBuilder} again being the one reading a node built by someone
 * else's View.
 */
@Parameters
public interface UserConfigurationViewParameters {

	/**
	 * Returns the root node of the configuration view, for the HMI builder to graft into the scene graph.
	 *
	 * @return the configuration view's root node, built once at activation
	 */
	Node getRoot();
}
