/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.exclusionpolicy.interaction.impl;

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
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.hmi.action.ActionProviderServices;
import org.opales.covalent.hmi.impl.HmiCoreImpl;
import org.opales.covalent.supervision.exclusionpolicy.BuiltInExclusions;
import org.opales.covalent.supervision.exclusionpolicy.controller.ExclusionPolicyController;
import org.opales.covalent.supervision.exclusionpolicy.controller.impl.ExclusionPolicyControllerImpl;
import org.opales.covalent.supervision.exclusionpolicy.controller.ExclusionPolicyControllerServices;
import org.opales.covalent.supervision.exclusionpolicy.interaction.ExclusionPolicyActionKey;
import org.opales.covalent.supervision.exclusionpolicy.interaction.ExclusionPolicyViewKey;
import org.opales.covalent.supervision.exclusionpolicy.view.ExclusionPolicyViewServices;
import org.opales.covalent.supervision.supervisionhmi.SupervisorSettings;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

/**
 * {@link ExclusionPolicyInteractionImpl} against hand-written fakes of the services it uses. It stores the
 * exclusions in the supervisor's preferences, so each test starts from an empty store and puts back what was there.
 */
public class ExclusionPolicyInteractionImplTests {

	private static final Preferences STORE = Preferences.userRoot().node("org/opales/covalent/supervisor");

	private static final String INTERNAL = SupervisorSettings.EXCLUDE_INTERNAL_COMPONENTS;

	private static final String EXCLUDED = SupervisorSettings.EXCLUDED_COMPONENTS;

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
	private static final class FakeControllerServices implements ExclusionPolicyControllerServices {
		final List<List<Class<?>>> setExcludedCalls = new ArrayList<>();
		final List<Class<?>> addedTypes = new ArrayList<>();
		final List<Boolean> internalCalls = new ArrayList<>();
		int publishCalls;

		@Override
		public void setExcludedComponents(final List<Class<?>> componentTypes) {
			this.setExcludedCalls.add(new ArrayList<>(componentTypes));
		}

		@Override
		public void addExcludedComponent(final Class<?> componentType) {
			this.addedTypes.add(componentType);
		}

		@Override
		public void setExcludeInternalComponents(final boolean exclude) {
			this.internalCalls.add(exclude);
		}

		@Override
		public void publishState() {
			this.publishCalls++;
		}
	}

	private static final class FakeViewServices implements ExclusionPolicyViewServices {
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

	/** Set by {@link InitializationProbe}'s static initializer, which runs only when that class is initialized. */
	private static final java.util.concurrent.atomic.AtomicBoolean PROBE_INITIALIZED = new java.util.concurrent.atomic.AtomicBoolean();

	private static final class InitializationProbe {
		static {
			PROBE_INITIALIZED.set(true);
		}
	}

	private String savedInternal;
	private List<String> savedExcluded;

	private FakeActionProviderServices actions;
	private FakeControllerServices controller;
	private FakeViewServices view;
	private ExclusionPolicyInteractionImpl interaction;

	@Before
	public void setUp() throws Exception {
		this.savedInternal = STORE.get(INTERNAL, null);
		this.savedExcluded = SupervisorSettings.getStringList(EXCLUDED);
		clearStore();

		this.actions = new FakeActionProviderServices();
		this.controller = new FakeControllerServices();
		this.view = new FakeViewServices();
		this.interaction = new ExclusionPolicyInteractionImpl();
		inject("actionManagerServices", this.actions);
		inject("useControllerServices", this.controller);
		inject("useViewServices", this.view);
	}

	@After
	public void tearDown() throws Exception {
		clearStore();
		if (this.savedInternal != null) {
			STORE.put(INTERNAL, this.savedInternal);
		}
		if (this.savedExcluded != null) {
			SupervisorSettings.putStringList(EXCLUDED, this.savedExcluded);
		}
		STORE.flush();
	}

	private static void clearStore() throws BackingStoreException {
		STORE.remove(INTERNAL);
		if (STORE.nodeExists(EXCLUDED)) {
			STORE.node(EXCLUDED).removeNode();
		}
		STORE.flush();
	}

	private void inject(final String fieldName, final Object value) throws Exception {
		Field field = ExclusionPolicyInteractionImpl.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(this.interaction, value);
	}

	private static String name(final Class<?> type) {
		return type.getName();
	}

	@Test
	public void activationRegistersTheFourActions() {
		this.interaction.registerActions();

		Assert.assertEquals(Set.of(ExclusionPolicyActionKey.values()), this.actions.registered);
	}

	@Test
	public void withNothingStoredTheInternalComponentsAreExcludedAndTheListIsLeftAlone() {
		this.interaction.registerActions();

		Assert.assertEquals(List.of(true), this.controller.internalCalls);
		Assert.assertTrue(this.controller.setExcludedCalls.isEmpty());
	}

