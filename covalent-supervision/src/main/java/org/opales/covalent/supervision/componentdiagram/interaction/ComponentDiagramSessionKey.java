/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.interaction;

import java.util.UUID;

import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

/**
 * One Component Diagram tab's own identity - UUID-based, nothing more.
 * {@code ComponentDiagramControllerImpl.openNewSession()} mints one per tab, for the first always-open tab and for
 * tabs opened from the View's "+ New diagram" button alike. Two keys are equal when their UUIDs are. Mirrors the
 * private {@code SequenceDiagramSessionKey} of {@code SequenceDiagramControllerImpl}.
 */
public final class ComponentDiagramSessionKey implements ViewKey {

	/** Creates a key with a fresh random identity. */
	public ComponentDiagramSessionKey() {
		// Nothing to initialize.
	}

	private final UUID id = UUID.randomUUID();

	@Override
	public boolean equals(final Object other) {
		return other instanceof ComponentDiagramSessionKey && this.id.equals(((ComponentDiagramSessionKey) other).id);
	}

	@Override
	public int hashCode() {
		return this.id.hashCode();
	}

	@Override
	public String toString() {
		return "ComponentDiagramSessionKey[" + this.id + "]";
	}
}
