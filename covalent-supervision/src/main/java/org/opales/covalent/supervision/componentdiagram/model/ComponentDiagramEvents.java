/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.model;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

import org.opales.covalent.container.component.annotations.Events;

/**
 * Produced by {@code ComponentDiagramModel} and consumed by
 * {@code ComponentDiagramView}, {@code ComponentDiagramInteraction} and the
 * Cockpit's Controller (managed autowiring). Notifies of every change to one tab's topology
 * graph, selection, or focus path - the View reads what changed back from the
 * Model through {@code ComponentDiagramModelReadServices} and renders it. {@code onSessionCreated}
 * is also how the Interaction learns of a new tab to show.
 * <p>
 * {@link #onPortActivityChanged(Class, Class, ComponentPortKind, boolean)} is the
 * one notification here that is NOT scoped to a tab - see its own javadoc.
 */
@Events
public interface ComponentDiagramEvents {

	/**
	 * Fired once, when a new tab is opened - the always-open first tab
	 * included.
	 *
	 * @param sessionKey the new tab's identity
	 */
	void onSessionCreated(ViewKey sessionKey);

	/**
	 * Fired whenever one tab's topology graph is replaced wholesale: the new
	 * topology is read through
	 * {@link ComponentDiagramModelReadServices#getTopology(ViewKey)}.
	 *
	 * @param sessionKey the tab this topology belongs to
	 */
	void onTopologyChanged(ViewKey sessionKey);

	/**
	 * Fired whenever one tab's selected component set changes.
	 *
	 * @param sessionKey             the tab this selection belongs to
	 * @param selectedComponentTypes the new selection
	 */
	void onSelectionChanged(ViewKey sessionKey, Set<Class<?>> selectedComponentTypes);

	/**
	 * Fired whenever a port's live call-activity state changes, to every
	 * open tab alike - the activity belongs to the observed component
	 * itself, not to any one tab's own narrowed view of the topology, so
	 * this carries no {@code ViewKey}. A tab that does not show
	 * {@code componentType} simply finds no matching cell to flash.
	 *
	 * @param componentType the component the port belongs to
	 * @param portType      the specific services/parameters/events interface this activity
	 *                      concerns - a component showing several distinct interfaces of the
	 *                      same kind gets one port flashing per interface, not all of them
	 *                      at once for whichever happens to be busy
	 * @param portKind      the port's kind
	 * @param active        {@code true} if a call is in flight
	 *                      through this port
	 */
	void onPortActivityChanged(Class<?> componentType, Class<?> portType, ComponentPortKind portKind, boolean active);

	/**
	 * The live topology's activity over the last sampling period.
	 *
	 * @param sampleTimeMillis when the period ended
	 * @param activities per component that saw calls, ancestors included; a component absent
	 *        saw none
	 */
	void onActivitySampled(long sampleTimeMillis, Map<Class<?>, ComponentActivity> activities);

	/**
	 * The whole live structure changed: components, links, or what is
	 * excluded. It is read, not narrowed to any tab's level, through
	 * {@link ComponentDiagramModelReadServices#getLiveStructure()}.
	 */
	void onLiveStructureChanged();

	/**
	 * The components' statuses changed: they are read through
	 * {@link ComponentDiagramModelReadServices#getComponentStatuses()}.
	 */
	void onComponentStatusesChanged();

	/**
	 * Fired whenever a component's dependency edges should be shown or
	 * hidden in one tab's rendering - a transient rendering hint, not part
	 * of the topology graph itself. It carries
	 * {@code ComponentDiagramControllerServices.toggleDependenciesVisibility(...)} to the View, since a Controller-side
	 * method cannot reach it and only the Interaction talks to the View.
	 *
	 * @param sessionKey    the tab this change applies to
	 * @param componentType the component whose dependency edges to affect
	 * @param visible       {@code true} to show them, {@code false} to hide
	 */
	void onDependenciesVisibilityChanged(ViewKey sessionKey, Class<?> componentType, boolean visible);

	/**
	 * Fired whenever one tab's drill-down focus path changes - root to
	 * current, empty meaning the top-level forest. The View uses this to
	 * render that tab's breadcrumb; {@link #onTopologyChanged(ViewKey)}
	 * fires alongside it, the topology already narrowed to that focus.
	 *
	 * @param sessionKey the tab whose focus path changed
	 * @param focusPath  the new focus path
	 */
	void onFocusPathChanged(ViewKey sessionKey, List<Class<?>> focusPath);

	/**
	 * Fired right after a cross-composite navigation lands on the target's
	 * parent level in one tab, naming the component to flash -
	 * see {@code ComponentDiagramControllerServices.navigateToComponent(ViewKey, Class)}.
	 *
	 * @param sessionKey    the tab this flash applies to
	 * @param componentType the component to flash
	 */
	void onFlashComponentChanged(ViewKey sessionKey, Class<?> componentType);

	/**
	 * Fired right after a navigation lands on the level showing two linked
	 * components, naming them so that the View selects them and highlights
	 * the links between them - see {@code
	 * ComponentDiagramControllerServices.showLink(ViewKey, Class, Class)}.
	 *
	 * @param sessionKey the tab this applies to
	 * @param sourceType one end of the links, as shown at that level
	 * @param targetType the other end of the links, as shown at that level
	 */
	void onLinkHighlightRequested(ViewKey sessionKey, Class<?> sourceType, Class<?> targetType);

	/**
	 * Fired once, when a tab closes and its Model entry is discarded for
	 * good.
	 *
	 * @param sessionKey the closed tab's identity
	 */
	void onSessionClosed(ViewKey sessionKey);
}
