/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.interaction;

import org.opales.covalent.supervision.componentdiagram.model.PortColorKey;

import org.opales.covalent.hmi.action.ActionKey;

/**
 * ActionKeys for this function's genuinely tool-wide preferences - the ones
 * SupervisionHmi's shared "Preferences" panel and View menu reach directly,
 * never any one tab's own state. Kept apart from the per-tab
 * {@link ComponentDiagramActionKey}: a palette or the auto-layout
 * toggle is shared by every open Component Diagram tab alike, not
 * something a {@code ViewKey} could meaningfully scope.
 */
public enum ComponentDiagramPreferencesActionKey implements ActionKey {

	/**
	 * SupervisionHmi's shared "Preferences" panel's "Recalculate layout when
	 * hiding components" check item - {@code Boolean} payload. Off by
	 * default: see {@code ComponentDiagramViewImpl}'s own field javadoc.
	 */
	ToggleAutoLayoutOnHide,

	/**
	 * SupervisionHmi's shared "Preferences" panel's port-color pickers, one
	 * gesture per entry - {@code PortColorKey}/web color ({@code #rrggbbaa})
	 * payload pair. Applied to {@code PortPalette} and repainted immediately
	 * in every open tab by {@code ComponentDiagramViewImpl.setPortColor(...)}.
	 */
	SetPortColor,

	/**
	 * SupervisionHmi's View menu theme switch - {@code SupervisorTheme}
	 * payload. Switches {@code PortPalette} to that theme's own palette
	 * (each theme keeps its own, edited independently through
	 * {@code SetPortColor}) and republishes {@code PortColorsSnapshot}.
	 */
	SetTheme,

	/**
	 * SupervisionHmi's Colors panel's "Restore default colors" button - no
	 * payload. Restores the current theme's default palette and republishes
	 * {@code PortColorsSnapshot}; the panel forgets the theme's stored colors
	 * itself before executing it. Available only while one of the current
	 * theme's colors differs from its default.
	 */
	ResetPortColors,

	/**
	 * Technical value key: {@code ActionProviderServices.setActionValue(...)}
	 * only, never registered as an execution. Pushes a {@code Map<PortColorKey, String>}
	 * snapshot of every current port color, so the Preferences panel's color
	 * pickers can seed themselves via {@code ActionConsumerServices.bindActionValue(...)}.
	 * Pushed once at this function's own {@code @Activation}, and again every
	 * time {@code SetPortColor} or {@code SetTheme} changes something.
	 */
	PortColorsSnapshot
}
