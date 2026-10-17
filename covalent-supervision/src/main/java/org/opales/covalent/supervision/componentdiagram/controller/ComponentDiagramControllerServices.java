/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.controller;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.opales.covalent.supervision.diagrampersistence.SavedView;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Per-tab selection/navigation commands plus tab-lifecycle commands on the
 * component topology, provided by {@code ComponentDiagramController} and
 * used by {@code ComponentDiagramInteraction}. No separate public
 * {@code ComponentDiagramServices}: nothing outside this function calls
 * into it - {@code SequenceDiagram} is reached the other way around, by
 * this Controller calling out to {@code SequenceDiagramServices}.
 */
@Services
public interface ComponentDiagramControllerServices {

	/**
	 * Opens a brand-new tab in fresh default state (nothing selected,
	 * top-level focus path) - minted with a new session identity. Called
	 * once at this function's own {@code @Activation} for the always-open
	 * first tab, and again every time the "+ New diagram" button is
	 * clicked from any tab's own toolbar.
	 */
	void openNewSession();

	/**
	 * Discards a tab's Controller- and Model-held state for good - called
	 * once, when its tab closes.
	 *
	 * @param sessionKey the tab to discard
	 */
	void closeSession(ViewKey sessionKey);

	/**
	 * Replaces one tab's current selection wholesale.
	 *
	 * @param sessionKey     the tab whose selection to replace
	 * @param componentTypes the new selection
	 */
	void selectComponents(ViewKey sessionKey, Set<Class<?>> componentTypes);

	/**
	 * Adds or removes a single component type from one tab's current
	 * selection.
	 *
	 * @param sessionKey    the tab whose selection to toggle in
	 * @param componentType the component type to toggle
	 */
	void toggleComponentSelection(ViewKey sessionKey, Class<?> componentType);

	/**
	 * Shows or hides a component's dependency edges, in one tab.
	 *
	 * @param sessionKey    the tab this applies to
	 * @param componentType the component whose dependencies to toggle
	 * @param show          {@code true} to show them, {@code false} to hide
	 */
	void toggleDependenciesVisibility(ViewKey sessionKey, Class<?> componentType, boolean show);

	/**
	 * Starts a recording session for the component types
	 * selected in one tab, via {@code SequenceDiagramServices.startRecording(...)}.
	 *
	 * @param sessionKey the tab whose current selection to observe
	 */
	void startRecording(ViewKey sessionKey);

	/**
	 * Stops the current recording session, if any, via
	 * {@code SequenceDiagramServices.stopRecording()} - not scoped to a
	 * tab, since at most one recording is ever active across every open
	 * tab (see {@code SequenceDiagramController}'s own javadoc).
	 */
	void stopRecording();

	/**
	 * Tells whether a recording session is active.
	 *
	 * @return whether a recording session is active, whichever tab or function started it, as
	 *         {@code SequenceDiagramServices.isRecording()} says
	 */
	boolean isRecording();

	/**
	 * Enters a composite in one tab: pushes it onto that tab's drill-down
	 * focus path, clears that tab's current selection (a level change
	 * makes the prior selection and dependencies-visibility highlighting
	 * meaningless), and republishes the topology narrowed to that
	 * composite's direct children.
	 *
	 * @param sessionKey    the tab to navigate in
	 * @param compositeType the composite component type to enter
	 */
	void enterComposite(ViewKey sessionKey, Class<?> compositeType);

	/**
	 * Shows a composite's content in one tab, wherever the composite lives in the hierarchy:
	 * sets that tab's focus path to the composite's own chain of ancestors followed by the
	 * composite itself, in one republication of the topology, then selects the composite so that
	 * the inspector describes it. Does nothing for a type the container does not know.
	 *
	 * @param sessionKey    the tab to navigate in
	 * @param compositeType the composite whose content is shown
	 */
	void showComposite(ViewKey sessionKey, Class<?> compositeType);

	/**
	 * Jumps back up one tab's drill-down focus path to the given depth
	 * (0 = the top-level forest), clears that tab's current selection, and
	 * republishes the topology narrowed to that level. A breadcrumb click
	 * and the "back" gesture both go through this - the latter as
	 * {@code depth - 1} of that tab's current path.
	 *
	 * @param sessionKey the tab to navigate in
	 * @param depth      how many focus path entries to keep, clamped to
	 *                   {@code [0, that tab's current path length]}
	 */
	void exitToDepth(ViewKey sessionKey, int depth);

	/**
	 * Navigates one tab to a component that is not among its current
	 * level's children - typically the far end of a {@code
	 * ComponentDiagramExternalPort} the user just clicked. Sets that tab's
	 * focus path to that component's own parent chain (so it becomes
	 * visible as an ordinary card there, wherever it actually lives) and
	 * briefly flashes it once rendered.
	 *
	 * @param sessionKey    the tab to navigate in
	 * @param componentType the component to reveal
	 */
	void navigateToComponent(ViewKey sessionKey, Class<?> componentType);

	/**
	 * Shows the link between two components in one tab: sets its focus path
	 * to the deepest composite containing both, where each is shown as
	 * itself or as its enclosing composite, then has the View select both
	 * and highlight the links between them.
	 *
	 * @param sessionKey the tab to show the link in
	 * @param sourceType one end of the link
	 * @param targetType the other end of the link
	 */
	void showLink(ViewKey sessionKey, Class<?> sourceType, Class<?> targetType);

	/**
	 * Saves one tab as a view of the views library, for the observed
	 * container: this Controller's own focus path and selection, plus the
	 * View-only hidden components, positions and zoom that {@code
	 * ComponentDiagramInteractionImpl} gathers from the View first. The
	 * tab is then the view's: saving it again replaces it.
	 *
	 * @param sessionKey       the tab to save
	 * @param name             the view's name
	 * @param description      the view's description, empty if none
	 * @param hiddenComponents the tab's hidden components
	 * @param positions        the tab's positions, per component
	 * @param zoom             the tab's zoom scale
	 * @param thumbnailPng     the tab's thumbnail, as PNG bytes
	 * @return the saved view
	 * @throws IOException if no container is observed, or the view could not
	 *                     be written
	 */
	SavedView saveView(ViewKey sessionKey, String name, String description, Set<Class<?>> hiddenComponents,
			Map<Class<?>, double[]> positions, double zoom, byte[] thumbnailPng) throws IOException;

	/**
	 * Opens a saved view in a new tab, named after it: its focus path and
	 * selection restored, its hidden components, positions and zoom applied by
	 * the View once the tab shows its components. Components that are not in
	 * the observed container are left out.
	 *
	 * @param view the view to open
	 */
	void openView(SavedView view);

	/**
	 * Returns the view a tab was opened from or last saved to.
	 *
	 * @param sessionKey the tab
	 * @return its view, {@code null} if none
	 */
	SavedView getSessionView(ViewKey sessionKey);

	/**
	 * Records a tab's new title, given by the user. A tab renamed away from
	 * its view's name is no longer that view's: saving it proposes a new view
	 * instead of replacing that one.
	 *
	 * @param sessionKey the tab
	 * @param title      its new title
	 */
	void renameSession(ViewKey sessionKey, String title);

	/**
	 * Returns the name of the observed container.
	 *
	 * @return its name, {@code null} if none is observed
	 */
	String getObservedContainerName();

	/**
	 * Returns the components of a view that are not in the observed
	 * container; only a view of the observed container is checked.
	 *
	 * @param view the view
	 * @return the class names of its missing components, empty if none or if
	 *         the view is another container's
	 */
	Set<String> getMissingComponents(SavedView view);
}
