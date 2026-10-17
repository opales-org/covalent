/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.interaction;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for ComponentDiagram's Interaction Controller. Registers this
 * function's genuinely tool-wide preference ActionKeys ({@code
 * ComponentDiagramPreferencesActionKey}) via {@code ActionProviderServices}
 * at its own {@code @Activation}, then mints a fresh per-tab ActionKey set
 * (Record toggle, zoom set/observe, recenter, export, close, selection,
 * navigation, "+ New diagram") for each tab as it is created ({@code
 * onSessionCreated}) and retires that set when the tab closes ({@code
 * onSessionClosed}), as {@code SequenceDiagramInteraction} does. Queried by
 * SupervisionHmi's shared toolbar only through the
 * technical {@code SupervisorTabDescriptor} contract, never through
 * {@code ComponentDiagramControllerServices}/{@code ViewServices} directly.
 * The always-open first tab is opened the same way as any tab opened with
 * its own "+ New diagram" button.
 */
@Component
public interface ComponentDiagramInteraction {

}
