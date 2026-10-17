/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.ecosystem.view;

import org.opales.covalent.container.component.annotations.Parameters;

import javafx.scene.Node;

/**
 * Exposes the ecosystem view's two root JavaFX nodes - the chart and the
 * counts table - kept separate so HmiBuilder can lay them out in
 * different places (the counts table sits beside the chart, not below
 * it). Two named, argument-less getters, offered by EcosystemView and
 * accessed by HmiBuilder to embed each in the application window.
 */
@Parameters
public interface EcosystemViewParameters {

    /**
     * Returns the root of the population chart.
     * @return the chart card node
     */
    Node getChartRoot();

    /**
     * Returns the root of the counts table.
     * @return the counts table card node
     */
    Node getCountsRoot();
}
