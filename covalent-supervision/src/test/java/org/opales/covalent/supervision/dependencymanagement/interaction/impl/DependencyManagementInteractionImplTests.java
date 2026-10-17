/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.dependencymanagement.interaction.impl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import org.opales.covalent.supervision.dependencymanagement.controller.DependencyManagementControllerServices;
import org.opales.covalent.supervision.dependencymanagement.interaction.DependencyManagementActionKey;
import org.opales.covalent.supervision.dependencymanagement.interaction.DependencyManagementViewKey;
import org.opales.covalent.supervision.dependencymanagement.view.DependencyManagementViewServices;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

/** {@link DependencyManagementInteractionImpl} against hand-written fakes of the services it uses. */
public class DependencyManagementInteractionImplTests {

	/** The actions the interaction registers, runnable by the test. */
	private static final class FakeActionProviderServices implements ActionProviderServices {
		final Map<ActionKey, Consumer<Object>> consumers = new LinkedHashMap<>();
		final Map<ActionKey, Runnable> runnables = new LinkedHashMap<>();
		final Set<ActionKey> registered = new HashSet<>();
		final List<ActionKey> unregistered = new ArrayList<>();
		final Map<ActionKey, Object> values = new HashMap<>();
		boolean failOnUnregister;

		@Override
		@SuppressWarnings("unchecked")
		public <T> void registerActionExecution(final ActionKey anActionKey, final Consumer<T> anInteractionImpl,
				final Class<T> anInputDataClass) {
			this.registered.add(anActionKey);
			this.consumers.put(anActionKey, (Consumer<Object>) anInteractionImpl);
		}

		@Override
		public <T1, T2> void registerActionExecution(final ActionKey anActionKey,
				final BiConsumer<T1, T2> anInteractionImpl, final Class<T1> anInputDataClass1,
				final Class<T2> anInputDataClass2) {
			this.registered.add(anActionKey);
		}

		@Override
		public void registerActionExecution(final ActionKey anActionKey, final Runnable anInteractionImpl) {
			this.registered.add(anActionKey);
			this.runnables.put(anActionKey, anInteractionImpl);
		}

		@Override
		public void unregisterActionExecution(final ActionKey anActionKey) {
			if (this.failOnUnregister) {
				throw new IllegalStateException("already passivated");
			}
			this.unregistered.add(anActionKey);
		}

		@Override
		public void setActionAvailable(final ActionKey anActionKey, final boolean isAvailable) {
		}

		@Override
		public void setActionValue(final ActionKey anActionKey, final Object aValue) {
			this.values.put(anActionKey, aValue);
		}

		void fire(final ActionKey key, final Object input) {
			this.consumers.get(key).accept(input);
		}

		void fire(final ActionKey key) {
			this.runnables.get(key).run();
		}
	}

	private static final class FakeControllerServices implements DependencyManagementControllerServices {
		int refreshCalls;

		@Override
		public void refreshGraph() {
			this.refreshCalls++;
		}
	}

	private static final class FakeViewServices implements DependencyManagementViewServices {
		final List<ViewKey> shown = new ArrayList<>();
		final List<ViewKey> hidden = new ArrayList<>();
		final List<Double> zoomCalls = new ArrayList<>();
		int recenterCalls;
		int exportCalls;

		@Override
		public void showView(final ViewKey key) {
			this.shown.add(key);
		}

		@Override
		public void hideView(final ViewKey key) {
			this.hidden.add(key);
		}

		@Override
		public void recenter() {
			this.recenterCalls++;
		}

		@Override
		public void setZoomScale(final double scale) {
			this.zoomCalls.add(scale);
		}

		@Override
		public void exportSnapshot() {
			this.exportCalls++;
		}
	}

	private FakeActionProviderServices actions;
	private FakeControllerServices controller;
	private FakeViewServices view;
	private DependencyManagementInteractionImpl interaction;

	@Before
	public void setUp() throws Exception {
		this.actions = new FakeActionProviderServices();
		this.controller = new FakeControllerServices();
		this.view = new FakeViewServices();
		this.interaction = new DependencyManagementInteractionImpl();
		inject("actionManagerServices", this.actions);
		inject("useControllerServices", this.controller);
		inject("useViewServices", this.view);
	}

