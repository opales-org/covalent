/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.containermanager.view;

import org.opales.covalent.container.component.annotations.Parameters;

import javafx.scene.Node;

/**
 * Exposes the managed-container selector's root JavaFX node, offered by
 * {@code ContainerManagerView} and accessed by {@code SupervisionHmiView} to
 * embed it into its own chrome.
 */
@Parameters
public interface ContainerManagerViewParameters {

	/**
	 * Returns the managed-container selector's root node.
	 *
	 * @return the selector's root node, built once at activation
	 */
	Node getSelectorRoot();
}
