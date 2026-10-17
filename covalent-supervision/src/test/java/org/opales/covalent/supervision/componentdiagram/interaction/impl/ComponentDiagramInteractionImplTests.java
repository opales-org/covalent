/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.interaction.impl;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;


import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.hmi.action.ActionProviderServices;
import org.opales.covalent.supervision.componentdiagram.controller.ComponentDiagramControllerServices;
import org.opales.covalent.supervision.componentdiagram.interaction.ComponentDiagramActionKey;
import org.opales.covalent.supervision.componentdiagram.interaction.ComponentDiagramActionKey.Role;
import org.opales.covalent.supervision.componentdiagram.interaction.ExportViewRequest;
import org.opales.covalent.supervision.componentdiagram.interaction.RenameViewRequest;
import org.opales.covalent.supervision.componentdiagram.interaction.SaveViewRequest;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramModelReadServices;
import org.opales.covalent.supervision.componentdiagram.model.ComponentDiagramSnapshot;
import org.opales.covalent.supervision.componentdiagram.model.PortColorKey;
import org.opales.covalent.supervision.componentdiagram.view.ComponentDiagramViewServices;
import org.opales.covalent.supervision.componentdiagram.view.SaveViewForm;
import org.opales.covalent.supervision.componentdiagram.view.ViewLibraryContent;
import org.opales.covalent.supervision.diagrampersistence.SavedView;
import org.opales.covalent.supervision.diagrampersistence.controller.DiagramPersistenceServices;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisorTheme;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;
import org.opales.covalent.supervision.testsupport.CapturingLogHandler;

/**
 * Unit tests for {@link ComponentDiagramInteractionImpl}'s per-tab action wiring and
 * views library coordination, using hand-written fakes for its three {@code @UsedServices}
 * (no container, no JavaFX scene). Deliberately never calls {@code registerPreferenceActions()}
 * (the real {@code @Activation}): that method reads {@code SupervisorSettings}, a real
 * OS-backed {@code Preferences} store this test must not depend on. Each test instead drives
 * {@link ComponentDiagramInteractionImpl} either through its public {@code
 * ComponentDiagramEvents} and {@code RecordingStateEvents} overrides, through the per-tab action
 * implementations captured by the fake {@code ActionProviderServices} during {@code
 * onSessionCreated}, or - for the few genuinely private, prefs-free methods ({@code
 * onOpenComposite}, {@code onShowLink}) - by reflection.
 */
public class ComponentDiagramInteractionImplTests {

	private static final class TestViewKey implements ViewKey {
		private final String label;

		TestViewKey(final String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return "TestViewKey[" + this.label + "]";
		}
	}

	/** Records every registration/unregistration/availability/value call, and lets tests fire
	 * back into whatever was registered under a given key. */
	private static final class FakeActionProviderServices implements ActionProviderServices {
		final Set<ActionKey> registered = new HashSet<>();
		final Map<ActionKey, Runnable> runnables = new LinkedHashMap<>();
		final Map<ActionKey, Consumer<Object>> consumers = new LinkedHashMap<>();
		final Map<ActionKey, BiConsumer<Object, Object>> biConsumers = new LinkedHashMap<>();
		final Map<ActionKey, Boolean> availability = new LinkedHashMap<>();
		final Map<ActionKey, Object> values = new LinkedHashMap<>();

		@SuppressWarnings("unchecked")
		@Override
		public <T> void registerActionExecution(final ActionKey anActionKey, final Consumer<T> anInteractionImpl,
				final Class<T> anInputDataClass) {
			this.registered.add(anActionKey);
			this.consumers.put(anActionKey, (Consumer<Object>) anInteractionImpl);
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T1, T2> void registerActionExecution(final ActionKey anActionKey,
				final BiConsumer<T1, T2> anInteractionImpl, final Class<T1> anInputDataClass1,
				final Class<T2> anInputDataClass2) {
			this.registered.add(anActionKey);
			this.biConsumers.put(anActionKey, (BiConsumer<Object, Object>) anInteractionImpl);
		}

		@Override
		public void registerActionExecution(final ActionKey anActionKey, final Runnable anInteractionImpl) {
			this.registered.add(anActionKey);
			this.runnables.put(anActionKey, anInteractionImpl);
		}

		@Override
		public void unregisterActionExecution(final ActionKey anActionKey) {
			this.registered.remove(anActionKey);
			this.runnables.remove(anActionKey);
			this.consumers.remove(anActionKey);
			this.biConsumers.remove(anActionKey);
		}

		@Override
		public void setActionAvailable(final ActionKey anActionKey, final boolean isAvailable) {
			this.availability.put(anActionKey, isAvailable);
		}

		@Override
		public void setActionValue(final ActionKey anActionKey, final Object aValue) {
			this.values.put(anActionKey, aValue);
		}

		void fire(final ViewKey sessionKey, final Role role) {
			this.runnables.get(new ComponentDiagramActionKey(sessionKey, role)).run();
		}

		void fire(final ViewKey sessionKey, final Role role, final Object input) {
			this.consumers.get(new ComponentDiagramActionKey(sessionKey, role)).accept(input);
		}

		void fire(final ViewKey sessionKey, final Role role, final Object input1, final Object input2) {
			this.biConsumers.get(new ComponentDiagramActionKey(sessionKey, role)).accept(input1, input2);
		}
	}

