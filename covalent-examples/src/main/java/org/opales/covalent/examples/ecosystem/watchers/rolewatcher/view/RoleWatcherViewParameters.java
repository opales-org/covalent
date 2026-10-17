/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.rolewatcher.view;

import org.opales.covalent.container.component.annotations.Parameters;

import javafx.scene.Node;

/**
 * Exposes RoleWatcher's two root JavaFX nodes (one prey table, one
 * predator table), kept separate so HmiBuilder can lay them out in
 * different places. Two named, argument-less getters - never a single
 * parameterized getRoot(role) - since a Parameters getter must take no
 * arguments.
 */
@Parameters
public interface RoleWatcherViewParameters {

    /**
     * Returns the root of the prey log table card.
     * @return the prey table card node
     */
    Node getPreyRoot();

    /**
     * Returns the root of the predator log table card.
     * @return the predator table card node
     */
    Node getPredatorRoot();
}
