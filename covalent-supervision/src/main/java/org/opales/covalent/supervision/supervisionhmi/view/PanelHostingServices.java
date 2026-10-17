/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.supervisionhmi.view;

import org.opales.covalent.container.component.annotations.Services;

import javafx.scene.Node;

/**
 * Hosts panels into SupervisionHmi's tab pane, provided by
 * {@code SupervisionHmiView} and used by every hosted function's own View.
 * Push, not pull: a function's View calls {@link #openPanel} once it has
 * built the panel it wants shown, and {@link #closePanel} if/when it wants
 * that panel gone - {@code SupervisionHmiView} is never a party to how or
 * when a function decides to show or hide itself, only to the fact that it
 * did.
 */
@Services
public interface PanelHostingServices {

	/**
	 * Shows a panel as its own tab, creating the tab if {@code key} is not
	 * already hosted, or simply resurfacing the existing tab otherwise.
	 *
	 * @param key        opaque identifier for this panel, supplied by the caller
	 * @param panel      the panel's root node
	 * @param descriptor the tab's shared-toolbar contract
	 */
	void openPanel(ViewKey key, Node panel, SupervisorTabDescriptor descriptor);

	/**
	 * Removes a previously opened panel's tab, if still present.
	 *
	 * @param key the panel's identifier, as passed to {@link #openPanel}
	 */
	void closePanel(ViewKey key);

	/**
	 * Sets a tab's title, as the user would by renaming it.
	 *
	 * @param key   the tab's panel
	 * @param title its new title
	 */
	void setPanelTitle(ViewKey key, String title);
}
