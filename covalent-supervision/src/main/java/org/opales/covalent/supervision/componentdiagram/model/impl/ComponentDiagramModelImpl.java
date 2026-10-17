/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.model.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.opales.covalent.supervision.componentdiagram.model.ComponentActivity;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramEvents;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramLayout;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramModel;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramModelReadServices;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramModelServices;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramStructure;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramTopology;
import org.opales.covalent.supervision.componentdiagram.model.ComponentPortKind;
import org.opales.covalent.supervision.componentdiagram.model.ComponentStatus;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;

/**
 * ComponentDiagram's Model: one entry per open tab, each holding that tab's
 * topology graph and focus path. No logic beyond storing what it is given and
 * firing the matching notification; a selection is only relayed, as a
 * notification, since the Controller is the one that keeps it.
 */
@Implementation
public class ComponentDiagramModelImpl
		implements ComponentDiagramModel, ComponentDiagramModelServices, ComponentDiagramModelReadServices {

	/** Creates an empty model, with no tab and an empty live structure. */
	public ComponentDiagramModelImpl() {
		// Nothing to initialize.
	}

	@ProducedEvents
	private ComponentDiagramEvents produceComponentDiagramEvents;

	private static final ComponentDiagramTopology EMPTY_TOPOLOGY = new ComponentDiagramTopology(Set.of(), Set.of(),
			Set.of(), Set.of(), Set.of(), Map.of());

	private final Map<ViewKey, ComponentDiagramTopology> topologyBySession = new ConcurrentHashMap<>();

	private final Map<ViewKey, List<Class<?>>> focusPathBySession = new ConcurrentHashMap<>();

	private volatile ComponentDiagramStructure liveStructure = new ComponentDiagramStructure(Set.of(), Set.of(),
			Set.of());

	private volatile Map<Class<?>, ComponentStatus> componentStatuses = Map.of();

	private final Map<ViewKey, String> titleBySession = new ConcurrentHashMap<>();

	private final Map<ViewKey, ComponentDiagramLayout> initialLayoutBySession = new ConcurrentHashMap<>();

	@Override
	public String getSessionTitle(final ViewKey sessionKey) {
		return this.titleBySession.get(sessionKey);
	}

	@Override
	public List<Class<?>> getFocusPath(final ViewKey sessionKey) {
		return this.focusPathBySession.getOrDefault(sessionKey, List.of());
	}

	@Override
	public ComponentDiagramLayout getInitialLayout(final ViewKey sessionKey) {
		return this.initialLayoutBySession.get(sessionKey);
	}

	@Override
	public void createSession(final ViewKey sessionKey, final String title, final ComponentDiagramLayout layout) {
		if (title != null) {
			this.titleBySession.put(sessionKey, title);
		}
		if (layout != null) {
			this.initialLayoutBySession.put(sessionKey, layout);
		}
		this.topologyBySession.put(sessionKey, EMPTY_TOPOLOGY);
		this.focusPathBySession.put(sessionKey, List.of());
		this.produceComponentDiagramEvents.onSessionCreated(sessionKey);
	}

	@Override
	public void setTopology(final ViewKey sessionKey, final ComponentDiagramTopology topology) {
		this.topologyBySession.put(sessionKey, topology);
		this.produceComponentDiagramEvents.onTopologyChanged(sessionKey);
	}

	@Override
	public ComponentDiagramTopology getTopology(final ViewKey sessionKey) {
		return this.topologyBySession.getOrDefault(sessionKey, EMPTY_TOPOLOGY);
	}

	@Override
	public ComponentDiagramStructure getLiveStructure() {
		return this.liveStructure;
	}

	@Override
	public Map<Class<?>, ComponentStatus> getComponentStatuses() {
		return this.componentStatuses;
	}

	@Override
	public void setSelectedComponents(final ViewKey sessionKey, final Set<Class<?>> componentTypes) {
		this.produceComponentDiagramEvents.onSelectionChanged(sessionKey, componentTypes);
	}

	@Override
	public void setPortActivity(final Class<?> componentType, final Class<?> portType, final ComponentPortKind portKind,
			final boolean active) {
		this.produceComponentDiagramEvents.onPortActivityChanged(componentType, portType, portKind, active);
	}

	@Override
	public void setActivity(final long sampleTimeMillis, final Map<Class<?>, ComponentActivity> activities) {
		this.produceComponentDiagramEvents.onActivitySampled(sampleTimeMillis, Map.copyOf(activities));
	}

	@Override
	public void setLiveStructure(final ComponentDiagramStructure structure) {
		this.liveStructure = structure;
		this.produceComponentDiagramEvents.onLiveStructureChanged();
	}

	@Override
	public void setComponentStatuses(final Map<Class<?>, ComponentStatus> statuses) {
		this.componentStatuses = Map.copyOf(statuses);
		this.produceComponentDiagramEvents.onComponentStatusesChanged();
	}

	@Override
	public void setDependenciesVisibility(final ViewKey sessionKey, final Class<?> componentType, final boolean visible) {
		this.produceComponentDiagramEvents.onDependenciesVisibilityChanged(sessionKey, componentType, visible);
	}

	@Override
	public void setFocusPath(final ViewKey sessionKey, final List<Class<?>> focusPath) {
		this.focusPathBySession.put(sessionKey, focusPath);
		this.produceComponentDiagramEvents.onFocusPathChanged(sessionKey, focusPath);
	}

	@Override
	public void setFlashComponent(final ViewKey sessionKey, final Class<?> componentType) {
		this.produceComponentDiagramEvents.onFlashComponentChanged(sessionKey, componentType);
	}

	@Override
	public void setHighlightedLink(final ViewKey sessionKey, final Class<?> sourceType, final Class<?> targetType) {
		this.produceComponentDiagramEvents.onLinkHighlightRequested(sessionKey, sourceType, targetType);
	}

	@Override
	public void closeSession(final ViewKey sessionKey) {
		this.topologyBySession.remove(sessionKey);
		this.focusPathBySession.remove(sessionKey);
		this.titleBySession.remove(sessionKey);
		this.initialLayoutBySession.remove(sessionKey);
		this.produceComponentDiagramEvents.onSessionClosed(sessionKey);
	}
}
