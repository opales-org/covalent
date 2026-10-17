/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.controller.impl;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.supervision.componentdiagram.controller.ComponentDiagramController;
import org.opales.covalent.supervision.componentdiagram.model.ComponentActivity;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramSnapshot;
import org.opales.covalent.supervision.diagrampersistence.SavedView;
import org.opales.covalent.supervision.diagrampersistence.controller.impl.DiagramPersistenceControllerImpl;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;
import org.opales.covalent.supervision.observed.ObservedPortKind;
import org.opales.covalent.supervision.observed.ObservedPortRate;
import org.opales.covalent.supervision.testsupport.observed.CompositeAComponent;
import org.opales.covalent.supervision.testsupport.observed.CompositeBComponent;
import org.opales.covalent.supervision.testsupport.observed.LeafAProviderComponent;
import org.opales.covalent.supervision.testsupport.observed.LeafAUserComponent;
import org.opales.covalent.supervision.testsupport.observed.LeafBProviderComponent;
import org.opales.covalent.supervision.testsupport.observed.ObservedComponentDiagramTestSupport;
import org.opales.covalent.supervision.testsupport.observed.SampleServicesA;
import org.opales.covalent.supervision.testsupport.observed.impl.CompositeAComponentImpl;
import org.opales.covalent.supervision.testsupport.observed.impl.CompositeBComponentImpl;

/**
 * Covers how {@code ComponentDiagramControllerImpl} navigates to a link or a component, saves and reopens views
 * (including a saved path that is partly gone), reports an unreadable view as having nothing missing, rebuilds the
 * topology when the observed container reports a structural change, and drops live updates once passivated.
 */
public class ComponentDiagramNavigationAndEventsTests extends ObservedComponentDiagramTestSupport {

	private final List<Class<?>[]> highlightedLinks = new CopyOnWriteArrayList<>();

	private Path viewsFolder;

	@Override
	public void onLinkHighlightRequested(final ViewKey sessionKey, final Class<?> sourceType,
			final Class<?> targetType) {
		this.highlightedLinks.add(new Class<?>[] { sourceType, targetType });
	}

	@After
	public void deleteViewsFolder() throws IOException {
		if (this.viewsFolder != null) {
			try (Stream<Path> paths = Files.walk(this.viewsFolder)) {
				paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
			}
		}
	}

	private ComponentDiagramControllerImpl controller() {
		return (ComponentDiagramControllerImpl) this.supervisionRcs.useServices(RegistryReadServices.class)
				.getComponentInstance(ComponentDiagramController.class);
	}

	private void start() {
		this.observed.deployComponents(CompositeAComponentImpl.class, CompositeBComponentImpl.class);
		this.startBoth();
	}

	private DiagramPersistenceControllerImpl useTemporaryLibrary() throws Exception {
		this.viewsFolder = Files.createTempDirectory("component-diagram-views");
		java.lang.reflect.Constructor<DiagramPersistenceControllerImpl> constructor =
				DiagramPersistenceControllerImpl.class.getDeclaredConstructor(File.class);
		constructor.setAccessible(true);
		DiagramPersistenceControllerImpl library = constructor.newInstance(this.viewsFolder.toFile());
		Field field = ComponentDiagramControllerImpl.class.getDeclaredField("useDiagramPersistenceServices");
		field.setAccessible(true);
		field.set(this.controller(), library);
		return library;
	}

	// --- showLink ---

	@Test
	public void aLinkBetweenSiblingsOpensTheirCommonParentAndHighlightsIt() {
		this.start();

		this.componentDiagramControllerServices.showLink(this.sessionKey(), LeafAUserComponent.class,
				LeafAProviderComponent.class);

		Assert.assertEquals(List.of(CompositeAComponent.class), this.lastFocusPath());
		Assert.assertEquals(1, this.highlightedLinks.size());
		Assert.assertArrayEquals(new Class<?>[] { LeafAUserComponent.class, LeafAProviderComponent.class },
				this.highlightedLinks.get(0));
	}

