/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.view;

import java.util.Map;
import java.util.Set;


import org.opales.covalent.supervision.componentdiagram.model.PortColorKey;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisorTheme;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Piloting commands for the ComponentDiagram view, provided by
 * {@code ComponentDiagramView} and used by
 * {@code ComponentDiagramInteraction}. The per-tab accessors below
 * (zoom, recenter, snapshot) are scoped by {@link ViewKey}, like
 * {@code SequenceDiagramViewServices}; {@link #setAutoLayoutOnHide(boolean)},
 * {@link #setPortColor(PortColorKey, String)}/{@link #getPortColor(PortColorKey)}
 * and {@link #setTheme(SupervisorTheme)} are not - they are this tool's
 * genuinely shared preferences, the same for every open tab, so there is no
 * tab to scope them to.
 * <p>
 * Opening and closing a tab is not exposed here, as in {@code
 * SequenceDiagramViewServices}: {@code ComponentDiagramViewImpl} reacts to
 * {@code ComponentDiagramEvents#onSessionCreated}/{@code #onSessionClosed}
 * directly, as it does to {@code #onTopologyChanged} and the other events,
 * so no Interaction-initiated "show/hide" command is needed.
 * <p>
 * The methods that return nothing may be called from any thread: each one hands its work over to the JavaFX
 * application thread. The ones that return a value ({@link #getZoomScale}, {@link #getHiddenComponents},
 * {@link #getPositions}, {@link #captureThumbnail}, {@link #getTabTitle}, {@link #confirm}, {@link #getPortColor}
 * and {@link #getDefaultPortColor}) read the view's own state directly and must be called from the JavaFX application
 * thread only.
 */
@Services
public interface ComponentDiagramViewServices {

	/**
	 * Re-fits one tab's graph viewport to its current content.
	 *
	 * @param sessionKey the tab to recenter
	 */
	void recenter(ViewKey sessionKey);

	/**
	 * Shows this tab, switching the side rail to it.
	 *
	 * @param sessionKey the tab to show
	 */
	void bringToFront(ViewKey sessionKey);

	/**
	 * Sets one tab's zoom scale.
	 *
	 * @param sessionKey the tab to rescale
	 * @param scale      the new zoom scale
	 */
	void setZoomScale(ViewKey sessionKey, double scale);

	/**
	 * Returns one tab's zoom scale.
	 *
	 * @param sessionKey the tab to read the zoom scale of
	 * @return the current zoom scale
	 */
	double getZoomScale(ViewKey sessionKey);

	/**
	 * Exports one tab's graph's current rendering: saves it to a timestamped
	 * PNG in the snapshot folder and places it on the clipboard. Does nothing
	 * for an unknown tab.
	 *
	 * @param sessionKey the tab to export
	 */
	void exportSnapshot(ViewKey sessionKey);

	/**
	 * Returns the components hidden in one tab's graph.
	 *
	 * @param sessionKey the tab to read
	 * @return every component type hidden in that tab's graph -
	 *         for {@code DiagramPersistenceServices} to save, via {@code
	 *         ComponentDiagramControllerServices#saveView}; visibility
	 *         itself is pure View rendering state, never modeled in the
	 *         Model/Controller.
	 */
	Set<Class<?>> getHiddenComponents(ViewKey sessionKey);

	/**
	 * Returns the position of every component present in one tab's graph.
	 *
	 * @param sessionKey the tab to read
	 * @return each present component type's own {@code
	 *         (layoutX, layoutY)} - manual or auto-laid-out, whichever is
	 *         current - for {@code DiagramPersistenceServices} to save
	 */
	Map<Class<?>, double[]> getPositions(ViewKey sessionKey);

	/**
	 * Captures a tab's diagram as a thumbnail for the views library.
	 *
	 * @param sessionKey the tab
	 * @return the thumbnail, as PNG bytes, {@code null} if it could not be made
	 */
	byte[] captureThumbnail(ViewKey sessionKey);

	/**
	 * Returns a tab's title.
	 *
	 * @param sessionKey the tab
	 * @return its title, empty if the tab is unknown
	 */
	String getTabTitle(ViewKey sessionKey);

	/**
	 * Sets a tab's title.
	 *
	 * @param sessionKey the tab
	 * @param title      its new title
	 */
	void setTabTitle(ViewKey sessionKey, String title);

	/**
	 * Shows the save dialog of a tab; once confirmed, it dispatches the tab's
	 * {@code ConfirmSaveView} action.
	 *
	 * @param sessionKey the tab to save
	 * @param form       what the dialog shows
	 */
	void showSaveViewDialog(ViewKey sessionKey, SaveViewForm form);

	/**
	 * Shows the views library over a tab's window; its actions dispatch the
	 * tab's {@code OpenView}, {@code RenameView}, {@code DeleteView}, {@code
	 * ExportView} and {@code ImportView} actions.
	 *
	 * @param sessionKey the tab
	 * @param content    what the library shows
	 */
	void showViewLibrary(ViewKey sessionKey, ViewLibraryContent content);

	/**
	 * Updates the views library if it is shown, after a view was renamed,
	 * deleted or imported.
	 *
	 * @param content what the library shows now
	 */
	void updateViewLibrary(ViewLibraryContent content);

	/**
	 * Asks the user to confirm, in a modal dialog owned by a tab's window.
	 *
	 * @param sessionKey   the tab whose window owns the dialog
	 * @param title        the dialog's title
	 * @param message      what to confirm
	 * @param confirmLabel the confirming button's label
	 * @return {@code true} if confirmed
	 */
	boolean confirm(ViewKey sessionKey, String title, String message, String confirmLabel);

	/**
	 * Shows a short, modal error dialog owned by one tab's own window -
	 * used to report a views library {@code IOException} without {@code
	 * ComponentDiagramInteractionImpl} touching any JavaFX widget
	 * itself.
	 *
	 * @param sessionKey the tab whose window owns the dialog
	 * @param message    the error message to show
	 */
	void showError(ViewKey sessionKey, String message);

	/**
	 * Sets whether hiding or showing a component relays out the graph automatically.
	 *
	 * @param autoLayoutOnHide whether toggling a component's visibility from
	 *                         any tab's component tree triggers an
	 *                         automatic relayout of that tab's graph
	 */
	void setAutoLayoutOnHide(boolean autoLayoutOnHide);

	/**
	 * Applies a new color, within the current theme's palette, to every
	 * port/wire of this kind and repaints every visible cell and
	 * edge in every open tab immediately - called from SupervisionHmi's
	 * Preferences panel.
	 *
	 * @param key   which palette entry to change
	 * @param value the new color, as {@code #rrggbbaa}; ignored when unreadable
	 */
	void setPortColor(PortColorKey key, String value);

	/**
	 * Returns one color of the current theme's palette.
	 *
	 * @param key which palette entry to read
	 * @return    that entry's color in the current theme's palette, as {@code #rrggbbaa}, used to
	 *            seed the Preferences panel's color pickers
	 */
	String getPortColor(PortColorKey key);

	/**
	 * Switches to {@code theme}'s own palette - each theme keeps its own,
	 * edited independently through {@link #setPortColor} - and repaints
	 * every visible cell and edge in every open tab.
	 *
	 * @param theme the theme to switch to
	 */
	void setTheme(SupervisorTheme theme);

	/**
	 * Returns one color of the current theme's default palette.
	 *
	 * @param key which palette entry to read
	 * @return    that entry's default color in the current theme, as {@code #rrggbbaa}
	 */
	String getDefaultPortColor(PortColorKey key);

	/**
	 * Restores the current theme's default palette and repaints every
	 * visible cell and edge in every open tab.
	 */
	void resetPortColors();
}
