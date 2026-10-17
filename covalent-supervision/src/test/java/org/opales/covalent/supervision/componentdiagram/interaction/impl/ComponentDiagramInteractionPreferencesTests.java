/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.interaction.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.hmi.action.ActionProviderServices;
import org.opales.covalent.supervision.componentdiagram.controller.ComponentDiagramControllerServices;
import org.opales.covalent.supervision.componentdiagram.interaction.ComponentDiagramActionKey;
import org.opales.covalent.supervision.componentdiagram.interaction.ComponentDiagramPreferencesActionKey;
import org.opales.covalent.supervision.componentdiagram.interaction.ComponentDiagramSessionKey;
import org.opales.covalent.supervision.componentdiagram.interaction.ComponentDiagramToolActionKey;
import org.opales.covalent.supervision.componentdiagram.model.ComponentPortKind;
import org.opales.covalent.supervision.componentdiagram.model.PortColorKey;
import org.opales.covalent.supervision.componentdiagram.view.ComponentDiagramViewServices;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisorTheme;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

/**
 * Verifies the tool-wide preference actions of {@link ComponentDiagramInteractionImpl} - auto-layout on hide, port
 * colors, theme - the activation that registers them and opens the first tab, and that the events the interaction
 * does not care about leave every collaborator untouched. Collaborators are recording proxies; no container and no
 * JavaFX scene is involved.
 */
public class ComponentDiagramInteractionPreferencesTests {

	/** Records the name and arguments of every call; answers with the configured value of the method name. */
	private static final class Recorder {
		final List<String> calls = new ArrayList<>();
		final Map<String, Object> answers = new java.util.HashMap<>();
		final List<Object[]> arguments = new ArrayList<>();