	/** Records every call's arguments; a test configures {@code loadResult}/{@code
	 * failOnSave}/{@code failOnLoad} before driving the controller. */
	private static final class FakeControllerServices implements ComponentDiagramControllerServices {
		int openNewSessionCalls;
		Runnable onOpenNewSession = () -> { };
		ViewKey lastClosedSession;
		final List<Object[]> selectComponentsCalls = new ArrayList<>();
		final List<Object[]> toggleComponentSelectionCalls = new ArrayList<>();
		final List<Object[]> toggleDependenciesVisibilityCalls = new ArrayList<>();
		int startRecordingCalls;
		ViewKey startRecordingSession;
		int stopRecordingCalls;
		boolean recording;
		final List<Object[]> enterCompositeCalls = new ArrayList<>();
		final List<Object[]> exitToDepthCalls = new ArrayList<>();
		final List<Object[]> navigateToComponentCalls = new ArrayList<>();
		final List<Object[]> showCompositeCalls = new ArrayList<>();
		final List<Object[]> showLinkCalls = new ArrayList<>();
		final List<Object[]> saveViewCalls = new ArrayList<>();
		final List<SavedView> openViewCalls = new ArrayList<>();
		final List<Object[]> renameSessionCalls = new ArrayList<>();
		boolean failOnSave;
		SavedView sessionView;
		String observedContainerName = "container";

		@Override
		public void openNewSession() {
			this.openNewSessionCalls++;
			this.onOpenNewSession.run();
		}

		@Override
		public void closeSession(final ViewKey sessionKey) {
			this.lastClosedSession = sessionKey;
		}

		@Override
		public void selectComponents(final ViewKey sessionKey, final Set<Class<?>> componentTypes) {
			this.selectComponentsCalls.add(new Object[] { sessionKey, componentTypes });
		}

		@Override
		public void toggleComponentSelection(final ViewKey sessionKey, final Class<?> componentType) {
			this.toggleComponentSelectionCalls.add(new Object[] { sessionKey, componentType });
		}

		@Override
		public void toggleDependenciesVisibility(final ViewKey sessionKey, final Class<?> componentType,
				final boolean show) {
			this.toggleDependenciesVisibilityCalls.add(new Object[] { sessionKey, componentType, show });
		}

		@Override
		public void startRecording(final ViewKey sessionKey) {
			this.startRecordingCalls++;
			this.startRecordingSession = sessionKey;
			this.recording = true;
		}

		@Override
		public void stopRecording() {
			this.stopRecordingCalls++;
			this.recording = false;
		}

		@Override
		public boolean isRecording() {
			return this.recording;
		}

		@Override
		public void enterComposite(final ViewKey sessionKey, final Class<?> compositeType) {
			this.enterCompositeCalls.add(new Object[] { sessionKey, compositeType });
		}

		@Override
		public void showComposite(final ViewKey sessionKey, final Class<?> compositeType) {
			this.showCompositeCalls.add(new Object[] { sessionKey, compositeType });
		}

		@Override
		public void exitToDepth(final ViewKey sessionKey, final int depth) {
			this.exitToDepthCalls.add(new Object[] { sessionKey, depth });
		}

		@Override
		public void navigateToComponent(final ViewKey sessionKey, final Class<?> componentType) {
			this.navigateToComponentCalls.add(new Object[] { sessionKey, componentType });
		}

		@Override
		public void showLink(final ViewKey sessionKey, final Class<?> sourceType, final Class<?> targetType) {
			this.showLinkCalls.add(new Object[] { sessionKey, sourceType, targetType });
		}

		@Override
		public SavedView saveView(final ViewKey sessionKey, final String name, final String description,
				final Set<Class<?>> hiddenComponents, final Map<Class<?>, double[]> positions, final double zoom,
				final byte[] thumbnailPng) throws IOException {
			if (this.failOnSave) {
				throw new IOException("simulated save failure");
			}
			this.saveViewCalls.add(new Object[] { sessionKey, name, description, hiddenComponents, positions, zoom });
			return null;
		}

		@Override
		public void openView(final SavedView view) {
			this.openViewCalls.add(view);
		}

		@Override
		public SavedView getSessionView(final ViewKey sessionKey) {
			return this.sessionView;
		}

		@Override
		public void renameSession(final ViewKey sessionKey, final String title) {
			this.renameSessionCalls.add(new Object[] { sessionKey, title });
		}

