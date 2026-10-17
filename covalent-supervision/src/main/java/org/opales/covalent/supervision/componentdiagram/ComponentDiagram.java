/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the ComponentDiagram MVIC (Model/View/Controller/
 * Interaction): the observed container's live topology graph -
 * component types, composition/service/parameter/event edges, selection,
 * and per-port call-activity highlighting. An arbitrary number of tabs can
 * be open at once, each its own independent selection/focus-path/drill-down
 * state over that same shared live topology - the always-open first tab and
 * every tab opened with its own "+ New diagram" button, each keyed by its
 * own {@code ComponentDiagramSessionKey}, the same pattern {@code
 * SequenceDiagram} uses for its own sessions. No separate public
 * {@code ComponentDiagramServices}: nothing outside this function calls
 * into it.
 */
@Component
public interface ComponentDiagram {

}
