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
import org.opales.covalent.supervision.containermanager.raw.ObservedContainerControl;
import org.opales.covalent.supervision.observed.ObservedLifecycleControlServices;
import org.opales.covalent.supervision.observed.ObservedLifecycleReadServices;
import org.opales.covalent.supervision.observed.ObservedPortKind;
import org.opales.covalent.supervision.observed.ObservedPortRate;

/**
 * Verifies the parts of {@link CockpitControllerImpl} the figures tests leave aside, with
 * hand-written fakes and no container: the connection taken at activation, the split of rates by
 * port kind, composite boundaries crossed by events, dependents through events and parameters,
 * the console's levels (single root, internal composites, invalid levels), the bounded console log,
 * the state the container is really in, and every lifecycle command the console can run.
 */
public class CockpitControllerImplConsoleTests {

	private interface CompositeA {
	}

	private interface LeafA1 {
	}

	private interface LeafA2 {
	}

	private interface StandaloneLeaf {
	}

	private interface SomeService {
	}

	private interface Root {
	}

	private interface SubA {
	}

	private interface SubB {
	}

	private interface SubALeaf {
	}

	private interface SubBLeaf {
	}

	private interface TopA {
	}

	private interface Mid {
	}

	private interface MidLeaf {
	}

	private interface Deep {
	}

	private interface DeepLeaf {
	}

	private interface CompositeB {
	}

	private interface LeafB1 {
	}

	@Internal
	private interface InternalComposite {
	}

	private interface InternalSub {
	}

	@Internal
	private interface InternalLeaf {
	}

	private static final class FakeModel implements CockpitModelServices {
		CockpitSnapshot last;
		int published;
		final List<Boolean> connectedHistory = new ArrayList<>();

		@Override
		public void setSnapshot(final CockpitSnapshot snapshot) {
			this.last = snapshot;
			this.published++;
		}

		@Override
		public void setConnected(final boolean connected) {
			this.connectedHistory.add(connected);
		}
	}

	private static final class FakeRead implements ObservedLifecycleReadServices {
		final Map<Class<?>, ComponentState> states = new HashMap<>();

		@Override
		public ComponentState readComponentState(final Class<?> componentType) {
			return this.states.get(componentType);
		}
	}

	private static final class FakeControl implements ObservedLifecycleControlServices {
		final List<String> calls = new ArrayList<>();

		@Override
		public void instantiateComponent(final Class<?> componentType) {
			this.calls.add("instantiate " + componentType.getSimpleName());
		}

		@Override
		public void initializeComponent(final Class<?> componentType) {
			this.calls.add("initialize " + componentType.getSimpleName());
		}

		@Override
		public void activateComponent(final Class<?> componentType) {
			this.calls.add("activate " + componentType.getSimpleName());
		}

		@Override
		public void passivateComponent(final Class<?> componentType) {
			this.calls.add("passivate " + componentType.getSimpleName());
		}

		@Override
		public void removeComponent(final Class<?> componentType) {
			this.calls.add("remove " + componentType.getSimpleName());
		}

		@Override
		public void resetFailedComponent(final Class<?> componentType) {
			this.calls.add("reset " + componentType.getSimpleName());
		}

		@Override
		public void undeployComponent(final Class<?> componentType) {
			this.calls.add("undeploy " + componentType.getSimpleName());
		}
	}

	private CockpitControllerImpl controller;
	private FakeModel model;
	private FakeRead read;
	private FakeControl control;
	private ComponentDiagramStructure live;

	@Before
	public void setUp() throws Exception {
		this.controller = new CockpitControllerImpl();
		this.model = new FakeModel();
		this.read = new FakeRead();
		this.control = new FakeControl();
		this.set("useModelServices", this.model);
		this.set("lifecycleReadServices", this.read);
		this.set("lifecycleControlServices", this.control);
		this.set("useComponentDiagramModelReadServices", ComponentDiagramModelReadServices.class.cast(
				Proxy.newProxyInstance(ComponentDiagramModelReadServices.class.getClassLoader(),
						new Class<?>[] { ComponentDiagramModelReadServices.class }, (proxy, method, args) -> {
							if ("getLiveStructure".equals(method.getName())) {
								return this.live;
							}
							return "getComponentStatuses".equals(method.getName()) ? Map.of() : null;
						})));
	}

	private void set(final String name, final Object value) throws Exception {
		Field field = CockpitControllerImpl.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(this.controller, value);
	}

	private void structure(final ComponentDiagramStructure structure) {
		this.live = structure;
		this.controller.onLiveStructureChanged();
	}

	private CockpitSnapshot sample(final ObservedPortRate... rates) {
		this.controller.onPortRatesSampled(1_000L, 1_000L, List.of(rates));
		return this.model.last;
	}

