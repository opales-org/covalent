/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.cockpit.controller.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.opales.covalent.container.component.internals.Internal;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.supervision.cockpit.model.CockpitModelServices;
import org.opales.covalent.supervision.cockpit.model.CockpitSnapshot;
import org.opales.covalent.supervision.cockpit.model.LifecycleCommand;
import org.opales.covalent.supervision.cockpit.model.Phrase;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramEdge;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramModelReadServices;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramStructure;
import org.opales.covalent.supervision.componentdiagram.model.ComponentPortKind;
import org.opales.covalent.supervision.componentdiagram.model.ComponentStatus;
import org.opales.covalent.supervision.observed.ObservedLifecycleControlServices;
import org.opales.covalent.supervision.observed.ObservedLifecycleReadServices;
import org.opales.covalent.supervision.observed.ObservedPortKind;
import org.opales.covalent.supervision.observed.ObservedPortRate;

/**
 * Unit tests for {@link CockpitControllerImpl}'s figures - counts, rates, failures, composites,
 * console slices and the console's lifecycle commands - using hand-written fakes for its
 * {@code @UsedServices} (no container, no JavaFX). Structures are built by hand from the plain
 * marker interfaces below rather than through a real observed container.
 */
public class CockpitControllerImplTests {

	// --- Fixture component types: a two-level composite tree plus a standalone leaf ---

	private interface CompositeA {
	}

	private interface LeafA1 {
	}

	private interface LeafA2 {
	}

	private interface CompositeB {
	}

	private interface LeafB1 {
	}

	private interface StandaloneLeaf {
	}

	private interface RawLeaf {
	}

	@Internal
	private interface InternalComposite {
	}

	@Internal
	private interface InternalLeaf {
	}

	private interface SomeService {
	}

	// --- Fixture component types for the console's levels: TopA > Mid > Deep ---

	private interface TopA {
	}

	private interface TopLeaf {
	}

	private interface Mid {
	}

	private interface MidLeaf {
	}

	private interface Deep {
	}

	private interface DeepLeaf {
	}

	private static final class FakeCockpitModelServices implements CockpitModelServices {
		CockpitSnapshot last;
		final List<Boolean> connectedHistory = new ArrayList<>();

		@Override
		public void setSnapshot(final CockpitSnapshot snapshot) {
			this.last = snapshot;
		}

		@Override
		public void setConnected(final boolean connected) {
			this.connectedHistory.add(connected);
		}
	}

	private static final class FakeObservedLifecycleReadServices implements ObservedLifecycleReadServices {
		final Map<Class<?>, ComponentState> states = new HashMap<>();

		@Override
		public ComponentState readComponentState(final Class<?> componentType) {
			return this.states.get(componentType);
		}
	}

	private static final class FakeObservedLifecycleControlServices implements ObservedLifecycleControlServices {
		final List<String> calls = new ArrayList<>();
		RuntimeException failWith;

		private void record(final String what, final Class<?> componentType) {
			this.calls.add(what + " " + componentType.getSimpleName());
			if (this.failWith != null) {
				throw this.failWith;
			}
		}

		@Override
		public void instantiateComponent(final Class<?> componentType) {
			this.record("instantiate", componentType);
		}

		@Override
		public void initializeComponent(final Class<?> componentType) {
			this.record("initialize", componentType);
		}

		@Override
		public void activateComponent(final Class<?> componentType) {
			this.record("activate", componentType);
		}

		@Override
		public void passivateComponent(final Class<?> componentType) {
			this.record("passivate", componentType);
		}

		@Override
		public void removeComponent(final Class<?> componentType) {
			this.record("remove", componentType);
		}

		@Override
		public void resetFailedComponent(final Class<?> componentType) {
			this.record("reset", componentType);
		}

		@Override
		public void undeployComponent(final Class<?> componentType) {
			this.record("undeploy", componentType);
		}
	}

