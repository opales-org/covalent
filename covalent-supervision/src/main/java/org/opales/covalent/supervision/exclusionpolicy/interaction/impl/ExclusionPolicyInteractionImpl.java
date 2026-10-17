/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.exclusionpolicy.interaction.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.opales.covalent.supervision.exclusionpolicy.BuiltInExclusions;
import org.opales.covalent.supervision.exclusionpolicy.ExclusionTypes;
import org.opales.covalent.supervision.exclusionpolicy.controller.ExclusionPolicyControllerServices;
import org.opales.covalent.supervision.exclusionpolicy.interaction.ExclusionPolicyActionKey;
import org.opales.covalent.supervision.exclusionpolicy.interaction.ExclusionPolicyInteraction;
import org.opales.covalent.supervision.exclusionpolicy.interaction.ExclusionPolicyViewKey;
import org.opales.covalent.supervision.exclusionpolicy.model.ExclusionPolicyEvents;
import org.opales.covalent.supervision.exclusionpolicy.view.ExclusionPolicyViewServices;
import org.opales.covalent.supervision.supervisionhmi.SupervisorSettings;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionProviderServices;

/**
 * ExclusionPolicy's Interaction Controller: registers this function's
 * ActionKeys at its own {@code @Activation}, panel closed by default -
 * declares no business-specific {@code SupervisorTabDescriptor} content
 * (see {@code ExclusionPolicyViewImpl}'s empty
 * {@code supportedToolbarFunctions()}), only the panel open/close toggle.
 *
 * <p>
 * Also keeps the exclusions from one run to the next, in {@link SupervisorSettings}: the
 * "exclude internal components" switch and the user's own excluded types, by class name. They
 * are restored at activation and written at every change. The built-in exclusions are always
 * applied and never stored. A stored type that cannot be loaded - typically one excluded while
 * observing another application - is kept stored but not applied nor shown, so that it applies
 * again whenever it can be loaded. The exclusions are kept by component type: a component implementation
 * named by the user, or found in the stored names, is turned into its type ({@link ExclusionTypes}).
 */
@Implementation
public class ExclusionPolicyInteractionImpl implements ExclusionPolicyInteraction, ExclusionPolicyEvents {

	/** Creates the Interaction Controller; the container injects the services it uses. */
	public ExclusionPolicyInteractionImpl() {
		// Nothing to initialize.
	}

	@UsedServices
	private ActionProviderServices actionManagerServices;

	@UsedServices
	private ExclusionPolicyViewServices useViewServices;

	@UsedServices
	private ExclusionPolicyControllerServices useControllerServices;

	private boolean shown = false;

	/** The user's own excluded types, by class name: those applied and those not loadable. */
	private final Set<String> userExcludedNames = new LinkedHashSet<>();

	/** Stored excluded types that cannot be loaded: kept stored, neither applied nor shown. */
	private final Set<String> unresolvedNames = new LinkedHashSet<>();

	/**
	 * Restores the stored exclusions, then registers the exclusion actions and the panel toggle with the
	 * HMI's action manager.
	 */
	@Activation
	public void registerActions() {
		this.restoreExclusions();
		this.actionManagerServices.registerActionExecution(ExclusionPolicyActionKey.SetExcludedComponents,
				this::onSetExcludedComponents, List.class);
		this.actionManagerServices.registerActionExecution(ExclusionPolicyActionKey.AddExcludedComponent,
				this::onAddExcludedComponent, Class.class);
		this.actionManagerServices.registerActionExecution(ExclusionPolicyActionKey.SetExcludeInternalComponents,
				this::onSetExcludeInternalComponents, Boolean.class);
		this.actionManagerServices.registerActionExecution(ExclusionPolicyActionKey.Toggle, this::onToggle);
	}

