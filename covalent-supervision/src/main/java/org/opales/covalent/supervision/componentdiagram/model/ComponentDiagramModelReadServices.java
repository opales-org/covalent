/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.model;

import java.util.List;
import java.util.Map;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

/**
 * Reads the current state of the component diagram model. The state
 * notifications of {@link ComponentDiagramEvents} carry no state: a consumer
 * reads it here when it handles them, so it always renders the latest state,
 * whatever order the notifications arrive in.
 */
@Services
public interface ComponentDiagramModelReadServices {

	/**
	 * Returns a tab's current topology.
	 *
	 * @param sessionKey the tab
	 * @return its topology, empty if the tab is unknown
	 */
	ComponentDiagramTopology getTopology(ViewKey sessionKey);

	/**
	 * Returns the current live structure of the observed container.
	 *
	 * @return the live structure, empty if nothing is observed
	 */
	ComponentDiagramStructure getLiveStructure();

	/**
	 * Returns the current status of each component.
	 *
	 * @return the statuses, by component type
	 */
	Map<Class<?>, ComponentStatus> getComponentStatuses();

	/**
	 * Returns a tab's title.
	 *
	 * @param sessionKey the tab
	 * @return its title, {@code null} for the default one
	 */
	String getSessionTitle(ViewKey sessionKey);

	/**
	 * Returns the layout a tab starts with.
	 *
	 * @param sessionKey the tab
	 * @return its initial layout, {@code null} for none
	 */
	ComponentDiagramLayout getInitialLayout(ViewKey sessionKey);

	/**
	 * Returns a tab's focus path.
	 *
	 * @param sessionKey the tab
	 * @return its focus path, root to current, empty for the top level
	 */
	List<Class<?>> getFocusPath(ViewKey sessionKey);
}
