/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.interaction;

import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

/**
 * A per-tab ActionKey: {@code (sessionKey, role)}, with structural equality on both - the same shape as
 * {@code SequenceDiagramActionKey}. Tabs are created at runtime, the first auto-opened one included (see
 * {@code ComponentDiagramSessionKey}), so these keys cannot be fixed enum constants.
 * {@code ComponentDiagramInteractionImpl} registers/unregisters one set of these per session (at
 * {@code ComponentDiagramEvents#onSessionCreated}/{@code #onSessionClosed}); {@code ComponentDiagramViewImpl}
 * constructs the identical keys independently, by structural equality, when building each session's tab
 * descriptor and dispatching its gestures.
 * <p>
 * The genuinely function-wide preferences this tool's shared "Preferences" panel edits (auto-layout-on-hide, port
 * colors, theme) are not a single tab's own state and stay out of this class entirely - see {@code
 * ComponentDiagramPreferencesActionKey}.
 */
public final class ComponentDiagramActionKey implements ActionKey {

	/** Which gesture/value this key stands for, within one tab. */
	public enum Role {

		/** Shared toolbar's Record toggle button click, for this tab - no payload. */
		RecordToggle,

		/** Shared toolbar's zoom slider dragged, for this tab - {@code Double} payload. */
		ZoomSetScale,

		/**
		 * Technical value key: {@code ActionProviderServices.setActionValue(...)}
		 * only, never registered as an execution. Carries this tab's current
		 * zoom scale - {@code Double} value.
		 */
		ZoomCurrentScale,

		/** This tab's own zoom actually changed - {@code Double} payload. */
		ZoomChanged,

		/** This tab's own Recenter button - no payload. */
		Recenter,

		/** This tab's own Snapshot/export button - no payload. */
		Export,

		/**
		 * This tab's native tab-close gesture, or its own in-panel equivalent - no payload. It discards the session for
		 * good, as {@code SequenceDiagramActionKey.Role#Close} does; see
		 * {@code ComponentDiagramControllerServices#closeSession(ViewKey)}.
		 */
		Close,

		/** A gesture that replaces the whole selection - {@code Set<Class<?>>} payload. */
		SelectComponents,

		/** A Ctrl+click that toggles a single component - {@code Class<?>} payload. */
		ToggleComponentSelection,

		/** The Show/Hide deps buttons - {@code Class<?>}/{@code Boolean} payload. */
		ToggleDependenciesVisibility,

		/** Double-click/enter gesture on a composite - {@code Class<?>} payload. */
		EnterComposite,

		/**
		 * Breadcrumb click or the "back" button - {@code Integer} payload, the
		 * focus path depth to jump to (0 = top-level forest).
		 */
		ExitToDepth,

		/**
		 * A click on a composite in the component tree - {@code Class<?>} payload. Shows that
		 * composite's content wherever it lives in the hierarchy, and selects it, in one step.
		 */
		ShowComposite,

		/**
		 * A navigable port's small blue badge, clicked directly or chosen from
		 * its picker when it has several targets - {@code Class<?>} payload,
		 * the real external component type to reveal (see {@code
		 * ComponentDiagramExternalPort}). Jumps the focus path to that
		 * component's own parent and briefly flashes it.
		 */
		NavigateToComponent,

		/**
		 * This tab's own "+ New diagram" button - no payload. Opens a brand
		 * new, independent tab in fresh default state; which existing tab's
		 * button was clicked is incidental, not part of what the gesture
		 * means - see {@code ComponentDiagramControllerServices#openNewSession()}.
		 */
		NewSession,

		/**
		 * This tab's own "Save view..." button - no payload: the save dialog
		 * is shown.
		 */
		SaveView,

		/**
		 * Ctrl+S in this tab - no payload: a tab opened from or saved to a view
		 * of the observed container replaces it directly; otherwise the save
		 * dialog is shown.
		 */
		QuickSaveView,

		/**
		 * The save dialog confirmed - {@code SaveViewRequest} payload.
		 */
		ConfirmSaveView,

		/**
		 * This tab's own "Views library..." button, or Ctrl+O - no payload: the
		 * views library is shown.
		 */
		OpenViewLibrary,

		/**
		 * A view opened from the views library - {@code SavedView} payload. It
		 * always opens in a new tab.
		 */
		OpenView,

		/**
		 * A view renamed in the views library - {@code RenameViewRequest}
		 * payload.
		 */
		RenameView,

		/**
		 * A view deleted in the views library - {@code SavedView} payload.
		 */
		DeleteView,

		/**
		 * A view exported from the views library, once the user has picked a
		 * file - {@code ExportViewRequest} payload.
		 */
		ExportView,

		/**
		 * A view file imported into the views library, once the user has
		 * picked it - {@code File} payload.
		 */
		ImportView,

		/**
		 * This tab renamed by the user - {@code String} payload, the new
		 * title. A tab renamed away from its view's name no longer replaces
		 * that view when saved.
		 */
		RenameTab
	}

	private final ViewKey sessionKey;
	private final Role role;

	/**
	 * Creates the key of one gesture or value of one tab.
	 *
	 * @param sessionKey the tab this key belongs to
	 * @param role which gesture or value it stands for
	 */
	public ComponentDiagramActionKey(final ViewKey sessionKey, final Role role) {
		this.sessionKey = sessionKey;
		this.role = role;
	}

	/**
	 * Returns the tab this key belongs to.
	 *
	 * @return the tab this key belongs to
	 */
	public ViewKey getSessionKey() {
		return this.sessionKey;
	}

	/**
	 * Returns the gesture or value this key stands for.
	 *
	 * @return which gesture or value of the tab this key stands for
	 */
	public Role getRole() {
		return this.role;
	}

	@Override
	public boolean equals(final Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof ComponentDiagramActionKey)) {
			return false;
		}
		ComponentDiagramActionKey that = (ComponentDiagramActionKey) other;
		return this.sessionKey.equals(that.sessionKey) && this.role == that.role;
	}

	@Override
	public int hashCode() {
		return 31 * this.sessionKey.hashCode() + this.role.hashCode();
	}

	@Override
	public String toString() {
		return "ComponentDiagramActionKey[" + this.sessionKey + ", " + this.role + "]";
	}
}