		<T> T proxy(final Class<T> type) {
			return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (p, method, args) -> {
				this.calls.add(method.getName());
				this.arguments.add(args == null ? new Object[0] : args);
				if (this.answers.containsKey(method.getName())) {
					return this.answers.get(method.getName());
				}
				Class<?> r = method.getReturnType();
				if (r == boolean.class) {
					return false;
				}
				if (r == double.class) {
					return 1.0;
				}
				return List.class.equals(r) ? List.of() : null;
			}));
		}

		Object[] lastArgumentsOf(final String name) {
			for (int i = this.calls.size() - 1; i >= 0; i--) {
				if (this.calls.get(i).equals(name)) {
					return this.arguments.get(i);
				}
			}
			return null;
		}

		long count(final String name) {
			return this.calls.stream().filter(name::equals).count();
		}
	}

	/** An action manager that remembers registrations and published values. */
	private static final class Actions implements ActionProviderServices {
		final Map<ActionKey, Object> implementations = new java.util.LinkedHashMap<>();
		final Map<ActionKey, Object> values = new java.util.LinkedHashMap<>();
		final Map<ActionKey, Boolean> availability = new java.util.LinkedHashMap<>();

		@Override
		public <T> void registerActionExecution(final ActionKey key, final Consumer<T> impl, final Class<T> type) {
			this.implementations.put(key, impl);
		}

		@Override
		public <T1, T2> void registerActionExecution(final ActionKey key, final BiConsumer<T1, T2> impl,
				final Class<T1> type1, final Class<T2> type2) {
			this.implementations.put(key, impl);
		}

		@Override
		public void registerActionExecution(final ActionKey key, final Runnable impl) {
			this.implementations.put(key, impl);
		}

		@Override
		public void unregisterActionExecution(final ActionKey key) {
			this.implementations.remove(key);
		}

		@Override
		public void setActionAvailable(final ActionKey key, final boolean available) {
			this.availability.put(key, available);
		}

		@Override
		public void setActionValue(final ActionKey key, final Object value) {
			this.values.put(key, value);
		}
	}

	private ComponentDiagramInteractionImpl impl;
	private Actions actions;
	private Recorder viewRecorder;
	private Recorder controllerRecorder;
	private ComponentDiagramViewServices view;

	@Before
	public void setUp() throws Exception {
		this.impl = new ComponentDiagramInteractionImpl();
		this.actions = new Actions();
		this.viewRecorder = new Recorder();
		this.controllerRecorder = new Recorder();
		this.view = this.viewRecorder.proxy(ComponentDiagramViewServices.class);
		this.viewRecorder.answers.put("getPortColor", "#123456");
		this.set("actionManagerServices", this.actions);
		this.set("useComponentDiagramViewServices", this.view);
		this.set("useComponentDiagramControllerServices", this.controllerRecorder.proxy(ComponentDiagramControllerServices.class));
	}

	private void set(final String name, final Object value) throws Exception {
		Field field = ComponentDiagramInteractionImpl.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(this.impl, value);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void togglingAutoLayoutOnHideForwardsTheValueToTheView() {
		this.impl.registerPreferenceActions();
		((Consumer<Boolean>) this.actions.implementations.get(ComponentDiagramPreferencesActionKey.ToggleAutoLayoutOnHide))
				.accept(Boolean.TRUE);
		Assert.assertEquals(Boolean.TRUE, this.viewRecorder.lastArgumentsOf("setAutoLayoutOnHide")[0]);
	}

	@SuppressWarnings("unchecked")
	@Test
	public void settingAPortColorForwardsItAndRepublishesTheWholePalette() {
		this.impl.registerPreferenceActions();
		this.actions.values.clear();
		((BiConsumer<PortColorKey, String>) this.actions.implementations.get(ComponentDiagramPreferencesActionKey.SetPortColor))
				.accept(PortColorKey.SELECTION, "#abcdef");
		Object[] args = this.viewRecorder.lastArgumentsOf("setPortColor");
		Assert.assertEquals(PortColorKey.SELECTION, args[0]);
		Assert.assertEquals("#abcdef", args[1]);
		Map<?, ?> snapshot = (Map<?, ?>) this.actions.values.get(ComponentDiagramPreferencesActionKey.PortColorsSnapshot);
		Assert.assertEquals(PortColorKey.values().length, snapshot.size());
		Assert.assertEquals("#123456", snapshot.get(PortColorKey.SELECTION));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void changingTheThemeForwardsItAndRepublishesThePalette() {
		this.impl.registerPreferenceActions();
		this.actions.values.clear();
		long before = this.viewRecorder.count("setTheme");
		((Consumer<SupervisorTheme>) this.actions.implementations.get(ComponentDiagramPreferencesActionKey.SetTheme))
				.accept(SupervisorTheme.DARK);
		Assert.assertEquals(before + 1, this.viewRecorder.count("setTheme"));
		Assert.assertEquals(SupervisorTheme.DARK, this.viewRecorder.lastArgumentsOf("setTheme")[0]);
		Assert.assertTrue(this.actions.values.containsKey(ComponentDiagramPreferencesActionKey.PortColorsSnapshot));
	}

	@Test
	public void resettingThePortColorsHasTheViewRestoreTheDefaultsAndRepublishesThePalette() {
		this.impl.registerPreferenceActions();
		this.actions.values.clear();

		((Runnable) this.actions.implementations.get(ComponentDiagramPreferencesActionKey.ResetPortColors)).run();

		Assert.assertEquals(1, this.viewRecorder.count("resetPortColors"));
		Assert.assertTrue(this.actions.values.containsKey(ComponentDiagramPreferencesActionKey.PortColorsSnapshot));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void resetIsAvailableOnlyWhileAColorDiffersFromItsDefault() {
		this.viewRecorder.answers.put("getDefaultPortColor", "#123456");
		this.impl.registerPreferenceActions();
		Assert.assertEquals(Boolean.FALSE,
				this.actions.availability.get(ComponentDiagramPreferencesActionKey.ResetPortColors));

		this.viewRecorder.answers.put("getDefaultPortColor", "#000000");
		((BiConsumer<PortColorKey, String>) this.actions.implementations.get(ComponentDiagramPreferencesActionKey.SetPortColor))
				.accept(PortColorKey.SELECTION, "#123456");

		Assert.assertEquals(Boolean.TRUE,
				this.actions.availability.get(ComponentDiagramPreferencesActionKey.ResetPortColors));
	}

	@Test
	public void activationRegistersEveryToolActionPushesThePaletteAndOpensTheFirstTab() {
		this.impl.registerPreferenceActions();
		for (ComponentDiagramPreferencesActionKey key : new ComponentDiagramPreferencesActionKey[] {
				ComponentDiagramPreferencesActionKey.ToggleAutoLayoutOnHide,
				ComponentDiagramPreferencesActionKey.SetPortColor, ComponentDiagramPreferencesActionKey.SetTheme,
				ComponentDiagramPreferencesActionKey.ResetPortColors }) {
			Assert.assertTrue(key.name(), this.actions.implementations.containsKey(key));
		}
		for (ComponentDiagramToolActionKey key : new ComponentDiagramToolActionKey[] {
				ComponentDiagramToolActionKey.NewDiagram, ComponentDiagramToolActionKey.ShowLink,
				ComponentDiagramToolActionKey.OpenComposite }) {
			Assert.assertTrue(key.name(), this.actions.implementations.containsKey(key));
		}
		Assert.assertTrue(this.actions.values.containsKey(ComponentDiagramPreferencesActionKey.PortColorsSnapshot));
		Assert.assertEquals(1, this.viewRecorder.count("setAutoLayoutOnHide"));
		Assert.assertEquals(1, this.controllerRecorder.count("openNewSession"));
	}

	@Test
	public void passivationAfterActivationUnregistersTheToolWideActions() {
		this.impl.registerPreferenceActions();
		this.impl.unregisterEverything();
		Assert.assertTrue(this.actions.implementations.isEmpty());
	}

	@Test
	public void eventsTheInteractionIgnoresTouchNoCollaborator() {
		ViewKey tab = new ComponentDiagramSessionKey();
		this.impl.onTopologyChanged(tab);
		this.impl.onPortActivityChanged(String.class, Integer.class, ComponentPortKind.SERVICE, true);
		this.impl.onLiveStructureChanged();
		this.impl.onActivitySampled(1L, Map.of());
		this.impl.onComponentStatusesChanged();
		this.impl.onDependenciesVisibilityChanged(tab, String.class, true);
		this.impl.onFocusPathChanged(tab, List.of(String.class));
		this.impl.onFlashComponentChanged(tab, String.class);
		this.impl.onLinkHighlightRequested(tab, String.class, Integer.class);
		Assert.assertTrue(this.viewRecorder.calls.isEmpty());
		Assert.assertTrue(this.controllerRecorder.calls.isEmpty());
		Assert.assertTrue(this.actions.implementations.isEmpty());
		Assert.assertTrue(this.actions.values.isEmpty());
	}

	@Test
	public void selectionChangeOfAnUnknownTabIsIgnored() {
		this.impl.onSelectionChanged(new ComponentDiagramSessionKey(), Set.of(String.class));
		Assert.assertTrue(this.actions.values.isEmpty());
		Assert.assertTrue(this.controllerRecorder.calls.isEmpty());
	}

	@SuppressWarnings("unchecked")
	private <T> T implementation(final ViewKey tab, final ComponentDiagramActionKey.Role role) {
		return (T) this.actions.implementations.get(new ComponentDiagramActionKey(tab, role));
	}

	@Test
	public void recenterActionHasTheViewRecenterThatTab() {
		ViewKey tab = new ComponentDiagramSessionKey();
		this.impl.onSessionCreated(tab);

		this.<Runnable>implementation(tab, ComponentDiagramActionKey.Role.Recenter).run();

		Assert.assertEquals(tab, this.viewRecorder.lastArgumentsOf("recenter")[0]);
	}

	@Test
	public void recordToggleDoesNothingWithoutASelectionWhenNothingIsRecording() {
		ViewKey tab = new ComponentDiagramSessionKey();
		this.impl.onSessionCreated(tab);

		this.<Runnable>implementation(tab, ComponentDiagramActionKey.Role.RecordToggle).run();

		Assert.assertEquals(0, this.controllerRecorder.count("startRecording"));
		Assert.assertEquals(0, this.controllerRecorder.count("stopRecording"));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void openingACompositeDoesNothingWhenNoDiagramCouldBeOpened() {
		this.impl.registerPreferenceActions();
		long opened = this.controllerRecorder.count("openNewSession");

		((Consumer<Class<?>>) this.actions.implementations.get(ComponentDiagramToolActionKey.OpenComposite))
				.accept(String.class);

		Assert.assertEquals(opened + 1, this.controllerRecorder.count("openNewSession"));
		Assert.assertEquals(0, this.controllerRecorder.count("enterComposite"));
		Assert.assertEquals(0, this.viewRecorder.count("bringToFront"));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void showLinkIgnoresEndsThatAreNotBothTypes() {
		this.impl.registerPreferenceActions();
		this.impl.onSessionCreated(new ComponentDiagramSessionKey());
		Consumer<List<?>> showLink = (Consumer<List<?>>) this.actions.implementations
				.get(ComponentDiagramToolActionKey.ShowLink);

		showLink.accept(List.of("not a type", Integer.class));
		showLink.accept(List.of(String.class, "not a type"));
		showLink.accept(List.of(String.class));
		showLink.accept(null);
		Assert.assertEquals(0, this.controllerRecorder.count("showLink"));

		showLink.accept(List.of(String.class, Integer.class));
		Assert.assertEquals(1, this.controllerRecorder.count("showLink"));
	}

	@Test
	public void quickSaveWithAViewButNoObservedContainerReportsTheMissingContainer() {
		ViewKey tab = new ComponentDiagramSessionKey();
		this.impl.onSessionCreated(tab);
		this.controllerRecorder.answers.put("getSessionView", new org.opales.covalent.supervision.diagrampersistence.SavedView(
				"v", "d", "some container", null, null, null, null, null));

		this.<Runnable>implementation(tab, ComponentDiagramActionKey.Role.QuickSaveView).run();

		Assert.assertEquals(0, this.controllerRecorder.count("saveView"));
		Assert.assertEquals(1, this.viewRecorder.count("showError"));
	}
}