	private CockpitControllerImpl controller;
	private FakeCockpitModelServices modelServices;
	private FakeObservedLifecycleReadServices readServices;
	private FakeObservedLifecycleControlServices controlServices;
	private ComponentDiagramStructure liveStructure;
	private Map<Class<?>, ComponentStatus> componentStatuses = Map.of();

	@Before
	public void setUp() throws Exception {
		this.controller = new CockpitControllerImpl();
		this.modelServices = new FakeCockpitModelServices();
		this.readServices = new FakeObservedLifecycleReadServices();
		this.controlServices = new FakeObservedLifecycleControlServices();
		this.setField("useModelServices", this.modelServices);
		this.setField("lifecycleReadServices", this.readServices);
		this.setField("lifecycleControlServices", this.controlServices);
		this.setField("useComponentDiagramModelReadServices", ComponentDiagramModelReadServices.class.cast(
				Proxy.newProxyInstance(ComponentDiagramModelReadServices.class.getClassLoader(),
						new Class<?>[] { ComponentDiagramModelReadServices.class }, (proxy, method, args) -> {
							switch (method.getName()) {
							case "getLiveStructure":
								return this.liveStructure;
							case "getComponentStatuses":
								return this.componentStatuses;
							default:
								return null;
							}
						})));
	}

	/** Publishes a new live structure, then signals it as the component diagram model does. */
	private void liveStructureChanged(final ComponentDiagramStructure structure) {
		this.liveStructure = structure;
		this.controller.onLiveStructureChanged();
	}

	/** Publishes new component statuses, then signals them as the component diagram model does. */
	private void componentStatusesChanged(final Map<Class<?>, ComponentStatus> statuses) {
		this.componentStatuses = statuses;
		this.controller.onComponentStatusesChanged();
	}

	private void setField(final String name, final Object value) throws Exception {
		Field field = CockpitControllerImpl.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(this.controller, value);
	}

	private ComponentDiagramStructure twoCompositesStructure() {
		Set<Class<?>> types = Set.of(CompositeA.class, LeafA1.class, LeafA2.class, CompositeB.class, LeafB1.class,
				StandaloneLeaf.class, RawLeaf.class);
		Set<ComponentDiagramEdge> edges = Set.of(
				new ComponentDiagramEdge(CompositeA.class, LeafA1.class, ComponentPortKind.COMPONENT_TYPE, null),
				new ComponentDiagramEdge(CompositeA.class, LeafA2.class, ComponentPortKind.COMPONENT_TYPE, null),
				new ComponentDiagramEdge(CompositeB.class, LeafB1.class, ComponentPortKind.COMPONENT_TYPE, null),
				new ComponentDiagramEdge(StandaloneLeaf.class, LeafA1.class, ComponentPortKind.SERVICE,
						SomeService.class));
		return new ComponentDiagramStructure(types, edges, Set.of(RawLeaf.class));
	}

	/** TopA holds TopLeaf and Mid, Mid holds MidLeaf and Deep, Deep holds DeepLeaf; CompositeB stands beside TopA. */
	private ComponentDiagramStructure nestedStructure() {
		Set<Class<?>> types = Set.of(TopA.class, TopLeaf.class, Mid.class, MidLeaf.class, Deep.class, DeepLeaf.class,
				CompositeB.class, LeafB1.class);
		Set<ComponentDiagramEdge> edges = Set.of(
				new ComponentDiagramEdge(TopA.class, TopLeaf.class, ComponentPortKind.COMPONENT_TYPE, null),
				new ComponentDiagramEdge(TopA.class, Mid.class, ComponentPortKind.COMPONENT_TYPE, null),
				new ComponentDiagramEdge(Mid.class, MidLeaf.class, ComponentPortKind.COMPONENT_TYPE, null),
				new ComponentDiagramEdge(Mid.class, Deep.class, ComponentPortKind.COMPONENT_TYPE, null),
				new ComponentDiagramEdge(Deep.class, DeepLeaf.class, ComponentPortKind.COMPONENT_TYPE, null),
				new ComponentDiagramEdge(CompositeB.class, LeafB1.class, ComponentPortKind.COMPONENT_TYPE, null));
		return new ComponentDiagramStructure(types, edges, Set.of());
	}

