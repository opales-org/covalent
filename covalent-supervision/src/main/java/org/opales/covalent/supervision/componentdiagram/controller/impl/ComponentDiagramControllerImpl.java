/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.controller.impl;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.opales.covalent.supervision.SupervisionThreads;
import org.opales.covalent.container.events.impl.ConsumerInvocationHandler;
import org.opales.covalent.container.events.impl.ConsumerMetadata;
import org.opales.covalent.container.component.ComponentAnnotations;
import org.opales.covalent.container.metamodel.metamodel.ReadOnlyComponentMetamodel;
import org.opales.covalent.supervision.observed.ObservedContainerConnectionEvents;
import org.opales.covalent.supervision.observed.CallOutcome;
import org.opales.covalent.supervision.observed.ObservedEventsManagerEvents;
import org.opales.covalent.supervision.observed.ObservedEventsManagerServices;
import org.opales.covalent.supervision.observed.ObservedLifecycleEvents;
import org.opales.covalent.supervision.observed.ObservedLifecycleReadServices;
import org.opales.covalent.supervision.observed.ObservedMetamodelReadServices;
import org.opales.covalent.supervision.observed.ObservedParametersManagerEvents;
import org.opales.covalent.supervision.observed.ObservedParametersManagerServices;
import org.opales.covalent.supervision.observed.ObservedPortActivityEvents;
import org.opales.covalent.supervision.observed.ObservedPortRate;
import org.opales.covalent.supervision.observed.ObservedPortRateEvents;
import org.opales.covalent.supervision.observed.ObservedPortKind;
import org.opales.covalent.supervision.observed.ObservedRegistryEvents;
import org.opales.covalent.supervision.observed.ObservedRegistryReadServices;
import org.opales.covalent.supervision.observed.ObservedServicesManagerEvents;
import org.opales.covalent.supervision.observed.ObservedServicesManagerServices;
import org.opales.covalent.supervision.componentdiagram.controller.ComponentDiagramController;
import org.opales.covalent.supervision.componentdiagram.controller.ComponentDiagramControllerServices;
import org.opales.covalent.supervision.componentdiagram.interaction.ComponentDiagramSessionKey;
import org.opales.covalent.supervision.componentdiagram.model.ComponentActivity;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramEdge;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramLayout;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramExternalPort;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramModelServices;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramSnapshot;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramStructure;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramTopology;
import org.opales.covalent.supervision.componentdiagram.model.ComponentPortKind;
import org.opales.covalent.supervision.componentdiagram.model.ComponentPortRole;
import org.opales.covalent.supervision.componentdiagram.model.ComponentStatus;
import org.opales.covalent.supervision.exclusionpolicy.ExclusionCascade;
import org.opales.covalent.supervision.exclusionpolicy.controller.ExclusionParameters;
import org.opales.covalent.supervision.exclusionpolicy.model.ExclusionPolicyEvents;
import org.opales.covalent.supervision.stereotypepolicy.MetamodelStereotypes;
import org.opales.covalent.supervision.stereotypepolicy.StereotypeRule;
import org.opales.covalent.supervision.stereotypepolicy.controller.StereotypeParameters;
import org.opales.covalent.supervision.stereotypepolicy.model.StereotypePolicyEvents;
import org.opales.covalent.container.component.internals.Internal;
import org.opales.covalent.supervision.containermanager.raw.ObservedContainerControl;
import org.opales.covalent.supervision.diagrampersistence.SavedView;
import org.opales.covalent.supervision.diagrampersistence.controller.DiagramPersistenceServices;
import org.opales.covalent.supervision.sequencediagram.controller.SequenceDiagramServices;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

import org.opales.covalent.container.component.annotations.AccessedParameters;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;

/**
 * ComponentDiagram's Controller: rebuilds the topology graph from the
 * registry/metamodel/services/parameters/events Services of the observed
 * container, filtered through {@link ExclusionParameters}, and turns live
 * port-activity, port-rate and lifecycle notifications into activity and
 * status updates. The observed container is reached only through the
 * {@code Observed*} mirror Services and Events, injected like any other
 * dependency of this composite.
 * <p>
 * The topology/edges rebuilt by {@code rebuildTopology()} - the observed
 * container's actual live structure - is the same regardless of how many
 * tabs are open: what differs per tab is only the drill-down {@code
 * focusPath} and selection narrowing that shared structure down to what one
 * tab shows. So every topology-changing notification below
 * republishes the narrowed view to EVERY open tab, not just one - this is
 * what keeps every tab's canvas in sync with live registry/services/
 * parameters/events/exclusion-policy/container-connection changes.
 * <p>
 * This Controller has no rendering to debounce and simply rebuilds on every
 * topology-changing notification.
 * <p>
 * Only actually registered, non-excluded component types become topology
 * nodes: a service user or parameters accessor that is not registered gets
 * no placeholder node.
 */
