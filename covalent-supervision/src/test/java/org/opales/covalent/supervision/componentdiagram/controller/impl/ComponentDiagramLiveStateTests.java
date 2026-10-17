/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.controller.impl;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.container.lifecycle.ComponentState;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.supervision.componentdiagram.controller.ComponentDiagramController;
import org.opales.covalent.supervision.componentdiagram.model.ComponentActivity;
import org.opales.covalent.supervision.componentdiagram.model.ComponentPortKind;
import org.opales.covalent.supervision.componentdiagram.model.ComponentPortRole;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramSnapshot;
import org.opales.covalent.supervision.componentdiagram.model.ComponentStatus;
import org.opales.covalent.supervision.diagrampersistence.SavedView;
import org.opales.covalent.supervision.observed.ObservedPortKind;
import org.opales.covalent.supervision.observed.ObservedPortRate;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;
import org.opales.covalent.supervision.testsupport.observed.CompositeAComponent;
import org.opales.covalent.supervision.testsupport.observed.CompositeBComponent;
import org.opales.covalent.supervision.testsupport.observed.LeafAProviderComponent;
import org.opales.covalent.supervision.testsupport.observed.LeafAUserComponent;
import org.opales.covalent.supervision.testsupport.observed.ObservedComponentDiagramTestSupport;
import org.opales.covalent.supervision.testsupport.observed.SampleServicesA;
import org.opales.covalent.supervision.testsupport.observed.SampleServicesB;
import org.opales.covalent.supervision.testsupport.observed.impl.CompositeAComponentImpl;
import org.opales.covalent.supervision.testsupport.observed.impl.CompositeBComponentImpl;

/**
 * Covers what {@code ComponentDiagramControllerImpl} does with what it is told about the observed
 * container while it runs - port activity, sampled rates, lifecycle states - and the tab commands
 * the other tests leave out: opening and closing tabs, showing a link, and opening or saving views.
 * The live-state updates run on the controller's own worker, so they are awaited, not assumed.
 */
public class ComponentDiagramLiveStateTests extends ObservedComponentDiagramTestSupport {

	private final List<Map<Class<?>, ComponentActivity>> activities = new CopyOnWriteArrayList<>();

	private final List<Class<?>[]> highlightedLinks = new CopyOnWriteArrayList<>();

	@Override
	public void onActivitySampled(final long sampleTimeMillis, final Map<Class<?>, ComponentActivity> sampled) {
		this.activities.add(sampled);
	}

	@Override
	public void onLinkHighlightRequested(final ViewKey sessionKey, final Class<?> sourceType,
			final Class<?> targetType) {
		this.highlightedLinks.add(new Class<?>[] { sourceType, targetType });
	}

	private ComponentDiagramControllerImpl controller() {
		return (ComponentDiagramControllerImpl) this.supervisionRcs.useServices(RegistryReadServices.class)
				.getComponentInstance(ComponentDiagramController.class);
	}

	private void deployCompositesAndStart() {
		this.observed.deployComponents(CompositeAComponentImpl.class, CompositeBComponentImpl.class);
		this.startBoth();
	}

