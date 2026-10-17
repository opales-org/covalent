/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.interaction;

import org.opales.covalent.hmi.action.ActionKey;

/**
 * This function's genuinely tool-wide COMMANDS - as opposed to {@code
 * ComponentDiagramPreferencesActionKey}'s tool-wide appearance/behavior
 * SETTINGS, and {@code ComponentDiagramActionKey}'s per-tab gestures.
 * Registered once at {@code ComponentDiagramInteractionImpl}'s own
 * {@code @Activation}, exactly like {@code ComponentDiagramPreferencesActionKey}
 * - see that class's javadoc for why a {@code ViewKey}-scoped key cannot
 * serve here.
 */
public enum ComponentDiagramToolActionKey implements ActionKey {

	/**
	 * SupervisionHmi's File menu's "New Component Diagram" item. Opens a
	 * brand new, independent tab in fresh default state - identical in
	 * effect to any existing tab's own "+ New diagram" button/{@code
	 * ComponentDiagramActionKey.Role#NewSession} (see that key's javadoc),
	 * but reachable with zero Component Diagram tabs open, since
	 * {@code Role#NewSession} is only ever registered per-session and so has
	 * nothing listening once the last tab is closed. No payload.
	 */
	NewDiagram,

	/**
	 * Shows, in the first Component Diagram tab, the link between two
	 * components - e.g. from a sequence diagram message. Payload: a
	 * {@code List} of two component types, source then target. The tab
	 * goes to the level where both are visible, as themselves or as their
	 * enclosing composites, selects them and highlights their links.
	 */
	ShowLink,

	/** Shows a composite's inside in the first open diagram, brought to front - {@code Class} payload. */
	OpenComposite
}