@Implementation
public class ComponentDiagramControllerImpl implements ComponentDiagramController, ComponentDiagramControllerServices,
		ObservedRegistryEvents, ObservedServicesManagerEvents, ObservedParametersManagerEvents, ObservedEventsManagerEvents,
		ObservedPortActivityEvents, ObservedPortRateEvents, ObservedLifecycleEvents,
		ObservedContainerConnectionEvents, ExclusionPolicyEvents, StereotypePolicyEvents {

	/** Creates the controller; its collaborators are injected by the container. */
	public ComponentDiagramControllerImpl() {
		// Nothing to initialize.
	}

	@UsedServices
	private ObservedRegistryReadServices registryReadServices;

	@UsedServices
	private ObservedMetamodelReadServices metamodelReadServices;

	@UsedServices
	private ObservedServicesManagerServices servicesManagerServices;

	@UsedServices
	private ObservedParametersManagerServices parametersManagerServices;

	@UsedServices
	private ObservedEventsManagerServices eventsManagerServices;

	@UsedServices
	private ObservedLifecycleReadServices lifecycleReadServices;

	@AccessedParameters
	private ExclusionParameters accessExclusionParameters;

	@AccessedParameters
	private StereotypeParameters accessStereotypeParameters;

	@UsedServices
	private ComponentDiagramModelServices useComponentDiagramModelServices;

	@UsedServices
	private SequenceDiagramServices useSequenceDiagramServices;

	@UsedServices
	private DiagramPersistenceServices useDiagramPersistenceServices;

	@UsedServices
	private ObservedContainerControl observedContainerControl;

	/**
	 * This Controller's own working copy of every open tab's selection - this
	 * Controller never reads the Model. A tab's entry only exists
	 * between {@link #openNewSession()} and {@link #closeSession(ViewKey)}.
	 */
	private final Map<ViewKey, Set<Class<?>>> selectedComponentTypesBySession = new ConcurrentHashMap<>();

	/**
	 * This Controller's own working copy of every open tab's drill-down
	 * focus path, root to current; empty means the top-level forest (every
	 * component with no composing parent). Wholesale-replaced on
	 * navigation, like the Model's own copy - never mutated in place.
	 */
	private final Map<ViewKey, List<Class<?>>> focusPathBySession = new ConcurrentHashMap<>();

	/** The view each tab was opened from or last saved to. */
	private final Map<ViewKey, SavedView> viewBySession = new ConcurrentHashMap<>();

	// Vertical mode wires this Controller's own output ports in preInitialize,
	// in a fixed order (used services, then consumed events, then used
	// subscribers, then accessed parameters - see VerticalLifecycleTransitions),
	// and each port's creation self-notifies through the very
	// RegistryEvents/ServicesManagerEvents/ParametersManagerEvents/
	// EventsManagerEvents callbacks this Controller consumes to react to
	// OTHER components' wiring changes. So onEventsSubscribed/
	// onParametersAccessed/etc. below already fire for this Controller's own
	// ports as they come up one by one, well before accessExclusionParameters
	// (the last one wired) is set - rebuildTopology() must not run off that
	// premature, partial self-notification. activated guards it: false until
	// this Controller's own @Activation runs (after every port is wired), so
	// every rebuildTopology() call arriving before then is a no-op.
	// NOTE: this Controller must NOT open the first session itself here, even
	// though that would seem like the natural place for it: vertical-mode
	// activation runs ComponentDiagram's four parts one at a time, fully
	// activating each before the next begins, in this composite's own
	// declaration order (Model, Controller, View, Interaction - see
	// ComponentDiagramImpl). Opening a session fires
	// ComponentDiagramEvents#onSessionCreated through the Model, and a
	// consumed-events port only auto-subscribes during ITS OWN component's
	// preInitialize (see VerticalLifecycleTransitions#preInitialize) - so if
	// this Controller opened the first session from its own @Activation,
	// View and Interaction (activated after it) would not have
	// subscribed yet and would silently miss the event: no tab, no error.
	// ComponentDiagramInteractionImpl#registerPreferenceActions() is
	// what opens the first session instead, since it activates last (it
	// @UsedServices both this Controller and the View).
	private volatile boolean activated;

	/** Serializes the writes of the topologies and the live structure in the model. */
	private final Object topologyLock = new Object();

	/**
	 * The shared, cached "live topology" - every non-excluded registered
	 * component type and every composition/service/parameter/event edge
	 * between them, exactly as {@link #rebuildTopology()} last computed it
	 * from the observed container. Narrowed per tab by {@link #focusTopology}
	 * without ever being recomputed for that narrowing alone - see {@link
	 * #renarrowSession(ViewKey)} and this class's own javadoc on why a
	 * single tab's own navigation must not touch this.
	 */
	private Set<Class<?>> cachedComponentTypes = Set.of();

	/** @see #cachedComponentTypes */
	private Set<ComponentDiagramEdge> cachedEdges = Set.of();

	/**
	 * Serializes every port-activity and lifecycle-state update, in arrival
	 * order, without any lock: those updates call other components'
	 * services, and a lock held across such a call can deadlock with a
	 * container starting on another thread. Its fields below are only ever
	 * touched from it. Exists from this Controller's activation to its
	 * passivation; updates arriving outside that span are dropped.
	 */
	private volatile ExecutorService liveStateWorker;

	/** Live activity count per component and port kind, ancestors included - see {@link #onPortActivityChanged}. */
	private final Map<ActivityKey, Integer> activityCounts = new HashMap<>();

	/** Last known lifecycle state of every component of the live topology. */
	private final Map<Class<?>, ComponentState> componentStates = new HashMap<>();

	/** The composition edges {@link #componentStates} was last read against. */
	private Set<ComponentDiagramEdge> statusEdges = Set.of();

	/** Composition ancestry per component, root first - reset with the topology. */
	private final Map<Class<?>, List<Class<?>>> ancestryCache = new ConcurrentHashMap<>();

	/** Shuts down the live-state worker thread when the component is passivated; later live updates are dropped. */
	@Passivation
	public void stopLiveStateWorker() {
		ExecutorService worker = this.liveStateWorker;
		this.liveStateWorker = null;
		if (worker != null) {
			worker.shutdownNow();
		}
	}

	private void onLiveStateWorker(final Runnable update) {
		ExecutorService worker = this.liveStateWorker;
		if (worker != null) {
			worker.execute(() -> {
				try {
					update.run();
				} catch (IllegalStateException e) {
					// The services it reads are already invalidated, as part of the container-stop cascade:
					// the update is dropped.
				}
			});
		}
	}

	/**
	 * Starts the single daemon thread that applies live activity and status updates, then marks the controller
	 * as activated, which lets topology rebuilds run.
	 */
	@Activation
	public void activate() {
		this.liveStateWorker = Executors.newSingleThreadExecutor(SupervisionThreads.factory("component-diagram-live-state"));
		this.activated = true;
	}

	// --- ComponentDiagramControllerServices: tab lifecycle ---

	@Override
	public void openNewSession() {
		ViewKey sessionKey = new ComponentDiagramSessionKey();
		this.selectedComponentTypesBySession.put(sessionKey, Set.of());
		this.focusPathBySession.put(sessionKey, List.of());
		this.useComponentDiagramModelServices.createSession(sessionKey, null, null);
		this.useComponentDiagramModelServices.setFocusPath(sessionKey, List.of());
		this.rebuildTopology();
	}

	@Override
	public void closeSession(final ViewKey sessionKey) {
		this.viewBySession.remove(sessionKey);
		this.selectedComponentTypesBySession.remove(sessionKey);
		this.focusPathBySession.remove(sessionKey);
		this.useComponentDiagramModelServices.closeSession(sessionKey);
	}

	// --- ComponentDiagramControllerServices: per-tab selection/navigation ---

	@Override
	public void selectComponents(final ViewKey sessionKey, final Set<Class<?>> componentTypes) {
		this.selectedComponentTypesBySession.put(sessionKey, new LinkedHashSet<>(componentTypes));
		this.useComponentDiagramModelServices.setSelectedComponents(sessionKey, Set.copyOf(componentTypes));
	}

	@Override
	public void toggleComponentSelection(final ViewKey sessionKey, final Class<?> componentType) {
		Set<Class<?>> updated = new LinkedHashSet<>(this.selectedComponentTypesBySession.getOrDefault(sessionKey, Set.of()));
		if (!updated.remove(componentType)) {
			updated.add(componentType);
		}
		this.selectComponents(sessionKey, updated);
	}

	@Override
	public void toggleDependenciesVisibility(final ViewKey sessionKey, final Class<?> componentType, final boolean show) {
		this.useComponentDiagramModelServices.setDependenciesVisibility(sessionKey, componentType, show);
	}

	@Override
	public void enterComposite(final ViewKey sessionKey, final Class<?> compositeType) {
		List<Class<?>> newPath = new ArrayList<>(this.focusPathBySession.getOrDefault(sessionKey, List.of()));
		newPath.add(compositeType);
		this.setFocusPathAndRebuild(sessionKey, List.copyOf(newPath));
	}

	@Override
	public void showComposite(final ViewKey sessionKey, final Class<?> compositeType) {
		Class<?> resolvedType = this.componentType(compositeType);
		if (resolvedType == null) {
			return;
		}
		List<Class<?>> path = new ArrayList<>(this.parentPathOf(resolvedType));
		path.add(resolvedType);
		this.setFocusPathAndRebuild(sessionKey, List.copyOf(path));
		this.selectComponents(sessionKey, Set.of(resolvedType));
	}

	@Override
	public void exitToDepth(final ViewKey sessionKey, final int depth) {
		List<Class<?>> currentPath = this.focusPathBySession.getOrDefault(sessionKey, List.of());
		int clamped = Math.max(0, Math.min(depth, currentPath.size()));
		this.setFocusPathAndRebuild(sessionKey, List.copyOf(currentPath.subList(0, clamped)));
	}

	@Override
	public void navigateToComponent(final ViewKey sessionKey, final Class<?> componentType) {
		Class<?> resolvedType = this.componentType(componentType);
		if (resolvedType == null) {
			return;
		}
		this.setFocusPathAndRebuild(sessionKey, this.parentPathOf(resolvedType));
		this.useComponentDiagramModelServices.setFlashComponent(sessionKey, resolvedType);
	}

	@Override
	public void showLink(final ViewKey sessionKey, final Class<?> sourceType, final Class<?> targetType) {
		Class<?> source = this.componentType(sourceType);
		Class<?> target = this.componentType(targetType);
		if (source == null || target == null) {
			return;
		}
		List<Class<?>> sourceChain = new ArrayList<>(this.parentPathOf(source));
		sourceChain.add(source);
		List<Class<?>> targetChain = new ArrayList<>(this.parentPathOf(target));
		targetChain.add(target);
		int shared = 0;
		while (shared < sourceChain.size() && shared < targetChain.size()
				&& sourceChain.get(shared).equals(targetChain.get(shared))) {
			shared++;
		}
		// One end containing the other: show the container among its siblings.
		if (shared == sourceChain.size() || shared == targetChain.size()) {
			shared--;
		}
		if (shared < 0) {
			return;
		}
		this.setFocusPathAndRebuild(sessionKey, List.copyOf(sourceChain.subList(0, shared)));
		this.useComponentDiagramModelServices.setHighlightedLink(sessionKey, sourceChain.get(shared),
				targetChain.get(shared));
	}

	/**
	 * Returns the composing parent of a component type, read from its live metamodel.
	 *
	 * @param componentType the component type to look up
	 * @return its immediate parent, or {@code null} for a top-level or unknown component
	 */
	private Class<?> containerOf(final Class<?> componentType) {
		ReadOnlyComponentMetamodel metamodel = this.metamodelReadServices.readMetamodel(componentType);
		ReadOnlyComponentMetamodel parent = metamodel == null ? null : metamodel.getParentMetamodel();
		return parent == null ? null : parent.getComponentType();
	}

	/**
	 * Walks {@code resolvedType}'s own composition ancestry, root to
	 * immediate parent, via {@code ReadOnlyComponentMetamodel.getParentMetamodel()}
	 * directly - not {@link #focusTopology}'s edge-derived {@code parentOf}
	 * map, which only ever covers one tab's focused level. A
	 * target reached through a {@code ComponentDiagramExternalPort} is by
	 * definition outside that tab's current focus, so its ancestry has to
	 * come from the live metamodel instead, exactly like {@link
	 * #collectComposition} does for the full topology.
	 *
	 * @param resolvedType the component type to look up
	 * @return its ancestors from the root down to its immediate parent; empty for a top-level component
	 */
	private List<Class<?>> parentPathOf(final Class<?> resolvedType) {
		List<Class<?>> path = new ArrayList<>();
		ReadOnlyComponentMetamodel metamodel = this.metamodelReadServices.readMetamodel(resolvedType);
		ReadOnlyComponentMetamodel parent = metamodel == null ? null : metamodel.getParentMetamodel();
		while (parent != null) {
			path.add(0, parent.getComponentType());
			parent = parent.getParentMetamodel();
		}
		return List.copyOf(path);
	}

	/**
	 * Shared tail of {@link #enterComposite(ViewKey, Class)}/{@link
	 * #exitToDepth(ViewKey, int)}: a level change makes that tab's prior
	 * selection and dependencies-visibility highlighting meaningless, so
	 * both are cleared before the new level's topology is published. Only
	 * {@code sessionKey}'s own focus actually changed - a purely local,
	 * per-tab navigation gesture, not a change to the observed application
	 * or the exclusion policy - so this re-narrows the already-cached shared
	 * topology via {@link #renarrowSession(ViewKey)} and republishes only to
	 * {@code sessionKey}; it must never call {@link #rebuildTopology()} or
	 * touch any other open tab's state (see class javadoc).
	 */
	private void setFocusPathAndRebuild(final ViewKey sessionKey, final List<Class<?>> newFocusPath) {
		this.focusPathBySession.put(sessionKey, newFocusPath);
		this.useComponentDiagramModelServices.setFocusPath(sessionKey, newFocusPath);
		this.selectComponents(sessionKey, Set.of());
		this.renarrowSession(sessionKey);
	}

	@Override
	public void startRecording(final ViewKey sessionKey) {
		Set<Class<?>> selection = this.selectedComponentTypesBySession.getOrDefault(sessionKey, Set.of());
		this.useSequenceDiagramServices.startRecording(Set.copyOf(selection));
	}

	@Override
	public void stopRecording() {
		this.useSequenceDiagramServices.stopRecording();
	}

	@Override
	public boolean isRecording() {
		return this.useSequenceDiagramServices.isRecording();
	}

	// --- ComponentDiagramControllerServices: views library ---

	@Override
	public SavedView saveView(final ViewKey sessionKey, final String name, final String description,
			final Set<Class<?>> hiddenComponents, final Map<Class<?>, double[]> positions, final double zoom,
			final byte[] thumbnailPng) throws IOException {
		String containerName = this.getObservedContainerName();
		if (containerName == null) {
			throw new IOException("No container observed: the view cannot be saved.");
		}
		ComponentDiagramSnapshot snapshot = new ComponentDiagramSnapshot(
				names(this.focusPathBySession.getOrDefault(sessionKey, List.of())), Set.copyOf(names(hiddenComponents)),
				namedPositions(positions),
				Set.copyOf(names(this.selectedComponentTypesBySession.getOrDefault(sessionKey, Set.of()))), zoom);
		SavedView view = this.useDiagramPersistenceServices.saveView(containerName, name, description, snapshot,
				thumbnailPng);
		this.viewBySession.put(sessionKey, view);
		return view;
	}

	@Override
	public void openView(final SavedView view) {
		ComponentDiagramSnapshot snapshot = view.snapshot();
		List<Class<?>> focusPath = new ArrayList<>();
		for (String className : snapshot.focusPath()) {
			Class<?> resolved = this.resolve(className);
			if (resolved == null) {
				// A broken link partway down the path: the levels past it can no longer be
				// trusted to exist as saved, so the tab opens one level short of it.
				break;
			}
			focusPath.add(resolved);
		}
		Map<Class<?>, double[]> positions = new LinkedHashMap<>();
		for (Entry<String, double[]> entry : snapshot.positions().entrySet()) {
			Class<?> resolved = this.resolve(entry.getKey());
			if (resolved != null) {
				positions.put(resolved, entry.getValue());
			}
		}
		ComponentDiagramLayout layout = new ComponentDiagramLayout(Set.copyOf(this.resolveAll(snapshot.hiddenComponents())),
				Map.copyOf(positions), snapshot.zoom());

		ViewKey sessionKey = new ComponentDiagramSessionKey();
		this.selectedComponentTypesBySession.put(sessionKey, Set.of());
		this.focusPathBySession.put(sessionKey, List.copyOf(focusPath));
		this.viewBySession.put(sessionKey, view);
		this.useComponentDiagramModelServices.createSession(sessionKey, view.name(), layout);
		this.useComponentDiagramModelServices.setFocusPath(sessionKey, List.copyOf(focusPath));
		this.rebuildTopology();
		this.selectComponents(sessionKey, this.resolveAll(snapshot.selection()));
	}

	@Override
	public SavedView getSessionView(final ViewKey sessionKey) {
		return this.viewBySession.get(sessionKey);
	}

	@Override
	public void renameSession(final ViewKey sessionKey, final String title) {
		SavedView view = this.viewBySession.get(sessionKey);
		if (view != null && !view.name().equals(title)) {
			this.viewBySession.remove(sessionKey);
		}
	}

	@Override
	public String getObservedContainerName() {
		return this.observedContainerControl.getObservedContainerName();
	}

	@Override
	public Set<String> getMissingComponents(final SavedView view) {
		if (!view.readable() || !view.containerName().equals(this.getObservedContainerName())) {
			return Set.of();
		}
		Set<String> present = new LinkedHashSet<>();
		for (Class<?> componentType : this.registryReadServices.getInstances().keySet()) {
			present.add(componentType.getName());
		}
		Set<String> missing = new LinkedHashSet<>(view.snapshot().componentNames());
		missing.removeAll(present);
		return missing;
	}

	private static List<String> names(final Collection<Class<?>> types) {
		List<String> names = new ArrayList<>();
		for (Class<?> type : types) {
			names.add(type.getName());
		}
		return List.copyOf(names);
	}

	private static Map<String, double[]> namedPositions(final Map<Class<?>, double[]> positions) {
		Map<String, double[]> named = new LinkedHashMap<>();
		for (Entry<Class<?>, double[]> entry : positions.entrySet()) {
			named.put(entry.getKey().getName(), entry.getValue());
		}
		return named;
	}

	private Set<Class<?>> resolveAll(final Collection<String> classNames) {
		Set<Class<?>> resolved = new LinkedHashSet<>();
		for (String className : classNames) {
			Class<?> type = this.resolve(className);
			if (type != null) {
				resolved.add(type);
			}
		}
		return resolved;
	}

	/** Resolves a saved class name, {@code null} for a type that no longer exists. */
	private Class<?> resolve(final String className) {
		try {
			return Class.forName(className, false, ComponentDiagramControllerImpl.class.getClassLoader());
		} catch (ClassNotFoundException | LinkageError e) {
			return null;
		}
	}

	// --- Topology rebuild ---

	/**
	 * Recomputes the shared "live topology" from the observed container's
	 * current registry/metamodel/services/parameters/events state, filtered
	 * through the exclusion policy, caches it in {@link #cachedComponentTypes}/
	 * {@link #cachedEdges}, and republishes the narrowed view to EVERY open
	 * tab via {@link #republishAllSessions()} - because the underlying data
	 * genuinely changed for everyone. Called only from the genuinely
	 * external-change callbacks below (container connection, exclusion
	 * policy, registry/services/parameters/events) - never from a single
	 * tab's own navigation, which goes through {@link #renarrowSession(ViewKey)}
	 * instead (see class javadoc and {@link #setFocusPathAndRebuild}).
	 * <p>
	 * Each rebuild reads the observed container's current state and writes
	 * the result in the model under {@link #topologyLock}: a rebuild started
	 * before another one cannot write its older result after it, so the model
	 * always ends with the latest state, whatever thread each rebuild runs on.
	 */
	private void rebuildTopology() {
		synchronized (this.topologyLock) {
			this.rebuildTopologyLocked();
		}
	}

	private void rebuildTopologyLocked() {
		if (!this.activated) {
			return;
		}
		this.ancestryCache.clear();
		Set<Class<?>> componentTypes = new LinkedHashSet<>();
		for (Class<?> registeredType : this.registryReadServices.getInstances().keySet()) {
			if (!ExclusionCascade.isExcluded(registeredType, this.accessExclusionParameters, this::containerOf)) {
				componentTypes.add(registeredType);
			}
		}

		Set<ComponentDiagramEdge> edges = new LinkedHashSet<>();
		this.collectComposition(componentTypes, edges);
		this.collectServices(componentTypes, edges);
		this.collectParameters(componentTypes, edges);
		this.collectEvents(componentTypes, edges);

		this.cachedComponentTypes = componentTypes;
		this.cachedEdges = edges;
		this.republishAllSessions();
		this.dropVanishedSelections(componentTypes);
		Set<Class<?>> rawTypes = new LinkedHashSet<>();
		for (Class<?> componentType : componentTypes) {
			if (this.isRaw(componentType)) {
				rawTypes.add(componentType);
			}
		}
		this.useComponentDiagramModelServices.setLiveStructure(
				new ComponentDiagramStructure(componentTypes, edges, rawTypes));
		this.refreshComponentStatuses(componentTypes);
	}

	/**
	 * Removes from every tab's selection the component types that are no longer in the live
	 * topology (excluded, or gone from the observed container), so that nothing keeps showing a
	 * component the diagram no longer has.
	 */
	private void dropVanishedSelections(final Set<Class<?>> liveTypes) {
		for (ViewKey sessionKey : Set.copyOf(this.selectedComponentTypesBySession.keySet())) {
			Set<Class<?>> selection = this.selectedComponentTypesBySession.getOrDefault(sessionKey, Set.of());
			Set<Class<?>> kept = new LinkedHashSet<>(selection);
			if (kept.retainAll(liveTypes)) {
				this.selectComponents(sessionKey, kept);
			}
		}
	}

	/** Re-reads every component's lifecycle state, then republishes all status badges - on {@link #liveStateWorker}. */
	private void refreshComponentStatuses(final Set<Class<?>> componentTypes) {
		Set<ComponentDiagramEdge> edges = this.cachedEdges;
		this.onLiveStateWorker(() -> {
			this.componentStates.clear();
			for (Class<?> componentType : componentTypes) {
				this.componentStates.put(componentType, this.lifecycleReadServices.readComponentState(componentType));
			}
			this.statusEdges = edges;
			this.publishComponentStatuses();
		});
	}

	/**
	 * A leaf's badge shows its own state; a composite's aggregates the
	 * states of every leaf it contains, at any depth - see
	 * {@link ComponentStatus#aggregate}.
	 */
	private void publishComponentStatuses() {
		Map<Class<?>, Set<Class<?>>> childrenOf = new HashMap<>();
		for (ComponentDiagramEdge edge : this.statusEdges) {
			if (edge.getKind() == ComponentPortKind.COMPONENT_TYPE) {
				childrenOf.computeIfAbsent(edge.getFrom(), ignored -> new LinkedHashSet<>()).add(edge.getTo());
			}
		}
		Map<Class<?>, ComponentStatus> statuses = new HashMap<>();
		for (Class<?> componentType : this.componentStates.keySet()) {
			if (childrenOf.containsKey(componentType)) {
				List<ComponentState> leafStates = new ArrayList<>();
				this.collectLeafStates(componentType, childrenOf, leafStates);
				statuses.put(componentType, ComponentStatus.aggregate(leafStates));
			} else {
				statuses.put(componentType, ComponentStatus.ofLeaf(this.componentStates.get(componentType)));
			}
		}
		this.useComponentDiagramModelServices.setComponentStatuses(statuses);
	}

	private void collectLeafStates(final Class<?> componentType, final Map<Class<?>, Set<Class<?>>> childrenOf,
			final List<ComponentState> leafStates) {
		Set<Class<?>> children = childrenOf.get(componentType);
		if (children == null) {
			leafStates.add(this.componentStates.get(componentType));
			return;
		}
		for (Class<?> child : children) {
			this.collectLeafStates(child, childrenOf, leafStates);
		}
	}

	/**
	 * Re-narrows the already-cached shared topology (see {@link #rebuildTopology()})
	 * to EVERY open tab's own focusPath and republishes it to each - every
	 * open tab shares this same live structure, only each one's own
	 * focusPath narrows it differently (see class javadoc).
	 */
	private void republishAllSessions() {
		for (ViewKey sessionKey : Set.copyOf(this.focusPathBySession.keySet())) {
			this.renarrowSession(sessionKey);
		}
	}

	/**
	 * Cheap, local re-narrow for ONE tab: reuses the already-cached shared
	 * topology instead of recomputing it from the observed container, and
	 * updates/publishes only {@code sessionKey}'s own state - never any
	 * other open tab's. This is what {@link #setFocusPathAndRebuild} calls
	 * for a tab's own navigation, and what {@link #republishAllSessions()}
	 * calls once per open tab right after a genuine {@link #rebuildTopology()}.
	 */
	private void renarrowSession(final ViewKey sessionKey) {
		synchronized (this.topologyLock) {
			List<Class<?>> focusPath = this.focusPathBySession.getOrDefault(sessionKey, List.of());
			this.useComponentDiagramModelServices.setTopology(sessionKey,
					this.focusTopology(this.cachedComponentTypes, this.cachedEdges, focusPath));
		}
	}

	/**
	 * Narrows the full live topology down to one tab's current drill-down
	 * focus: the top-level forest (components with no composing parent)
	 * when {@code focusPath} is empty, or the direct children of its last
	 * entry otherwise. Composition edges are never included in a focused
	 * level - navigating into a composite already conveys that
	 * relationship.
	 * <p>
	 * Every other edge's two ends are each rolled up to their nearest
	 * ancestor within the visible set, if any (an already-visible type
	 * rolls up to itself in zero steps). This resolves into three cases:
	 * <ul>
	 * <li>both ends roll up to the SAME visible ancestor - the edge is
	 * entirely internal to one composite at this level, nothing to draw
	 * (same spirit as excluding composition itself);</li>
	 * <li>both ends roll up to DIFFERENT visible ancestors - an ordinary
	 * edge between the two cards actually on screen, even if the real
	 * wiring is several levels deeper on either or both sides (e.g. two
	 * leaves in sibling composites wired directly to each other, rolled up
	 * to a composite-to-composite wire);</li>
	 * <li>exactly one end rolls up to a visible ancestor, the other finds
	 * none at all - genuinely external to this whole level. Rolled into a
	 * {@link ComponentDiagramExternalPort} on the visible side, deduplicated
	 * per {@link ComponentPortRole#isFanOut()}: ports published by a
	 * composite are deduplicated by external target.</li>
	 * </ul>
	 * An edge where NEITHER end has any ancestor in the visible set belongs
	 * to a branch unrelated to this level entirely and is simply skipped.
	 *
	 * @param allComponentTypes every live, non-excluded component type
	 * @param allEdges          every live composition/service/parameter/
	 *                          event edge
	 * @param focusPath         the one tab's own drill-down focus path to
	 *                          narrow this snapshot to
	 */
	private ComponentDiagramTopology focusTopology(final Set<Class<?>> allComponentTypes, final Set<ComponentDiagramEdge> allEdges,
			final List<Class<?>> focusPath) {
		Map<Class<?>, Class<?>> parentOf = new LinkedHashMap<>();
		for (ComponentDiagramEdge edge : allEdges) {
			if (edge.getKind() == ComponentPortKind.COMPONENT_TYPE) {
				parentOf.put(edge.getTo(), edge.getFrom());
			}
		}

		Class<?> focus = focusPath.isEmpty() ? null : focusPath.get(focusPath.size() - 1);
		Set<Class<?>> visible = new LinkedHashSet<>();
		for (Class<?> type : allComponentTypes) {
			if (Objects.equals(parentOf.get(type), focus)) {
				visible.add(type);
			}
		}

		Set<ComponentDiagramEdge> visibleEdges = new LinkedHashSet<>();
		Map<ExternalPortKey, Set<Class<?>>> externalTargetsByKey = new LinkedHashMap<>();
		for (ComponentDiagramEdge edge : allEdges) {
			if (edge.getKind() == ComponentPortKind.COMPONENT_TYPE) {
				continue;
			}
			Class<?> visibleFrom = this.nearestVisibleAncestor(edge.getFrom(), parentOf, visible);
			Class<?> visibleTo = this.nearestVisibleAncestor(edge.getTo(), parentOf, visible);
			if (visibleFrom != null && visibleTo != null) {
				if (!visibleFrom.equals(visibleTo)) {
					visibleEdges.add(new ComponentDiagramEdge(visibleFrom, visibleTo, edge.getKind(), edge.getRelation()));
				}
				continue;
			}
			if (visibleFrom != null) {
				this.addExternalTarget(externalTargetsByKey, visibleFrom, edge.getKind(),
						this.roleFor(edge.getKind(), true), edge.getTo());
			} else if (visibleTo != null) {
				this.addExternalTarget(externalTargetsByKey, visibleTo, edge.getKind(),
						this.roleFor(edge.getKind(), false), edge.getFrom());
			}
		}

		Set<ComponentDiagramExternalPort> externalPorts = new LinkedHashSet<>();
		for (Map.Entry<ExternalPortKey, Set<Class<?>>> entry : externalTargetsByKey.entrySet()) {
			ExternalPortKey key = entry.getKey();
			externalPorts.add(new ComponentDiagramExternalPort(key.owner(), key.kind(), key.role(),
					Set.copyOf(entry.getValue())));
		}

		Set<Class<?>> compositeTypes = new LinkedHashSet<>();
		for (Class<?> type : visible) {
			if (parentOf.containsValue(type)) {
				compositeTypes.add(type);
			}
		}

		Set<Class<?>> rawTypes = new LinkedHashSet<>();
		for (Class<?> type : visible) {
			if (!compositeTypes.contains(type) && this.isRaw(type)) {
				rawTypes.add(type);
			}
		}

		Map<Class<?>, String> stereotypes = new LinkedHashMap<>();
		for (Class<?> type : visible) {
			stereotypes.put(type, this.stereotypeOf(type, compositeTypes.contains(type), rawTypes.contains(type)));
		}

		Map<Class<?>, Class<?>> implementations = new LinkedHashMap<>();
		for (Class<?> type : visible) {
			ReadOnlyComponentMetamodel metamodel = this.metamodelReadServices.readMetamodel(type);
			Class<?> implementation = metamodel == null ? null : metamodel.getImplementation();
			if (implementation != null && implementation != type) {
				implementations.put(type, implementation);
			}
		}

		return new ComponentDiagramTopology(visible, visibleEdges, compositeTypes, externalPorts, allComponentTypes,
				parentOf, rawTypes, stereotypes, implementations, Objects.toString(this.getObservedContainerName(), ""));
	}

	/**
	 * What a card stands for: the stereotype the table gives its name or, when no name ending
	 * matches, what the metamodel says - the same rule as the sequence diagram's lifelines.
	 */
	private String stereotypeOf(final Class<?> type, final boolean composite, final boolean raw) {
		String stereotype = this.accessStereotypeParameters.stereotypeFor(type);
		if (stereotype != null) {
			return stereotype;
		}
		if (type.isAnnotationPresent(Internal.class)) {
			return MetamodelStereotypes.INTERNAL_COMPONENT;
		}
		if (raw) {
			return MetamodelStereotypes.RAW_COMPONENT;
		}
		return composite ? MetamodelStereotypes.COMPOSITE : MetamodelStereotypes.COMPONENT;
	}

	/**
	 * A raw component is registered as is, without the component annotations a managed one
	 * carries on its type or implementation; the container still gives it a metamodel, which
	 * tells it. A type without a metamodel is raw unless it carries those annotations itself.
	 */
	private boolean isRaw(final Class<?> type) {
		ReadOnlyComponentMetamodel metamodel = this.metamodelReadServices.readMetamodel(type);
		if (metamodel != null) {
			return metamodel.isRaw();
		}
		return !ComponentAnnotations.isComponent(type) && !ComponentAnnotations.isImplementation(type);
	}

	/**
	 * Walks {@code parentOf} up from {@code type} until it finds a member
	 * of {@code visible}, returning it - {@code type} itself when it is
	 * already visible (zero steps), {@code null} if the chain runs out
	 * (parentless) before ever reaching one.
	 */
	private Class<?> nearestVisibleAncestor(final Class<?> type, final Map<Class<?>, Class<?>> parentOf, final Set<Class<?>> visible) {
		Class<?> current = type;
		while (current != null) {
			if (visible.contains(current)) {
				return current;
			}
			current = parentOf.get(current);
		}
		return null;
	}

	/**
	 * The role {@code owner} plays when it is on the {@code isFromSide} end
	 * of a {@code kind} edge - matches {@code ComponentDiagramViewImpl.addTopologyEdge(...)}'s
	 * switch exactly (SERVICE: from=USED/to=PROVIDED; EVENT: from=PRODUCED/
	 * to=CONSUMED - the one kind where the fan-out side is {@code from}, not
	 * {@code to}; PARAMETER: from=ACCESSED/to=OFFERED), kept as a second,
	 * JavaFX-free copy here since the View's version returns the diagram
	 * package's own {@code ComponentPortRole}.
	 */
	private ComponentPortRole roleFor(final ComponentPortKind kind, final boolean isFromSide) {
		switch (kind) {
		case SERVICE:
			return isFromSide ? ComponentPortRole.USED : ComponentPortRole.PROVIDED;
		case EVENT:
			return isFromSide ? ComponentPortRole.PRODUCED : ComponentPortRole.CONSUMED;
		case PARAMETER:
			return isFromSide ? ComponentPortRole.ACCESSED : ComponentPortRole.OFFERED;
		default:
			throw new IllegalArgumentException("No role for " + kind);
		}
	}

	/**
	 * Accumulates one external edge's far end into the right {@link
	 * ComponentDiagramExternalPort}-to-be: a fan-out role (see {@link
	 * ComponentPortRole#isFanOut()}) ignores the specific target in its key,
	 * so every external taker of the same (owner, kind, role) merges into
	 * one port's growing target set; a non-fan-out role keys on the target
	 * too, so each distinct external provider/producer/offeror keeps its
	 * own port even when several internal descendants of {@code owner}
	 * independently reach it (those just add the same target to the same
	 * key's set - a no-op on the second and later additions, since it's a
	 * {@link Set}).
	 */
	private void addExternalTarget(final Map<ExternalPortKey, Set<Class<?>>> targetsByKey, final Class<?> owner,
			final ComponentPortKind kind, final ComponentPortRole role, final Class<?> target) {
		ExternalPortKey key = new ExternalPortKey(owner, kind, role, role.isFanOut() ? null : target);
		targetsByKey.computeIfAbsent(key, unused -> new LinkedHashSet<>()).add(target);
	}

	/** @see #addExternalTarget(Map, Class, ComponentPortKind, ComponentPortRole, Class) */
	private record ExternalPortKey(Class<?> owner, ComponentPortKind kind, ComponentPortRole role, Class<?> specificTarget) {
	}

	private void collectComposition(final Set<Class<?>> componentTypes, final Set<ComponentDiagramEdge> edges) {
		for (Class<?> parentType : componentTypes) {
			ReadOnlyComponentMetamodel parentMetamodel = this.metamodelReadServices.readMetamodel(parentType);
			if (parentMetamodel == null) {
				continue;
			}
			for (ReadOnlyComponentMetamodel childMetamodel : parentMetamodel.getComposedMetamodels()) {
				Class<?> childType = childMetamodel.getComponentType();
				if (!componentTypes.contains(childType)) {
					continue;
				}
				edges.add(new ComponentDiagramEdge(parentType, childType, ComponentPortKind.COMPONENT_TYPE, null));
			}
		}
	}

	private void collectServices(final Set<Class<?>> componentTypes, final Set<ComponentDiagramEdge> edges) {
		var providers = this.servicesManagerServices.getProviderToServices();
		var serviceUsers = this.servicesManagerServices.getServiceToUsers();
		for (Entry<Class<?>, Set<Class<?>>> e : providers.entrySet()) {
			Class<?> providerType = this.componentType(e.getKey());
			if (providerType == null || !componentTypes.contains(providerType)) {
				continue;
			}
			for (Class<?> service : e.getValue()) {
				for (Class<?> user : serviceUsers.getOrDefault(service, Set.of())) {
					Class<?> userType = this.componentType(user);
					if (userType == null || userType.equals(providerType) || !componentTypes.contains(userType)) {
						// A component wrongly using its own provided service is skipped -
						// implementation coupling should be composition, not a
						// self-referencing services edge.
						continue;
					}
					edges.add(new ComponentDiagramEdge(userType, providerType, ComponentPortKind.SERVICE, service));
				}
			}
		}
	}

	private void collectParameters(final Set<Class<?>> componentTypes, final Set<ComponentDiagramEdge> edges) {
		var offerors = this.parametersManagerServices.getOfferorToParameters();
		var accessors = this.parametersManagerServices.getParametersToAccessor();
		for (Entry<Class<?>, Set<Class<?>>> e : offerors.entrySet()) {
			Class<?> offerorType = this.componentType(e.getKey());
			if (offerorType == null || !componentTypes.contains(offerorType)) {
				continue;
			}
			for (Class<?> parameters : e.getValue()) {
				for (Class<?> accessor : accessors.getOrDefault(parameters, Set.of())) {
					Class<?> accessorType = this.componentType(accessor);
					if (accessorType == null || !componentTypes.contains(accessorType)) {
						continue;
					}
					edges.add(new ComponentDiagramEdge(accessorType, offerorType, ComponentPortKind.PARAMETER, parameters));
				}
			}
		}
	}

	private void collectEvents(final Set<Class<?>> componentTypes, final Set<ComponentDiagramEdge> edges) {
		var publishers = this.eventsManagerServices.getProducerToEvent();
		var subscribers = this.eventsManagerServices.getSubscribers();
		for (Entry<Class<?>, Set<Class<?>>> e : publishers.entrySet()) {
			Class<?> producerType = this.componentType(e.getKey());
			if (producerType == null || !componentTypes.contains(producerType)) {
				continue;
			}
			for (Class<?> event : e.getValue()) {
				for (ConsumerMetadata<?> consumerMetadata : subscribers.getOrDefault(event, Set.of())) {
					Class<?> consumerType = this.componentType(this.resolveConsumerClass(consumerMetadata));
					if (consumerType == null || !componentTypes.contains(consumerType)) {
						continue;
					}
					edges.add(new ComponentDiagramEdge(producerType, consumerType, ComponentPortKind.EVENT, event));
				}
			}
		}
	}

	/**
	 * Resolves a consumer's class: declared directly on the subscription's
	 * metadata for a managed component, or, for a non-managed subscriber
	 * (e.g. a raw proxy), unwrapped from its invocation handler.
	 */
	private Class<?> resolveConsumerClass(final ConsumerMetadata<?> consumerMetadata) {
		Class<?> consumerClass = consumerMetadata.getConsumerComponentType();
		if (consumerClass != null) {
			return consumerClass;
		}
		Object consumer = consumerMetadata.getInstance();
		if (consumer != null && Proxy.isProxyClass(consumer.getClass())) {
			InvocationHandler invocationHandler = Proxy.getInvocationHandler(consumer);
			if (invocationHandler instanceof ConsumerInvocationHandler) {
				return ((ConsumerInvocationHandler<?>) invocationHandler).getConsumer().getClass();
			}
		}
		return null;
	}

	private Class<?> componentType(final Class<?> componentClass) {
		if (componentClass == null) {
			return null;
		}
		Class<?> componentType = this.metamodelReadServices.getType(componentClass);
		return componentType == null ? componentClass : componentType;
	}

	// --- ObservedContainerConnectionEvents ---

	@Override
	public void onContainerConnected() {
		// The bridge forgets in-flight calls on a switch, without reporting them finished.
		this.onLiveStateWorker(this.activityCounts::clear);
		this.rebuildTopology();
	}

	@Override
	public void onContainerDisconnected() {
		// Nothing observed any longer: every read answers empty, so the topology empties.
		this.onLiveStateWorker(this.activityCounts::clear);
		this.rebuildTopology();
	}

	// --- ExclusionPolicyEvents: either change can add or remove nodes from the
	// topology, so both trigger a full rebuild, same as the registry/services/
	// parameters/events topology-changing callbacks above. ---

	@Override
	public void onExcludedComponentsChanged(final List<Class<?>> componentTypes) {
		this.rebuildTopology();
	}

	@Override
	public void onExcludeInternalComponentsChanged(final boolean exclude) {
		this.rebuildTopology();
	}

	// --- StereotypePolicyEvents: a card's stereotype is part of the topology every tab shows, so a
	// change of the table republishes it to all of them - no rebuild of the observed state. ---

	@Override
	public void onStereotypesChanged(final List<StereotypeRule> rules) {
		synchronized (this.topologyLock) {
			this.republishAllSessions();
		}
	}

	// --- ObservedPortRateEvents ---

	/**
	 * Turns the sampled call counts into rates per component, interface and role. A composite
	 * gets those of the components it contains: its own ports are among them, the ones its
	 * components share with the outside.
	 */
	@Override
	public void onPortRatesSampled(final long sampleTimeMillis, final long periodMillis,
			final List<ObservedPortRate> rates) {
		this.onLiveStateWorker(() -> {
			double perSecond = 1000.0 / Math.max(1, periodMillis);
			// Per component, the calls per interface and role: a composite gets those of the
			// components it contains, as its ports stand for theirs.
			Map<Class<?>, Map<PortRateKey, Double>> ports = new HashMap<>();
			for (ObservedPortRate rate : rates) {
				double callsPerSecond = rate.calls() * perSecond;
				Class<?> componentType = rate.componentType();
				ComponentPortKind kind = toPortKind(rate.portKind());
				PortRateKey key = new PortRateKey(rate.portType(), kind, toPortRole(kind, rate.receiving()));
				ports.computeIfAbsent(componentType, ignored -> new HashMap<>()).merge(key, callsPerSecond, Double::sum);
				for (Class<?> ancestor : this.ancestryOf(componentType)) {
					ports.computeIfAbsent(ancestor, ignored -> new HashMap<>()).merge(key, callsPerSecond, Double::sum);
				}
			}
			Map<Class<?>, ComponentActivity> activities = new HashMap<>();
			for (Map.Entry<Class<?>, Map<PortRateKey, Double>> component : ports.entrySet()) {
				List<ComponentActivity.PortRate> portRates = new ArrayList<>();
				component.getValue().forEach((key, callsPerSecond) -> portRates.add(
						new ComponentActivity.PortRate(key.portType(), key.kind(), key.role(), callsPerSecond)));
				portRates.sort((first, second) -> Double.compare(second.callsPerSecond(), first.callsPerSecond()));
				activities.put(component.getKey(), new ComponentActivity(List.copyOf(portRates)));
			}
			this.useComponentDiagramModelServices.setActivity(sampleTimeMillis, activities);
		});
	}

	private record PortRateKey(Class<?> portType, ComponentPortKind kind, ComponentPortRole role) {
	}

	private static ComponentPortRole toPortRole(final ComponentPortKind kind, final boolean receiving) {
		switch (kind) {
			case SERVICE:
				return receiving ? ComponentPortRole.PROVIDED : ComponentPortRole.USED;
			case EVENT:
				return receiving ? ComponentPortRole.CONSUMED : ComponentPortRole.PRODUCED;
			default:
				return receiving ? ComponentPortRole.OFFERED : ComponentPortRole.ACCESSED;
		}
	}

	// --- ObservedLifecycleEvents ---

	@Override
	public void onComponentStateChanged(final Class<?> componentType, final ComponentState reported) {
		this.onLiveStateWorker(() -> {
			if (this.componentStates.containsKey(componentType)) {
				// Deliveries made by the events pool can overtake one another, so the state the
				// container is actually in is the one kept, not the last one reported.
				ComponentState current = this.lifecycleReadServices.readComponentState(componentType);
				this.componentStates.put(componentType, current != null ? current : reported);
				this.publishComponentStatuses();
			}
		});
	}

	// --- ObservedPortActivityEvents ---

	/**
	 * Activity is reported for the component a call actually goes through,
	 * usually a leaf, while a tab mostly shows composites: each change is
	 * therefore applied to that component and to every one of its
	 * ancestors, so whichever card stands for it at a tab's own level
	 * lights up. A composite stays active as long as any of its descendants
	 * is - hence the count per component and port kind.
	 */
	@Override
	public void onPortActivityChanged(final Class<?> componentType, final Class<?> portType,
			final ObservedPortKind portKind, final boolean active) {
		this.onLiveStateWorker(() -> this.applyPortActivity(componentType, portType, toPortKind(portKind), active));
	}

	private void applyPortActivity(final Class<?> componentType, final Class<?> portType, final ComponentPortKind kind,
			final boolean active) {
		List<Class<?>> affected = new ArrayList<>(this.ancestryOf(componentType));
		affected.add(componentType);
		for (Class<?> type : affected) {
			ActivityKey key = new ActivityKey(type, portType, kind);
			int count = this.activityCounts.getOrDefault(key, 0);
			int updated = active ? count + 1 : Math.max(0, count - 1);
			if (updated == 0) {
				this.activityCounts.remove(key);
			} else {
				this.activityCounts.put(key, updated);
			}
			if ((count == 0) != (updated == 0)) {
				this.useComponentDiagramModelServices.setPortActivity(type, portType, kind, updated > 0);
			}
		}
	}

	/** {@link #parentPathOf}, cached: it is read on every activity change. */
	private List<Class<?>> ancestryOf(final Class<?> componentType) {
		return this.ancestryCache.computeIfAbsent(componentType, this::parentPathOf);
	}

	/**
	 * Keyed by the specific services/parameters/events interface, not just the owning
	 * component and port kind: a component can provide or use several distinct interfaces
	 * of the same kind, and each one has its own counter, so that a busy interface does not
	 * keep an idle one lit on every card between the real caller and whichever visible
	 * ancestor rolled it up.
	 */
	private record ActivityKey(Class<?> componentType, Class<?> portType, ComponentPortKind portKind) {
	}

	private static ComponentPortKind toPortKind(final ObservedPortKind portKind) {
		switch (portKind) {
			case SERVICE:
				return ComponentPortKind.SERVICE;
			case PARAMETER:
				return ComponentPortKind.PARAMETER;
			case EVENT:
				return ComponentPortKind.EVENT;
			default:
				throw new IllegalArgumentException("Unknown port kind: " + portKind);
		}
	}

	// --- RegistryEvents ---

	@Override
	public <CT> void onComponentRegistered(final Class<CT> componentType, final Object componentInstance) {
		this.rebuildTopology();
	}

	@Override
	public <CT> void onComponentUnregistered(final Class<CT> componentType, final Object componentInstance) {
		this.rebuildTopology();
	}

	// --- ServicesManagerEvents: topology-changing methods rebuild; per-call
	// methods are not needed here, port activity arriving coalesced through
	// ObservedPortActivityEvents instead. ---

	@Override
	public <CT, SERVICES> void onServicesProvided(final Class<CT> componentType, final Class<SERVICES> servicesInterface) {
		this.rebuildTopology();
	}

	@Override
	public <CT, SERVICES> void onServicesUnprovided(final Class<CT> componentType, final Class<SERVICES> servicesInterface) {
		this.rebuildTopology();
	}

	@Override
	public <CT, SERVICES> void onServicesUsed(final Class<CT> servicesUser, final Class<SERVICES> servicesInterface) {
		this.rebuildTopology();
	}

	@Override
	public <CT, SERVICES> void onServicesUnused(final Class<CT> servicesUser, final Class<SERVICES> servicesInterface) {
		this.rebuildTopology();
	}

	@Override
	public <SERVICES> void onUsedServiceCallStarted(final Thread callThread, final long callTimeNanos,
			final long callSequence, final Class<SERVICES> servicesInterface, final Method method,
			final Class<?> servicesUser, final String argumentsDescription) {
		// Port activity arrives coalesced, through ObservedPortActivityEvents.
	}

	@Override
	public <SERVICES> void onUsedServiceCallFinished(final Thread callThread, final long callTimeNanos,
			final long callSequence, final Class<SERVICES> servicesInterface, final Method method,
			final Class<?> servicesUser, final long durationNanos, final Throwable failure,
			final CallOutcome outcome) {
		// Port activity arrives coalesced, through ObservedPortActivityEvents.
	}

	@Override
	public <SERVICES> void onProvidedServiceCallStarted(final Thread callThread, final long callTimeNanos,
			final long callSequence, final Class<SERVICES> servicesInterface, final Method method,
			final Class<?> servicesProvider, final String argumentsDescription) {
		// Port activity arrives coalesced, through ObservedPortActivityEvents.
	}

	@Override
	public <SERVICES> void onProvidedServiceCallFinished(final Thread callThread, final long callTimeNanos,
			final long callSequence, final Class<SERVICES> servicesInterface, final Method method,
			final Class<?> servicesProvider, final long durationNanos, final Throwable failure,
			final CallOutcome outcome) {
		// Port activity arrives coalesced, through ObservedPortActivityEvents.
	}

	// --- ParametersManagerEvents: same split as ServicesManagerEvents above. ---

	@Override
	public <PARAMETERS> void onParametersOffered(final Class<?> componentType, final Class<PARAMETERS> parametersInterface) {
		this.rebuildTopology();
	}

	@Override
	public <PARAMETERS> void onParametersUnoffered(final Class<?> componentType, final Class<PARAMETERS> parametersInterface) {
		this.rebuildTopology();
	}

	@Override
	public <PARAMETERS> void onParametersAccessed(final Class<?> parametersAccessor, final Class<PARAMETERS> parametersInterface) {
		this.rebuildTopology();
	}

	@Override
	public <PARAMETERS> void onParametersUnaccessed(final Class<?> parametersAccessor, final Class<PARAMETERS> parametersInterface) {
		this.rebuildTopology();
	}

	@Override
	public <PARAMETERS> void onAccessedParametersCallStarted(final Thread callThread, final long callTimeNanos,
			final long callSequence, final Class<PARAMETERS> parametersInterface, final Method method,
			final Class<?> parametersAccessor, final String argumentsDescription) {
		// Port activity arrives coalesced, through ObservedPortActivityEvents.
	}

	@Override
	public <PARAMETERS> void onAccessedParametersCallFinished(final Thread callThread, final long callTimeNanos,
			final long callSequence, final Class<PARAMETERS> parametersInterface, final Method method,
			final Class<?> parametersAccessor, final long durationNanos, final Throwable failure,
			final CallOutcome outcome) {
		// Port activity arrives coalesced, through ObservedPortActivityEvents.
	}

	@Override
	public <PARAMETERS> void onOfferedParametersCallStarted(final Thread callThread, final long callTimeNanos,
			final long callSequence, final Class<PARAMETERS> parametersInterface, final Method method,
			final Class<?> parametersOfferor, final String argumentsDescription) {
		// Port activity arrives coalesced, through ObservedPortActivityEvents.
	}

	@Override
	public <PARAMETERS> void onOfferedParametersCallFinished(final Thread callThread, final long callTimeNanos,
			final long callSequence, final Class<PARAMETERS> parametersInterface, final Method method,
			final Class<?> parametersOfferor, final long durationNanos, final Throwable failure,
			final CallOutcome outcome) {
		// Port activity arrives coalesced, through ObservedPortActivityEvents.
	}

	// --- EventsManagerEvents: same split as ServicesManagerEvents above. ---

	@Override
	public <EVENTS> void onEventsProduced(final Class<?> componentType, final Class<EVENTS> eventsInterface) {
		this.rebuildTopology();
	}

	@Override
	public <EVENTS> void onEventsUnproduced(final Class<?> componentType, final Class<EVENTS> eventsInterface) {
		this.rebuildTopology();
	}

	@Override
	public <EVENTS> void onEventsSubscribed(final Class<? extends EVENTS> eventsConsumer, final Class<EVENTS> eventsInterface) {
		this.rebuildTopology();
	}

	@Override
	public <EVENTS> void onEventsUnsubscribed(final Class<? extends EVENTS> eventsConsumer, final Class<EVENTS> eventsInterface) {
		this.rebuildTopology();
	}

	@Override
	public <EVENTS> void onEventNotificationStarted(final Thread callThread, final long callTimeNanos,
			final long callSequence, final Class<EVENTS> eventsInterface, final Method method,
			final Class<?> eventsProducer, final Class<?> eventsConsumer, final Class<? extends EVENTS> subscriberType,
			final boolean asynchronous, final long queueWaitNanos, final String argumentsDescription) {
		// Port activity arrives coalesced, through ObservedPortActivityEvents.
	}

	@Override
	public <EVENTS> void onEventNotificationFinished(final Thread callThread, final long callTimeNanos,
			final long callSequence, final Class<EVENTS> eventsInterface, final Method method,
			final Class<?> eventsProducer, final Class<?> eventsConsumer, final Class<? extends EVENTS> subscriberType, final long durationNanos,
			final Throwable failure) {
		// Port activity arrives coalesced, through ObservedPortActivityEvents.
	}
}
