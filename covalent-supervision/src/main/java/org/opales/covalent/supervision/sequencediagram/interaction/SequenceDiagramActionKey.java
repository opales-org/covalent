/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.interaction;

import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

/**
 * A per-session ActionKey: {@code (sessionKey, role)}, with structural
 * equality on both. SequenceDiagram mints one tab per recording session at
 * runtime, so its ActionKeys cannot be fixed enum values; {@code
 * ComponentDiagramActionKey} has the same shape. {@code SequenceDiagramInteractionImpl}
 * registers/unregisters one set of these per session (at
 * {@code onSessionCreated}/{@code onSessionClosed}); {@code
 * SequenceDiagramViewImpl} constructs the identical keys independently, by
 * structural equality, when building each session's tab descriptor and
 * dispatching its gestures - the two never need to pass ActionKey instances
 * to each other.
 */
public final class SequenceDiagramActionKey implements ActionKey {

	/** Which gesture/value this key stands for, within one session. */
	public enum Role {
		/** Shared toolbar's zoom slider dragged, for this session's tab - {@code Double} payload. */
		ZoomSetScale,

		/**
		 * Technical value key: {@code ActionProviderServices.setActionValue(...)}
		 * only, never registered as an execution. See {@code ComponentDiagramActionKey}.
		 */
		ZoomCurrentScale,

		/** This session's own zoom actually changed - {@code Double} payload. */
		ZoomChanged,

		/** A gesture that replaces this session's whole actor selection - {@code Set<Class<?>>} payload. */
		SelectActors,

		/** A Ctrl+click that toggles a single actor in this session - {@code Class<?>} payload. */
		ToggleActorSelection,

		/** This session's panel's native tab-close gesture - no payload. */
		Close,

		/**
		 * This session's own control-panel toggle flips the user's explicit
		 * animation preference for this diagram - {@code Boolean} payload.
		 * See {@code SequenceDiagramViewServices#setAnimationActive}.
		 */
		ToggleAnimationActive,

		/**
		 * This session's Save log tool - {@code File} payload: where to write
		 * every message recorded, as chosen in the View's own save dialog; the
		 * file's extension tells the log's format.
		 */
		ExportTrace,

		/** This session's Recenter tool: fits the diagram's width to its actors - no payload. */
		Recenter,

		/** This session's Snapshot tool: saves and copies an image of the diagram - no payload. */
		Snapshot
	}

	private final ViewKey sessionKey;
	private final Role role;

	/**
	 * Creates the key of one role in one session.
	 *
	 * @param sessionKey the session the key belongs to
	 * @param role the gesture or value the key stands for
	 */
	public SequenceDiagramActionKey(final ViewKey sessionKey, final Role role) {
		this.sessionKey = sessionKey;
		this.role = role;
	}

	/**
	 * Returns the session this key belongs to.
	 *
	 * @return the session key
	 */
	public ViewKey getSessionKey() {
		return this.sessionKey;
	}

	/**
	 * Returns the gesture or value this key stands for.
	 *
	 * @return the role
	 */
	public Role getRole() {
		return this.role;
	}

	@Override
	public boolean equals(final Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof SequenceDiagramActionKey)) {
			return false;
		}
		SequenceDiagramActionKey that = (SequenceDiagramActionKey) other;
		return this.sessionKey.equals(that.sessionKey) && this.role == that.role;
	}

	@Override
	public int hashCode() {
		return 31 * this.sessionKey.hashCode() + this.role.hashCode();
	}

	@Override
	public String toString() {
		return "SequenceDiagramActionKey[" + this.sessionKey + ", " + this.role + "]";
	}
}
