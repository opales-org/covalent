/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.supervisionhmi.view;

import java.util.Set;

import org.opales.covalent.hmi.action.ActionKey;

/**
 * Per-tab contract queried by SupervisionHmi's chrome: the side-rail entry
 * the tab is hosted under, which {@link ToolbarFunction}s its function
 * supports, and the {@link ActionKey}s to bind for the ones it declares. Bundled into
 * {@link PanelHostingServices#openPanel(ViewKey, javafx.scene.Node,
 * SupervisorTabDescriptor)} so {@code SupervisionHmiView} always has the
 * right descriptor for a given tab.
 */
public interface SupervisorTabDescriptor {

	/**
	 * Returns the title shown on the tab.
	 *
	 * @return the text to display on this tab, or {@code ""} to leave it
	 *         unset. Unlike the toolbar accessors below, every tab is free
	 *         to have a title regardless of {@link #supportedToolbarFunctions()},
	 *         so this is a plain default rather than an
	 *         {@link UnsupportedOperationException}-throwing one.
	 */
	default String title() {
		return "";
	}

	/**
	 * Tells whether the user can rename the tab.
	 *
	 * @return whether the user can rename this tab: a double click on its
	 *         title, or Rename in its context menu, edits it in place. A tab
	 *         renamed by the user keeps its new title when it is opened again.
	 */
	default boolean renamable() {
		return false;
	}

	/**
	 * Returns the action executed when the user renames the tab.
	 *
	 * @return the action executed with the new title, a {@link String}, when
	 *         the user renames this tab, or {@code null} if its owner need not
	 *         know
	 */
	default ActionKey renameActionKey() {
		return null;
	}

	/**
	 * Returns the side-rail entry the tab belongs to.
	 *
	 * @return the side-rail entry this panel is hosted under
	 */
	RailEntry railEntry();

	/**
	 * Returns the shared-toolbar widgets the tab drives.
	 *
	 * @return the toolbar functions this tab's function actually supports
	 */
	Set<ToolbarFunction> supportedToolbarFunctions();

	/**
	 * Returns the action that toggles the tab's recording.
	 *
	 * @return the ActionKey toggling this tab's Record/Stop button
	 */
	ActionKey recordActionKey();

	/**
	 * Returns the action that sets the tab's zoom scale.
	 *
	 * @return the ActionKey setting this tab's zoom scale
	 */
	ActionKey zoomSetScaleActionKey();

	/**
	 * Returns the value key through which the tab's zoom scale is observed.
	 *
	 * @return the ActionKey observing this tab's current zoom scale
	 */
	ActionKey zoomCurrentScaleActionKey();

	/**
	 * Returns the action executed when the tab is closed with its own close gesture.
	 *
	 * @return the ActionKey to execute when this tab's native close gesture
	 *         (the tab's own close "x") is used, or {@code null} if this tab
	 *         has no notion of being closed that way, which is the default. A
	 *         function whose dynamically-opened tabs dispose of their own
	 *         per-instance state when the user closes them that way (rather
	 *         than only ever being closed/reopened through the function's
	 *         own in-panel controls) overrides this to return the key its
	 *         own Interaction already registers for that purpose - see
	 *         {@code ComponentDiagramActionKey.Role#Close}/{@code
	 *         SequenceDiagramActionKey.Role#Close}. Not part of {@link
	 *         ToolbarFunction}: this is about the tab widget's own native
	 *         close gesture, not a shared-toolbar widget.
	 */
	default ActionKey closeActionKey() {
		return null;
	}

	/**
	 * Tells whether the tab shows a close button.
	 *
	 * @return whether this tab shows a close button - {@code true} by default. A tab that stays as
	 *         long as its rail entry, such as the cockpit's, returns {@code false}.
	 */
	default boolean closable() {
		return true;
	}

	/**
	 * Returns the value key carrying the tab's recording state.
	 *
	 * @return the technical value key ({@code ActionProviderServices.setActionValue(...)}
	 *         only) carrying this tab's recording state as a {@code Long}:
	 *         the recording's start time in epoch milliseconds, or {@code 0}
	 *         while not recording - so the shared Record button can show
	 *         whether this tab records, and for how long; {@code null} - the
	 *         default - for a tab that never records. Only meaningful alongside {@link ToolbarFunction#RECORD_TOGGLE}.
	 */
	default ActionKey recordingStateActionKey() {
		return null;
	}
}
