/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.stereotypepolicy.controller.impl;

import java.util.List;

import org.opales.covalent.supervision.stereotypepolicy.BuiltInStereotypes;
import org.opales.covalent.supervision.stereotypepolicy.StereotypeRule;
import org.opales.covalent.supervision.stereotypepolicy.StereotypeRules;
import org.opales.covalent.supervision.stereotypepolicy.controller.StereotypeParameters;
import org.opales.covalent.supervision.stereotypepolicy.controller.StereotypePolicyController;
import org.opales.covalent.supervision.stereotypepolicy.controller.StereotypePolicyControllerServices;
import org.opales.covalent.supervision.stereotypepolicy.model.StereotypePolicyModelServices;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;

/**
 * StereotypePolicy's Controller: the sole implementer of the external {@link StereotypeParameters}
 * port. {@link #stereotypeFor(Class)}'s rule is the longest name ending of the table that the
 * type's simple name ends with.
 * <p>
 * Keeps its own write-through copy of the table (the same pattern as
 * {@code ExclusionPolicyControllerImpl}), replaced as a whole on every change, so that
 * {@link #stereotypeFor(Class)} - called while the diagrams are built, from several threads -
 * never needs a round trip through {@code StereotypePolicyModelServices} and never sees a list
 * being modified. It starts with the {@link BuiltInStereotypes}, like the Model.
 */
@Implementation
public class StereotypePolicyControllerImpl
		implements StereotypePolicyController, StereotypePolicyControllerServices, StereotypeParameters {

	/** Creates the Controller, starting from the built-in rules. */
	public StereotypePolicyControllerImpl() {
		// Nothing to initialize.
	}

	@UsedServices
	private StereotypePolicyModelServices useModelServices;

	private volatile List<StereotypeRule> rules = BuiltInStereotypes.rules();

	@Override
	public void setRules(final List<StereotypeRule> newRules) {
		this.rules = List.copyOf(newRules);
		this.useModelServices.setRules(this.rules);
	}

	@Override
	public void publishState() {
		this.useModelServices.publishState();
	}

	@Override
	public String stereotypeFor(final Class<?> componentType) {
		if (componentType == null) {
			return null;
		}
		return StereotypeRules.match(this.rules, componentType.getSimpleName());
	}
}