	private static CockpitSnapshot.Slice slice(final CockpitSnapshot snapshot, final Class<?> compositeType) {
		return snapshot.slices().stream().filter(slice -> slice.compositeType() == compositeType).findFirst()
				.orElseThrow();
	}

	private static CockpitSnapshot.Channel channel(final CockpitSnapshot.Slice slice, final Class<?> componentType) {
		return slice.channels().stream().filter(channel -> channel.componentType() == componentType).findFirst()
				.orElseThrow();
	}

	private ObservedPortRate serviceRate(final Class<?> componentType, final int calls, final int failures,
			final boolean receiving) {
		return new ObservedPortRate(componentType, SomeService.class, ObservedPortKind.SERVICE, receiving, calls,
				failures, -1, failures > 0 ? "someMethod" : null);
	}

	private CockpitSnapshot sample(final List<ObservedPortRate> rates) {
		this.controller.onPortRatesSampled(1_000L, 1_000L, rates);
		return this.modelServices.last;
	}

	private void awaitCommands() throws Exception {
		Field field = CockpitControllerImpl.class.getDeclaredField("commands");
		field.setAccessible(true);
		ExecutorService executor = (ExecutorService) field.get(this.controller);
		Assert.assertNotNull("execute()/executeOnContainer() should have created the executor", executor);
		executor.submit(() -> {
		}).get(2, TimeUnit.SECONDS);
	}

	// --- Structure and state bookkeeping ---

	@Test
	public void liveStructureChangeReadsStateOnlyForNewlySeenComponents() {
		this.readServices.states.put(LeafA1.class, ComponentState.activated);
		this.liveStructureChanged(this.twoCompositesStructure());

		CockpitSnapshot snapshot = this.sample(List.of());
		Assert.assertEquals(Integer.valueOf(1), snapshot.states().get(ComponentState.activated));

		// A later change to the read services must not override a state already captured.
		this.readServices.states.put(LeafA1.class, ComponentState.passivated);
		this.liveStructureChanged(this.twoCompositesStructure());
		snapshot = this.sample(List.of());
		Assert.assertEquals(Integer.valueOf(1), snapshot.states().get(ComponentState.activated));
	}

	@Test
	public void countsExcludeComposites() {
		this.liveStructureChanged(this.twoCompositesStructure());
		CockpitSnapshot snapshot = this.sample(List.of());
		// 7 components - 2 composites - 1 raw leaf = 4 managed leaves, 1 raw leaf.
		Assert.assertEquals(4, snapshot.managedCount());
		Assert.assertEquals(1, snapshot.rawCount());
		Assert.assertEquals(2, snapshot.compositeCount());
	}

	@Test
	public void linksAreCountedPerKind() {
		this.liveStructureChanged(this.twoCompositesStructure());
		CockpitSnapshot snapshot = this.sample(List.of());
		Assert.assertEquals(1, snapshot.serviceLinks());
		Assert.assertEquals(0, snapshot.eventLinks());
		Assert.assertEquals(0, snapshot.parameterLinks());
	}

	@Test
	public void stateCountsExcludeComposites() {
		this.liveStructureChanged(this.twoCompositesStructure());
		this.controller.onComponentStateChanged(CompositeA.class, ComponentState.activated);
		this.controller.onComponentStateChanged(LeafA1.class, ComponentState.activated);
		this.controller.onComponentStateChanged(LeafA2.class, ComponentState.passivated);

		CockpitSnapshot snapshot = this.sample(List.of());
		Assert.assertEquals(Integer.valueOf(1), snapshot.states().get(ComponentState.activated));
		Assert.assertEquals(Integer.valueOf(1), snapshot.states().get(ComponentState.passivated));
	}

