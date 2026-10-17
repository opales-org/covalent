/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.dependencymanagement.interaction;

import org.opales.covalent.hmi.action.ActionKey;

/**
 * ActionKeys registered by {@code DependencyManagementInteraction} at
 * its own {@code @Activation}. Same shared-toolbar button set as {@code
 * ComponentDiagram}/{@code SequenceDiagram}, minus a Record/Stop button:
 * this function has no session to record, only a recompute of the graph on
 * demand.
 */
public enum DependencyManagementActionKey implements ActionKey {

	/** Shared toolbar's zoom slider dragged - {@code Double} payload. */
	ZoomSetScale,

	/**
	 * Technical value key: {@code ActionProviderServices.setActionValue(...)}
	 * only, never registered as an execution. See {@code ComponentDiagramActionKey}.
	 */
	ZoomCurrentScale,

	/** This View's own zoom actually changed - {@code Double} payload. */
	ZoomChanged,

	/** This View's own Recenter button - no payload. */
	Recenter,

	/** This View's own Refresh button: recomputes the graph - no payload. */
	Refresh,

	/** This View's own Snapshot/export button - no payload. */
	Export,

	/**
	 * SupervisionHmi's permanent toolbar toggle button for this function's
	 * panel - closed by default, opened on the first click and closed again
	 * on the next. No payload.
	 */
	Toggle
}
