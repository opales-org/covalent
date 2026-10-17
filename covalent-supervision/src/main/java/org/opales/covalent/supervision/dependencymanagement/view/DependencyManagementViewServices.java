/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.dependencymanagement.view;


import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Piloting commands for the DependencyManagement view, provided by
 * {@code DependencyManagementView} and used by
 * {@code DependencyManagementInteraction}. Offers the same Zoom/Recenter/Export
 * commands as {@code ComponentDiagramViewServices}, for its single panel.
 * <p>
 * All methods may be called from any thread: each one hands its work over to the JavaFX application thread.
 */
@Services
public interface DependencyManagementViewServices {

	/**
	 * Opens this function's panel in SupervisionHmi's shared tab pane.
	 *
	 * @param key this function's view key
	 */
	void showView(ViewKey key);

	/**
	 * Closes this function's panel.
	 *
	 * @param key this function's view key
	 */
	void hideView(ViewKey key);

	/** Re-fits the graph viewport to its current content. */
	void recenter();

	/**
	 * Sets the zoom scale of the graph viewport.
	 *
	 * @param scale the zoom scale to apply
	 */
	void setZoomScale(double scale);

	/**
	 * Saves the graph's current rendering as a timestamped PNG in the snapshot folder and places it on the
	 * clipboard.
	 */
	void exportSnapshot();
}