		@Override
		public String getObservedContainerName() {
			return this.observedContainerName;
		}

		@Override
		public Set<String> getMissingComponents(final SavedView view) {
			return Set.of();
		}
	}

	private static final class FakeViewServices implements ComponentDiagramViewServices {
		ViewKey lastBroughtToFront;
		final Map<ViewKey, Double> zoomScale = new HashMap<>();
		final List<String> errors = new ArrayList<>();
		final List<String> notices = new ArrayList<>();
		Set<Class<?>> hiddenComponents = Set.of();
		Map<Class<?>, double[]> positions = Map.of();
		final Map<ViewKey, String> tabTitles = new HashMap<>();
		final List<SaveViewForm> saveViewForms = new ArrayList<>();
		final List<ViewKey> exportedSessions = new ArrayList<>();
		boolean confirmResult = true;
		int confirmCalls;
		int libraryUpdates;
		ViewLibraryContent shownLibrary;

		@Override
		public void recenter(final ViewKey sessionKey) {
		}

		@Override
		public void bringToFront(final ViewKey sessionKey) {
			this.lastBroughtToFront = sessionKey;
		}

		@Override
		public void setZoomScale(final ViewKey sessionKey, final double scale) {
			this.zoomScale.put(sessionKey, scale);
		}

		@Override
		public double getZoomScale(final ViewKey sessionKey) {
			return this.zoomScale.getOrDefault(sessionKey, 1.0);
		}

		@Override
		public void exportSnapshot(final ViewKey sessionKey) {
			this.exportedSessions.add(sessionKey);
		}

		@Override
		public Set<Class<?>> getHiddenComponents(final ViewKey sessionKey) {
			return this.hiddenComponents;
		}

		@Override
		public Map<Class<?>, double[]> getPositions(final ViewKey sessionKey) {
			return this.positions;
		}

		@Override
		public byte[] captureThumbnail(final ViewKey sessionKey) {
			return null;
		}

		@Override
		public String getTabTitle(final ViewKey sessionKey) {
			return this.tabTitles.get(sessionKey);
		}

		@Override
		public void setTabTitle(final ViewKey sessionKey, final String title) {
			this.tabTitles.put(sessionKey, title);
		}

		@Override
		public void showSaveViewDialog(final ViewKey sessionKey, final SaveViewForm form) {
			this.saveViewForms.add(form);
		}

		@Override
		public void showViewLibrary(final ViewKey sessionKey, final ViewLibraryContent content) {
			this.shownLibrary = content;
		}

		@Override
		public void updateViewLibrary(final ViewLibraryContent content) {
			this.libraryUpdates++;
		}

		@Override
		public boolean confirm(final ViewKey sessionKey, final String title, final String message,
				final String confirmLabel) {
			this.confirmCalls++;
			return this.confirmResult;
		}

		@Override
		public void showError(final ViewKey sessionKey, final String message) {
			this.errors.add(message);
		}

		@Override
		public void setAutoLayoutOnHide(final boolean autoLayoutOnHide) {
		}

		@Override
		public void setPortColor(final PortColorKey key, final String value) {
		}

		@Override
		public String getPortColor(final PortColorKey key) {
			return null;
		}

		@Override
		public void setTheme(final SupervisorTheme theme) {
		}

		@Override
		public String getDefaultPortColor(final PortColorKey key) {
			return null;
		}

		@Override
		public void resetPortColors() {
		}
	}

	/** Records every call; {@code failWith}, when set, makes every call that can fail do so. */
	private static final class FakeDiagramPersistenceServices implements DiagramPersistenceServices {
		final List<SavedView> views = new ArrayList<>();
		final Set<String> existing = new HashSet<>();
		final List<String> calls = new ArrayList<>();
		final File folder = new File("views");
		IOException failWith;
		SavedView toRead;

		private void failIfAsked() throws IOException {
			if (this.failWith != null) {
				throw this.failWith;
			}
		}

		@Override
		public File getViewsFolder() {
			return this.folder;
		}

		@Override
		public List<SavedView> listViews() {
			return List.copyOf(this.views);
		}

		@Override
		public boolean hasView(final String containerName, final String name) {
			return this.existing.contains(containerName + "|" + name);
		}

		@Override
		public SavedView saveView(final String containerName, final String name, final String description,
				final ComponentDiagramSnapshot snapshot, final byte[] thumbnailPng) throws IOException {
			return null;
		}

		@Override
		public SavedView renameView(final SavedView view, final String newName) throws IOException {
			this.failIfAsked();
			this.calls.add("rename " + view.name() + " " + newName);
			return view;
		}

		@Override
		public void deleteView(final SavedView view) throws IOException {
			this.failIfAsked();
			this.calls.add("delete " + view.name());
		}

