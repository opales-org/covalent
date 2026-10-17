/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.rubikscubesolver.cube.view;

import org.opales.covalent.container.component.annotations.Parameters;

import javafx.scene.Node;

/**
 * Exposes the cube view's root JavaFX node, offered by {@code CubeView} and
 * accessed by {@code HmiBuilder} to embed it in the application window. See
 * {@code DatasetViewParameters} in the Neural Network Trainer demo for why
 * a settled JavaFX node like this is modeled as {@code @Parameters} rather
 * than {@code @Services}.
 */
@Parameters
public interface CubeViewParameters {

	/**
	 * Returns the node that {@code HmiBuilder} embeds in the application window.
	 *
	 * @return the cube view's root node, built once at activation
	 */
	Node getRoot();
}