	@Test
	public void aLinkAcrossCompositesIsShownBetweenTheCompositesAtTheTopLevel() {
		this.start();
		this.componentDiagramControllerServices.enterComposite(this.sessionKey(), CompositeAComponent.class);

		this.componentDiagramControllerServices.showLink(this.sessionKey(), LeafAUserComponent.class,
				LeafBProviderComponent.class);

		Assert.assertEquals(List.of(), this.lastFocusPath());
		Assert.assertArrayEquals(new Class<?>[] { CompositeAComponent.class, CompositeBComponent.class },
				this.highlightedLinks.get(0));
	}

	@Test
	public void aLinkFromAContainerToItsOwnComponentIsShownAmongTheContainersSiblings() {
		this.start();

		this.componentDiagramControllerServices.showLink(this.sessionKey(), CompositeAComponent.class,
				LeafAUserComponent.class);

		Assert.assertEquals(List.of(), this.lastFocusPath());
		Assert.assertArrayEquals(new Class<?>[] { CompositeAComponent.class, CompositeAComponent.class },
				this.highlightedLinks.get(0));
	}

	@Test
	public void aLinkWithAMissingEndIsIgnored() {
		this.start();
		int focusChanges = this.focusPathChangeCount();

		this.componentDiagramControllerServices.showLink(this.sessionKey(), null, LeafAUserComponent.class);
		this.componentDiagramControllerServices.showLink(this.sessionKey(), LeafAUserComponent.class, null);

		Assert.assertEquals(focusChanges, this.focusPathChangeCount());
		Assert.assertTrue(this.highlightedLinks.isEmpty());
	}

	// --- navigateToComponent ---

	@Test
	public void navigatingToAComponentOpensItsParentAndFlashesIt() {
		this.start();

		this.componentDiagramControllerServices.navigateToComponent(this.sessionKey(), LeafBProviderComponent.class);

		Assert.assertEquals(List.of(CompositeBComponent.class), this.lastFocusPath());
		Assert.assertEquals(LeafBProviderComponent.class, this.lastFlashed());
	}

	@Test
	public void navigatingToNoComponentChangesNothing() {
		this.start();
		int focusChanges = this.focusPathChangeCount();

		this.componentDiagramControllerServices.navigateToComponent(this.sessionKey(), null);

		Assert.assertEquals(focusChanges, this.focusPathChangeCount());
		Assert.assertNull(this.lastFlashed());
	}

	// --- Views ---