	@Test
	public void onContainerConnectedClearsStatesTransitionsAndLog() {
		this.liveStructureChanged(this.twoCompositesStructure());
		this.controller.onComponentStateChanged(LeafA1.class, ComponentState.activated);
		this.controller.onComponentStateChanged(LeafA1.class, ComponentState.passivated);

		this.controller.onContainerConnected();

		CockpitSnapshot snapshot = this.sample(List.of());
		Assert.assertTrue(snapshot.states().isEmpty());
		Assert.assertTrue(snapshot.recentTransitions().isEmpty());
		Assert.assertTrue(snapshot.consoleLog().isEmpty());
		Assert.assertEquals(List.of(true), this.modelServices.connectedHistory);
	}

	@Test
	public void onContainerDisconnectedPublishesAnEmptySnapshotAtOnce() {
		this.liveStructureChanged(this.twoCompositesStructure());
		this.controller.onComponentStateChanged(LeafA1.class, ComponentState.activated);
		this.componentStatusesChanged(Map.of(CompositeA.class, ComponentStatus.ACTIVATED));

		this.controller.onContainerDisconnected();

		// Disconnecting clears everything known of the container and reports it disconnected,
		// without going through onContainerConnected()'s own "connected" notification.
		Assert.assertEquals(List.of(false), this.modelServices.connectedHistory);
		CockpitSnapshot snapshot = this.modelServices.last;
		Assert.assertEquals(0, snapshot.managedCount());
		Assert.assertEquals(0, snapshot.compositeCount());
		Assert.assertEquals(0, snapshot.serviceLinks());
		Assert.assertTrue(snapshot.states().isEmpty());
		Assert.assertTrue(snapshot.composites().isEmpty());
		Assert.assertTrue(snapshot.consoleLog().isEmpty());
	}

	// --- Transitions and the console's log ---

	@Test
	public void stateChangeIsIgnoredWhenTheStateDoesNotActuallyChange() {
		this.controller.onComponentStateChanged(LeafA1.class, ComponentState.activated);
		this.controller.onComponentStateChanged(LeafA1.class, ComponentState.activated);

		CockpitSnapshot snapshot = this.sample(List.of());
		Assert.assertEquals(1, snapshot.recentTransitions().size());
	}

	@Test
	public void transitionsAreCappedAtTwelveNewestFirst() {
		ComponentState[] cycle = { ComponentState.activated, ComponentState.passivated };
		for (int i = 0; i < 15; i++) {
			this.controller.onComponentStateChanged(LeafA1.class, cycle[i % 2]);
		}
		CockpitSnapshot snapshot = this.sample(List.of());
		Assert.assertEquals(12, snapshot.recentTransitions().size());
		Assert.assertEquals(cycle[14 % 2], snapshot.recentTransitions().get(0).to());
	}

	@Test
	public void failedStateChangeIsLoggedAsAFailure() {
		this.controller.onComponentStateChanged(LeafA1.class, ComponentState.failed);
		CockpitSnapshot snapshot = this.sample(List.of());
		CockpitSnapshot.LogEntry entry = snapshot.consoleLog().get(0);
		Assert.assertEquals(Phrase.of("log.transition", "LeafA1", Phrase.of("state.failed")), entry.text());
		Assert.assertTrue(entry.failed());
	}

	// --- Port rates: services/events/parameters, failures, hot ports ---

	@Test
	public void ratesForComponentsOutsideTheLiveStructureAreIgnored() {
		this.liveStructureChanged(this.twoCompositesStructure());
		CockpitSnapshot snapshot = this.sample(List.of(this.serviceRate(RawLeaf.class, 5, 0, true)));
		// RawLeaf is in the structure, so it counts; an unknown type must not.
		Assert.assertEquals(5.0, snapshot.servicesPerSecond(), 0.001);

		interface Unknown {
		}
		CockpitSnapshot ignored = this.sample(List.of(this.serviceRate(Unknown.class, 5, 0, true)));
		Assert.assertEquals(0.0, ignored.servicesPerSecond(), 0.001);
	}

