/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalwatch.view;

import org.opales.covalent.examples.solarsystem.hmibuilder.HmiBuilder;

import org.opales.covalent.container.component.annotations.Parameters;

import javafx.scene.Node;

/**
 * Exposes the watch view's root JavaFX node, offered by
 * {@code OrbitalWatchView} and accessed by {@code HmiBuilder} to embed it in
 * the application window.
 */
@Parameters
public interface OrbitalWatchViewParameters {

	/**
	 * Returns the root node of the watch view.
	 *
	 * @return the watch view's root node, built once at activation
	 */
	Node getRoot();
}
