/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.interaction.impl;

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

import org.opales.covalent.container.workers.WorkerPoolKind;
import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.hmi.action.ActionProviderServices;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;
import org.opales.covalent.supervision.threads.controller.ThreadsControllerServices;
import org.opales.covalent.supervision.threads.interaction.ThreadsActionKey;
import org.opales.covalent.supervision.threads.interaction.ThreadsViewKey;
import org.opales.covalent.supervision.threads.view.ThreadsViewServices;

/** {@link ThreadsInteractionImpl} against hand-written fakes of the services it uses. */
public class ThreadsInteractionImplTests {

	/** The actions the interaction registers, runnable by the test, and what it says about their availability. */
	private static final class FakeActionProviderServices implements ActionProviderServices {
		final Map<ActionKey, Consumer<Object>> consumers = new LinkedHashMap<>();
		final Map<ActionKey, BiConsumer<Object, Object>> biConsumers = new LinkedHashMap<>();
		final Map<ActionKey, Runnable> runnables = new LinkedHashMap<>();
		final Set<ActionKey> registered = new HashSet<>();
		final List<ActionKey> unregistered = new ArrayList<>();
		final Map<ActionKey, Boolean> availability = new HashMap<>();
		boolean failOnUnregister;

		@Override
		@SuppressWarnings("unchecked")
		public <T> void registerActionExecution(final ActionKey anActionKey, final Consumer<T> anInteractionImpl,
				final Class<T> anInputDataClass) {
			this.registered.add(anActionKey);
			this.consumers.put(anActionKey, (Consumer<Object>) anInteractionImpl);
		}

		@Override
		@SuppressWarnings("unchecked")
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
			if (this.failOnUnregister) {
				throw new IllegalStateException("already passivated");
			}
			this.unregistered.add(anActionKey);
		}

		@Override
		public void setActionAvailable(final ActionKey anActionKey, final boolean isAvailable) {
			this.availability.put(anActionKey, isAvailable);
		}

		@Override
		public void setActionValue(final ActionKey anActionKey, final Object aValue) {
		}

		void fire(final ActionKey key, final Object input) {
			this.consumers.get(key).accept(input);
		}

		void fire(final ActionKey key, final Object input1, final Object input2) {
			this.biConsumers.get(key).accept(input1, input2);
		}

