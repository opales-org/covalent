/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.model;

import java.util.Collection;

import org.opales.covalent.container.lifecycle.ComponentState;

/**
 * The status badge shown on a component card: a leaf's own lifecycle state,
 * or for a composite an aggregate of all its leaves' states - see
 * {@link #aggregate(Collection)}, which reuses the leaf states' own names
 * (in upper case) wherever they apply. Colors are palette entries, editable in the
 * Preferences panel.
 */
public enum ComponentStatus {

	/** Deployed, not yet instantiated; also the badge of an unknown state. */
	DEPLOYED("DEPLOYED", PortColorKey.STATUS_INACTIVE),
	/** Instantiated, not yet initialized. */
	INSTANTIATED("INSTANTIATED", PortColorKey.STATUS_STARTING),
	/** Initialized, not yet activated. */
	INITIALIZED("INITIALIZED", PortColorKey.STATUS_STARTING),
	/** Running. */
	ACTIVATED("ACTIVATED", PortColorKey.STATUS_ACTIVATED),
	/** Passivated, can be activated again. */
	PASSIVATED("PASSIVATED", PortColorKey.STATUS_PASSIVATED),
	/** Removed from the container. */
	REMOVED("REMOVED", PortColorKey.STATUS_INACTIVE),
	/** A lifecycle transition failed. */
	FAILED("FAILED", PortColorKey.STATUS_FAILED),

	/** Composite only: leaves in different states, none failed. */
	MIXED("MIXED", PortColorKey.STATUS_MIXED),
	/** Composite only: some leaves failed, not all. */
	DEGRADED("DEGRADED", PortColorKey.STATUS_DEGRADED);

	private final String label;
	private final PortColorKey colorKey;

	ComponentStatus(final String label, final PortColorKey colorKey) {
		this.label = label;
		this.colorKey = colorKey;
	}

	/**
	 * Returns the text shown on the badge.
	 *
	 * @return the text shown on the badge, the name of the status
	 */
	public String getLabel() {
		return this.label;
	}

	/**
	 * Returns the palette entry for the badge.
	 *
	 * @return the palette entry coloring this status's badge
	 */
	public PortColorKey getColorKey() {
		return this.colorKey;
	}

	/**
	 * Returns the badge of a leaf component, {@link #DEPLOYED} when its state is unknown.
	 *
	 * @param state a leaf's lifecycle state, {@code null} when unknown
	 * @return its badge
	 */
	public static ComponentStatus ofLeaf(final ComponentState state) {
		if (state == null) {
			return DEPLOYED;
		}
		switch (state) {
			case instantiated:
				return INSTANTIATED;
			case initialized:
				return INITIALIZED;
			case activated:
				return ACTIVATED;
			case passivated:
				return PASSIVATED;
			case removed:
				return REMOVED;
			case failed:
				return FAILED;
			default:
				return DEPLOYED;
		}
	}

	/**
	 * A composite's badge, from the states of all the leaves it contains:
	 * FAILED if every leaf failed, DEGRADED if some did, ACTIVATED if every
	 * leaf is activated, PASSIVATED if every leaf is passivated, MIXED
	 * otherwise (also when there is no leaf at all).
	 *
	 * @param leafStates the lifecycle states of every leaf under the composite
	 * @return its badge
	 */
	public static ComponentStatus aggregate(final Collection<ComponentState> leafStates) {
		if (leafStates.isEmpty()) {
			return MIXED;
		}
		long failed = leafStates.stream().filter(state -> state == ComponentState.failed).count();
		if (failed == leafStates.size()) {
			return FAILED;
		}
		if (failed > 0) {
			return DEGRADED;
		}
		if (leafStates.stream().allMatch(state -> state == ComponentState.activated)) {
			return ACTIVATED;
		}
		if (leafStates.stream().allMatch(state -> state == ComponentState.passivated)) {
			return PASSIVATED;
		}
		return MIXED;
	}
}