	/** Unregisters the actions and closes the panel if it is open. */
	@Passivation
	public void unregisterActions() {
		for (ExclusionPolicyActionKey key : ExclusionPolicyActionKey.values()) {
			try {
				this.actionManagerServices.unregisterActionExecution(key);
			} catch (IllegalStateException e) {
				// ActionManager already passivated as part of the same container-stop cascade.
			}
		}
		if (this.shown) {
			this.useViewServices.hideView(ExclusionPolicyViewKey.INSTANCE);
			this.shown = false;
		}
	}

	private void onToggle() {
		this.shown = !this.shown;
		if (this.shown) {
			this.useViewServices.showView(ExclusionPolicyViewKey.INSTANCE);
			// The Model's own activation-time publication may predate the View
			// being ready to render it.
			this.useControllerServices.publishState();
		} else {
			this.useViewServices.hideView(ExclusionPolicyViewKey.INSTANCE);
		}
	}

	@SuppressWarnings("unchecked")
	private void onSetExcludedComponents(final List<?> componentTypes) {
		this.useControllerServices.setExcludedComponents((List<Class<?>>) componentTypes);
		this.userExcludedNames.clear();
		this.userExcludedNames.addAll(this.unresolvedNames);
		for (Object componentType : componentTypes) {
			if (!BuiltInExclusions.contains((Class<?>) componentType)) {
				this.userExcludedNames.add(((Class<?>) componentType).getName());
			}
		}
		this.storeExcludedComponents();
	}

	private void onAddExcludedComponent(final Class<?> named) {
		Class<?> componentType = ExclusionTypes.typeOf(named);
		this.useControllerServices.addExcludedComponent(componentType);
		if (!BuiltInExclusions.contains(componentType)) {
			this.unresolvedNames.remove(componentType.getName());
			this.userExcludedNames.add(componentType.getName());
			this.storeExcludedComponents();
		}
	}

	private void onSetExcludeInternalComponents(final Boolean exclude) {
		this.useControllerServices.setExcludeInternalComponents(exclude);
		SupervisorSettings.putBoolean(SupervisorSettings.EXCLUDE_INTERNAL_COMPONENTS, exclude);
	}

	/** Applies the stored exclusions, if any, over the built-in ones. */
	private void restoreExclusions() {
		this.useControllerServices.setExcludeInternalComponents(
				SupervisorSettings.getBoolean(SupervisorSettings.EXCLUDE_INTERNAL_COMPONENTS, true));
		List<String> storedNames = SupervisorSettings.getStringList(SupervisorSettings.EXCLUDED_COMPONENTS);
		if (storedNames == null) {
			return;
		}
		List<Class<?>> componentTypes = new ArrayList<>(BuiltInExclusions.types());
		boolean renamed = false;
		for (String name : storedNames) {
			try {
				Class<?> componentType = ExclusionTypes.typeOf(Class.forName(name, false, ExclusionPolicyInteractionImpl.class.getClassLoader()));
				renamed |= !componentType.getName().equals(name);
				if (BuiltInExclusions.contains(componentType)) {
					continue;
				}
				this.userExcludedNames.add(componentType.getName());
				if (!componentTypes.contains(componentType)) {
					componentTypes.add(componentType);
				}
			} catch (ClassNotFoundException | LinkageError e) {
				this.userExcludedNames.add(name);
				this.unresolvedNames.add(name);
			}
		}
		this.useControllerServices.setExcludedComponents(componentTypes);
		if (renamed) {
			this.storeExcludedComponents();
		}
	}

	private void storeExcludedComponents() {
		SupervisorSettings.putStringList(SupervisorSettings.EXCLUDED_COMPONENTS, List.copyOf(this.userExcludedNames));
	}

	// --- ExclusionPolicyEvents: the View renders directly; nothing here reacts to content. ---

	@Override
	public void onExcludedComponentsChanged(final List<Class<?>> componentTypes) {
		// No action here: the View renders the change directly.
	}

	@Override
	public void onExcludeInternalComponentsChanged(final boolean exclude) {
		// No action here: the View renders the change directly.
	}
}
