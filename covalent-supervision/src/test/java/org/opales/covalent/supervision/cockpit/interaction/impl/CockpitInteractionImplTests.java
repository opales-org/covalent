/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.cockpit.interaction.impl;

import java.lang.reflect.Field;
import java.util.ArrayList;
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
import org.opales.covalent.supervision.cockpit.controller.CockpitControllerServices;
import org.opales.covalent.supervision.cockpit.interaction.CockpitActionKey;
import org.opales.covalent.supervision.cockpit.interaction.CockpitViewKey;
import org.opales.covalent.supervision.cockpit.interaction.LifecycleRequest;
import org.opales.covalent.supervision.cockpit.model.LifecycleCommand;
import org.opales.covalent.supervision.cockpit.view.CockpitViewServices;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

/** {@link CockpitInteractionImpl} against hand-written fakes of the services it uses. */
public class CockpitInteractionImplTests {

	/** The actions the interaction registers, runnable by the test. */
	private static final class FakeActionProviderServices implements ActionProviderServices {
		final Map<ActionKey, Consumer<Object>> consumers = new LinkedHashMap<>();
		final Map<ActionKey, Runnable> runnables = new LinkedHashMap<>();
		final Set<ActionKey> registered = new HashSet<>();
		final List<ActionKey> unregistered = new ArrayList<>();
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
		}

		void fire(final ActionKey key, final Object input) {
			this.consumers.get(key).accept(input);
		}

		void fire(final ActionKey key) {
			this.runnables.get(key).run();
		}
	}

	/** Records every call the interaction makes to the Controller. */
	private static final class FakeControllerServices implements CockpitControllerServices {
		final List<Object[]> executeCalls = new ArrayList<>();
		final List<LifecycleCommand> containerCommands = new ArrayList<>();
		final List<Class<?>> enteredComposites = new ArrayList<>();
		final List<Integer> consoleLevels = new ArrayList<>();

		@Override
		public void execute(final LifecycleCommand command, final Class<?> componentType) {
			this.executeCalls.add(new Object[] { command, componentType });
		}

		@Override
		public void executeOnContainer(final LifecycleCommand command) {
			this.containerCommands.add(command);
		}

		@Override
		public void enterComposite(final Class<?> compositeType) {
			this.enteredComposites.add(compositeType);
		}

		@Override
		public void showConsoleLevel(final int depth) {
			this.consoleLevels.add(depth);
		}
	}

	private static final class FakeViewServices implements CockpitViewServices {
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
	private CockpitInteractionImpl interaction;

	@Before
	public void setUp() throws Exception {
		this.actions = new FakeActionProviderServices();
		this.controller = new FakeControllerServices();
		this.view = new FakeViewServices();
		this.interaction = new CockpitInteractionImpl();
		inject("actionManagerServices", this.actions);
		inject("useControllerServices", this.controller);
		inject("useViewServices", this.view);
	}

	private void inject(final String fieldName, final Object value) throws Exception {
		Field field = CockpitInteractionImpl.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(this.interaction, value);
	}

	@Test
	public void activationRegistersTheFiveActions() {
		this.interaction.registerActions();

		Assert.assertEquals(Set.of(CockpitActionKey.values()), this.actions.registered);
		Assert.assertEquals(5, this.actions.registered.size());
	}

	@Test
	public void aCommandHandsItsKindAndItsComponentTypeToTheController() {
		this.interaction.registerActions();

		this.actions.fire(CockpitActionKey.Command, new LifecycleRequest(LifecycleCommand.RESTART, ArrayList.class));

		Assert.assertEquals(1, this.controller.executeCalls.size());
		Assert.assertArrayEquals(new Object[] { LifecycleCommand.RESTART, ArrayList.class },
				this.controller.executeCalls.get(0));
	}

	@Test
	public void aContainerCommandIsForwarded() {
		this.interaction.registerActions();

		this.actions.fire(CockpitActionKey.ContainerCommand, LifecycleCommand.RESET);

		Assert.assertEquals(List.of(LifecycleCommand.RESET), this.controller.containerCommands);
	}

	@Test
	public void enteringACompositeAndShowingAConsoleLevelAreForwarded() {
		this.interaction.registerActions();

		this.actions.fire(CockpitActionKey.EnterComposite, ArrayList.class);
		this.actions.fire(CockpitActionKey.ShowConsoleLevel, 3);

		Assert.assertEquals(List.of(ArrayList.class), this.controller.enteredComposites);
		Assert.assertEquals(List.of(3), this.controller.consoleLevels);
	}

	@Test
	public void togglingShowsThenHidesThePanel() {
		this.interaction.registerActions();

		this.actions.fire(CockpitActionKey.Toggle);
		Assert.assertEquals(List.of(CockpitViewKey.INSTANCE), this.view.shown);
		Assert.assertTrue(this.view.hidden.isEmpty());

		this.actions.fire(CockpitActionKey.Toggle);
		Assert.assertEquals(List.of(CockpitViewKey.INSTANCE), this.view.hidden);

		this.actions.fire(CockpitActionKey.Toggle);
		Assert.assertEquals("it can be shown again", List.of(CockpitViewKey.INSTANCE, CockpitViewKey.INSTANCE),
				this.view.shown);
	}

	@Test
	public void afterAReactivationTheFirstToggleShowsThePanel() {
		this.interaction.registerActions();
		this.actions.fire(CockpitActionKey.Toggle);
		this.interaction.unregisterActions();
		this.interaction.registerActions();

		this.actions.fire(CockpitActionKey.Toggle);

		Assert.assertEquals(List.of(CockpitViewKey.INSTANCE, CockpitViewKey.INSTANCE), this.view.shown);
		Assert.assertEquals(List.of(CockpitViewKey.INSTANCE), this.view.hidden);
	}

	@Test
	public void passivationUnregistersEveryActionAndLeavesAClosedPanelAlone() {
		this.interaction.registerActions();

		this.interaction.unregisterActions();

		Assert.assertEquals(Set.of(CockpitActionKey.values()), new HashSet<>(this.actions.unregistered));
		Assert.assertTrue(this.view.hidden.isEmpty());
	}

	@Test
	public void passivationClosesAnOpenPanelEvenWhenTheActionManagerIsAlreadyPassivated() {
		this.interaction.registerActions();
		this.actions.fire(CockpitActionKey.Toggle);
		this.actions.failOnUnregister = true;

		this.interaction.unregisterActions();

		Assert.assertEquals(List.of(CockpitViewKey.INSTANCE), this.view.hidden);
	}
}
