/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.stereotypepolicy.interaction.impl;

import java.lang.reflect.Field;
import java.util.ArrayList;
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
import org.opales.covalent.supervision.stereotypepolicy.BuiltInStereotypes;
import org.opales.covalent.supervision.stereotypepolicy.StereotypeRule;
import org.opales.covalent.supervision.stereotypepolicy.controller.StereotypePolicyControllerServices;
import org.opales.covalent.supervision.stereotypepolicy.interaction.StereotypePolicyActionKey;
import org.opales.covalent.supervision.stereotypepolicy.interaction.StereotypePolicyViewKey;
import org.opales.covalent.supervision.stereotypepolicy.view.StereotypePolicyViewServices;
import org.opales.covalent.supervision.supervisionhmi.SupervisorSettings;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

/**
 * {@link StereotypePolicyInteractionImpl} against hand-written fakes of the services it uses. It stores the rules
 * the user added in the supervisor's preferences, so each test starts from an empty store and puts back what was
 * there.
 */
public class StereotypePolicyInteractionImplTests {

	private static final Preferences STORE = Preferences.userRoot().node("org/opales/covalent/supervisor");

	private static final String RULES = SupervisorSettings.STEREOTYPE_RULES;

	private static final String REMOVED = SupervisorSettings.STEREOTYPE_REMOVED_BUILT_INS;

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

	private static final class FakeControllerServices implements StereotypePolicyControllerServices {
		final List<List<StereotypeRule>> setRulesCalls = new ArrayList<>();
		int publishCalls;

		@Override
		public void setRules(final List<StereotypeRule> rules) {
			this.setRulesCalls.add(rules);
		}

		@Override
		public void publishState() {
			this.publishCalls++;
		}

		List<StereotypeRule> lastRules() {
			return this.setRulesCalls.get(this.setRulesCalls.size() - 1);
		}
	}

	private static final class FakeViewServices implements StereotypePolicyViewServices {
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

	private List<String> savedRules;
	private List<String> savedRemoved;

	private FakeActionProviderServices actions;
	private FakeControllerServices controller;
	private FakeViewServices view;
	private StereotypePolicyInteractionImpl interaction;

	@Before
	public void setUp() throws Exception {
		this.savedRules = SupervisorSettings.getStringList(RULES);
		this.savedRemoved = SupervisorSettings.getStringList(REMOVED);
		clearStore();

		this.actions = new FakeActionProviderServices();
		this.controller = new FakeControllerServices();
		this.view = new FakeViewServices();
		this.interaction = new StereotypePolicyInteractionImpl();
		inject("actionManagerServices", this.actions);
		inject("useControllerServices", this.controller);
		inject("useViewServices", this.view);
	}

	@After
	public void tearDown() throws Exception {
		clearStore();
		if (this.savedRules != null) {
			SupervisorSettings.putStringList(RULES, this.savedRules);
		}
		if (this.savedRemoved != null) {
			SupervisorSettings.putStringList(REMOVED, this.savedRemoved);
		}
		STORE.flush();
	}

	private static void clearStore() throws BackingStoreException {
		if (STORE.nodeExists(RULES)) {
			STORE.node(RULES).removeNode();
		}
		if (STORE.nodeExists(REMOVED)) {
			STORE.node(REMOVED).removeNode();
		}
		STORE.flush();
	}

	private void inject(final String fieldName, final Object value) throws Exception {
		Field field = StereotypePolicyInteractionImpl.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(this.interaction, value);
	}

	private static List<StereotypeRule> builtInsPlus(final StereotypeRule... added) {
		List<StereotypeRule> rules = new ArrayList<>(BuiltInStereotypes.rules());
		rules.addAll(List.of(added));
		return rules;
	}

	@Test
	public void activationRegistersTheFourActions() {
		this.interaction.registerActions();

		Assert.assertEquals(Set.of(StereotypePolicyActionKey.values()), this.actions.registered);
		Assert.assertEquals(4, this.actions.registered.size());
	}

	@Test
	public void withNothingStoredTheControllerKeepsItsBuiltInRules() {
		this.interaction.registerActions();

		Assert.assertTrue(this.controller.setRulesCalls.isEmpty());
	}