	@Test
	public void onlyReceivingFailuresAreReportedAsNewFailures() {
		this.liveStructureChanged(this.twoCompositesStructure());
		CockpitSnapshot snapshot = this.sample(List.of(this.serviceRate(LeafA1.class, 3, 2, true),
				this.serviceRate(LeafA2.class, 3, 2, false)));
		Assert.assertEquals(1, snapshot.newFailures().size());
		Assert.assertEquals(LeafA1.class, snapshot.newFailures().get(0).owner());
		Assert.assertEquals(2, snapshot.newFailures().get(0).count());
	}

	@Test
	public void hotPortsAreSortedDescendingAndCappedAtEight() {
		this.liveStructureChanged(this.twoCompositesStructure());
		List<ObservedPortRate> rates = new ArrayList<>();
		// Reuse the structure's own component types so every rate is actually counted.
		Class<?>[] owners = { LeafA1.class, LeafA2.class, LeafB1.class, StandaloneLeaf.class, RawLeaf.class };
		for (int i = 0; i < owners.length; i++) {
			rates.add(this.serviceRate(owners[i], i + 1, 0, true));
		}
		CockpitSnapshot snapshot = this.sample(rates);
		Assert.assertTrue(snapshot.hottestPorts().size() <= 8);
		for (int i = 1; i < snapshot.hottestPorts().size(); i++) {
			Assert.assertTrue(
					snapshot.hottestPorts().get(i - 1).callsPerSecond() >= snapshot.hottestPorts().get(i).callsPerSecond());
		}
	}

	@Test
	public void compositeMessagesPerSecondAggregatesTheBoundaryCrossingCall() {
		this.liveStructureChanged(this.twoCompositesStructure());
		// LeafA1 receives StandaloneLeaf's calls, crossing CompositeA's boundary.
		CockpitSnapshot snapshot = this.sample(List.of(this.serviceRate(LeafA1.class, 4, 0, true)));
		CockpitSnapshot.CompositeSummary compositeA = snapshot.composites().stream()
				.filter(summary -> summary.compositeType() == CompositeA.class).findFirst().orElseThrow();
		Assert.assertEquals(4.0, compositeA.messagesPerSecond(), 0.001);
	}

	// --- Composites ---

	@Test
	public void compositeSummaryCarriesLeafStatesAndStatus() {
		this.liveStructureChanged(this.twoCompositesStructure());
		this.controller.onComponentStateChanged(LeafA1.class, ComponentState.activated);
		this.componentStatusesChanged(Map.of(CompositeA.class, ComponentStatus.DEGRADED));

		CockpitSnapshot snapshot = this.sample(List.of());
		CockpitSnapshot.CompositeSummary compositeA = snapshot.composites().stream()
				.filter(summary -> summary.compositeType() == CompositeA.class).findFirst().orElseThrow();
		Assert.assertEquals(List.of(ComponentState.activated), compositeA.componentStates());
		Assert.assertEquals(ComponentStatus.DEGRADED, compositeA.status());
	}

	@Test
	public void anInternalRootCompositeStaysAlongsideTheApplicationOnes() {
		Set<Class<?>> types = Set.of(CompositeA.class, LeafA1.class, InternalComposite.class, InternalLeaf.class);
		Set<ComponentDiagramEdge> edges = Set.of(
				new ComponentDiagramEdge(CompositeA.class, LeafA1.class, ComponentPortKind.COMPONENT_TYPE, null),
				new ComponentDiagramEdge(InternalComposite.class, InternalLeaf.class, ComponentPortKind.COMPONENT_TYPE,
						null));
		this.liveStructureChanged(new ComponentDiagramStructure(types, edges, Set.of()));

		CockpitSnapshot snapshot = this.sample(List.of());
		Set<Class<?>> compositeTypes = new java.util.HashSet<>();
		snapshot.composites().forEach(summary -> compositeTypes.add(summary.compositeType()));
		Assert.assertTrue(compositeTypes.contains(CompositeA.class));
		Assert.assertTrue(compositeTypes.contains(InternalComposite.class));
	}

	// --- Console slices ---

