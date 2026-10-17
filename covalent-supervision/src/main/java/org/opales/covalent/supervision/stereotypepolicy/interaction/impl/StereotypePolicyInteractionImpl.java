/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.stereotypepolicy.interaction.impl;

import java.util.ArrayList;
import java.util.List;

import org.opales.covalent.supervision.stereotypepolicy.BuiltInStereotypes;
import org.opales.covalent.supervision.stereotypepolicy.StereotypeRule;
import org.opales.covalent.supervision.stereotypepolicy.StereotypeRules;
import org.opales.covalent.supervision.stereotypepolicy.controller.StereotypePolicyControllerServices;
import org.opales.covalent.supervision.stereotypepolicy.interaction.StereotypePolicyActionKey;
import org.opales.covalent.supervision.stereotypepolicy.interaction.StereotypePolicyInteraction;
import org.opales.covalent.supervision.stereotypepolicy.interaction.StereotypePolicyViewKey;
import org.opales.covalent.supervision.stereotypepolicy.model.StereotypePolicyEvents;
import org.opales.covalent.supervision.stereotypepolicy.view.StereotypePolicyViewServices;
import org.opales.covalent.supervision.supervisionhmi.SupervisorSettings;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionProviderServices;

/**
 * StereotypePolicy's Interaction Controller: registers this function's ActionKeys at its own
 * {@code @Activation}, and keeps the rules the user added, and the built-in ones they removed,
 * from one run to the next, in {@link SupervisorSettings}: they are restored at activation and
 * written at every change. The built-in rules themselves are never stored. A rule is checked
 * again here before it is accepted, whatever the View did.
 * <p>
 * The panel is opened from the Settings page only, so there is no toggle: {@code Show} opens it
 * or brings it forward.
 */
@Implementation
public class StereotypePolicyInteractionImpl implements StereotypePolicyInteraction, StereotypePolicyEvents {

	/** Creates the Interaction Controller, with the built-in rules until the stored ones are restored. */
	public StereotypePolicyInteractionImpl() {
		// Nothing to initialize.
	}

	@UsedServices
	private ActionProviderServices actionManagerServices;

	@UsedServices
	private StereotypePolicyViewServices useViewServices;

	@UsedServices
	private StereotypePolicyControllerServices useControllerServices;

	/** The whole table as this Interaction Controller last set it, built-in rules included. */
	private volatile List<StereotypeRule> rules = BuiltInStereotypes.rules();

	private boolean shown = false;

	/** Restores the stored rules, then registers this function's actions. */
	@Activation
	public void registerActions() {
		this.restoreRules();
		this.actionManagerServices.registerActionExecution(StereotypePolicyActionKey.AddStereotype,
				this::onAddStereotype, StereotypeRule.class);
		this.actionManagerServices.registerActionExecution(StereotypePolicyActionKey.RemoveStereotype,
				this::onRemoveStereotype, String.class);
		this.actionManagerServices.registerActionExecution(StereotypePolicyActionKey.ResetStereotypes, this::onReset);
		this.actionManagerServices.registerActionExecution(StereotypePolicyActionKey.Show, this::onShow);
	}

	/** Unregisters this function's actions and closes its panel. */
	@Passivation
	public void unregisterActions() {
		for (StereotypePolicyActionKey key : StereotypePolicyActionKey.values()) {
			try {
				this.actionManagerServices.unregisterActionExecution(key);
			} catch (IllegalStateException e) {
				// ActionManager already passivated as part of the same container-stop cascade.
			}
		}
		if (this.shown) {
			this.useViewServices.hideView(StereotypePolicyViewKey.INSTANCE);
			this.shown = false;
		}
	}

	private void onShow() {
		this.shown = true;
		this.useViewServices.showView(StereotypePolicyViewKey.INSTANCE);
		// The Model's own activation-time publication may predate the View being ready to render it.
		this.useControllerServices.publishState();
	}

	private synchronized void onAddStereotype(final StereotypeRule rule) {
		if (rule == null) {
			return;
		}
		String suffix = rule.suffix().trim();
		String label = rule.label().trim();
		if (StereotypeRules.check(this.rules, suffix, label) != null) {
			return;
		}
		List<StereotypeRule> updated = new ArrayList<>(this.rules);
		updated.add(new StereotypeRule(suffix, label, false));
		this.apply(updated);
	}

	private synchronized void onRemoveStereotype(final String suffix) {
		List<StereotypeRule> updated = new ArrayList<>(this.rules);
		if (updated.removeIf(rule -> rule.suffix().equals(suffix))) {
			this.apply(updated);
		}
	}

	private synchronized void onReset() {
		if (!this.rules.equals(BuiltInStereotypes.rules())) {
			this.apply(BuiltInStereotypes.rules());
		}
	}

	private void apply(final List<StereotypeRule> updated) {
		this.rules = List.copyOf(updated);
		this.useControllerServices.setRules(this.rules);
		SupervisorSettings.putStringList(SupervisorSettings.STEREOTYPE_RULES, StereotypeRules.encode(this.rules));
		SupervisorSettings.putStringList(SupervisorSettings.STEREOTYPE_REMOVED_BUILT_INS,
				StereotypeRules.removedBuiltIns(this.rules));
	}

	/** Applies the stored rules, if any, over the built-in ones the user did not remove. */
	private void restoreRules() {
		List<String> stored = SupervisorSettings.getStringList(SupervisorSettings.STEREOTYPE_RULES);
		List<String> removed = SupervisorSettings.getStringList(SupervisorSettings.STEREOTYPE_REMOVED_BUILT_INS);
		if (stored == null && removed == null) {
			return;
		}
		this.rules = StereotypeRules.restore(stored == null ? List.of() : stored, removed == null ? List.of() : removed);
		this.useControllerServices.setRules(this.rules);
	}

	// --- StereotypePolicyEvents: the View renders directly; nothing here reacts to content. ---

	@Override
	public void onStereotypesChanged(final List<StereotypeRule> changedRules) {
		// No action here: the View renders the table directly.
	}
}
