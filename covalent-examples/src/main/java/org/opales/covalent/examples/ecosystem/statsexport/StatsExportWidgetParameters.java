/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.statsexport;

import javafx.scene.Node;

/**
 * Raw parameters interface offered by {@link StatsExportWidget}, read by
 * HmiBuilder through the same {@code @AccessedParameters} + {@code
 * getRoot()} pattern used for every managed function's own
 * *ViewParameters. Deliberately not annotated with {@code @Parameters}:
 * raw offerors and accessors wire this interface manually through
 * {@code RawComponentsServices}, outside the managed component model (see
 * the Covalent Raw Components Guide).
 */
public interface StatsExportWidgetParameters {

    /**
     * The checkbox + line-count label, embedded as a single row.
     *
     * @return the root node of the controls row
     */
    Node getRoot();

    /**
     * A running log of export activity (one entry per exported CSV
     * line), embedded as its own card alongside the other watcher
     * tables.
     *
     * @return the root node of the log card
     */
    Node getLogRoot();
}