	private void awaitCommands() throws Exception {
		Field field = CockpitControllerImpl.class.getDeclaredField("commands");
		field.setAccessible(true);
		ExecutorService executor = (ExecutorService) field.get(this.controller);
		executor.submit(() -> {
		}).get(2, TimeUnit.SECONDS);
	}

	private static ComponentDiagramEdge contains(final Class<?> composite, final Class<?> child) {
		return new ComponentDiagramEdge(composite, child, ComponentPortKind.COMPONENT_TYPE, null);
	}

	private static ObservedPortRate rate(final Class<?> owner, final ObservedPortKind kind, final boolean receiving,
			final int calls) {
		return new ObservedPortRate(owner, SomeService.class, kind, receiving, calls, 0, 1_000, null);
	}

	/** CompositeA holds LeafA1 and LeafA2; StandaloneLeaf stands alone. */
	private ComponentDiagramStructure withEdges(final ComponentDiagramEdge... extra) {
		Set<ComponentDiagramEdge> edges = new java.util.HashSet<>(List.of(contains(CompositeA.class, LeafA1.class),
				contains(CompositeA.class, LeafA2.class)));
		edges.addAll(List.of(extra));
		return new ComponentDiagramStructure(
				Set.of(CompositeA.class, LeafA1.class, LeafA2.class, StandaloneLeaf.class), edges, Set.of());
	}

	private static CockpitSnapshot.Slice slice(final CockpitSnapshot snapshot, final Class<?> composite) {
		return snapshot.slices().stream().filter(slice -> slice.compositeType() == composite).findFirst().orElseThrow();
	}

	// --- Connection and listeners ---

	@Test
	public void activationTakesTheConnectionOnlyWhenAContainerIsAlreadyObserved() throws Exception {
		this.set("useObservedContainerControl", Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { ObservedContainerControl.class }, (proxy, method, args) -> null));
		this.controller.readConnection();
		Assert.assertTrue(this.model.connectedHistory.isEmpty());