	@Test
	public void storedRulesAreRestoredOverTheBuiltInOnes() {
		SupervisorSettings.putStringList(RULES, List.of("Handler=Handler", "Gateway=Gate"));

		this.interaction.registerActions();

		Assert.assertEquals(
				builtInsPlus(new StereotypeRule("Handler", "Handler", false), new StereotypeRule("Gateway", "Gate", false)),
				this.controller.lastRules());
	}

	@Test
	public void onlyRemovedBuiltInRulesStoredStillRemoveThemFromTheBuiltInOnes() {
		SupervisorSettings.putStringList(REMOVED, List.of("Controller"));

		this.interaction.registerActions();

		List<StereotypeRule> withoutController = new ArrayList<>(BuiltInStereotypes.rules());
		withoutController.removeIf(rule -> rule.suffix().equals("Controller"));
		Assert.assertEquals(withoutController, this.controller.lastRules());
	}

	@Test
	public void aDamagedOrDuplicateStoredRuleIsDropped() {
		SupervisorSettings.putStringList(RULES,
				List.of("no separator", "Controller=Again", "=Empty", "Long=ThisLabelIsFarTooLong", "Handler=Handler"));

		this.interaction.registerActions();

		Assert.assertEquals(builtInsPlus(new StereotypeRule("Handler", "Handler", false)), this.controller.lastRules());
	}

	@Test
	public void addingARuleAppliesItAndStoresIt() {
		this.interaction.registerActions();

		this.actions.fire(StereotypePolicyActionKey.AddStereotype, new StereotypeRule("Handler", "Handler", false));

		Assert.assertEquals(builtInsPlus(new StereotypeRule("Handler", "Handler", false)), this.controller.lastRules());
		Assert.assertEquals(List.of("Handler=Handler"), SupervisorSettings.getStringList(RULES));
	}

	@Test
	public void aRuleIsTrimmedAndAlwaysAddedAsTheUsersOwn() {
		this.interaction.registerActions();

		this.actions.fire(StereotypePolicyActionKey.AddStereotype, new StereotypeRule("  Handler ", " Handle ", true));

		Assert.assertEquals(builtInsPlus(new StereotypeRule("Handler", "Handle", false)), this.controller.lastRules());
	}

	@Test
	public void anUnacceptableRuleIsIgnoredWhateverTheViewDid() {
		this.interaction.registerActions();

		this.actions.fire(StereotypePolicyActionKey.AddStereotype, new StereotypeRule("", "Label", false));
		this.actions.fire(StereotypePolicyActionKey.AddStereotype, new StereotypeRule("Not valid", "Label", false));
		this.actions.fire(StereotypePolicyActionKey.AddStereotype, new StereotypeRule("Handler", "", false));
		this.actions.fire(StereotypePolicyActionKey.AddStereotype,
				new StereotypeRule("Handler", "ThisLabelIsFarTooLong", false));
		this.actions.fire(StereotypePolicyActionKey.AddStereotype, new StereotypeRule("Controller", "Other", false));

		Assert.assertTrue(this.controller.setRulesCalls.isEmpty());
		Assert.assertNull(SupervisorSettings.getStringList(RULES));
	}

	@Test
	public void aRuleAlreadyAddedCannotBeAddedAgain() {
		this.interaction.registerActions();
		this.actions.fire(StereotypePolicyActionKey.AddStereotype, new StereotypeRule("Handler", "Handler", false));

		this.actions.fire(StereotypePolicyActionKey.AddStereotype, new StereotypeRule("Handler", "Other", false));

		Assert.assertEquals(1, this.controller.setRulesCalls.size());
		Assert.assertEquals(List.of("Handler=Handler"), SupervisorSettings.getStringList(RULES));
	}

	@Test
	public void removingTheUsersRuleAppliesAndStoresTheRest() {
		this.interaction.registerActions();
		this.actions.fire(StereotypePolicyActionKey.AddStereotype, new StereotypeRule("Handler", "Handler", false));
		this.actions.fire(StereotypePolicyActionKey.AddStereotype, new StereotypeRule("Gateway", "Gate", false));

		this.actions.fire(StereotypePolicyActionKey.RemoveStereotype, "Handler");

		Assert.assertEquals(builtInsPlus(new StereotypeRule("Gateway", "Gate", false)), this.controller.lastRules());
		Assert.assertEquals(List.of("Gateway=Gate"), SupervisorSettings.getStringList(RULES));
	}

