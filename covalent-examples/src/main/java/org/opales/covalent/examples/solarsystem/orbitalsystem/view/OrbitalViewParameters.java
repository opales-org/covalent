/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalsystem.view;

import org.opales.covalent.examples.solarsystem.hmibuilder.HmiBuilder;

import org.opales.covalent.container.component.annotations.Parameters;

import javafx.scene.Node;

/**
 * Exposes the orbital system view's root JavaFX node, offered by
 * {@code OrbitalView} and accessed by {@code HmiBuilder} to embed it in the
 * application window.
 */
@Parameters
public interface OrbitalViewParameters {

	/**
	 * Returns the root node of the orbital system view.
	 *
	 * @return the orbital system view's root node, built once at activation
	 */
	Node getRoot();
}
