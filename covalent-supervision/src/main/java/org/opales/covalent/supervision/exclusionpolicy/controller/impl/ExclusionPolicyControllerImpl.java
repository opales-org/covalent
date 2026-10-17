/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.exclusionpolicy.controller.impl;

import java.util.ArrayList;
import java.util.List;

import org.opales.covalent.container.component.internals.Internal;
import org.opales.covalent.supervision.exclusionpolicy.BuiltInExclusions;
import org.opales.covalent.supervision.exclusionpolicy.controller.ExclusionParameters;
import org.opales.covalent.supervision.exclusionpolicy.controller.ExclusionPolicyController;
import org.opales.covalent.supervision.exclusionpolicy.controller.ExclusionPolicyControllerServices;
import org.opales.covalent.supervision.exclusionpolicy.model.ExclusionPolicyModelServices;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;

/**
 * ExclusionPolicy's Controller: the sole implementer of the external
 * {@link ExclusionParameters} port. {@link #isExcluded(Class)}'s rule is
 * an explicit type match, or "exclude internal" plus the {@link Internal} annotation. Keeps
 * its own write-through copy of both fields (mirroring
 * {@code ComponentDiagramControllerImpl}'s own local-selection pattern) so
 * {@link #isExcluded(Class)} - called by the Controllers of ComponentDiagram,
 * SequenceDiagram and DependencyManagement - never needs a round trip
 * through {@code ExclusionPolicyModelServices}.
 * <p>
 * {@link #isExcluded(Class)} is a pure predicate: it never adds an
 * internal-annotated type to {@code excludedComponents}, since a shared,
 * externally-consumed Parameters accessor silently mutating this
 * function's own Model state on every caller's read would be a layering
 * violation; the annotation-based check is decided on every call anyway.
 * <p>
 * Both fields default to the same opt-out starting point as
 * {@code ExclusionPolicyModelImpl} - see that class's javadoc for why:
 * {@code excludeInternal} starts {@code true}, and
 * {@code excludedComponents} starts with the {@link BuiltInExclusions}. These two copies exist
 * independently (this one is what {@link #isExcluded(Class)} actually
 * reads; the Model's is what the View would render, if opened, once pushed
 * through an edit), so both are kept in sync at their initial value rather
 * than one deferring to the other at startup.
 */
@Implementation
public class ExclusionPolicyControllerImpl implements ExclusionPolicyController, ExclusionPolicyControllerServices,
		ExclusionParameters {

	/** Creates the Controller, starting from the default exclusions. */
	public ExclusionPolicyControllerImpl() {
		// Nothing to initialize.
	}

	@UsedServices
	private ExclusionPolicyModelServices useModelServices;

	/**
	 * Write-through local mirror - see class javadoc. Replaced as a whole on every change, so
	 * that {@link #isExcluded(Class)}, read from other threads, never sees a list being modified.
	 */
	private volatile List<Class<?>> excludedComponents = BuiltInExclusions.types();

	private volatile boolean excludeInternal = true;

	@Override
	public synchronized void setExcludedComponents(final List<Class<?>> componentTypes) {
		this.excludedComponents = List.copyOf(componentTypes);
		this.useModelServices.setExcludedComponents(this.excludedComponents);
	}

	@Override
	public synchronized void addExcludedComponent(final Class<?> componentType) {
		if (this.excludedComponents.contains(componentType)) {
			return;
		}
		List<Class<?>> componentTypes = new ArrayList<>(this.excludedComponents);
		componentTypes.add(componentType);
		this.excludedComponents = List.copyOf(componentTypes);
		this.useModelServices.setExcludedComponents(this.excludedComponents);
	}

	@Override
	public void setExcludeInternalComponents(final boolean exclude) {
		this.excludeInternal = exclude;
		this.useModelServices.setExcludeInternalComponents(exclude);
	}

	@Override
	public void publishState() {
		this.useModelServices.publishState();
	}

	@Override
	public boolean isExcluded(final Class<?> componentType) {
		if (componentType == null) {
			return true;
		}
		if (this.excludedComponents.contains(componentType)) {
			return true;
		}
		return this.excludeInternal && componentType.isAnnotationPresent(Internal.class);
	}
}