	@Test
	public void aViewCannotBeSavedWhenNoContainerIsObserved() {
		this.start();
		this.disconnectObserved();

		try {
			this.componentDiagramControllerServices.saveView(this.sessionKey(), "name", "", Set.of(), Map.of(), 1.0, null);
			Assert.fail("expected an IOException");
		} catch (IOException expected) {
			Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("No container observed"));
		}
		Assert.assertNull(this.componentDiagramControllerServices.getSessionView(this.sessionKey()));
	}

	@Test
	public void aSavedViewKeepsTheTabsFocusSelectionHiddenComponentsPositionsAndZoom() throws Exception {
		this.start();
		DiagramPersistenceControllerImpl library = this.useTemporaryLibrary();
		ViewKey tab = this.sessionKey();
		this.componentDiagramControllerServices.enterComposite(tab, CompositeAComponent.class);
		this.componentDiagramControllerServices.selectComponents(tab, Set.of(LeafAUserComponent.class));

		SavedView saved = this.componentDiagramControllerServices.saveView(tab, "Layout", "desc",
				Set.of(LeafAProviderComponent.class), Map.of(LeafAUserComponent.class, new double[] { 5, 6 }), 1.5, null);

		Assert.assertSame(saved, this.componentDiagramControllerServices.getSessionView(tab));
		ComponentDiagramSnapshot snapshot = saved.snapshot();
		Assert.assertEquals(List.of(CompositeAComponent.class.getName()), snapshot.focusPath());
		Assert.assertEquals(Set.of(LeafAProviderComponent.class.getName()), snapshot.hiddenComponents());
		Assert.assertEquals(Set.of(LeafAUserComponent.class.getName()), snapshot.selection());
		Assert.assertArrayEquals(new double[] { 5, 6 }, snapshot.positions().get(LeafAUserComponent.class.getName()), 0.0);
		Assert.assertEquals(1.5, snapshot.zoom(), 0.0);
		Assert.assertEquals(1, library.listViews().size());
		Assert.assertEquals(this.observed.getName(), saved.containerName());
	}

	@Test
	public void aViewWhoseSavedPathIsPartlyGoneOpensOneLevelShortOfTheBreak() {
		this.start();
		ComponentDiagramSnapshot snapshot = new ComponentDiagramSnapshot(
				List.of(CompositeAComponent.class.getName(), "org.acme.Gone", LeafAUserComponent.class.getName()),
				Set.of(), Map.of(), Set.of(), 1.0);
		SavedView view = new SavedView("Broken", "", this.observed.getName(), Instant.now(), snapshot,
				new File("Broken.cow"), new File("Broken.png"), null);

		this.componentDiagramControllerServices.openView(view);

		Assert.assertEquals(List.of(CompositeAComponent.class), this.lastFocusPath());
	}

	@Test
	public void anUnreadableViewHasNoMissingComponents() {
		this.start();
		SavedView unreadable = new SavedView("Bad", "", this.observed.getName(), null, null, new File("Bad.cow"), null,
				"corrupt");

		Assert.assertFalse(unreadable.readable());
		Assert.assertEquals(Set.of(), this.componentDiagramControllerServices.getMissingComponents(unreadable));
	}

	@Test
	public void renamingATabWithoutAViewChangesNothing() {
		this.start();

		this.componentDiagramControllerServices.renameSession(this.sessionKey(), "anything");

		Assert.assertNull(this.componentDiagramControllerServices.getSessionView(this.sessionKey()));
	}

	@Test
	public void closingATabForgetsItsView() throws Exception {
		this.start();
		this.useTemporaryLibrary();
		ViewKey tab = this.sessionKey();
		this.componentDiagramControllerServices.saveView(tab, "Kept", "", Set.of(), Map.of(), 1.0, null);

		this.componentDiagramControllerServices.closeSession(tab);

		Assert.assertNull(this.componentDiagramControllerServices.getSessionView(tab));
	}

	// --- Observed structural changes ---

	@Test
	public void everyStructuralChangeOfTheObservedContainerRebuildsTheTopology() {
		this.start();
		ComponentDiagramControllerImpl controller = this.controller();
		int count = this.topologyChangeCount();

		controller.onComponentRegistered(String.class, "x");
		Assert.assertTrue(this.topologyChangeCount() > count);
		count = this.topologyChangeCount();
		controller.onComponentUnregistered(String.class, "x");
		Assert.assertTrue(this.topologyChangeCount() > count);
		count = this.topologyChangeCount();
		controller.onServicesProvided(String.class, Runnable.class);
		Assert.assertTrue(this.topologyChangeCount() > count);
		count = this.topologyChangeCount();
		controller.onServicesUnprovided(String.class, Runnable.class);
		Assert.assertTrue(this.topologyChangeCount() > count);
		count = this.topologyChangeCount();
		controller.onServicesUsed(String.class, Runnable.class);
		Assert.assertTrue(this.topologyChangeCount() > count);
		count = this.topologyChangeCount();
		controller.onServicesUnused(String.class, Runnable.class);
		Assert.assertTrue(this.topologyChangeCount() > count);
		count = this.topologyChangeCount();
		controller.onParametersOffered(String.class, Runnable.class);
		Assert.assertTrue(this.topologyChangeCount() > count);
		count = this.topologyChangeCount();
		controller.onParametersUnoffered(String.class, Runnable.class);
		Assert.assertTrue(this.topologyChangeCount() > count);
		count = this.topologyChangeCount();
		controller.onParametersAccessed(String.class, Runnable.class);
		Assert.assertTrue(this.topologyChangeCount() > count);
		count = this.topologyChangeCount();
		controller.onParametersUnaccessed(String.class, Runnable.class);
		Assert.assertTrue(this.topologyChangeCount() > count);
		count = this.topologyChangeCount();
		controller.onEventsProduced(String.class, Runnable.class);
		Assert.assertTrue(this.topologyChangeCount() > count);
		count = this.topologyChangeCount();
		controller.onEventsUnproduced(String.class, Runnable.class);
		Assert.assertTrue(this.topologyChangeCount() > count);
		count = this.topologyChangeCount();
		controller.onEventsSubscribed(Runnable.class, Runnable.class);
		Assert.assertTrue(this.topologyChangeCount() > count);
		count = this.topologyChangeCount();
		controller.onEventsUnsubscribed(Runnable.class, Runnable.class);
		Assert.assertTrue(this.topologyChangeCount() > count);
	}

	@Test
	public void callTracesAreIgnoredBecausePortActivityArrivesCoalesced() {
		this.start();
		ComponentDiagramControllerImpl controller = this.controller();
		int topologies = this.topologyChangeCount();
		int activities = this.portActivityHistory().size();

		controller.onUsedServiceCallStarted(null, 0, 0, Runnable.class, null, String.class, "");
		controller.onUsedServiceCallFinished(null, 0, 0, Runnable.class, null, String.class, 0, null, null);
		controller.onProvidedServiceCallStarted(null, 0, 0, Runnable.class, null, String.class, "");
		controller.onProvidedServiceCallFinished(null, 0, 0, Runnable.class, null, String.class, 0, null, null);
		controller.onAccessedParametersCallStarted(null, 0, 0, Runnable.class, null, String.class, "");
		controller.onAccessedParametersCallFinished(null, 0, 0, Runnable.class, null, String.class, 0, null, null);
		controller.onOfferedParametersCallStarted(null, 0, 0, Runnable.class, null, String.class, "");
		controller.onOfferedParametersCallFinished(null, 0, 0, Runnable.class, null, String.class, 0, null, null);
		controller.onEventNotificationStarted(null, 0, 0, Runnable.class, null, String.class, String.class, Thread.class,
				false, 0, "");
		controller.onEventNotificationFinished(null, 0, 0, Runnable.class, null, String.class, String.class,
				Thread.class, 0, null);

		Assert.assertEquals(topologies, this.topologyChangeCount());
		Assert.assertEquals(activities, this.portActivityHistory().size());
	}

	@Test
	public void liveUpdatesAreDroppedOncePassivated() throws Exception {
		this.start();
		ComponentDiagramControllerImpl controller = this.controller();
		controller.stopLiveStateWorker();
		int activities = this.portActivityHistory().size();

		controller.onPortActivityChanged(LeafAUserComponent.class, SampleServicesA.class, ObservedPortKind.SERVICE, true);
		Thread.sleep(50);

		Assert.assertEquals(activities, this.portActivityHistory().size());
		controller.stopLiveStateWorker();
	}

	// --- Rates, recording, unactivated controller ---

	private final List<Map<Class<?>, ComponentActivity>> activities = new CopyOnWriteArrayList<>();

	@Override
	public void onActivitySampled(final long sampleTimeMillis, final Map<Class<?>, ComponentActivity> sampled) {
		this.activities.add(sampled);
	}

	@Test
	public void eachKindAndDirectionOfSampledCallsBecomesTheRoleItPlays() throws Exception {
		this.start();

		this.controller().onPortRatesSampled(1_000L, 1_000L, List.of(
				new ObservedPortRate(LeafAUserComponent.class, SampleServicesA.class, ObservedPortKind.SERVICE, true, 1, 0, -1, null),
				new ObservedPortRate(LeafAUserComponent.class, SampleServicesA.class, ObservedPortKind.SERVICE, false, 2, 0, -1, null),
				new ObservedPortRate(LeafAUserComponent.class, SampleServicesA.class, ObservedPortKind.EVENT, true, 3, 0, -1, null),
				new ObservedPortRate(LeafAUserComponent.class, SampleServicesA.class, ObservedPortKind.EVENT, false, 4, 0, -1, null),
				new ObservedPortRate(LeafAUserComponent.class, SampleServicesA.class, ObservedPortKind.PARAMETER, true, 5, 0, -1, null),
				new ObservedPortRate(LeafAUserComponent.class, SampleServicesA.class, ObservedPortKind.PARAMETER, false, 6, 0, -1, null)));
		long deadline = System.currentTimeMillis() + 5_000;
		while (this.activities.isEmpty()) {
			Assert.assertTrue("Timed out waiting for the live state worker", System.currentTimeMillis() < deadline);
			Thread.sleep(5);
		}

		ComponentActivity activity = this.activities.get(this.activities.size() - 1).get(LeafAUserComponent.class);
		Map<String, Double> rates = new java.util.HashMap<>();
		for (ComponentActivity.PortRate rate : activity.ports()) {
			rates.put(rate.kind() + "/" + rate.role(), rate.callsPerSecond());
		}
		Assert.assertEquals(Map.of("SERVICE/PROVIDED", 1.0, "SERVICE/USED", 2.0, "EVENT/CONSUMED", 3.0,
				"EVENT/PRODUCED", 4.0, "PARAMETER/OFFERED", 5.0, "PARAMETER/ACCESSED", 6.0), rates);
		Assert.assertEquals("busiest port first", 6.0, activity.ports().get(0).callsPerSecond(), 0.0);
	}

	@Test
	public void theRecordingStateIsTheSequenceDiagramsOwn() {
		this.start();
		Assert.assertFalse(this.componentDiagramControllerServices.isRecording());

		this.componentDiagramControllerServices.selectComponents(this.sessionKey(), Set.of(LeafAUserComponent.class));
		this.componentDiagramControllerServices.startRecording(this.sessionKey());
		Assert.assertTrue(this.componentDiagramControllerServices.isRecording());
		Assert.assertEquals(Set.of(LeafAUserComponent.class), this.stubSequenceDiagram.lastSelectedTypes);

		this.componentDiagramControllerServices.stopRecording();
		Assert.assertFalse(this.componentDiagramControllerServices.isRecording());
		Assert.assertEquals(1, this.stubSequenceDiagram.stopRecordingCalls);
	}

	@Test
	public void aViewSelectionNamingAGoneComponentKeepsOnlyTheOnesThatStillExist() {
		this.start();
		ComponentDiagramSnapshot snapshot = new ComponentDiagramSnapshot(List.of(), Set.of(),
				Map.of("org.acme.Gone", new double[] { 1, 2 }), Set.of("org.acme.Gone", CompositeAComponent.class.getName()),
				1.0);
		SavedView view = new SavedView("Partly gone", "", this.observed.getName(), Instant.now(), snapshot,
				new File("p.cow"), new File("p.png"), null);

		this.componentDiagramControllerServices.openView(view);

		Assert.assertEquals(Set.of(CompositeAComponent.class), this.lastSelection());
		Assert.assertTrue(this.componentDiagramModelReadServices().getInitialLayout(this.sessionKey()).positions().isEmpty());
	}

	@Test
	public void aControllerThatIsNotActivatedYetIgnoresTopologyChangesWithoutTouchingItsCollaborators() {
		ComponentDiagramControllerImpl notActivated = new ComponentDiagramControllerImpl();

		notActivated.onComponentRegistered(String.class, "x");
		notActivated.onContainerConnected();
		notActivated.onServicesProvided(String.class, Runnable.class);
	}
}