	private static void await(final BooleanSupplier condition) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 5_000;
		while (!condition.getAsBoolean()) {
			Assert.assertTrue("Timed out waiting for the live state worker", System.currentTimeMillis() < deadline);
			Thread.sleep(5);
		}
	}

	// --- Container stop ---

	@Test
	public void anUpdateRunningWhileTheContainerStopsIsDroppedQuietly() throws Exception {
		this.deployCompositesAndStart();
		ComponentDiagramControllerImpl controller = this.controller();
		Method onLiveStateWorker = ComponentDiagramControllerImpl.class.getDeclaredMethod("onLiveStateWorker",
				Runnable.class);
		onLiveStateWorker.setAccessible(true);
		List<Throwable> uncaught = new CopyOnWriteArrayList<>();
		AtomicBoolean laterUpdateRan = new AtomicBoolean();
		Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
		Thread.setDefaultUncaughtExceptionHandler((thread, error) -> uncaught.add(error));
		try {
			onLiveStateWorker.invoke(controller, (Runnable) () -> {
				throw new IllegalStateException("Proxy is invalidated");
			});
			onLiveStateWorker.invoke(controller, (Runnable) () -> laterUpdateRan.set(true));
			await(laterUpdateRan::get);
		} finally {
			Thread.setDefaultUncaughtExceptionHandler(previous);
		}

		Assert.assertTrue("an invalidated service is no error: " + uncaught, uncaught.isEmpty());
	}

	// --- Port activity ---

	@Test
	public void portActivityIsReportedForTheComponentAndEachOfItsAncestorsOnlyWhenItChanges() throws Exception {
		this.deployCompositesAndStart();
		ComponentDiagramControllerImpl controller = this.controller();

		controller.onPortActivityChanged(LeafAUserComponent.class, SampleServicesA.class, ObservedPortKind.SERVICE, true);
		controller.onPortActivityChanged(LeafAUserComponent.class, SampleServicesA.class, ObservedPortKind.SERVICE, true);
		controller.onPortActivityChanged(LeafAUserComponent.class, SampleServicesA.class, ObservedPortKind.SERVICE, false);
		controller.onPortActivityChanged(LeafAUserComponent.class, SampleServicesA.class, ObservedPortKind.SERVICE, false);
		// A last, distinct change that tells every update before it has been handled.
		controller.onPortActivityChanged(LeafAUserComponent.class, SampleServicesB.class, ObservedPortKind.SERVICE, true);
		await(() -> this.portActivityHistory().size() >= 6);

		List<Object[]> history = this.portActivityHistory();
		Assert.assertEquals("2 changes (on, off) x 2 components, then the sentinel on both", 6, history.size());
		Set<Class<?>> reported = Set.of((Class<?>) history.get(0)[0], (Class<?>) history.get(1)[0]);
		Assert.assertEquals(Set.of(CompositeAComponent.class, LeafAUserComponent.class), reported);
		Assert.assertEquals(true, history.get(0)[3]);
		Assert.assertEquals(true, history.get(1)[3]);
		Assert.assertEquals(false, history.get(2)[3]);
		Assert.assertEquals(false, history.get(3)[3]);
		Assert.assertEquals(ComponentPortKind.SERVICE, history.get(0)[2]);
		Assert.assertEquals(SampleServicesA.class, history.get(0)[1]);
		Assert.assertEquals(SampleServicesB.class, history.get(4)[1]);
	}

	@Test
	public void portActivityOfEachKindIsMapped() throws Exception {
		this.deployCompositesAndStart();
		ComponentDiagramControllerImpl controller = this.controller();

		controller.onPortActivityChanged(LeafAUserComponent.class, SampleServicesA.class, ObservedPortKind.PARAMETER, true);
		controller.onPortActivityChanged(LeafAUserComponent.class, SampleServicesA.class, ObservedPortKind.EVENT, true);
		await(() -> this.portActivityHistory().size() >= 4);

		Set<Object> kinds = Set.of(this.portActivityHistory().get(0)[2], this.portActivityHistory().get(2)[2]);
		Assert.assertEquals(Set.of(ComponentPortKind.PARAMETER, ComponentPortKind.EVENT), kinds);
	}

	// --- Sampled rates ---

	@Test
	public void sampledRatesAreRolledUpToTheAncestorsPerInterfaceAndRole() throws Exception {
		this.deployCompositesAndStart();

		this.controller().onPortRatesSampled(1_000L, 500L, List.of(
				new ObservedPortRate(LeafAUserComponent.class, SampleServicesA.class, ObservedPortKind.SERVICE, true, 5, 0,
						-1, null),
				new ObservedPortRate(LeafAUserComponent.class, SampleServicesB.class, ObservedPortKind.EVENT, false, 1, 0,
						-1, null),
				new ObservedPortRate(LeafAProviderComponent.class, SampleServicesA.class, ObservedPortKind.PARAMETER, true,
						3, 0, -1, null)));
		await(() -> !this.activities.isEmpty());

		Map<Class<?>, ComponentActivity> sampled = this.activities.get(this.activities.size() - 1);
		Assert.assertTrue(sampled.keySet().containsAll(
				Set.of(LeafAUserComponent.class, LeafAProviderComponent.class, CompositeAComponent.class)));
		// A 500 ms period doubles the per-second figures; the busiest port comes first.
		ComponentActivity composite = sampled.get(CompositeAComponent.class);
		Assert.assertEquals(3, composite.ports().size());
		ComponentActivity.PortRate busiest = composite.ports().get(0);
		Assert.assertEquals(10.0, busiest.callsPerSecond(), 0.0);
		Assert.assertEquals(SampleServicesA.class, busiest.portType());
		Assert.assertEquals(ComponentPortKind.SERVICE, busiest.kind());
		Assert.assertEquals(ComponentPortRole.PROVIDED, busiest.role());
		Set<ComponentPortRole> roles = Set.of(composite.ports().get(0).role(), composite.ports().get(1).role(),
				composite.ports().get(2).role());
		Assert.assertEquals(Set.of(ComponentPortRole.PROVIDED, ComponentPortRole.PRODUCED, ComponentPortRole.OFFERED),
				roles);
	}

	// --- Lifecycle states ---

	@Test
	public void aStateChangeKeepsTheStateTheContainerIsActuallyIn() throws Exception {
		this.deployCompositesAndStart();
		// The container's state changes are delivered by events: the status is awaited, not read at once.
		await(() -> this.componentDiagramModelReadServices().getComponentStatuses()
				.get(LeafAUserComponent.class) == ComponentStatus.ACTIVATED);

		// A late, stale report: the container still has the component activated.
		this.controller().onComponentStateChanged(LeafAUserComponent.class, ComponentState.passivated);
		// A component of no tab's topology is not tracked.
		this.controller().onComponentStateChanged(String.class, ComponentState.failed);
		this.controller().onPortActivityChanged(LeafAUserComponent.class, SampleServicesB.class, ObservedPortKind.SERVICE,
				true);
		await(() -> !this.portActivityHistory().isEmpty());

		Map<Class<?>, ComponentStatus> statuses = this.componentDiagramModelReadServices().getComponentStatuses();
		Assert.assertEquals(ComponentStatus.ACTIVATED, statuses.get(LeafAUserComponent.class));
		Assert.assertFalse(statuses.containsKey(String.class));
	}

	@Test
	public void disconnectingForgetsTheLiveActivity() throws Exception {
		this.deployCompositesAndStart();
		this.controller().onPortActivityChanged(LeafAUserComponent.class, SampleServicesA.class, ObservedPortKind.SERVICE,
				true);
		await(() -> this.portActivityHistory().size() >= 2);

		this.disconnectObserved();
		this.reconnect();
		this.controller().onPortActivityChanged(LeafAUserComponent.class, SampleServicesA.class, ObservedPortKind.SERVICE,
				true);
		await(() -> this.portActivityHistory().size() >= 4);

		// Lit again from scratch: the count of the first, never switched off, was forgotten.
		Assert.assertEquals(true, this.portActivityHistory().get(2)[3]);
		Assert.assertEquals(true, this.portActivityHistory().get(3)[3]);
	}

	// --- Tabs ---

	@Test
	public void aNewTabStartsAtTheTopAndAClosedTabIsForgotten() {
		this.deployCompositesAndStart();
		ViewKey first = this.sessionKey();

		this.componentDiagramControllerServices.openNewSession();
		ViewKey second = this.sessionKey();

		Assert.assertNotEquals(first, second);
		Assert.assertEquals(List.of(), this.lastFocusPath());
		Assert.assertEquals(Set.of(CompositeAComponent.class, CompositeBComponent.class),
				this.componentDiagramModelReadServices().getTopology(second).getComponentTypes());

		this.componentDiagramControllerServices.closeSession(second);
		Assert.assertTrue(this.componentDiagramModelReadServices().getTopology(second).getComponentTypes().isEmpty());
		Assert.assertFalse(this.componentDiagramModelReadServices().getTopology(first).getComponentTypes().isEmpty());
	}

	// --- Links ---

	@Test
	public void showingALinkBetweenSiblingsEntersTheirCommonComposite() {
		this.deployCompositesAndStart();

		this.componentDiagramControllerServices.showLink(this.sessionKey(), LeafAUserComponent.class,
				LeafAProviderComponent.class);

		Assert.assertEquals(List.of(CompositeAComponent.class), this.lastFocusPath());
		Assert.assertEquals(1, this.highlightedLinks.size());
		Assert.assertArrayEquals(new Class<?>[] { LeafAUserComponent.class, LeafAProviderComponent.class },
				this.highlightedLinks.get(0));
	}

	@Test
	public void showingALinkBetweenComponentsOfDifferentCompositesStaysAtTheTop() {
		this.deployCompositesAndStart();

		this.componentDiagramControllerServices.showLink(this.sessionKey(), LeafAUserComponent.class,
				CompositeBComponent.class);

		Assert.assertEquals(List.of(), this.lastFocusPath());
		Assert.assertArrayEquals(new Class<?>[] { CompositeAComponent.class, CompositeBComponent.class },
				this.highlightedLinks.get(0));
	}

	@Test
	public void showingALinkBetweenACompositeAndItsOwnComponentShowsTheCompositeAmongItsSiblings() {
		this.deployCompositesAndStart();

		this.componentDiagramControllerServices.showLink(this.sessionKey(), CompositeAComponent.class,
				LeafAUserComponent.class);

		Assert.assertEquals(List.of(), this.lastFocusPath());
		Assert.assertEquals(1, this.highlightedLinks.size());
	}

	@Test
	public void showingALinkWithoutATargetDoesNothing() {
		this.deployCompositesAndStart();
		int before = this.focusPathChangeCount();

		this.componentDiagramControllerServices.showLink(this.sessionKey(), LeafAUserComponent.class, null);

		Assert.assertEquals(before, this.focusPathChangeCount());
		Assert.assertTrue(this.highlightedLinks.isEmpty());
	}

	// --- Views ---

	@Test
	public void aViewCannotBeSavedWhileNothingIsObserved() throws Exception {
		this.deployCompositesAndStart();
		this.disconnectObserved();

		try {
			this.componentDiagramControllerServices.saveView(this.sessionKey(), "name", "description", Set.of(), Map.of(),
					1.0, null);
			Assert.fail("Saving must be refused when no container is observed");
		} catch (IOException expected) {
			Assert.assertTrue(expected.getMessage().contains("No container observed"));
		}
	}

	@Test
	public void aViewWhoseFocusPathIsBrokenOpensOneLevelShortOfTheBreak() {
		this.deployCompositesAndStart();
		ComponentDiagramSnapshot snapshot = new ComponentDiagramSnapshot(
				List.of(CompositeAComponent.class.getName(), "org.acme.Missing", LeafAUserComponent.class.getName()),
				Set.of(), Map.of(), Set.of(), 1.0);
		SavedView view = new SavedView("Broken", "", this.observed.getName(), Instant.now(), snapshot,
				new File("Broken.cow"), new File("Broken.png"), null);

		this.componentDiagramControllerServices.openView(view);

		Assert.assertEquals(List.of(CompositeAComponent.class), this.lastFocusPath());
	}
}
