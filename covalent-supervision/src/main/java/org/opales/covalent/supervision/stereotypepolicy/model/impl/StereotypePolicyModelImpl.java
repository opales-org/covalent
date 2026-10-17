/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.stereotypepolicy.model.impl;

import java.util.List;

import org.opales.covalent.supervision.stereotypepolicy.BuiltInStereotypes;
import org.opales.covalent.supervision.stereotypepolicy.StereotypeRule;
import org.opales.covalent.supervision.stereotypepolicy.model.StereotypePolicyEvents;
import org.opales.covalent.supervision.stereotypepolicy.model.StereotypePolicyModel;
import org.opales.covalent.supervision.stereotypepolicy.model.StereotypePolicyModelServices;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.lifecycle.Activation;

/**
 * Plain state holder - see {@code StereotypePolicyModelServices}'s javadoc. The table starts with
 * the {@link BuiltInStereotypes}.
 */
@Implementation
public class StereotypePolicyModelImpl implements StereotypePolicyModel, StereotypePolicyModelServices {

	/** Creates the Model, starting from the built-in rules. */
	public StereotypePolicyModelImpl() {
		// Nothing to initialize.
	}

	@ProducedEvents
	private StereotypePolicyEvents produceStereotypePolicyEvents;

	private volatile List<StereotypeRule> rules = BuiltInStereotypes.rules();

	/**
	 * Fires the table once, at startup: the only way a consumer that renders from push events
	 * alone can learn the real initial value.
	 */
	@Activation
	public void publishInitialState() {
		this.publishState();
	}

	@Override
	public void publishState() {
		this.produceStereotypePolicyEvents.onStereotypesChanged(this.rules);
	}

	@Override
	public void setRules(final List<StereotypeRule> newRules) {
		this.rules = List.copyOf(newRules);
		this.produceStereotypePolicyEvents.onStereotypesChanged(this.rules);
	}
}