	@Test
	public void standaloneComponentsFormTheirOwnSliceListedFirst() {
		this.liveStructureChanged(this.twoCompositesStructure());
		CockpitSnapshot snapshot = this.sample(List.of());
		CockpitSnapshot.Slice first = snapshot.slices().get(0);
		Assert.assertNull(first.compositeType());
		boolean hasStandalone = first.channels().stream().anyMatch(channel -> channel.componentType() == StandaloneLeaf.class);
		Assert.assertTrue(hasStandalone);
	}

	@Test
	public void sliceDependentsCaptureTheOutsideServiceUser() {
		this.liveStructureChanged(this.twoCompositesStructure());
		CockpitSnapshot snapshot = this.sample(List.of());
		CockpitSnapshot.Slice compositeA = snapshot.slices().stream()
				.filter(slice -> slice.compositeType() == CompositeA.class).findFirst().orElseThrow();
		Assert.assertEquals(1, compositeA.dependents().size());
		Assert.assertEquals(StandaloneLeaf.class, compositeA.dependents().get(0).componentType());
		Assert.assertEquals(SomeService.class, compositeA.dependents().get(0).portType());
	}

	@Test
	public void componentsThatLeftTheLiveStructureAreKeptUntilUndeployed() {
		this.liveStructureChanged(this.twoCompositesStructure());
		this.controller.onComponentStateChanged(StandaloneLeaf.class, ComponentState.activated);
		// Still readable from the observed container once it leaves the live structure, since
		// it has not been undeployed.
		this.readServices.states.put(StandaloneLeaf.class, ComponentState.activated);

		// StandaloneLeaf disappears from the live structure without being undeployed.
		this.liveStructureChanged(new ComponentDiagramStructure(
				Set.of(CompositeA.class, LeafA1.class, LeafA2.class), Set.of(
						new ComponentDiagramEdge(CompositeA.class, LeafA1.class, ComponentPortKind.COMPONENT_TYPE, null),
						new ComponentDiagramEdge(CompositeA.class, LeafA2.class, ComponentPortKind.COMPONENT_TYPE, null)),
				Set.of()));

		CockpitSnapshot snapshot = this.sample(List.of());
		boolean stillShown = snapshot.slices().get(0).channels().stream()
				.anyMatch(channel -> channel.componentType() == StandaloneLeaf.class);
		Assert.assertTrue(stillShown);
	}

	// --- Console commands ---

	@Test
	public void executeRunsTheCommandAndLogsItsCompletion() throws Exception {
		this.controller.execute(LifecycleCommand.ACTIVATE, LeafA1.class);
		this.awaitCommands();

		Assert.assertEquals(List.of("activate LeafA1"), this.controlServices.calls);
		CockpitSnapshot snapshot = this.sample(List.of());
		boolean logged = snapshot.consoleLog().stream().anyMatch(entry -> entry.text()
				.equals(Phrase.of("log.run", Phrase.of(LifecycleCommand.ACTIVATE), "LeafA1")));
		Assert.assertTrue(logged);
	}

	@Test
	public void aCommandFailureWithoutAMessageIsLoggedByTheFailureName() throws Exception {
		this.controlServices.failWith = new IllegalStateException();
		this.controller.execute(LifecycleCommand.PASSIVATE, LeafA1.class);
		this.awaitCommands();

		CockpitSnapshot snapshot = this.sample(List.of());
		boolean failureLogged = snapshot.consoleLog().stream()
				.anyMatch(entry -> entry.failed() && entry.text().arguments().contains("IllegalStateException"));
		Assert.assertTrue(failureLogged);
	}

	@Test
	public void executeLogsAFailureInsteadOfThrowing() throws Exception {
		this.controlServices.failWith = new IllegalStateException("boom");
		this.controller.execute(LifecycleCommand.PASSIVATE, LeafA1.class);
		this.awaitCommands();

		CockpitSnapshot snapshot = this.sample(List.of());
		boolean failureLogged = snapshot.consoleLog().stream()
				.anyMatch(entry -> entry.failed() && entry.text().arguments().contains("boom"));
		Assert.assertTrue(failureLogged);
	}

