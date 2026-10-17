/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Records every open tab's current topology graph and focus path, plus the
 * observed container's live port-activity state, provided by {@code
 * ComponentDiagramModel} and used by {@code ComponentDiagramController}. The Model carries no logic beyond storing
 * what it is given and firing the matching {@link ComponentDiagramEvents}
 * notification.
 * <p>
 * Port activity is the one thing here that is NOT per tab: it is a live
 * property of the observed component itself, the same regardless of how
 * many tabs happen to show it - see {@link
 * #setPortActivity(Class, Class, ComponentPortKind, boolean)}.
 */
@Services
public interface ComponentDiagramModelServices {

	/**
	 * Creates a tab.
	 *
	 * @param sessionKey the new tab's identity
	 * @param title      its title, {@code null} for the default one
	 * @param layout     the layout it starts with, {@code null} for none
	 */
	void createSession(ViewKey sessionKey, String title, ComponentDiagramLayout layout);

	/**
	 * Replaces one tab's topology graph wholesale, and fires
	 * {@link ComponentDiagramEvents#onTopologyChanged(ViewKey)}.
	 *
	 * @param sessionKey the tab whose topology to replace
	 * @param topology   the new topology
	 */
	void setTopology(ViewKey sessionKey, ComponentDiagramTopology topology);

	/**
	 * Announces one tab's new selected component set, with
	 * {@link ComponentDiagramEvents#onSelectionChanged(ViewKey, Set)}; the Model does not keep it.
	 *
	 * @param sessionKey     the tab whose selection to replace
	 * @param componentTypes the new selection
	 */
	void setSelectedComponents(ViewKey sessionKey, Set<Class<?>> componentTypes);

	/**
	 * Records a port's live call-activity state, and fires
	 * {@link ComponentDiagramEvents#onPortActivityChanged(Class, Class, ComponentPortKind, boolean)}
	 * to every open tab alike - not scoped to one, since the activity
	 * belongs to the observed component, not to any one tab's own view of
	 * it.
	 *
	 * @param componentType the component the port belongs to
	 * @param portType      the specific services/parameters/events interface this activity
	 *                      concerns
	 * @param portKind      the port's kind
	 * @param active        {@code true} if a call is in flight
	 *                      through this port
	 */
	void setPortActivity(Class<?> componentType, Class<?> portType, ComponentPortKind portKind, boolean active);

	/**
	 * Records the status badge of every component of the observed
	 * container, and fires
	 * {@link ComponentDiagramEvents#onComponentStatusesChanged()} to every
	 * open tab alike - like port activity, a status belongs to the observed
	 * component, not to any one tab.
	 *
	 * @param statuses every component's status badge
	 */
	void setComponentStatuses(Map<Class<?>, ComponentStatus> statuses);

	/**
	 * Publishes the live topology's activity over the last sampling period, to every tab, via
	 * {@link ComponentDiagramEvents#onActivitySampled(long, Map)}.
	 *
	 * @param sampleTimeMillis when the period ended
	 * @param activities per component that saw calls
	 */
	void setActivity(long sampleTimeMillis, Map<Class<?>, ComponentActivity> activities);

	/**
	 * Publishes the whole live structure, via
	 * {@link ComponentDiagramEvents#onLiveStructureChanged()}.
	 *
	 * @param structure the structure, not narrowed to any tab's level
	 */
	void setLiveStructure(ComponentDiagramStructure structure);

	/**
	 * Forwards a dependencies-visibility change for one tab, and fires
	 * {@link ComponentDiagramEvents#onDependenciesVisibilityChanged(ViewKey, Class, boolean)}.
	 * See that method's javadoc for why this exists.
	 *
	 * @param sessionKey    the tab this change applies to
	 * @param componentType the component whose dependency edges to affect
	 * @param visible       {@code true} to show them, {@code false} to hide
	 */
	void setDependenciesVisibility(ViewKey sessionKey, Class<?> componentType, boolean visible);

	/**
	 * Replaces one tab's drill-down focus path wholesale (root to current,
	 * empty meaning the top-level forest), and fires
	 * {@link ComponentDiagramEvents#onFocusPathChanged(ViewKey, List)}.
	 *
	 * @param sessionKey the tab whose focus path to replace
	 * @param focusPath  the new focus path
	 */
	void setFocusPath(ViewKey sessionKey, List<Class<?>> focusPath);

	/**
	 * Marks one component in one tab for a brief one-off highlight flash,
	 * and fires {@link ComponentDiagramEvents#onFlashComponentChanged(ViewKey, Class)}.
	 * Used right after {@link #setFocusPath(ViewKey, List)} publishes the
	 * level a cross-composite navigation jumped to, so the View can flash
	 * the component the user actually navigated to once it is on screen -
	 * not part of the topology itself, same spirit as {@link
	 * #setDependenciesVisibility(ViewKey, Class, boolean)}'s transient
	 * rendering hint.
	 *
	 * @param sessionKey    the tab this flash applies to
	 * @param componentType the component to flash
	 */
	void setFlashComponent(ViewKey sessionKey, Class<?> componentType);

	/**
	 * Asks one tab to select two components and highlight the links between
	 * them, and fires {@link ComponentDiagramEvents#onLinkHighlightRequested(ViewKey, Class, Class)}.
	 * A transient rendering hint, like {@link #setFlashComponent(ViewKey, Class)}.
	 *
	 * @param sessionKey the tab this applies to
	 * @param sourceType one end of the links
	 * @param targetType the other end of the links
	 */
	void setHighlightedLink(ViewKey sessionKey, Class<?> sourceType, Class<?> targetType);

	/**
	 * Discards a tab's entry for good, and fires
	 * {@link ComponentDiagramEvents#onSessionClosed(ViewKey)}.
	 *
	 * @param sessionKey the tab to discard
	 */
	void closeSession(ViewKey sessionKey);
}