	@Test
	public void aStoredInternalSwitchIsRestoredAtActivation() {
		SupervisorSettings.putBoolean(INTERNAL, false);

		this.interaction.registerActions();

		Assert.assertEquals(List.of(false), this.controller.internalCalls);
	}

	@Test
	public void storedTypesAreAppliedOverTheBuiltInOnes() {
		SupervisorSettings.putStringList(EXCLUDED, List.of(name(ArrayList.class)));

		this.interaction.registerActions();

		Assert.assertEquals(1, this.controller.setExcludedCalls.size());
		List<Class<?>> applied = this.controller.setExcludedCalls.get(0);
		Assert.assertEquals(BuiltInExclusions.types(), applied.subList(0, BuiltInExclusions.types().size()));
		Assert.assertEquals(BuiltInExclusions.types().size() + 1, applied.size());
		Assert.assertEquals(ArrayList.class, applied.get(applied.size() - 1));
	}

	@Test
	public void aStoredBuiltInTypeIsNotAppliedTwice() {
		SupervisorSettings.putStringList(EXCLUDED, List.of(name(BuiltInExclusions.types().get(0))));

		this.interaction.registerActions();

		Assert.assertEquals(BuiltInExclusions.types(), this.controller.setExcludedCalls.get(0));
	}

	@Test
	public void aStoredTypeIsResolvedWithoutRunningItsStaticInitializer() {
		Assert.assertFalse(PROBE_INITIALIZED.get());
		SupervisorSettings.putStringList(EXCLUDED, List.of(name(InitializationProbe.class)));

		this.interaction.registerActions();

		List<Class<?>> applied = this.controller.setExcludedCalls.get(0);
		Assert.assertEquals(InitializationProbe.class, applied.get(applied.size() - 1));
		Assert.assertFalse(PROBE_INITIALIZED.get());
	}

	@Test
	public void aStoredTypeThatCannotBeLoadedIsNotApplied() {
		SupervisorSettings.putStringList(EXCLUDED, List.of("no.such.Type", name(ArrayList.class)));

		this.interaction.registerActions();

		List<Class<?>> applied = this.controller.setExcludedCalls.get(0);
		Assert.assertTrue(applied.contains(ArrayList.class));
		Assert.assertEquals(BuiltInExclusions.types().size() + 1, applied.size());
	}

	@Test
	public void aStoredTypeThatCannotBeLoadedStaysStoredWhenTheListChanges() {
		SupervisorSettings.putStringList(EXCLUDED, List.of("no.such.Type"));
		this.interaction.registerActions();

		this.actions.fire(ExclusionPolicyActionKey.SetExcludedComponents, List.of(HashMap.class));

		Assert.assertEquals(List.of("no.such.Type", name(HashMap.class)), SupervisorSettings.getStringList(EXCLUDED));
	}

	@Test
	public void settingTheListForwardsItAndStoresOnlyTheUsersOwnTypes() {
		this.interaction.registerActions();
		List<Class<?>> chosen = List.of(BuiltInExclusions.types().get(0), ArrayList.class);

		this.actions.fire(ExclusionPolicyActionKey.SetExcludedComponents, chosen);

		Assert.assertEquals(chosen, this.controller.setExcludedCalls.get(this.controller.setExcludedCalls.size() - 1));
		Assert.assertEquals(List.of(name(ArrayList.class)), SupervisorSettings.getStringList(EXCLUDED));
	}

	@Test
	public void addingATypeForwardsItAndStoresItOnce() {
		this.interaction.registerActions();

		this.actions.fire(ExclusionPolicyActionKey.AddExcludedComponent, ArrayList.class);
		this.actions.fire(ExclusionPolicyActionKey.AddExcludedComponent, ArrayList.class);

		Assert.assertEquals(List.of(ArrayList.class, ArrayList.class), this.controller.addedTypes);
		Assert.assertEquals(List.of(name(ArrayList.class)), SupervisorSettings.getStringList(EXCLUDED));
	}

	@Test
	public void addingAComponentImplementationForwardsAndStoresItsType() {
		this.interaction.registerActions();

		this.actions.fire(ExclusionPolicyActionKey.AddExcludedComponent, ExclusionPolicyControllerImpl.class);

		Assert.assertEquals(List.of(ExclusionPolicyController.class), this.controller.addedTypes);
		Assert.assertEquals(List.of(name(ExclusionPolicyController.class)), SupervisorSettings.getStringList(EXCLUDED));
	}

	@Test
	public void aStoredComponentImplementationIsAppliedAsItsTypeAndStoredAsSuch() {
		SupervisorSettings.putStringList(EXCLUDED, List.of(name(ExclusionPolicyControllerImpl.class)));

		this.interaction.registerActions();

		List<Class<?>> applied = this.controller.setExcludedCalls.get(0);
		Assert.assertTrue(applied.contains(ExclusionPolicyController.class));
		Assert.assertFalse(applied.contains(ExclusionPolicyControllerImpl.class));
		Assert.assertEquals(List.of(name(ExclusionPolicyController.class)), SupervisorSettings.getStringList(EXCLUDED));
	}

