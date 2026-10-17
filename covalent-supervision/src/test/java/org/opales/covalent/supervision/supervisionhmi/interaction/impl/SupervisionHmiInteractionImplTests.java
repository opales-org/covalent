/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.supervisionhmi.interaction.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.opales.covalent.supervision.supervisionhmi.interaction.SupervisionHmiActionKey;
import org.opales.covalent.supervision.supervisionhmi.view.RailEntry;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisionHmiViewServices;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisorTabDescriptor;
import org.opales.covalent.supervision.supervisionhmi.view.ToolbarFunction;

/**
 * Unit tests for {@link SupervisionHmiInteractionImpl}'s shared-toolbar rebinding: using
 * hand-written fakes for its two {@code @UsedServices} (no container, no JavaFX). Drives the
 * controller through the consumer the fake {@code ActionProviderServices} captures during
 * {@code registerActions()}, exactly as {@code SupervisionHmiView}'s tab selection listener
 * would through {@code ActionConsumerServices.executeAction(...)}.
 */
public class SupervisionHmiInteractionImplTests {

	private enum TestKey implements ActionKey {
		RECORD_A, ZOOM_A, RECORD_B, ZOOM_B;
	}

	private static final class TestTabDescriptor implements SupervisorTabDescriptor {
		private final Set<ToolbarFunction> supported;
		private final ActionKey recordKey;
		private final ActionKey zoomKey;

		TestTabDescriptor(final Set<ToolbarFunction> supported, final ActionKey recordKey, final ActionKey zoomKey) {
			this.supported = supported;
			this.recordKey = recordKey;
			this.zoomKey = zoomKey;
		}

		@Override
		public RailEntry railEntry() {
			return null;
		}

		@Override
		public Set<ToolbarFunction> supportedToolbarFunctions() {
			return this.supported;
		}

		@Override
		public ActionKey recordActionKey() {
			return this.recordKey;
		}

		@Override
		public ActionKey zoomSetScaleActionKey() {
			return this.zoomKey;
		}

		@Override
		public ActionKey zoomCurrentScaleActionKey() {
			return null;
		}
	}

	/** Captures the consumer registered for {@code TabSelected} and lets tests fire it. */
	private static final class FakeActionProviderServices implements ActionProviderServices {
		final Map<ActionKey, Object> registered = new HashMap<>();
		boolean unregisterThrows;
		final List<ActionKey> unregistered = new ArrayList<>();

		@Override
		public <T> void registerActionExecution(final ActionKey anActionKey, final Consumer<T> anInteractionImpl,
				final Class<T> anInputDataClass) {
			this.registered.put(anActionKey, anInteractionImpl);
		}

		@Override
		public <T1, T2> void registerActionExecution(final ActionKey anActionKey,
				final BiConsumer<T1, T2> anInteractionImpl, final Class<T1> anInputDataClass1,
				final Class<T2> anInputDataClass2) {
			this.registered.put(anActionKey, anInteractionImpl);
		}

		@Override
		public void registerActionExecution(final ActionKey anActionKey, final Runnable anInteractionImpl) {
			this.registered.put(anActionKey, anInteractionImpl);
		}

		@Override
		public void unregisterActionExecution(final ActionKey anActionKey) {
			this.unregistered.add(anActionKey);
			if (this.unregisterThrows) {
				throw new IllegalStateException("already passivated");
			}
		}

		@Override
		public void setActionAvailable(final ActionKey anActionKey, final boolean isAvailable) {
		}

		@Override
		public void setActionValue(final ActionKey anActionKey, final Object aValue) {
		}

		void fireShowWindow() {
			((Runnable) this.registered.get(SupervisionHmiActionKey.ShowWindow)).run();
		}

		@SuppressWarnings("unchecked")
		void fireTabSelected(final SupervisorTabDescriptor descriptor) {
			((Consumer<SupervisorTabDescriptor>) this.registered.get(SupervisionHmiActionKey.TabSelected))
					.accept(descriptor);
		}
	}

	private static final class FakeSupervisionHmiViewServices implements SupervisionHmiViewServices {
		final List<String> calls = new ArrayList<>();

		@Override
		public void bindToolbarFunction(final ToolbarFunction function, final ActionKey key) {
			this.calls.add("bind " + function + " " + key);
		}

		@Override
		public void unbindToolbarFunction(final ToolbarFunction function, final ActionKey key) {
			this.calls.add("unbind " + function + " " + key);
		}

		@Override
		public void showWindow() {
			this.calls.add("showWindow");
		}
	}

	private SupervisionHmiInteractionImpl controller;
	private FakeActionProviderServices actions;
	private FakeSupervisionHmiViewServices viewServices;

	@Before
	public void setUp() throws Exception {
		this.controller = new SupervisionHmiInteractionImpl();
		this.actions = new FakeActionProviderServices();
		this.viewServices = new FakeSupervisionHmiViewServices();
		this.setField("actionManagerServices", this.actions);
		this.setField("useSupervisionHmiViewServices", this.viewServices);
	}