		@Override
		public void exportView(final SavedView view, final File file) throws IOException {
			this.failIfAsked();
			this.calls.add("export " + view.name() + " " + file.getName());
		}

		@Override
		public SavedView readView(final File file) throws IOException {
			this.failIfAsked();
			return this.toRead;
		}

		@Override
		public SavedView importView(final File file) throws IOException {
			this.failIfAsked();
			this.calls.add("import " + file.getName());
			return this.toRead;
		}
	}

	private ComponentDiagramInteractionImpl controller;
	private FakeDiagramPersistenceServices persistence;
	private FakeActionProviderServices actions;
	private FakeControllerServices controllerServices;
	private FakeViewServices viewServices;

	private CapturingLogHandler componentDiagramInteractionLogs;

	@Before
	public void setUp() throws Exception {
		this.componentDiagramInteractionLogs = CapturingLogHandler.install(ComponentDiagramInteractionImpl.class);
		this.controller = new ComponentDiagramInteractionImpl();
		this.actions = new FakeActionProviderServices();
		this.controllerServices = new FakeControllerServices();
		this.viewServices = new FakeViewServices();
		this.persistence = new FakeDiagramPersistenceServices();
		this.setField("actionManagerServices", this.actions);
		this.setField("useComponentDiagramControllerServices", this.controllerServices);
		this.setField("useComponentDiagramViewServices", this.viewServices);
		this.setField("useComponentDiagramModelReadServices", emptyServices(ComponentDiagramModelReadServices.class));
		this.setField("useDiagramPersistenceServices", this.persistence);
	}

	@After
	public void restoreTheLogs() {
		this.componentDiagramInteractionLogs.restore();
	}

	/** A services implementation whose every method returns an empty list or {@code null}. */
	private static <T> T emptyServices(final Class<T> servicesInterface) {
		return servicesInterface.cast(Proxy.newProxyInstance(servicesInterface.getClassLoader(),
				new Class<?>[] { servicesInterface },
				(proxy, method, args) -> List.class.equals(method.getReturnType()) ? List.of() : null));
	}

	private static SavedView savedView(final String name, final String containerName) {
		return new SavedView(name, "description", containerName, null, null, null, null, null);
	}

	private void setField(final String name, final Object value) throws Exception {
		Field field = ComponentDiagramInteractionImpl.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(this.controller, value);
	}

	private Object invokePrivate(final String name, final Class<?>[] paramTypes, final Object... args) throws Exception {
		Method method = ComponentDiagramInteractionImpl.class.getDeclaredMethod(name, paramTypes);
		method.setAccessible(true);
		return method.invoke(this.controller, args);
	}

	// --- Session lifecycle: registration and unregistration ---

	@Test
	public void sessionCreatedRegistersEveryPerTabRole() {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);

		for (Role role : Role.values()) {
			if (role == Role.ZoomCurrentScale) {
				// Technical value-only key: never registered as an execution, only ever
				// set via setActionValue - see ComponentDiagramActionKey.Role's own javadoc.
				continue;
			}
			Assert.assertTrue("Role." + role + " must be registered on session creation",
					this.actions.registered.contains(new ComponentDiagramActionKey(session, role)));
		}
		Assert.assertEquals(Boolean.FALSE,
				this.actions.availability.get(new ComponentDiagramActionKey(session, Role.RecordToggle)));
	}

	@Test
	public void sessionClosedUnregistersEveryRoleForThatSessionOnly() {
		TestViewKey session = new TestViewKey("a");
		TestViewKey otherSession = new TestViewKey("b");
		this.controller.onSessionCreated(session);
		this.controller.onSessionCreated(otherSession);

		this.controller.onSessionClosed(session);

		for (Role role : Role.values()) {
			Assert.assertFalse("session's own key must be gone: " + role,
					this.actions.registered.contains(new ComponentDiagramActionKey(session, role)));
		}
		Assert.assertTrue("the other session's keys must be untouched",
				this.actions.registered.contains(new ComponentDiagramActionKey(otherSession, Role.RecordToggle)));
	}

	// --- Per-tab actions captured during onSessionCreated ---

	@Test
	public void recordToggleRecordsTheSelectionThenStopsTheRecording() {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);
		this.controller.onSelectionChanged(session, Set.of(String.class));

		this.actions.fire(session, Role.RecordToggle);
		Assert.assertEquals(1, this.controllerServices.startRecordingCalls);
		Assert.assertEquals(session, this.controllerServices.startRecordingSession);

