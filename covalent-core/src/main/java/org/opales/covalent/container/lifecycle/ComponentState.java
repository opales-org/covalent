/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.lifecycle;

/**
 * The states a component can go through over its lifetime. A component
 * normally moves forward through {@link #deployed}, {@link #instantiated},
 * {@link #initialized} and {@link #activated}, can cycle between
 * {@link #activated} and {@link #passivated} any number of times, and
 * is eventually {@link #removed}. A removed component stays deployed: it can
 * be instantiated again and go through the states anew. {@link #failed}
 * is reached from any state when a lifecycle callback throws.
 */
public enum ComponentState {

	/** The component type is known to the container but not yet instantiated. */
	deployed,

	/** The component implementation has been instantiated. */
	instantiated,

	/** {@link Initializable#initialize()} (or {@link Initialization}) has run. */
	initialized,

	/** The component is activated: its ports are published. */
	activated,

	/** The component has been passivated: its ports are unpublished, but it can be reactivated. */
	passivated,

	/** The component has been removed; it stays deployed and can be instantiated again. */
	removed,

	/** A lifecycle callback failed; the component can be reset back to deployed. */
	failed;
}