	@Test
	public void executeOnContainerActivatesProvidersBeforeTheirUsers() throws Exception {
		// StandaloneLeaf uses LeafA1's service; both are roots of their own tree.
		this.liveStructureChanged(new ComponentDiagramStructure(Set.of(LeafA1.class, StandaloneLeaf.class),
				Set.of(new ComponentDiagramEdge(StandaloneLeaf.class, LeafA1.class, ComponentPortKind.SERVICE,
						SomeService.class)),
				Set.of()));
		this.readServices.states.put(LeafA1.class, ComponentState.passivated);
		this.readServices.states.put(StandaloneLeaf.class, ComponentState.passivated);

		this.controller.executeOnContainer(LifecycleCommand.ACTIVATE);
		this.awaitCommands();

		Assert.assertEquals(List.of("activate LeafA1", "activate StandaloneLeaf"), this.controlServices.calls);
	}

	@Test
	public void executeOnContainerPassivatesAnActiveRootBeforeRemovingIt() throws Exception {
		this.liveStructureChanged(
				new ComponentDiagramStructure(Set.of(LeafA1.class), Set.of(), Set.of()));
		this.readServices.states.put(LeafA1.class, ComponentState.activated);
		// Passivating updates the read state the controller re-checks before removing.
		this.controlServices.calls.clear();

		this.controller.executeOnContainer(LifecycleCommand.REMOVE);
		this.awaitCommands();

		Assert.assertTrue(this.controlServices.calls.contains("passivate LeafA1"));
	}

	@Test
	public void executeOnContainerSkipsARootNotInAnAllowedState() throws Exception {
		this.liveStructureChanged(
				new ComponentDiagramStructure(Set.of(LeafA1.class), Set.of(), Set.of()));
		this.readServices.states.put(LeafA1.class, ComponentState.activated);

		this.controller.executeOnContainer(LifecycleCommand.ACTIVATE);
		this.awaitCommands();

		Assert.assertTrue(this.controlServices.calls.isEmpty());
	}

	@Test
	public void executeOnContainerActivatesARemovedRootLikeCovalentLifecycleAllows() throws Exception {
		this.liveStructureChanged(
				new ComponentDiagramStructure(Set.of(LeafA1.class), Set.of(), Set.of()));
		this.readServices.states.put(LeafA1.class, ComponentState.removed);

		this.controller.executeOnContainer(LifecycleCommand.ACTIVATE);
		this.awaitCommands();

		Assert.assertEquals(List.of("activate LeafA1"), this.controlServices.calls);
	}

	@Test
	public void executeOnContainerOnlyActsOnTopLevelComponentsExcludingRawOnes() throws Exception {
		this.liveStructureChanged(this.twoCompositesStructure());
		this.readServices.states.put(CompositeA.class, ComponentState.activated);
		this.readServices.states.put(CompositeB.class, ComponentState.activated);
		this.readServices.states.put(StandaloneLeaf.class, ComponentState.passivated);
		this.readServices.states.put(RawLeaf.class, ComponentState.passivated);

		this.controller.executeOnContainer(LifecycleCommand.ACTIVATE);
		this.awaitCommands();

		// CompositeA/CompositeB are roots too but not in an allowed state; LeafA1/LeafA2/LeafB1
		// are not roots (they are inside a composite); RawLeaf is raw and excluded outright,
		// even though its own state would otherwise allow the command.
		Assert.assertEquals(List.of("activate StandaloneLeaf"), this.controlServices.calls);
	}

	@Test
	public void commandsExecutorIsCreatedOnceAndStoppedByPassivation() throws Exception {
		this.controller.execute(LifecycleCommand.ACTIVATE, LeafA1.class);
		this.awaitCommands();

		Field field = CockpitControllerImpl.class.getDeclaredField("commands");
		field.setAccessible(true);
		ExecutorService first = (ExecutorService) field.get(this.controller);
		Assert.assertNotNull(first);

		this.controller.stopCommands();
		Assert.assertNull(field.get(this.controller));
		Assert.assertTrue(first.isShutdown());
	}

	// --- Console levels ---

