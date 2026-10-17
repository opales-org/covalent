/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.exclusionpolicy.model.impl;

import java.util.List;

import org.opales.covalent.supervision.exclusionpolicy.BuiltInExclusions;
import org.opales.covalent.supervision.exclusionpolicy.model.ExclusionPolicyEvents;
import org.opales.covalent.supervision.exclusionpolicy.model.ExclusionPolicyModel;
import org.opales.covalent.supervision.exclusionpolicy.model.ExclusionPolicyModelServices;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.lifecycle.Activation;

/**
 * Plain state holder - see {@code ExclusionPolicyModelServices}'s javadoc.
 * <p>
 * Defaults are opt-out, not opt-in: {@code excludeInternal} starts
 * {@code true} (every Covalent framework component, annotated
 * {@code @Internal}, is filtered from the supervision screens unless the
 * user unchecks "Exclude internal components"), and
 * {@code excludedComponents} starts with the {@link BuiltInExclusions}.
 */
@Implementation
public class ExclusionPolicyModelImpl implements ExclusionPolicyModel, ExclusionPolicyModelServices {

	/** Creates the Model, holding the default exclusions. */
	public ExclusionPolicyModelImpl() {
		// Nothing to initialize.
	}

	@ProducedEvents
	private ExclusionPolicyEvents produceExclusionPolicyEvents;

	private volatile List<Class<?>> excludedComponents = BuiltInExclusions.types();
	private volatile boolean excludeInternal = true;

	/**
	 * Re-fires both fields' default values once, at startup: the only
	 * way a consumer that renders from push events alone (the View's
	 * "Exclude internal components" checkbox, in particular) can ever learn
	 * the real initial value instead of silently defaulting to its own
	 * widget's own initial state.
	 */
	@Activation
	public void publishInitialState() {
		this.publishState();
	}

	@Override
	public void publishState() {
		this.produceExclusionPolicyEvents.onExcludedComponentsChanged(List.copyOf(this.excludedComponents));
		this.produceExclusionPolicyEvents.onExcludeInternalComponentsChanged(this.excludeInternal);
	}

	@Override
	public void setExcludedComponents(final List<Class<?>> componentTypes) {
		this.excludedComponents = List.copyOf(componentTypes);
		this.produceExclusionPolicyEvents.onExcludedComponentsChanged(this.excludedComponents);
	}

	@Override
	public void setExcludeInternalComponents(final boolean exclude) {
		this.excludeInternal = exclude;
		this.produceExclusionPolicyEvents.onExcludeInternalComponentsChanged(exclude);
	}
}