		this.actions.fire(session, Role.RecordToggle);
		Assert.assertEquals(1, this.controllerServices.stopRecordingCalls);
		Assert.assertEquals(1, this.controllerServices.startRecordingCalls);
	}

	@Test
	public void selectionChangedMakesRecordToggleAvailableOnlyWithANonEmptySelection() {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);

		this.controller.onSelectionChanged(session, Set.of(String.class));
		Assert.assertEquals(Boolean.TRUE,
				this.actions.availability.get(new ComponentDiagramActionKey(session, Role.RecordToggle)));

		this.controller.onSelectionChanged(session, Set.of());
		Assert.assertEquals(Boolean.FALSE,
				this.actions.availability.get(new ComponentDiagramActionKey(session, Role.RecordToggle)));
	}

	@Test
	public void navigationActionsDelegateToControllerServicesForTheRightSession() {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);

		this.actions.fire(session, Role.EnterComposite, String.class);
		Assert.assertArrayEquals(new Object[] { session, String.class },
				this.controllerServices.enterCompositeCalls.get(0));

		this.actions.fire(session, Role.ShowComposite, Long.class);
		Assert.assertArrayEquals(new Object[] { session, Long.class },
				this.controllerServices.showCompositeCalls.get(0));

		this.actions.fire(session, Role.ExitToDepth, 2);
		Assert.assertArrayEquals(new Object[] { session, 2 }, this.controllerServices.exitToDepthCalls.get(0));

		this.actions.fire(session, Role.NavigateToComponent, Integer.class);
		Assert.assertArrayEquals(new Object[] { session, Integer.class },
				this.controllerServices.navigateToComponentCalls.get(0));

		Set<Class<?>> selection = Set.of(String.class, Integer.class);
		this.actions.fire(session, Role.SelectComponents, selection);
		Assert.assertArrayEquals(new Object[] { session, selection },
				this.controllerServices.selectComponentsCalls.get(0));

		this.actions.fire(session, Role.ToggleComponentSelection, Double.class);
		Assert.assertArrayEquals(new Object[] { session, Double.class },
				this.controllerServices.toggleComponentSelectionCalls.get(0));

		this.actions.fire(session, Role.ToggleDependenciesVisibility, Float.class, Boolean.TRUE);
		Assert.assertArrayEquals(new Object[] { session, Float.class, true },
				this.controllerServices.toggleDependenciesVisibilityCalls.get(0));
	}

	@Test
	public void newSessionActionOpensANewSessionRegardlessOfWhichTabItCameFrom() {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);

		this.actions.fire(session, Role.NewSession);
		Assert.assertEquals(1, this.controllerServices.openNewSessionCalls);
	}

	@Test
	public void exportActionHasTheViewExportTheTabsRendering() {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);

		this.actions.fire(session, Role.Export);

		Assert.assertEquals(List.of(session), this.viewServices.exportedSessions);
	}

	@Test
	public void closeActionDelegatesToCloseSession() {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);

		this.actions.fire(session, Role.Close);
		Assert.assertEquals(session, this.controllerServices.lastClosedSession);
	}

	@Test
	public void zoomSetScaleAndZoomChangedGoToTheRightPlaces() {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);

		this.actions.fire(session, Role.ZoomSetScale, 2.0);
		Assert.assertEquals(2.0, this.viewServices.getZoomScale(session), 0.0);

		this.actions.fire(session, Role.ZoomChanged, 1.5);
		Assert.assertEquals(1.5, this.actions.values.get(new ComponentDiagramActionKey(session, Role.ZoomCurrentScale)));
	}

	// --- Views library coordination ---

	@Test
	public void confirmSaveViewGathersViewStateSavesAndNamesTheTab() {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);
		this.viewServices.hiddenComponents = Set.of(String.class);
		this.viewServices.positions = Map.of(String.class, new double[] { 1.0, 2.0 });
		this.viewServices.zoomScale.put(session, 3.0);

		this.actions.fire(session, Role.ConfirmSaveView, new SaveViewRequest("My view", "notes"));

		Object[] call = this.controllerServices.saveViewCalls.get(0);
		Assert.assertEquals(session, call[0]);
		Assert.assertEquals("My view", call[1]);
		Assert.assertEquals("notes", call[2]);
		Assert.assertEquals(Set.of(String.class), call[3]);
		Assert.assertEquals(Set.of(String.class), ((Map<?, ?>) call[4]).keySet());
		Assert.assertEquals(3.0, call[5]);
		Assert.assertEquals("My view", this.viewServices.tabTitles.get(session));
		Assert.assertTrue(this.viewServices.errors.isEmpty());
	}

	@Test
	public void confirmSaveViewShowsAnErrorOnIOExceptionAndKeepsTheTabTitle() {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);
		this.viewServices.tabTitles.put(session, "Components");
		this.controllerServices.failOnSave = true;

		this.actions.fire(session, Role.ConfirmSaveView, new SaveViewRequest("My view", ""));

		assertSingleError("My view", "simulated save failure");
		Assert.assertEquals("Components", this.viewServices.tabTitles.get(session));
		Assert.assertEquals(List.of("Failed to save the view My view"), this.componentDiagramInteractionLogs.messages(Level.WARNING));
	}

	@Test
	public void quickSaveViewReplacesTheTabsViewWithoutADialog() {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);
		this.controllerServices.sessionView = savedView("Saved", "container");

		this.actions.fire(session, Role.QuickSaveView);

		Assert.assertEquals("Saved", this.controllerServices.saveViewCalls.get(0)[1]);
		Assert.assertTrue(this.viewServices.saveViewForms.isEmpty());
	}

	@Test
	public void quickSaveViewShowsTheSaveDialogWhenTheTabHasNoViewOfTheObservedContainer() {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);
		this.viewServices.tabTitles.put(session, "Components");
		this.controllerServices.sessionView = savedView("Saved", "another container");

		this.actions.fire(session, Role.QuickSaveView);

		Assert.assertTrue(this.controllerServices.saveViewCalls.isEmpty());
		Assert.assertEquals("Components", this.viewServices.saveViewForms.get(0).name());
		Assert.assertEquals("container", this.viewServices.saveViewForms.get(0).containerName());
	}

	@Test
	public void saveViewShowsAnErrorWhenNoContainerIsObserved() {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);
		this.controllerServices.observedContainerName = null;

		this.actions.fire(session, Role.SaveView);

		Assert.assertEquals(1, this.viewServices.errors.size());
		Assert.assertTrue(this.viewServices.saveViewForms.isEmpty());
	}

	@Test
	public void openViewDelegatesToControllerServices() {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);
		SavedView view = savedView("Saved", "container");

		this.actions.fire(session, Role.OpenView, view);

		Assert.assertEquals(List.of(view), this.controllerServices.openViewCalls);
	}

	@Test
	public void renameTabSetsTheTitleAndRenamesTheSession() {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);

		this.actions.fire(session, Role.RenameTab, "Renamed");

		Assert.assertEquals("Renamed", this.viewServices.tabTitles.get(session));
		Assert.assertArrayEquals(new Object[] { session, "Renamed" }, this.controllerServices.renameSessionCalls.get(0));
	}

	// --- Private, prefs-free coordination methods, reached by reflection ---

	@Test
	public void openCompositeOpensANewSessionOnlyWhenNoneIsOpenYet() throws Exception {
		this.controllerServices.onOpenNewSession = () -> this.controller.onSessionCreated(new TestViewKey("minted"));

		this.invokePrivate("onOpenComposite", new Class<?>[] { Class.class }, String.class);

		Assert.assertEquals(1, this.controllerServices.openNewSessionCalls);
		Object[] enterCall = this.controllerServices.enterCompositeCalls.get(0);
		Assert.assertEquals(String.class, enterCall[1]);
		Object[] exitCall = this.controllerServices.exitToDepthCalls.get(0);
		Assert.assertEquals(0, exitCall[1]);
		Assert.assertNotNull(this.viewServices.lastBroughtToFront);
	}

	@Test
	public void openCompositeReusesAnAlreadyOpenSessionWithoutOpeningAnother() throws Exception {
		TestViewKey session = new TestViewKey("already-open");
		this.controller.onSessionCreated(session);

		this.invokePrivate("onOpenComposite", new Class<?>[] { Class.class }, Integer.class);

		Assert.assertEquals("an already-open session must be reused, not replaced", 0,
				this.controllerServices.openNewSessionCalls);
		Assert.assertEquals(session, this.controllerServices.enterCompositeCalls.get(0)[0]);
	}

	@Test
	public void openCompositeIgnoresANullCompositeType() throws Exception {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);

		this.invokePrivate("onOpenComposite", new Class<?>[] { Class.class }, (Object) null);

		Assert.assertTrue(this.controllerServices.enterCompositeCalls.isEmpty());
	}

	@Test
	public void showLinkDelegatesWithTheTwoEndsInOrder() throws Exception {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);

		this.invokePrivate("onShowLink", new Class<?>[] { List.class }, List.of(String.class, Integer.class));

		Assert.assertArrayEquals(new Object[] { session, String.class, Integer.class },
				this.controllerServices.showLinkCalls.get(0));
	}

	@Test
	public void showLinkIgnoresAMalformedOrEmptyEndsListAndRequiresAnOpenSession() throws Exception {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);

		this.invokePrivate("onShowLink", new Class<?>[] { List.class }, (Object) null);
		this.invokePrivate("onShowLink", new Class<?>[] { List.class }, List.of(String.class));
		this.invokePrivate("onShowLink", new Class<?>[] { List.class }, List.of(String.class, Integer.class, Double.class));

		Assert.assertTrue("none of these malformed inputs should have reached showLink",
				this.controllerServices.showLinkCalls.isEmpty());
	}

	@Test
	public void aRecordingStartedElsewhereCanBeStoppedFromAnyTab() {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);
		ActionKey record = new ComponentDiagramActionKey(session, Role.RecordToggle);
		Assert.assertEquals(Boolean.FALSE, this.actions.availability.get(record));

		// Started by another tab or function: this tab, with nothing selected, can stop it.
		this.controllerServices.recording = true;
		this.controller.onRecordingStarted();
		Assert.assertEquals(Boolean.TRUE, this.actions.availability.get(record));
		this.actions.fire(session, Role.RecordToggle);
		Assert.assertEquals(1, this.controllerServices.stopRecordingCalls);
		Assert.assertEquals(0, this.controllerServices.startRecordingCalls);

		this.controller.onRecordingStopped();
		Assert.assertEquals(Boolean.FALSE, this.actions.availability.get(record));
	}

	@Test
	public void lateRecordingNoticesLeaveTheRightAvailability() {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);
		ActionKey record = new ComponentDiagramActionKey(session, Role.RecordToggle);

		// A stop notice arriving after a new recording started: the state is asked, not assumed.
		this.controllerServices.recording = true;
		this.controller.onRecordingStopped();
		Assert.assertEquals(Boolean.TRUE, this.actions.availability.get(record));
	}

	// --- Views library: the dialog, the library and its commands ---

	@Test
	public void saveViewProposesTheTabsStateAndTheNamesAlreadyTakenForTheObservedContainer() throws Exception {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);
		this.setField("useComponentDiagramModelReadServices", ComponentDiagramModelReadServices.class.cast(
				Proxy.newProxyInstance(ComponentDiagramModelReadServices.class.getClassLoader(),
						new Class<?>[] { ComponentDiagramModelReadServices.class },
						(proxy, method, args) -> "getFocusPath".equals(method.getName())
								? List.of(String.class, Integer.class)
								: null)));
		this.persistence.views.add(savedView("Mine", "container"));
		this.persistence.views.add(savedView("Elsewhere", "another container"));
		this.persistence.views.add(new SavedView("Broken", "", "container", null, null, null, null, "unreadable"));
		this.controllerServices.sessionView = savedView("Mine", "container");
		this.viewServices.tabTitles.put(session, "Components");
		this.viewServices.positions = Map.of(String.class, new double[] { 1, 2 }, Integer.class, new double[] { 3, 4 });
		this.viewServices.hiddenComponents = Set.of(Double.class);
		this.viewServices.zoomScale.put(session, 1.5);

		this.actions.fire(session, Role.SaveView);

		SaveViewForm form = this.viewServices.saveViewForms.get(0);
		Assert.assertEquals("Components", form.name());
		Assert.assertEquals("description", form.description());
		Assert.assertEquals("container", form.containerName());
		Assert.assertEquals(Set.of("Mine"), form.existingNames());
		Assert.assertEquals(2, form.componentCount());
		Assert.assertEquals(1, form.hiddenCount());
		Assert.assertEquals(1.5, form.zoom(), 0.0);
		Assert.assertTrue(form.level().contains("String"));
		Assert.assertTrue(form.level().indexOf("String") < form.level().indexOf("Integer"));
	}

	@Test
	public void openingTheViewLibraryShowsEveryViewWithItsMissingComponents() {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);
		this.persistence.views.add(savedView("Mine", "container"));
		this.persistence.views.add(savedView("Elsewhere", "another container"));

		this.actions.fire(session, Role.OpenViewLibrary);

		ViewLibraryContent library = this.viewServices.shownLibrary;
		Assert.assertEquals("container", library.containerName());
		Assert.assertEquals(this.persistence.folder, library.viewsFolder());
		Assert.assertEquals(2, library.entries().size());
		Assert.assertEquals("Mine", library.entries().get(0).view().name());
	}

	@Test
	public void renamingAViewRefreshesTheLibrary() {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);
		SavedView view = savedView("Old", "container");

		this.actions.fire(session, Role.RenameView, new RenameViewRequest(view, "New"));

		Assert.assertEquals(List.of("rename Old New"), this.persistence.calls);
		Assert.assertEquals(1, this.viewServices.libraryUpdates);
		Assert.assertTrue(this.viewServices.errors.isEmpty());
	}

	@Test
	public void aFailedRenameIsReportedAndTheLibraryRefreshedAnyway() {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);
		this.persistence.failWith = new IOException("cannot rename");

		this.actions.fire(session, Role.RenameView, new RenameViewRequest(savedView("Old", "container"), "New"));

		assertSingleError("Old", "cannot rename");
		Assert.assertEquals(1, this.viewServices.libraryUpdates);
		Assert.assertEquals(List.of("Failed to rename the view Old"), this.componentDiagramInteractionLogs.messages(Level.WARNING));
	}

	@Test
	public void deletingAViewRefreshesTheLibraryAndAFailureIsReported() {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);
		SavedView view = savedView("Old", "container");

		this.actions.fire(session, Role.DeleteView, view);
		Assert.assertEquals(List.of("delete Old"), this.persistence.calls);
		Assert.assertEquals(1, this.viewServices.libraryUpdates);

		this.persistence.failWith = new IOException("cannot delete");
		this.actions.fire(session, Role.DeleteView, view);
		assertSingleError("Old", "cannot delete");
		Assert.assertEquals(2, this.viewServices.libraryUpdates);
		Assert.assertEquals(List.of("Failed to delete the view Old"), this.componentDiagramInteractionLogs.messages(Level.WARNING));
	}

	@Test
	public void aFailureWithoutAMessageIsReportedByItsName() {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);
		this.persistence.failWith = new IOException();

		this.actions.fire(session, Role.DeleteView, savedView("Old", "container"));

		assertSingleError("Old", "IOException");

		this.viewServices.errors.clear();
		this.persistence.failWith = new IOException("  ");
		this.actions.fire(session, Role.DeleteView, savedView("Old", "container"));

		assertSingleError("Old", "IOException");
	}

	/** One error was shown: it names what could not be done, then gives the reason after a blank line. */
	private void assertSingleError(final String subject, final String reason) {
		Assert.assertEquals(1, this.viewServices.errors.size());
		String error = this.viewServices.errors.get(0);
		Assert.assertTrue(error, error.contains(subject));
		Assert.assertTrue(error, error.endsWith("\n\n" + reason));
	}

	@Test
	public void exportingAViewWritesItAndAFailureIsReported() {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);
		SavedView view = savedView("Mine", "container");

		this.actions.fire(session, Role.ExportView, new ExportViewRequest(view, new File("out.cow")));
		Assert.assertEquals(List.of("export Mine out.cow"), this.persistence.calls);
		Assert.assertTrue(this.viewServices.errors.isEmpty());

		this.persistence.failWith = new IOException("disk full");
		this.actions.fire(session, Role.ExportView, new ExportViewRequest(view, new File("out.cow")));
		assertSingleError("Mine", "disk full");
		Assert.assertEquals(List.of("Failed to export the view Mine"), this.componentDiagramInteractionLogs.messages(Level.WARNING));
	}

	@Test
	public void importingANewViewNeedsNoConfirmation() {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);
		this.persistence.toRead = savedView("Fresh", "container");

		this.actions.fire(session, Role.ImportView, new File("fresh.cow"));

		Assert.assertEquals(0, this.viewServices.confirmCalls);
		Assert.assertEquals(List.of("import fresh.cow"), this.persistence.calls);
		Assert.assertEquals(1, this.viewServices.libraryUpdates);
	}

	@Test
	public void importingAViewThatReplacesAnotherAsksFirst() {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);
		this.persistence.toRead = savedView("Same", "container");
		this.persistence.existing.add("container|Same");

		this.viewServices.confirmResult = false;
		this.actions.fire(session, Role.ImportView, new File("same.cow"));
		Assert.assertEquals(1, this.viewServices.confirmCalls);
		Assert.assertTrue("declined: nothing is imported", this.persistence.calls.isEmpty());
		Assert.assertEquals(0, this.viewServices.libraryUpdates);

		this.viewServices.confirmResult = true;
		this.actions.fire(session, Role.ImportView, new File("same.cow"));
		Assert.assertEquals(List.of("import same.cow"), this.persistence.calls);
		Assert.assertEquals(1, this.viewServices.libraryUpdates);
	}

	@Test
	public void anUnreadableViewFileIsReportedAndImportsNothing() {
		TestViewKey session = new TestViewKey("a");
		this.controller.onSessionCreated(session);
		this.persistence.failWith = new IOException("not a view file");

		this.actions.fire(session, Role.ImportView, new File("junk.cow"));

		assertSingleError("junk.cow", "not a view file");
		Assert.assertTrue(this.persistence.calls.isEmpty());
		Assert.assertEquals(List.of("Failed to import the view file junk.cow"), this.componentDiagramInteractionLogs.messages(Level.WARNING));
	}

	// --- Passivation ---

	@Test
	public void passivationUnregistersTheActionsOfEveryOpenTab() {
		this.controller.onSessionCreated(new TestViewKey("a"));
		this.controller.onSessionCreated(new TestViewKey("b"));

		this.controller.unregisterEverything();

		Assert.assertTrue(this.actions.registered.isEmpty());
	}

	@Test
	public void passivationToleratesAnActionManagerThatAlreadyStopped() {
		this.controller.onSessionCreated(new TestViewKey("a"));
		ActionProviderServices stopped = ActionProviderServices.class.cast(Proxy.newProxyInstance(
				ActionProviderServices.class.getClassLoader(), new Class<?>[] { ActionProviderServices.class },
				(proxy, method, args) -> {
					throw new IllegalStateException("already passivated");
				}));
		try {
			this.setField("actionManagerServices", stopped);
		} catch (Exception e) {
			throw new AssertionError(e);
		}

		this.controller.unregisterEverything();
		this.controller.onSessionClosed(new TestViewKey("a"));
	}
}