	@Test
	public void aSliceShowsItsDirectChildrenWithCompositesAsChannels() {
		this.liveStructureChanged(this.nestedStructure());
		this.controller.onComponentStateChanged(MidLeaf.class, ComponentState.activated);
		CockpitSnapshot snapshot = this.sample(List.of());

		Assert.assertEquals(List.of(), snapshot.consoleFocus());
		CockpitSnapshot.Slice topA = slice(snapshot, TopA.class);
		Assert.assertTrue(topA.navigable());
		Assert.assertEquals(2, topA.channels().size());
		Assert.assertFalse(channel(topA, TopLeaf.class).composite());
		CockpitSnapshot.Channel mid = channel(topA, Mid.class);
		Assert.assertTrue(mid.composite());
		Assert.assertEquals(2, mid.componentCount());
		Assert.assertEquals(List.of(ComponentState.activated), mid.componentStates());
		Assert.assertFalse(slice(snapshot, CompositeB.class).navigable());
	}

	@Test
	public void enteringACompositeShowsItsChildrenAsSlices() {
		this.liveStructureChanged(this.nestedStructure());
		this.sample(List.of());

		this.controller.enterComposite(TopA.class);
		CockpitSnapshot snapshot = this.modelServices.last;

		Assert.assertEquals(List.of(TopA.class), snapshot.consoleFocus());
		Assert.assertNull(snapshot.slices().get(0).compositeType());
		Assert.assertEquals(List.of(TopLeaf.class),
				snapshot.slices().get(0).channels().stream().map(CockpitSnapshot.Channel::componentType).toList());
		Assert.assertEquals(2, snapshot.slices().size());
		Assert.assertTrue(slice(snapshot, Mid.class).navigable());
	}

	@Test
	public void enteringACompositeHoldingOnlyComponentsShowsTheLevelWhereItIsASlice() {
		this.liveStructureChanged(this.nestedStructure());
		this.sample(List.of());

		this.controller.enterComposite(Deep.class);
		CockpitSnapshot snapshot = this.modelServices.last;

		Assert.assertEquals(List.of(TopA.class, Mid.class), snapshot.consoleFocus());
		Assert.assertFalse(slice(snapshot, Deep.class).navigable());
	}

	@Test
	public void showConsoleLevelGoesBackUp() {
		this.liveStructureChanged(this.nestedStructure());
		this.sample(List.of());
		this.controller.enterComposite(Deep.class);

		this.controller.showConsoleLevel(1);
		Assert.assertEquals(List.of(TopA.class), this.modelServices.last.consoleFocus());
		this.controller.showConsoleLevel(0);
		Assert.assertEquals(List.of(), this.modelServices.last.consoleFocus());
	}

	@Test
	public void theFocusIsCutWhereAnEnteredCompositeLeft() {
		this.liveStructureChanged(this.nestedStructure());
		this.sample(List.of());
		this.controller.enterComposite(Deep.class);

		// Mid and what it holds are undeployed: they leave the structure and cannot be read any more.
		this.liveStructureChanged(new ComponentDiagramStructure(
				Set.of(TopA.class, TopLeaf.class, CompositeB.class, LeafB1.class),
				Set.of(new ComponentDiagramEdge(TopA.class, TopLeaf.class, ComponentPortKind.COMPONENT_TYPE, null),
						new ComponentDiagramEdge(CompositeB.class, LeafB1.class, ComponentPortKind.COMPONENT_TYPE,
								null)),
				Set.of()));

		Assert.assertEquals(List.of(TopA.class), this.sample(List.of()).consoleFocus());
	}

	@Test
	public void theContainerCountCoversEveryLevel() {
		this.liveStructureChanged(this.nestedStructure());
		this.controller.onComponentStateChanged(DeepLeaf.class, ComponentState.activated);
		this.controller.onComponentStateChanged(LeafB1.class, ComponentState.activated);

		CockpitSnapshot snapshot = this.sample(List.of());
		Assert.assertEquals(2, snapshot.consoleActivated());
		Assert.assertEquals(4, snapshot.consoleDriven());
	}
}
