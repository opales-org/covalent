/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.userconfiguration.view;

import org.opales.covalent.container.component.annotations.Parameters;

import javafx.scene.Node;

/** Exposes the configuration view's root JavaFX node, accessed by HmiBuilder. */
@Parameters
public interface UserConfigurationViewParameters {

    /**
     * Returns the root of the configuration card.
     * @return the configuration root node
     */
    Node getRoot();
}