		void fire(final ActionKey key) {
			this.runnables.get(key).run();
		}
	}

	/** Records every call the interaction makes to the Controller. */
	private static final class FakeControllerServices implements ThreadsControllerServices {
		final List<Boolean> trackingCalls = new ArrayList<>();
		final List<Long> readStackCalls = new ArrayList<>();
		final List<Set<Long>> interruptCalls = new ArrayList<>();
		final List<Set<Class<?>>> toggleRecordingCalls = new ArrayList<>();
		final List<Object[]> workerCountCalls = new ArrayList<>();
		final List<Object[]> queueCapacityCalls = new ArrayList<>();
		boolean recording;

		@Override
		public void setTaskTracking(final boolean tracking) {
			this.trackingCalls.add(tracking);
		}

		@Override
		public void readStack(final long threadId) {
			this.readStackCalls.add(threadId);
		}

		@Override
		public void interruptThreads(final Set<Long> threadIds) {
			this.interruptCalls.add(threadIds);
		}

		@Override
		public void toggleRecording(final Set<Class<?>> components) {
			this.toggleRecordingCalls.add(components);
		}

		@Override
		public boolean isRecording() {
			return this.recording;
		}

		@Override
		public void setWorkerCount(final WorkerPoolKind kind, final int count) {
			this.workerCountCalls.add(new Object[] { kind, count });
		}

		@Override
		public void setQueueCapacity(final WorkerPoolKind kind, final int capacity) {
			this.queueCapacityCalls.add(new Object[] { kind, capacity });
		}
	}

	private static final class FakeViewServices implements ThreadsViewServices {
		final List<ViewKey> shown = new ArrayList<>();
		final List<ViewKey> hidden = new ArrayList<>();

		@Override
		public void showView(final ViewKey key) {
			this.shown.add(key);
		}

		@Override
		public void hideView(final ViewKey key) {
			this.hidden.add(key);
		}
	}

	private FakeActionProviderServices actions;
	private FakeControllerServices controller;
	private FakeViewServices view;
	private ThreadsInteractionImpl interaction;

	@Before
	public void setUp() throws Exception {
		this.actions = new FakeActionProviderServices();
		this.controller = new FakeControllerServices();
		this.view = new FakeViewServices();
		this.interaction = new ThreadsInteractionImpl();
		inject("actionManagerServices", this.actions);
		inject("useControllerServices", this.controller);
		inject("useViewServices", this.view);
	}

	private void inject(final String fieldName, final Object value) throws Exception {
		Field field = ThreadsInteractionImpl.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(this.interaction, value);
	}

	private boolean recordAvailable() {
		return this.actions.availability.get(ThreadsActionKey.RecordToggle);
	}

	@Test
	public void activationRegistersTheNineActions() {
		this.interaction.registerActions();

		Assert.assertEquals(Set.of(ThreadsActionKey.values()), this.actions.registered);
		Assert.assertEquals(9, this.actions.registered.size());
	}

	@Test
	public void recordIsUnavailableWithNoSelectionAndNoRecording() {
		this.interaction.registerActions();

		Assert.assertFalse(this.recordAvailable());
	}

	@Test
	public void aSelectionOfComponentsMakesRecordAvailable() {
		this.interaction.registerActions();

		this.actions.fire(ThreadsActionKey.SetRecordScope, Set.of(ArrayList.class));
		Assert.assertTrue(this.recordAvailable());

		this.actions.fire(ThreadsActionKey.SetRecordScope, Set.of());
		Assert.assertFalse(this.recordAvailable());
	}

	@Test
	public void aRunningRecordingKeepsRecordAvailableToStopIt() {
		this.interaction.registerActions();

		this.controller.recording = true;
		this.interaction.onRecordingStarted();
		Assert.assertTrue(this.recordAvailable());

		this.controller.recording = false;
		this.interaction.onRecordingStopped();
		Assert.assertFalse(this.recordAvailable());
	}

	@Test
	public void recordRecordsTheSelectedComponents() {
		this.interaction.registerActions();
		this.actions.fire(ThreadsActionKey.SetRecordScope, Set.of(ArrayList.class));

		this.actions.fire(ThreadsActionKey.RecordToggle);

		Assert.assertEquals(List.of(Set.of(ArrayList.class)), this.controller.toggleRecordingCalls);
	}

	@Test
	public void recordWithNothingSelectedHandsOverAnEmptyScope() {
		this.interaction.registerActions();

		this.actions.fire(ThreadsActionKey.RecordToggle);

		Assert.assertEquals(List.of(Set.of()), this.controller.toggleRecordingCalls);
	}

	@Test
	public void theRecordScopeIsCopied() {
		this.interaction.registerActions();
		Set<Class<?>> scope = new HashSet<>(Set.of(ArrayList.class));
		this.actions.fire(ThreadsActionKey.SetRecordScope, scope);

		scope.add(HashMap.class);
		this.actions.fire(ThreadsActionKey.RecordToggle);

		Assert.assertEquals(List.of(Set.of(ArrayList.class)), this.controller.toggleRecordingCalls);
	}

	@Test
	public void taskTrackingAndStackReadingAreForwarded() {
		this.interaction.registerActions();

		this.actions.fire(ThreadsActionKey.SetTaskTracking, Boolean.TRUE);
		this.actions.fire(ThreadsActionKey.SetTaskTracking, Boolean.FALSE);
		this.actions.fire(ThreadsActionKey.ReadStack, 42L);

		Assert.assertEquals(List.of(true, false), this.controller.trackingCalls);
		Assert.assertEquals(List.of(42L), this.controller.readStackCalls);
	}

	@Test
	public void interruptingThreadsHandsOverACopyOfTheIds() {
		this.interaction.registerActions();
		Set<Long> ids = new HashSet<>(Set.of(1L, 2L));

		this.actions.fire(ThreadsActionKey.InterruptThreads, ids);
		ids.add(3L);

		Assert.assertEquals(List.of(Set.of(1L, 2L)), this.controller.interruptCalls);
		Assert.assertNotSame(ids, this.controller.interruptCalls.get(0));
	}

	@Test
	public void thePoolSettingsAreForwarded() {
		this.interaction.registerActions();
		WorkerPoolKind kind = WorkerPoolKind.values()[0];

		this.actions.fire(ThreadsActionKey.SetPoolWorkers, kind, 4);
		this.actions.fire(ThreadsActionKey.SetPoolQueueCapacity, kind, 128);

		Assert.assertEquals(1, this.controller.workerCountCalls.size());
		Assert.assertArrayEquals(new Object[] { kind, 4 }, this.controller.workerCountCalls.get(0));
		Assert.assertEquals(1, this.controller.queueCapacityCalls.size());
		Assert.assertArrayEquals(new Object[] { kind, 128 }, this.controller.queueCapacityCalls.get(0));
	}

	@Test
	public void togglingShowsThenHidesThePanel() {
		this.interaction.registerActions();

		this.actions.fire(ThreadsActionKey.Toggle);
		Assert.assertEquals(List.of(ThreadsViewKey.INSTANCE), this.view.shown);
		Assert.assertTrue(this.view.hidden.isEmpty());

		this.actions.fire(ThreadsActionKey.Toggle);
		Assert.assertEquals(List.of(ThreadsViewKey.INSTANCE), this.view.hidden);
	}

	@Test
	public void toggleAfterAShowActionHidesThePanel() {
		this.interaction.registerActions();

		this.actions.fire(ThreadsActionKey.Show);
		this.actions.fire(ThreadsActionKey.Toggle);

		Assert.assertEquals(List.of(ThreadsViewKey.INSTANCE), this.view.shown);
		Assert.assertEquals(List.of(ThreadsViewKey.INSTANCE), this.view.hidden);
	}

	@Test
	public void afterAReactivationTheFirstToggleShowsThePanel() {
		this.interaction.registerActions();
		this.actions.fire(ThreadsActionKey.Toggle);
		this.interaction.unregisterActions();
		this.interaction.registerActions();

		this.actions.fire(ThreadsActionKey.Toggle);

		Assert.assertEquals(List.of(ThreadsViewKey.INSTANCE, ThreadsViewKey.INSTANCE), this.view.shown);
		Assert.assertEquals(List.of(ThreadsViewKey.INSTANCE), this.view.hidden);
	}

	@Test
	public void passivationUnregistersEveryActionAndLeavesAClosedPanelAlone() {
		this.interaction.registerActions();

		this.interaction.unregisterActions();

		Assert.assertEquals(Set.of(ThreadsActionKey.values()), new HashSet<>(this.actions.unregistered));
		Assert.assertTrue(this.view.hidden.isEmpty());
	}

	@Test
	public void passivationClosesAnOpenPanelEvenWhenTheActionManagerIsAlreadyPassivated() {
		this.interaction.registerActions();
		this.actions.fire(ThreadsActionKey.Show);
		this.actions.failOnUnregister = true;

		this.interaction.unregisterActions();

		Assert.assertEquals(List.of(ThreadsViewKey.INSTANCE), this.view.hidden);
	}
}