		this.set("useObservedContainerControl", Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { ObservedContainerControl.class },
				(proxy, method, args) -> "getObservedContainerName".equals(method.getName()) ? "observed" : null));
		this.controller.readConnection();
		Assert.assertEquals(List.of(true), this.model.connectedHistory);
	}

	@Test
	public void structureChangesWhileConnectedPublishNoSampleOfTheirOwn() {
		this.controller.onContainerConnected();
		this.structure(this.withEdges());
		Assert.assertNull(this.model.last);
		Assert.assertEquals(4 - 1, this.sample().managedCount());
	}

	@Test
	public void structureChangesWhileDisconnectedPublishTheFiguresAtOnce() {
		this.structure(this.withEdges());
		Assert.assertEquals(1, this.model.published);
		Assert.assertEquals(3, this.model.last.managedCount());
	}

	@Test
	public void eventsOfTheOtherDiagramsDoNotChangeTheCockpit() {
		this.structure(this.withEdges());
		int before = this.model.published;
		this.controller.onSessionCreated(null);
		this.controller.onTopologyChanged(null);
		this.controller.onSelectionChanged(null, Set.of());
		this.controller.onPortActivityChanged(LeafA1.class, SomeService.class, ComponentPortKind.SERVICE, true);
		this.controller.onActivitySampled(0, Map.of());
		this.controller.onDependenciesVisibilityChanged(null, LeafA1.class, true);
		this.controller.onFocusPathChanged(null, List.of());
		this.controller.onFlashComponentChanged(null, LeafA1.class);
		this.controller.onLinkHighlightRequested(null, LeafA1.class, LeafA2.class);
		this.controller.onSessionClosed(null);
		Assert.assertEquals(before, this.model.published);
		Assert.assertEquals(3, this.sample().managedCount());
	}

	@Test
	public void theStateTheContainerIsReallyInPrevailsOverTheOneReported() {
		this.read.states.put(LeafA1.class, ComponentState.activated);
		this.controller.onComponentStateChanged(LeafA1.class, ComponentState.passivated);
		Assert.assertEquals(ComponentState.activated, this.sample().recentTransitions().get(0).to());
	}

	@Test
	public void theReportedStateIsKeptWhenTheContainerCannotBeRead() {
		this.controller.onComponentStateChanged(LeafA1.class, ComponentState.failed);
		CockpitSnapshot snapshot = this.sample();
		Assert.assertEquals(ComponentState.failed, snapshot.recentTransitions().get(0).to());
		Assert.assertNull(snapshot.recentTransitions().get(0).from());
	}

	@Test
	public void theConsoleLogKeepsTheFortyNewestEntries() {
		for (int index = 0; index < 50; index++) {
			this.controller.onComponentStateChanged(LeafA1.class,
					index % 2 == 0 ? ComponentState.activated : ComponentState.passivated);
		}
		CockpitSnapshot snapshot = this.sample();
		Assert.assertEquals(40, snapshot.consoleLog().size());
		Assert.assertEquals(Phrase.of("log.transition", "LeafA1", Phrase.of("state.passivated")),
				snapshot.consoleLog().get(0).text());
	}

	// --- Rates ---

	@Test
	public void ratesAreSplitByPortKindAndOnlyReceivedCallsAreCounted() {
		this.structure(this.withEdges());
		CockpitSnapshot snapshot = this.sample(rate(StandaloneLeaf.class, ObservedPortKind.SERVICE, true, 4),
				rate(LeafA1.class, ObservedPortKind.EVENT, true, 3), rate(LeafA2.class, ObservedPortKind.PARAMETER, true, 2),
				rate(StandaloneLeaf.class, ObservedPortKind.SERVICE, false, 10),
				rate(LeafA2.class, ObservedPortKind.SERVICE, true, 0));
		Assert.assertEquals(4, snapshot.servicesPerSecond(), 1e-9);
		Assert.assertEquals(3, snapshot.eventsPerSecond(), 1e-9);
		Assert.assertEquals(2, snapshot.parametersPerSecond(), 1e-9);
		Assert.assertEquals(3, snapshot.hottestPorts().size());
		Assert.assertEquals(ComponentPortKind.SERVICE, snapshot.hottestPorts().get(0).kind());
		Assert.assertEquals(ComponentPortKind.EVENT, snapshot.hottestPorts().get(1).kind());
		Assert.assertEquals(ComponentPortKind.PARAMETER, snapshot.hottestPorts().get(2).kind());
	}

	@Test
	public void aProducedEventLeavingAStructureCountsForTheCompositeItLeaves() {
		this.structure(this.withEdges(
				new ComponentDiagramEdge(LeafA1.class, StandaloneLeaf.class, ComponentPortKind.EVENT, SomeService.class),
				new ComponentDiagramEdge(LeafA2.class, StandaloneLeaf.class, ComponentPortKind.SERVICE, null)));
		CockpitSnapshot snapshot = this.sample(rate(LeafA1.class, ObservedPortKind.EVENT, false, 5),
				rate(LeafA2.class, ObservedPortKind.SERVICE, false, 7));
		CockpitSnapshot.CompositeSummary composite = snapshot.composites().stream()
				.filter(summary -> summary.compositeType() == CompositeA.class).findFirst().orElseThrow();
		Assert.assertEquals(5, composite.messagesPerSecond(), 1e-9);
	}

	@Test
	public void callersOutsideAreDependentsOfTheCompositeThroughEventsAndParameters() {
		this.structure(this.withEdges(
				new ComponentDiagramEdge(LeafA1.class, StandaloneLeaf.class, ComponentPortKind.EVENT, SomeService.class),
				new ComponentDiagramEdge(StandaloneLeaf.class, LeafA2.class, ComponentPortKind.PARAMETER, SomeService.class),
				new ComponentDiagramEdge(StandaloneLeaf.class, LeafA2.class, ComponentPortKind.SERVICE, null)));
		CockpitSnapshot.Slice slice = slice(this.sample(), CompositeA.class);
		Assert.assertEquals(List.of(new CockpitSnapshot.Dependent(StandaloneLeaf.class, SomeService.class)),
				slice.dependents());
	}

	// --- Console levels ---

	private ComponentDiagramStructure singleRootStructure() {
		return new ComponentDiagramStructure(
				Set.of(Root.class, SubA.class, SubB.class, SubALeaf.class, SubBLeaf.class),
				Set.of(contains(Root.class, SubA.class), contains(Root.class, SubB.class),
						contains(SubA.class, SubALeaf.class), contains(SubB.class, SubBLeaf.class)),
				Set.of());
	}

	@Test
	public void aSingleRootCompositeHoldingCompositesShowsItsChildrenAsTheTopLevel() {
		this.structure(this.singleRootStructure());
		CockpitSnapshot snapshot = this.sample();
		Set<Class<?>> sliceComposites = new java.util.HashSet<>();
		snapshot.slices().forEach(slice -> sliceComposites.add(slice.compositeType()));
		Assert.assertEquals(Set.of(SubA.class, SubB.class), sliceComposites);
		Set<Class<?>> summarized = new java.util.HashSet<>();
		snapshot.composites().forEach(summary -> summarized.add(summary.compositeType()));
		Assert.assertEquals(Set.of(SubA.class, SubB.class), summarized);
		Assert.assertTrue(snapshot.consoleFocus().isEmpty());
	}

	private ComponentDiagramStructure nested() {
		return new ComponentDiagramStructure(
				Set.of(TopA.class, Mid.class, MidLeaf.class, Deep.class, DeepLeaf.class, CompositeB.class, LeafB1.class),
				Set.of(contains(TopA.class, Mid.class), contains(Mid.class, MidLeaf.class), contains(Mid.class, Deep.class),
						contains(Deep.class, DeepLeaf.class), contains(CompositeB.class, LeafB1.class)),
				Set.of());
	}

	@Test
	public void enteringBeforeAnySampleKeepsTheLevelForTheFirstOne() {
		this.controller.onContainerConnected();
		this.structure(this.nested());
		this.controller.enterComposite(TopA.class);
		Assert.assertNull("nothing to republish yet", this.model.last);
		this.controller.enterComposite(Mid.class);
		Assert.assertEquals(List.of(TopA.class, Mid.class), this.sample().consoleFocus());
	}

	@Test
	public void enteringSomethingThatIsNotACompositeChangesNothing() {
		this.structure(this.nested());
		CockpitSnapshot before = this.sample();
		this.controller.enterComposite(MidLeaf.class);
		Assert.assertSame(before, this.model.last);
	}

	@Test
	public void leavingToALevelThatDoesNotExistChangesNothing() {
		this.structure(this.nested());
		this.sample();
		this.controller.enterComposite(Mid.class);
		CockpitSnapshot entered = this.model.last;
		Assert.assertEquals(List.of(TopA.class, Mid.class), entered.consoleFocus());
		this.controller.showConsoleLevel(-1);
		Assert.assertSame(entered, this.model.last);
		this.controller.showConsoleLevel(2);
		Assert.assertSame(entered, this.model.last);
		this.controller.showConsoleLevel(1);
		Assert.assertEquals(List.of(TopA.class), this.model.last.consoleFocus());
	}

	@Test
	public void aFocusNotRootedAtTheTopLevelIsDropped() throws Exception {
		this.structure(this.nested());
		this.set("consoleFocus", List.of(Mid.class));
		Assert.assertTrue(this.sample().consoleFocus().isEmpty());
	}

	@Test
	public void anInternalRootCompositeCanBeEnteredWhereAnApplicationOneStandsAlongside() {
		this.structure(new ComponentDiagramStructure(
				Set.of(CompositeA.class, LeafA1.class, InternalComposite.class, InternalSub.class, InternalLeaf.class),
				Set.of(contains(CompositeA.class, LeafA1.class), contains(InternalComposite.class, InternalSub.class),
						contains(InternalSub.class, InternalLeaf.class)),
				Set.of()));
		this.sample();
		this.controller.enterComposite(InternalComposite.class);
		CockpitSnapshot snapshot = this.model.last;
		Assert.assertEquals(List.of(InternalComposite.class), snapshot.consoleFocus());
		CockpitSnapshot.Slice inner = slice(snapshot, InternalSub.class);
		Assert.assertTrue(inner.internal());
	}

	// --- Commands ---

	@Test
	public void everyLifecycleCommandCallsTheMatchingService() throws Exception {
		for (LifecycleCommand command : LifecycleCommand.values()) {
			this.controller.execute(command, LeafA1.class);
		}
		this.awaitCommands();
		Assert.assertEquals(List.of("instantiate LeafA1", "initialize LeafA1", "activate LeafA1", "passivate LeafA1",
				"passivate LeafA1", "activate LeafA1", "remove LeafA1", "undeploy LeafA1", "reset LeafA1"),
				this.control.calls);
	}

	@Test
	public void commandsOnTheWholeContainerSkipInternalComponentsAndAreLogged() throws Exception {
		this.structure(new ComponentDiagramStructure(Set.of(LeafA1.class, InternalLeaf.class), Set.of(), Set.of()));
		this.read.states.put(LeafA1.class, ComponentState.passivated);
		this.read.states.put(InternalLeaf.class, ComponentState.passivated);
		this.controller.executeOnContainer(LifecycleCommand.ACTIVATE);
		this.awaitCommands();
		Assert.assertEquals(List.of("activate LeafA1"), this.control.calls);
		Assert.assertTrue(this.sample().consoleLog().stream().anyMatch(entry -> entry.text().equals(Phrase.of("all.ACTIVATE"))));
	}

	@Test
	public void passivatingWithoutAnyCommandRunDoesNothing() {
		this.controller.stopCommands();
		Assert.assertTrue(this.control.calls.isEmpty());
	}

	@Test
	public void commandsRunAgainAfterTheExecutorWasStopped() throws Exception {
		this.controller.execute(LifecycleCommand.ACTIVATE, LeafA1.class);
		this.awaitCommands();
		this.controller.stopCommands();
		this.controller.execute(LifecycleCommand.PASSIVATE, LeafA1.class);
		this.awaitCommands();
		Assert.assertEquals(List.of("activate LeafA1", "passivate LeafA1"), this.control.calls);
	}
}