	private void setField(final String name, final Object value) throws Exception {
		Field field = SupervisionHmiInteractionImpl.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(this.controller, value);
	}

	@Test
	public void registerActionsRegistersTabSelectedAndShowWindow() {
		this.controller.registerActions();
		Assert.assertTrue(this.actions.registered.containsKey(SupervisionHmiActionKey.TabSelected));
		Assert.assertTrue(this.actions.registered.containsKey(SupervisionHmiActionKey.ShowWindow));
	}

	@Test
	public void unregisterActionsUnregistersTabSelectedAndShowWindow() {
		this.controller.unregisterActions();
		Assert.assertEquals(Set.of(SupervisionHmiActionKey.TabSelected, SupervisionHmiActionKey.ShowWindow),
				Set.copyOf(this.actions.unregistered));
	}

	@Test
	public void unregisterActionsSwallowsAnAlreadyPassivatedManager() {
		this.actions.unregisterThrows = true;
		this.controller.unregisterActions();
		// No exception propagates; the attempt is still recorded by the fake.
		Assert.assertFalse(this.actions.unregistered.isEmpty());
	}

	@Test
	public void showWindowAsksTheViewToShowTheWindow() {
		this.controller.registerActions();

		this.actions.fireShowWindow();

		Assert.assertEquals(List.of("showWindow"), this.viewServices.calls);
	}

	@Test
	public void tabSelectionBindsEachSupportedFunctionToItsOwnKey() {
		this.controller.registerActions();
		TestTabDescriptor descriptor = new TestTabDescriptor(
				Set.of(ToolbarFunction.RECORD_TOGGLE, ToolbarFunction.ZOOM_SLIDER), TestKey.RECORD_A, TestKey.ZOOM_A);

		this.actions.fireTabSelected(descriptor);

		Assert.assertTrue(this.viewServices.calls.contains("bind RECORD_TOGGLE RECORD_A"));
		Assert.assertTrue(this.viewServices.calls.contains("bind ZOOM_SLIDER ZOOM_A"));
	}

	@Test
	public void tabSelectionUnbindsThePreviousTabsKeyBeforeBindingTheNewOne() {
		this.controller.registerActions();
		this.actions.fireTabSelected(new TestTabDescriptor(Set.of(ToolbarFunction.RECORD_TOGGLE), TestKey.RECORD_A,
				TestKey.ZOOM_A));
		this.viewServices.calls.clear();

		this.actions.fireTabSelected(new TestTabDescriptor(Set.of(ToolbarFunction.RECORD_TOGGLE), TestKey.RECORD_B,
				TestKey.ZOOM_B));

		Assert.assertEquals(
				List.of("unbind RECORD_TOGGLE RECORD_A", "bind RECORD_TOGGLE RECORD_B"), this.viewServices.calls);
	}

	@Test
	public void aFunctionTheNewTabDoesNotSupportKeepsItsCurrentBinding() {
		this.controller.registerActions();
		// First tab supports both functions.
		this.actions.fireTabSelected(new TestTabDescriptor(
				Set.of(ToolbarFunction.RECORD_TOGGLE, ToolbarFunction.ZOOM_SLIDER), TestKey.RECORD_A, TestKey.ZOOM_A));
		this.viewServices.calls.clear();

		// Second tab only supports the zoom slider: RECORD_TOGGLE must be left bound to tab A.
		this.actions.fireTabSelected(
				new TestTabDescriptor(Set.of(ToolbarFunction.ZOOM_SLIDER), TestKey.RECORD_B, TestKey.ZOOM_B));

		Assert.assertEquals(List.of("unbind ZOOM_SLIDER ZOOM_A", "bind ZOOM_SLIDER ZOOM_B"), this.viewServices.calls);

		// Confirmed by switching to a third tab that again supports RECORD_TOGGLE: its previous
		// key must still be tab A's, not null/unset.
		this.viewServices.calls.clear();
		this.actions.fireTabSelected(new TestTabDescriptor(Set.of(ToolbarFunction.RECORD_TOGGLE), TestKey.RECORD_B,
				TestKey.ZOOM_B));
		Assert.assertEquals(List.of("unbind RECORD_TOGGLE RECORD_A", "bind RECORD_TOGGLE RECORD_B"),
				this.viewServices.calls);
	}

	@Test
	public void actionKeyForResolvesRecordAndZoomFromTheDescriptor() throws Exception {
		Method method = SupervisionHmiInteractionImpl.class.getDeclaredMethod("actionKeyFor",
				SupervisorTabDescriptor.class, ToolbarFunction.class);
		method.setAccessible(true);
		TestTabDescriptor descriptor = new TestTabDescriptor(Set.of(), TestKey.RECORD_A, TestKey.ZOOM_A);

		Assert.assertEquals(TestKey.RECORD_A,
				method.invoke(this.controller, descriptor, ToolbarFunction.RECORD_TOGGLE));
		Assert.assertEquals(TestKey.ZOOM_A, method.invoke(this.controller, descriptor, ToolbarFunction.ZOOM_SLIDER));
	}
}