	@Test
	public void aStoredImplementationOfABuiltInTypeIsDroppedFromTheStore() {
		SupervisorSettings.putStringList(EXCLUDED, List.of(name(HmiCoreImpl.class)));

		this.interaction.registerActions();

		Assert.assertEquals(BuiltInExclusions.types(), this.controller.setExcludedCalls.get(0));
		Assert.assertEquals(List.of(), SupervisorSettings.getStringList(EXCLUDED));
	}

	@Test
	public void addingABuiltInTypeForwardsItButStoresNothing() {
		this.interaction.registerActions();
		Class<?> builtIn = BuiltInExclusions.types().get(0);

		this.actions.fire(ExclusionPolicyActionKey.AddExcludedComponent, builtIn);

		Assert.assertEquals(List.of(builtIn), this.controller.addedTypes);
		Assert.assertNull(SupervisorSettings.getStringList(EXCLUDED));
	}

	@Test
	public void theInternalSwitchIsForwardedAndStored() {
		this.interaction.registerActions();

		this.actions.fire(ExclusionPolicyActionKey.SetExcludeInternalComponents, Boolean.FALSE);

		Assert.assertEquals(List.of(true, false), this.controller.internalCalls);
		Assert.assertFalse(SupervisorSettings.getBoolean(INTERNAL, true));
	}

	@Test
	public void togglingShowsThenHidesThePanel() {
		this.interaction.registerActions();

		this.actions.fire(ExclusionPolicyActionKey.Toggle);
		Assert.assertEquals(List.of(ExclusionPolicyViewKey.INSTANCE), this.view.shown);
		Assert.assertEquals("the state is published once the panel is there", 1, this.controller.publishCalls);
		Assert.assertTrue(this.view.hidden.isEmpty());

		this.actions.fire(ExclusionPolicyActionKey.Toggle);
		Assert.assertEquals(List.of(ExclusionPolicyViewKey.INSTANCE), this.view.hidden);
		Assert.assertEquals(1, this.controller.publishCalls);
	}

	@Test
	public void passivationUnregistersEveryActionAndLeavesAClosedPanelAlone() {
		this.interaction.registerActions();

		this.interaction.unregisterActions();

		Assert.assertEquals(Set.of(ExclusionPolicyActionKey.values()), new HashSet<>(this.actions.unregistered));
		Assert.assertTrue(this.view.hidden.isEmpty());
	}

	@Test
	public void passivationClosesAnOpenPanel() {
		this.interaction.registerActions();
		this.actions.fire(ExclusionPolicyActionKey.Toggle);

		this.interaction.unregisterActions();

		Assert.assertEquals(List.of(ExclusionPolicyViewKey.INSTANCE), this.view.hidden);
	}

	@Test
	public void passivationToleratesAnActionManagerThatIsAlreadyPassivated() {
		this.interaction.registerActions();
		this.actions.fire(ExclusionPolicyActionKey.Toggle);
		this.actions.failOnUnregister = true;

		this.interaction.unregisterActions();

		Assert.assertEquals("the open panel is closed all the same", List.of(ExclusionPolicyViewKey.INSTANCE),
				this.view.hidden);
	}

	@Test
	public void afterAReactivationTheFirstToggleShowsThePanel() {
		this.interaction.registerActions();
		this.actions.fire(ExclusionPolicyActionKey.Toggle);
		this.interaction.unregisterActions();
		this.interaction.registerActions();

		this.actions.fire(ExclusionPolicyActionKey.Toggle);

		Assert.assertEquals("shown at the first opening and again after the reactivation",
				List.of(ExclusionPolicyViewKey.INSTANCE, ExclusionPolicyViewKey.INSTANCE), this.view.shown);
		Assert.assertEquals("hidden by the passivation only", List.of(ExclusionPolicyViewKey.INSTANCE), this.view.hidden);
	}

	@Test
	public void aStoredBuiltInTypeIsDroppedFromTheStoreWhateverIsDoneNext() {
		SupervisorSettings.putStringList(EXCLUDED, List.of(name(BuiltInExclusions.types().get(0))));
		this.interaction.registerActions();

		this.actions.fire(ExclusionPolicyActionKey.AddExcludedComponent, ArrayList.class);

		Assert.assertEquals(List.of(name(ArrayList.class)), SupervisorSettings.getStringList(EXCLUDED));
	}

	@Test
	public void theModelsNotificationsAreNotItsConcern() {
		this.interaction.registerActions();
		int setCalls = this.controller.setExcludedCalls.size();
		int internalCalls = this.controller.internalCalls.size();

		this.interaction.onExcludedComponentsChanged(List.of(ArrayList.class));
		this.interaction.onExcludeInternalComponentsChanged(false);

		Assert.assertEquals(setCalls, this.controller.setExcludedCalls.size());
		Assert.assertEquals(internalCalls, this.controller.internalCalls.size());
		Assert.assertNull(SupervisorSettings.getStringList(EXCLUDED));
	}
}