	private void inject(final String fieldName, final Object value) throws Exception {
		Field field = DependencyManagementInteractionImpl.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(this.interaction, value);
	}

	/** Every key the interaction executes: the current zoom scale is a value, never an execution. */
	private static Set<ActionKey> executedKeys() {
		Set<ActionKey> keys = new HashSet<>(Set.of(DependencyManagementActionKey.values()));
		keys.remove(DependencyManagementActionKey.ZoomCurrentScale);
		return keys;
	}

	@Test
	public void activationRegistersTheSixActions() {
		this.interaction.registerActions();

		Assert.assertEquals(executedKeys(), this.actions.registered);
		Assert.assertEquals(6, this.actions.registered.size());
	}

	@Test
	public void theZoomScaleIsHandedToTheView() {
		this.interaction.registerActions();

		this.actions.fire(DependencyManagementActionKey.ZoomSetScale, 1.5);

		Assert.assertEquals(List.of(1.5), this.view.zoomCalls);
	}

	@Test
	public void theCurrentZoomScaleIsPublishedAsAValue() {
		this.interaction.registerActions();

		this.actions.fire(DependencyManagementActionKey.ZoomChanged, 2.0);

		Assert.assertEquals(2.0, this.actions.values.get(DependencyManagementActionKey.ZoomCurrentScale));
	}

	@Test
	public void recenterAndExportAreHandedToTheView() {
		this.interaction.registerActions();

		this.actions.fire(DependencyManagementActionKey.Recenter);
		this.actions.fire(DependencyManagementActionKey.Export);
		this.actions.fire(DependencyManagementActionKey.Export);

		Assert.assertEquals(1, this.view.recenterCalls);
		Assert.assertEquals(2, this.view.exportCalls);
	}

	@Test
	public void refreshIsHandedToTheController() {
		this.interaction.registerActions();

		this.actions.fire(DependencyManagementActionKey.Refresh);

		Assert.assertEquals(1, this.controller.refreshCalls);
	}

	@Test
	public void togglingShowsThePanelAfterAskingForTheGraphThenHidesIt() {
		this.interaction.registerActions();

		this.actions.fire(DependencyManagementActionKey.Toggle);
		Assert.assertEquals(List.of(DependencyManagementViewKey.INSTANCE), this.view.shown);
		Assert.assertEquals("the graph is asked again once the panel is there", 1, this.controller.refreshCalls);
		Assert.assertTrue(this.view.hidden.isEmpty());

		this.actions.fire(DependencyManagementActionKey.Toggle);
		Assert.assertEquals(List.of(DependencyManagementViewKey.INSTANCE), this.view.hidden);
		Assert.assertEquals(1, this.controller.refreshCalls);
	}

	@Test
	public void afterAReactivationTheFirstToggleShowsThePanel() {
		this.interaction.registerActions();
		this.actions.fire(DependencyManagementActionKey.Toggle);
		this.interaction.unregisterActions();
		this.interaction.registerActions();

		this.actions.fire(DependencyManagementActionKey.Toggle);

		Assert.assertEquals(List.of(DependencyManagementViewKey.INSTANCE, DependencyManagementViewKey.INSTANCE),
				this.view.shown);
		Assert.assertEquals("hidden by the passivation only", List.of(DependencyManagementViewKey.INSTANCE),
				this.view.hidden);
	}

	@Test
	public void passivationUnregistersEveryExecutedActionAndLeavesAClosedPanelAlone() {
		this.interaction.registerActions();

		this.interaction.unregisterActions();

		Assert.assertEquals(executedKeys(), new HashSet<>(this.actions.unregistered));
		Assert.assertTrue(this.view.hidden.isEmpty());
	}

	@Test
	public void passivationClosesAnOpenPanelEvenWhenTheActionManagerIsAlreadyPassivated() {
		this.interaction.registerActions();
		this.actions.fire(DependencyManagementActionKey.Toggle);
		this.actions.failOnUnregister = true;

		this.interaction.unregisterActions();

		Assert.assertEquals(List.of(DependencyManagementViewKey.INSTANCE), this.view.hidden);
	}

	@Test
	public void theGraphsNotificationIsNotItsConcern() {
		this.interaction.registerActions();

		this.interaction.onGraphChanged();

		Assert.assertEquals(0, this.controller.refreshCalls);
		Assert.assertTrue(this.view.shown.isEmpty());
	}
}
