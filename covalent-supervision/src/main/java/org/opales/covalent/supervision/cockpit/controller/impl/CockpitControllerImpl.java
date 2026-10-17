/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.cockpit.controller.impl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.opales.covalent.supervision.SupervisionThreads;
import org.opales.covalent.container.component.ComponentAnnotations;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.supervision.cockpit.controller.CockpitController;
import org.opales.covalent.supervision.cockpit.controller.CockpitControllerServices;
import org.opales.covalent.supervision.cockpit.model.CockpitModelServices;
import org.opales.covalent.supervision.cockpit.model.CockpitSnapshot;
import org.opales.covalent.supervision.cockpit.model.LifecycleCommand;
import org.opales.covalent.supervision.cockpit.model.Phrase;
import org.opales.covalent.supervision.componentdiagram.model.ComponentActivity;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramEdge;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramEvents;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramModelReadServices;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramStructure;
import org.opales.covalent.supervision.componentdiagram.model.ComponentPortKind;
import org.opales.covalent.supervision.componentdiagram.model.ComponentStatus;
import org.opales.covalent.supervision.containermanager.raw.ObservedContainerControl;
import org.opales.covalent.supervision.observed.ObservedContainerConnectionEvents;
import org.opales.covalent.supervision.observed.ObservedLifecycleControlServices;
import org.opales.covalent.supervision.observed.ObservedLifecycleEvents;
import org.opales.covalent.supervision.observed.ObservedLifecycleReadServices;
import org.opales.covalent.supervision.observed.ObservedPortKind;
import org.opales.covalent.supervision.observed.ObservedPortRate;
import org.opales.covalent.supervision.observed.ObservedPortRateEvents;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

/**
 * Builds the cockpit's figures once per second, from the bridge's port rates, the lifecycle
 * states and the component diagram's live structure. A call is counted once, where it is
 * received.
 */
