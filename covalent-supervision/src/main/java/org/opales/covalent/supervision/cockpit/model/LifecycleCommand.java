/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.cockpit.model;

import java.util.EnumSet;
import java.util.Set;

import org.opales.covalent.container.lifecycle.ComponentState;

/**
 * A lifecycle transition the console can ask for, with the states it is allowed from - the
 * core's state diagram, narrowed to what makes sense from the console.
 */
public enum LifecycleCommand {

	/** Creates the instance of a deployed or removed component. */
	INSTANTIATE(EnumSet.of(ComponentState.deployed, ComponentState.removed), false),

	/** Initializes a component, instantiating it first when it is only deployed. */
	INITIALIZE(EnumSet.of(ComponentState.deployed, ComponentState.instantiated, ComponentState.removed), false),

	/** Activates a component, bringing it through the intermediate states when needed. */
	ACTIVATE(EnumSet.of(ComponentState.deployed, ComponentState.instantiated,
			ComponentState.initialized, ComponentState.passivated, ComponentState.removed), false),

	/** Passivates an activated component. */
	PASSIVATE(EnumSet.of(ComponentState.activated), false),

	/** Passivates then activates again. */
	RESTART(EnumSet.of(ComponentState.activated), false),

	/** Removes a component from the container, which asks for confirmation. */
	REMOVE(EnumSet.of(ComponentState.instantiated, ComponentState.initialized,
			ComponentState.passivated), true),

	/** Undeploys a component, which asks for confirmation. */
	UNDEPLOY(EnumSet.of(ComponentState.deployed, ComponentState.removed), true),

	/** Cleans a failed composite tree, back to deployed. */
	RESET(EnumSet.of(ComponentState.failed), false);

	private final Set<ComponentState> allowedFrom;
	private final boolean destructive;

	LifecycleCommand(final Set<ComponentState> allowedFrom, final boolean destructive) {
		this.allowedFrom = allowedFrom;
		this.destructive = destructive;
	}

	/**
	 * Tells whether the command applies to a component in the given state.
	 *
	 * @param state a component's current state, or {@code null} when unknown
	 * @return whether the command applies from it
	 */
	public boolean isAllowedFrom(final ComponentState state) {
		return state != null && this.allowedFrom.contains(state);
	}

	/**
	 * A raw component is built and registered by its owner, not by the container: deploying,
	 * instantiating and initializing it are its owner's business, and it is reactivated only
	 * from passivated.
	 *
	 * @param state a raw component's current state, or {@code null} when unknown
	 * @return whether the command applies to it from that state
	 */
	public boolean isAllowedForRawFrom(final ComponentState state) {
		return switch (this) {
			case INSTANTIATE, INITIALIZE -> false;
			case ACTIVATE -> state == ComponentState.passivated;
			default -> this.isAllowedFrom(state);
		};
	}

	/**
	 * Tells whether the command takes the component out of the container.
	 *
	 * @return whether it takes the component out of the container, asking for confirmation
	 */
	public boolean isDestructive() {
		return this.destructive;
	}
}