	@Test
	public void anUnknownRuleCannotBeRemoved() {
		this.interaction.registerActions();

		this.actions.fire(StereotypePolicyActionKey.RemoveStereotype, "NoSuchEnding");

		Assert.assertTrue(this.controller.setRulesCalls.isEmpty());
		Assert.assertNull(SupervisorSettings.getStringList(RULES));
	}

	@Test
	public void aRemovedBuiltInRuleIsStoredAndStaysRemoved() throws Exception {
		this.interaction.registerActions();

		this.actions.fire(StereotypePolicyActionKey.RemoveStereotype, "Controller");

		List<StereotypeRule> withoutController = new ArrayList<>(BuiltInStereotypes.rules());
		withoutController.removeIf(rule -> rule.suffix().equals("Controller"));
		Assert.assertEquals(withoutController, this.controller.lastRules());
		Assert.assertEquals(List.of("Controller"), SupervisorSettings.getStringList(REMOVED));

		FakeControllerServices restored = new FakeControllerServices();
		this.interaction = new StereotypePolicyInteractionImpl();
		inject("actionManagerServices", new FakeActionProviderServices());
		inject("useControllerServices", restored);
		inject("useViewServices", new FakeViewServices());
		this.interaction.registerActions();
		Assert.assertEquals("the next run starts without it", withoutController, restored.lastRules());
	}

	@Test
	public void resetBringsBackTheBuiltInRulesOnly() {
		this.interaction.registerActions();
		this.actions.fire(StereotypePolicyActionKey.AddStereotype, new StereotypeRule("Handler", "Handler", false));
		this.actions.fire(StereotypePolicyActionKey.RemoveStereotype, "Model");

		this.actions.fire(StereotypePolicyActionKey.ResetStereotypes);

		Assert.assertEquals(BuiltInStereotypes.rules(), this.controller.lastRules());
		Assert.assertEquals(List.of(), SupervisorSettings.getStringList(RULES));
		Assert.assertEquals(List.of(), SupervisorSettings.getStringList(REMOVED));
	}

	@Test
	public void resettingTheBuiltInRulesChangesNothing() {
		this.interaction.registerActions();

		this.actions.fire(StereotypePolicyActionKey.ResetStereotypes);

		Assert.assertTrue(this.controller.setRulesCalls.isEmpty());
	}

	@Test
	public void showOpensThePanelAndPublishesTheStateEachTime() {
		this.interaction.registerActions();

		this.actions.fire(StereotypePolicyActionKey.Show);
		this.actions.fire(StereotypePolicyActionKey.Show);

		Assert.assertEquals(List.of(StereotypePolicyViewKey.INSTANCE, StereotypePolicyViewKey.INSTANCE), this.view.shown);
		Assert.assertEquals(2, this.controller.publishCalls);
	}

	@Test
	public void passivationUnregistersEveryActionAndLeavesAnUnopenedPanelAlone() {
		this.interaction.registerActions();

		this.interaction.unregisterActions();

		Assert.assertEquals(Set.of(StereotypePolicyActionKey.values()), new HashSet<>(this.actions.unregistered));
		Assert.assertTrue(this.view.hidden.isEmpty());
	}

	@Test
	public void passivationClosesAnOpenPanelEvenWhenTheActionManagerIsAlreadyPassivated() {
		this.interaction.registerActions();
		this.actions.fire(StereotypePolicyActionKey.Show);
		this.actions.failOnUnregister = true;

		this.interaction.unregisterActions();

		Assert.assertEquals(List.of(StereotypePolicyViewKey.INSTANCE), this.view.hidden);
	}

	@Test
	public void aNullRuleIsIgnored() {
		this.interaction.registerActions();

		this.actions.fire(StereotypePolicyActionKey.AddStereotype, null);

		Assert.assertTrue(this.controller.setRulesCalls.isEmpty());
	}

	@Test
	public void aPanelClosedByPassivationIsNotClosedAgainByTheNextOne() {
		this.interaction.registerActions();
		this.actions.fire(StereotypePolicyActionKey.Show);
		this.interaction.unregisterActions();

		this.interaction.unregisterActions();

		Assert.assertEquals(1, this.view.hidden.size());
	}

	@Test
	public void theModelsNotificationIsNotItsConcern() {
		this.interaction.registerActions();

		this.interaction.onStereotypesChanged(BuiltInStereotypes.rules());

		Assert.assertTrue(this.controller.setRulesCalls.isEmpty());
		Assert.assertEquals(0, this.controller.publishCalls);
	}
}