@Implementation
public class CockpitControllerImpl implements CockpitController, CockpitControllerServices, ObservedPortRateEvents, ObservedLifecycleEvents,
		ObservedContainerConnectionEvents, ComponentDiagramEvents {

	/** Creates the controller of the cockpit function. */
	public CockpitControllerImpl() {
		// Nothing to initialize.
	}

	/** How many state changes the cockpit lists. */
	private static final int TRANSITIONS = 12;

	/** How many ports the cockpit ranks. */
	private static final int HOT_PORTS = 8;

	@UsedServices
	private CockpitModelServices useModelServices;

	@UsedServices
	private ObservedContainerControl useObservedContainerControl;

	@UsedServices
	private ComponentDiagramModelReadServices useComponentDiagramModelReadServices;

	/** Whether a container is observed: no port rate is sampled otherwise. */
	private boolean connected;

	@UsedServices
	private ObservedLifecycleReadServices lifecycleReadServices;

	@UsedServices
	private ObservedLifecycleControlServices lifecycleControlServices;

	/** How many lines the console's log keeps. */
	private static final int LOG = 40;

	private final Deque<CockpitSnapshot.LogEntry> log = new ArrayDeque<>();

	/** Runs the console's commands one at a time, away from the JavaFX thread. */
	private ExecutorService commands;

	/** The composites entered in the console, outermost first, empty at the top. */
	private List<Class<?>> consoleFocus = List.of();

	/** The last figures published, and the composites' rates they were made from. */
	private CockpitSnapshot lastSnapshot;
	private Map<Class<?>, Double> lastCompositeRates = Map.of();

	private ComponentDiagramStructure structure = new ComponentDiagramStructure(Set.of(), Set.of(), Set.of());

	/** Composition: each component's parent. */
	private Map<Class<?>, Class<?>> parentOf = Map.of();

	/** Composition: each composite's components, at any depth. */
	private Map<Class<?>, Set<Class<?>>> descendantsOf = Map.of();

	/**
	 * Every component seen since connected, with its composition and links: the console keeps
	 * showing a component that leaves the live structure - failed, removed or reset - until it
	 * is undeployed, so that it can still be acted on.
	 */
	private final Set<Class<?>> knownTypes = new LinkedHashSet<>();
	private final Set<ComponentDiagramEdge> knownEdges = new LinkedHashSet<>();
	private final Set<Class<?>> knownRawTypes = new HashSet<>();

	/** A structure with its composition worked out. */
	private record Tree(ComponentDiagramStructure structure, Map<Class<?>, Class<?>> parentOf,
			Map<Class<?>, Set<Class<?>>> descendantsOf) {

		static Tree of(final ComponentDiagramStructure structure) {
			Map<Class<?>, Class<?>> parents = new HashMap<>();
			for (ComponentDiagramEdge edge : structure.edges()) {
				if (edge.getKind() == ComponentPortKind.COMPONENT_TYPE) {
					parents.put(edge.getTo(), edge.getFrom());
				}
			}
			Map<Class<?>, Set<Class<?>>> descendants = new HashMap<>();
			for (Class<?> componentType : structure.componentTypes()) {
				for (Class<?> ancestor = parents.get(componentType); ancestor != null;
						ancestor = parents.get(ancestor)) {
					descendants.computeIfAbsent(ancestor, ignored -> new LinkedHashSet<>()).add(componentType);
				}
			}
			return new Tree(structure, parents, descendants);
		}
	}

	private final Map<Class<?>, ComponentState> states = new HashMap<>();
	private Map<Class<?>, ComponentStatus> statuses = Map.of();
	private final Deque<CockpitSnapshot.Transition> transitions = new ArrayDeque<>();

	// --- ComponentDiagramEvents: only the live structure and the statuses are used ---

	@Override
	public synchronized void onLiveStructureChanged() {
		this.applyStructure(this.useComponentDiagramModelReadServices.getLiveStructure());
		if (!this.connected) {
			// No port rate is sampled while disconnected: the figures are published here.
			this.publishEmptySample();
		}
	}

	private void applyStructure(final ComponentDiagramStructure structure) {
		this.structure = structure;
		Tree tree = Tree.of(structure);
		this.parentOf = tree.parentOf();
		this.descendantsOf = tree.descendantsOf();
		this.knownTypes.addAll(structure.componentTypes());
		this.knownEdges.addAll(structure.edges());
		this.knownRawTypes.addAll(structure.rawTypes());
		for (Class<?> componentType : structure.componentTypes()) {
			if (!this.states.containsKey(componentType)) {
				ComponentState state = this.lifecycleReadServices.readComponentState(componentType);
				if (state != null) {
					this.states.put(componentType, state);
				}
			}
		}
	}

	@Override
	public synchronized void onComponentStatusesChanged() {
		this.statuses = this.useComponentDiagramModelReadServices.getComponentStatuses();
	}

	@Override
	public void onSessionCreated(final ViewKey sessionKey) {
	}

	@Override
	public void onTopologyChanged(final ViewKey sessionKey) {
	}

	@Override
	public void onSelectionChanged(final ViewKey sessionKey, final Set<Class<?>> selectedComponentTypes) {
	}

	@Override
	public void onPortActivityChanged(final Class<?> componentType, final Class<?> portType,
			final ComponentPortKind portKind, final boolean active) {
	}

	@Override
	public void onActivitySampled(final long sampleTimeMillis, final Map<Class<?>, ComponentActivity> activities) {
	}

	@Override
	public void onDependenciesVisibilityChanged(final ViewKey sessionKey, final Class<?> componentType,
			final boolean visible) {
	}

	@Override
	public void onFocusPathChanged(final ViewKey sessionKey, final List<Class<?>> focusPath) {
	}

	@Override
	public void onFlashComponentChanged(final ViewKey sessionKey, final Class<?> componentType) {
	}

	@Override
	public void onLinkHighlightRequested(final ViewKey sessionKey, final Class<?> sourceType,
			final Class<?> targetType) {
	}

	@Override
	public void onSessionClosed(final ViewKey sessionKey) {
	}

	// --- ObservedLifecycleEvents ---

	@Override
	public synchronized void onComponentStateChanged(final Class<?> componentType, final ComponentState reported) {
		// Deliveries made by the events pool can overtake one another, so the state the
		// container is actually in is the one kept, not the last one reported.
		ComponentState current = this.lifecycleReadServices.readComponentState(componentType);
		ComponentState state = current != null ? current : reported;
		ComponentState previous = this.states.put(componentType, state);
		if (previous != state) {
			this.transitions.addFirst(new CockpitSnapshot.Transition(System.currentTimeMillis(), componentType,
					previous, state));
			while (this.transitions.size() > TRANSITIONS) {
				this.transitions.removeLast();
			}
			this.log(Phrase.of("log.transition", componentType.getSimpleName(), Phrase.of("state." + state.name())),
					state == ComponentState.failed);
		}
	}

	/**
	 * Takes the connection as made when a container is already observed: the supervisor connects
	 * to its first container before this component activates, so that connection is never
	 * notified to it.
	 */
	@Activation
	public void readConnection() {
		if (this.useObservedContainerControl.getObservedContainerName() != null) {
			this.onContainerConnected();
		}
	}

	// --- ObservedContainerConnectionEvents ---

	@Override
	public void onContainerConnected() {
		// this.useModelServices.setConnected(...) is a call to another component: it
		// must not be made while holding this instance's own lock, or it can deadlock
		// against this container's own shutdown sequence (see onContainerDisconnected).
		synchronized (this) {
			this.clear();
			this.connected = true;
		}
		this.useModelServices.setConnected(true);
	}

	/** Forgets everything known of the observed container. */
	private void clear() {
		this.consoleFocus = List.of();
		this.lastSnapshot = null;
		this.states.clear();
		this.transitions.clear();
		this.log.clear();
		this.knownTypes.clear();
		this.knownEdges.clear();
		this.knownRawTypes.clear();
	}

	/**
	 * Empties the cockpit at once: with no container observed there is no port rate to sample, so an
	 * empty snapshot is published here rather than awaited.
	 */
	@Override
	public void onContainerDisconnected() {
		// this.useModelServices.setConnected(...) is a call to another component, so it
		// must happen outside this instance's own lock: while disposing this container,
		// the shutdown-hook thread passivates this component (stopCommands(), below)
		// while holding the lifecycle manager's lock, so an outbound call made here
		// while still holding this instance's lock can deadlock against it (the call
		// round-trips through the lifecycle manager to check whether the container is
		// disposed).
		synchronized (this) {
			this.clear();
			this.connected = false;
			this.statuses = Map.of();
			this.applyStructure(new ComponentDiagramStructure(Set.of(), Set.of(), Set.of()));
		}
		this.useModelServices.setConnected(false);
		this.publishEmptySample();
	}

	/** Publishes the figures without any port rate. */
	private void publishEmptySample() {
		this.onPortRatesSampled(System.currentTimeMillis(), 1000, List.of());
	}

	// --- ObservedPortRateEvents: one snapshot per sample ---

	@Override
	public synchronized void onPortRatesSampled(final long sampleTimeMillis, final long periodMillis,
			final List<ObservedPortRate> rates) {
		double perSecond = 1000.0 / Math.max(1, periodMillis);
		double services = 0;
		double events = 0;
		double parameters = 0;
		List<CockpitSnapshot.Failure> failures = new ArrayList<>();
		List<CockpitSnapshot.HotPort> ports = new ArrayList<>();
		Map<Class<?>, Double> compositeRates = new HashMap<>();
		Set<String> boundary = this.boundaryPorts();
		for (ObservedPortRate rate : rates) {
			// Only what the diagrams show: excluded and internal components are left out.
			if (!this.structure.componentTypes().contains(rate.componentType())) {
				continue;
			}
			if (rate.failures() > 0 && rate.receiving()) {
				failures.add(new CockpitSnapshot.Failure(sampleTimeMillis, rate.componentType(), rate.portType(),
						rate.lastFailedMethod(), rate.failures()));
			}
			double callsPerSecond = rate.calls() * perSecond;
			for (Class<?> ancestor = this.parentOf.get(rate.componentType()); ancestor != null;
					ancestor = this.parentOf.get(ancestor)) {
				if (boundary.contains(boundaryKey(ancestor, rate.componentType(), rate.portType(), rate.receiving()))) {
					compositeRates.merge(ancestor, callsPerSecond, Double::sum);
				}
			}
			if (!rate.receiving()) {
				continue;
			}
			switch (rate.portKind()) {
				case SERVICE -> services += callsPerSecond;
				case EVENT -> events += callsPerSecond;
				default -> parameters += callsPerSecond;
			}
			if (callsPerSecond > 0) {
				ports.add(new CockpitSnapshot.HotPort(rate.portType(), toPortKind(rate.portKind()), rate.componentType(),
						callsPerSecond, rate.p95Nanos()));
			}
		}
		ports.sort((first, second) -> Double.compare(second.callsPerSecond(), first.callsPerSecond()));

		Tree console = this.consoleTree();
		List<CockpitSnapshot.Slice> slices = this.slices(console, compositeRates);
		int[] counts = this.consoleCounts(console);
		this.lastCompositeRates = compositeRates;
		this.lastSnapshot = new CockpitSnapshot(sampleTimeMillis, this.count(false), this.count(true),
				this.descendantsOf.size(), this.links(ComponentPortKind.SERVICE), this.links(ComponentPortKind.EVENT),
				this.links(ComponentPortKind.PARAMETER), services, events, parameters, failures, this.stateCounts(),
				List.copyOf(this.transitions), this.composites(compositeRates),
				List.copyOf(ports.subList(0, Math.min(HOT_PORTS, ports.size()))), slices, List.copyOf(this.log),
				this.consoleFocus, counts[0], counts[1]);
		this.useModelServices.setSnapshot(this.lastSnapshot);
	}

	/**
	 * Each composite's own ports: a link with one end inside it, at any depth, and the other
	 * outside, keyed by composite, inner component, interface and side.
	 */
	private Set<String> boundaryPorts() {
		Set<String> keys = new HashSet<>();
		for (ComponentDiagramEdge edge : this.structure.edges()) {
			if (edge.getKind() == ComponentPortKind.COMPONENT_TYPE || edge.getRelation() == null) {
				continue;
			}
			for (Map.Entry<Class<?>, Set<Class<?>>> composite : this.descendantsOf.entrySet()) {
				boolean fromInside = composite.getValue().contains(edge.getFrom());
				boolean toInside = composite.getValue().contains(edge.getTo());
				if (fromInside && !toInside) {
					keys.add(boundaryKey(composite.getKey(), edge.getFrom(), edge.getRelation(), false));
				} else if (toInside && !fromInside) {
					keys.add(boundaryKey(composite.getKey(), edge.getTo(), edge.getRelation(), true));
				}
			}
		}
		return keys;
	}

	private static String boundaryKey(final Class<?> composite, final Class<?> inner, final Class<?> portType,
			final boolean receiving) {
		return composite.getName() + "|" + inner.getName() + "|" + portType.getName() + "|" + receiving;
	}

	private int count(final boolean raw) {
		int count = 0;
		for (Class<?> componentType : this.structure.componentTypes()) {
			if (!this.descendantsOf.containsKey(componentType) && this.structure.rawTypes().contains(componentType) == raw) {
				count++;
			}
		}
		return count;
	}

	private int links(final ComponentPortKind kind) {
		int count = 0;
		for (ComponentDiagramEdge edge : this.structure.edges()) {
			if (edge.getKind() == kind) {
				count++;
			}
		}
		return count;
	}

	private Map<ComponentState, Integer> stateCounts() {
		Map<ComponentState, Integer> counts = new EnumMap<>(ComponentState.class);
		for (Class<?> componentType : this.structure.componentTypes()) {
			ComponentState state = this.states.get(componentType);
			if (state != null && !this.descendantsOf.containsKey(componentType)) {
				counts.merge(state, 1, Integer::sum);
			}
		}
		return counts;
	}

	/**
	 * The top-level composites, by name. A single root composite, such as the
	 * application itself, gives way to the composites it holds directly.
	 */
	private List<CockpitSnapshot.CompositeSummary> composites(final Map<Class<?>, Double> rates) {
		List<CockpitSnapshot.CompositeSummary> composites = new ArrayList<>();
		Tree current = new Tree(this.structure, this.parentOf, this.descendantsOf);
		List<Class<?>> level = this.topLevelComposites(current);
		Map<Class<?>, Set<Class<?>>> ordered = new LinkedHashMap<>();
		for (Class<?> componentType : level) {
			ordered.put(componentType, this.descendantsOf.get(componentType));
		}
		for (Map.Entry<Class<?>, Set<Class<?>>> composite : ordered.entrySet()) {
			List<ComponentState> componentStates = new ArrayList<>();
			for (Class<?> inner : composite.getValue()) {
				if (!this.descendantsOf.containsKey(inner)) {
					ComponentState state = this.states.get(inner);
					if (state != null) {
						componentStates.add(state);
					}
				}
			}
			composites.add(new CockpitSnapshot.CompositeSummary(composite.getKey(), componentStates,
					rates.getOrDefault(composite.getKey(), 0.0), this.statuses.get(composite.getKey())));
		}
		return composites;
	}

	/**
	 * The composite whose children the cockpit shows: none, unless a single root composite holds
	 * other composites, in which case the deepest such single composite.
	 */
	private Class<?> topLevelParent(final Tree tree) {
		Class<?> parent = null;
		List<Class<?>> level = this.applicationCompositesUnder(tree, null);
		while (level.size() == 1 && !this.applicationCompositesUnder(tree, level.get(0)).isEmpty()) {
			parent = level.get(0);
			level = this.applicationCompositesUnder(tree, parent);
		}
		return parent;
	}

	/**
	 * The composites the cockpit shows: the application's top-level ones - see
	 * {@link #topLevelParent} - plus Covalent's own, when they are not excluded, which stay at
	 * the root.
	 */
	private List<Class<?>> topLevelComposites(final Tree tree) {
		Class<?> parent = this.topLevelParent(tree);
		List<Class<?>> composites = new ArrayList<>(this.applicationCompositesUnder(tree, parent));
		for (Class<?> root : this.compositesUnder(tree, null)) {
			if (isInternal(tree, root)) {
				composites.add(root);
			}
		}
		return composites;
	}

	private List<Class<?>> applicationCompositesUnder(final Tree tree, final Class<?> parent) {
		List<Class<?>> composites = new ArrayList<>(this.compositesUnder(tree, parent));
		composites.removeIf(composite -> isInternal(tree, composite));
		return composites;
	}

	/** Whether a component is one of Covalent's own: marked {@code @Internal}, or inside one that is. */
	private static boolean isInternal(final Tree tree, final Class<?> componentType) {
		for (Class<?> type = componentType; type != null; type = tree.parentOf().get(type)) {
			if (ComponentAnnotations.isInternalComponent(type)) {
				return true;
			}
		}
		return false;
	}

	// --- Console ---

	/**
	 * The console's slices at the level it shows: one per composite of that level, plus one for
	 * the components standing alone there, those nobody depends on first. At the top, the level
	 * is the top-level composites' and the standalone components include those at the root.
	 */
	private List<CockpitSnapshot.Slice> slices(final Tree tree, final Map<Class<?>, Double> rates) {
		Class<?> parent = this.topLevelParent(tree);
		this.consoleFocus = this.validFocus(tree, parent);
		Class<?> level = this.consoleFocus.isEmpty() ? null : this.consoleFocus.get(this.consoleFocus.size() - 1);
		List<Class<?>> composites = level == null ? this.topLevelComposites(tree) : this.compositesUnder(tree, level);
		List<CockpitSnapshot.Slice> slices = new ArrayList<>();
		for (Class<?> composite : composites) {
			slices.add(new CockpitSnapshot.Slice(composite, this.stateOf(composite), rates.getOrDefault(composite, 0.0),
					this.channels(tree, childrenOf(tree, composite)), this.dependentsOf(tree, treeOf(tree, composite)),
					isInternal(tree, composite), !this.compositesUnder(tree, composite).isEmpty()));
		}
		slices.sort(Comparator.comparingInt((CockpitSnapshot.Slice slice) -> slice.dependents().size())
				.thenComparing(slice -> slice.compositeType().getSimpleName()));
		List<Class<?>> standalone = new ArrayList<>();
		for (Class<?> componentType : tree.structure().componentTypes()) {
			Class<?> owner = tree.parentOf().get(componentType);
			boolean here = level == null ? owner == null || owner.equals(parent) : level.equals(owner);
			if (!tree.descendantsOf().containsKey(componentType) && here) {
				standalone.add(componentType);
			}
		}
		if (!standalone.isEmpty()) {
			slices.add(0, new CockpitSnapshot.Slice(null, null, 0, this.channels(tree, standalone),
					this.dependentsOf(tree, new HashSet<>(standalone)), false, false));
		}
		return slices;
	}

	/**
	 * The composites entered that still make a path down from the top level: the focus is cut
	 * where a composite left the container or moved.
	 */
	private List<Class<?>> validFocus(final Tree tree, final Class<?> parent) {
		List<Class<?>> valid = new ArrayList<>();
		Class<?> above = parent;
		for (Class<?> composite : this.consoleFocus) {
			Class<?> owner = tree.parentOf().get(composite);
			boolean top = valid.isEmpty() && owner == null && isInternal(tree, composite);
			if (!tree.descendantsOf().containsKey(composite) || !top && !Objects.equals(owner, above)) {
				break;
			}
			valid.add(composite);
			above = composite;
		}
		return List.copyOf(valid);
	}

	/** The components whose parent is the given composite, by name. */
	private static List<Class<?>> childrenOf(final Tree tree, final Class<?> composite) {
		List<Class<?>> children = new ArrayList<>();
		for (Class<?> componentType : tree.structure().componentTypes()) {
			if (composite.equals(tree.parentOf().get(componentType))) {
				children.add(componentType);
			}
		}
		return children;
	}

	private List<CockpitSnapshot.Channel> channels(final Tree tree, final List<Class<?>> componentTypes) {
		List<CockpitSnapshot.Channel> channels = new ArrayList<>();
		for (Class<?> componentType : componentTypes) {
			Set<Class<?>> inside = tree.descendantsOf().get(componentType);
			List<ComponentState> componentStates = new ArrayList<>();
			int componentCount = 0;
			if (inside != null) {
				for (Class<?> inner : inside) {
					if (!tree.descendantsOf().containsKey(inner)) {
						componentCount++;
						ComponentState state = this.stateOf(inner);
						if (state != null) {
							componentStates.add(state);
						}
					}
				}
			}
			Set<Class<?>> members = inside == null ? Set.of(componentType) : treeOf(tree, componentType);
			channels.add(new CockpitSnapshot.Channel(componentType, this.stateOf(componentType),
					tree.structure().rawTypes().contains(componentType), this.dependentsOf(tree, members),
					isInternal(tree, componentType), inside != null, List.copyOf(componentStates), componentCount));
		}
		channels.sort(Comparator.comparingInt((CockpitSnapshot.Channel channel) -> channel.dependents().size())
				.thenComparing(channel -> channel.componentType().getSimpleName()));
		return channels;
	}

	/**
	 * The components the console drives, at any level - managed ones, not Covalent's own - and
	 * how many of them are activated.
	 */
	private int[] consoleCounts(final Tree tree) {
		int activated = 0;
		int driven = 0;
		for (Class<?> componentType : tree.structure().componentTypes()) {
			if (!tree.descendantsOf().containsKey(componentType) && !tree.structure().rawTypes().contains(componentType)
					&& !isInternal(tree, componentType)) {
				driven++;
				if (this.stateOf(componentType) == ComponentState.activated) {
					activated++;
				}
			}
		}
		return new int[] { activated, driven };
	}

	@Override
	public void enterComposite(final Class<?> compositeType) {
		synchronized (this) {
			Tree tree = this.consoleTree();
			if (!tree.descendantsOf().containsKey(compositeType)) {
				return;
			}
			Class<?> parent = this.topLevelParent(tree);
			List<Class<?>> path = new ArrayList<>();
			for (Class<?> type = compositeType; type != null && !type.equals(parent); type = tree.parentOf().get(type)) {
				path.add(0, type);
			}
			// A composite holding only components shows as a slice of the level above it.
			if (this.compositesUnder(tree, compositeType).isEmpty()) {
				path.remove(path.size() - 1);
			}
			this.consoleFocus = List.copyOf(path);
		}
		this.republishConsole();
	}

	@Override
	public void showConsoleLevel(final int depth) {
		synchronized (this) {
			if (depth < 0 || depth >= this.consoleFocus.size()) {
				return;
			}
			this.consoleFocus = List.copyOf(this.consoleFocus.subList(0, depth));
		}
		this.republishConsole();
	}

	/** Publishes the last figures again with the console at its new level, without waiting for the next sample. */
	private void republishConsole() {
		CockpitSnapshot snapshot;
		synchronized (this) {
			CockpitSnapshot last = this.lastSnapshot;
			if (last == null) {
				return;
			}
			List<CockpitSnapshot.Slice> slices = this.slices(this.consoleTree(), this.lastCompositeRates);
			snapshot = new CockpitSnapshot(last.timeMillis(), last.managedCount(), last.rawCount(),
					last.compositeCount(), last.serviceLinks(), last.eventLinks(), last.parameterLinks(),
					last.servicesPerSecond(), last.eventsPerSecond(), last.parametersPerSecond(), last.newFailures(),
					last.states(), last.recentTransitions(), last.composites(), last.hottestPorts(), slices,
					last.consoleLog(), this.consoleFocus, last.consoleActivated(), last.consoleDriven());
			this.lastSnapshot = snapshot;
		}
		this.useModelServices.setSnapshot(snapshot);
	}

	/**
	 * The components outside a set that depend on one inside: a services user on its provider,
	 * a parameters accessor on its offeror, an events consumer on its producer.
	 */
	private List<CockpitSnapshot.Dependent> dependentsOf(final Tree tree, final Set<Class<?>> inside) {
		Set<CockpitSnapshot.Dependent> dependents = new LinkedHashSet<>();
		for (ComponentDiagramEdge edge : tree.structure().edges()) {
			if (edge.getKind() == ComponentPortKind.COMPONENT_TYPE || edge.getRelation() == null) {
				continue;
			}
			boolean event = edge.getKind() == ComponentPortKind.EVENT;
			Class<?> provider = event ? edge.getFrom() : edge.getTo();
			Class<?> user = event ? edge.getTo() : edge.getFrom();
			if (inside.contains(provider) && !inside.contains(user)) {
				dependents.add(new CockpitSnapshot.Dependent(user, edge.getRelation()));
			}
		}
		return List.copyOf(dependents);
	}

	private synchronized void log(final Phrase text, final boolean failed) {
		this.log.addFirst(new CockpitSnapshot.LogEntry(System.currentTimeMillis(), text, failed));
		while (this.log.size() > LOG) {
			this.log.removeLast();
		}
	}

	/**
	 * The known components still in the observed container, with the links among them: the live
	 * structure plus what left it without being undeployed.
	 */
	private Tree consoleTree() {
		Set<Class<?>> types = new LinkedHashSet<>();
		for (Class<?> componentType : this.knownTypes) {
			if (this.structure.componentTypes().contains(componentType) || this.stateOf(componentType) != null) {
				types.add(componentType);
			}
		}
		Set<ComponentDiagramEdge> edges = new LinkedHashSet<>();
		for (ComponentDiagramEdge edge : this.knownEdges) {
			if (types.contains(edge.getFrom()) && types.contains(edge.getTo())) {
				edges.add(edge);
			}
		}
		Set<Class<?>> raw = new HashSet<>(this.knownRawTypes);
		raw.retainAll(types);
		return Tree.of(new ComponentDiagramStructure(types, edges, raw));
	}

	/** A component's state: as last seen while in the live structure, read again otherwise. */
	private ComponentState stateOf(final Class<?> componentType) {
		if (this.structure.componentTypes().contains(componentType)) {
			return this.states.get(componentType);
		}
		ComponentState state = this.lifecycleReadServices.readComponentState(componentType);
		if (state != null) {
			this.states.put(componentType, state);
		} else {
			this.states.remove(componentType);
		}
		return state;
	}

	@Override
	public void execute(final LifecycleCommand command, final Class<?> componentType) {
		this.commands().execute(() -> this.run(command, componentType));
	}

	@Override
	public void executeOnContainer(final LifecycleCommand command) {
		this.commands().execute(() -> {
			List<Class<?>> roots;
			synchronized (this) {
				Tree tree = this.consoleTree();
				roots = new ArrayList<>();
				for (Class<?> componentType : tree.structure().componentTypes()) {
					if (!tree.parentOf().containsKey(componentType)
							&& !tree.structure().rawTypes().contains(componentType)
							&& !isInternal(tree, componentType)) {
						roots.add(componentType);
					}
				}
				// Users before providers: dependents counted against the other roots' trees.
				roots.sort(Comparator.comparingInt(root -> this.dependentsOf(tree, treeOf(tree, root)).size()));
			}
			if (command == LifecycleCommand.ACTIVATE) {
				Collections.reverse(roots);
			}
			this.log(Phrase.of("all." + command.name()), false);
			for (Class<?> root : roots) {
				ComponentState state = this.lifecycleReadServices.readComponentState(root);
				if (command == LifecycleCommand.REMOVE && state == ComponentState.activated) {
					this.run(LifecycleCommand.PASSIVATE, root);
					state = this.lifecycleReadServices.readComponentState(root);
				}
				if (command.isAllowedFrom(state)) {
					this.run(command, root);
				}
			}
		});
	}

	private static Set<Class<?>> treeOf(final Tree tree, final Class<?> root) {
		Set<Class<?>> members = new HashSet<>(tree.descendantsOf().getOrDefault(root, Set.of()));
		members.add(root);
		return members;
	}

	private void run(final LifecycleCommand command, final Class<?> componentType) {
		String name = componentType.getSimpleName();
		this.log(Phrase.of("log.run", Phrase.of(command), name), false);
		try {
			switch (command) {
				case INSTANTIATE -> this.lifecycleControlServices.instantiateComponent(componentType);
				case INITIALIZE -> this.lifecycleControlServices.initializeComponent(componentType);
				case ACTIVATE -> this.lifecycleControlServices.activateComponent(componentType);
				case PASSIVATE -> this.lifecycleControlServices.passivateComponent(componentType);
				case RESTART -> {
					this.lifecycleControlServices.passivateComponent(componentType);
					this.lifecycleControlServices.activateComponent(componentType);
				}
				case REMOVE -> this.lifecycleControlServices.removeComponent(componentType);
				case UNDEPLOY -> this.lifecycleControlServices.undeployComponent(componentType);
				case RESET -> this.lifecycleControlServices.resetFailedComponent(componentType);
			}
		} catch (RuntimeException e) {
			this.log(Phrase.of("log.failed", Phrase.of(command), name,
					e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), true);
		}
	}

	private synchronized ExecutorService commands() {
		if (this.commands == null) {
			this.commands = Executors.newSingleThreadExecutor(SupervisionThreads.factory("cockpit-commands"));
		}
		return this.commands;
	}

	/**
	 * Stops the thread that runs the console's commands when the component passivates, dropping the
	 * commands still waiting.
	 */
	@Passivation
	public synchronized void stopCommands() {
		if (this.commands != null) {
			this.commands.shutdownNow();
			this.commands = null;
		}
	}

	/** The composites whose parent is the given one, or which have none when it is null. */
	private List<Class<?>> compositesUnder(final Tree tree, final Class<?> parent) {
		List<Class<?>> children = new ArrayList<>();
		for (Class<?> componentType : tree.structure().componentTypes()) {
			if (tree.descendantsOf().containsKey(componentType)
					&& Objects.equals(tree.parentOf().get(componentType), parent)) {
				children.add(componentType);
			}
		}
		children.sort(Comparator.comparing(Class::getSimpleName));
		return children;
	}

	private static ComponentPortKind toPortKind(final ObservedPortKind kind) {
		return switch (kind) {
			case SERVICE -> ComponentPortKind.SERVICE;
			case EVENT -> ComponentPortKind.EVENT;
			default -> ComponentPortKind.PARAMETER;
		};
	}
}
