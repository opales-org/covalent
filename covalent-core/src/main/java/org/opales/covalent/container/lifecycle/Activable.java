/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.lifecycle;

/**
 * Marker interface alternative to the {@link Activation} method annotation:
 * implementing this interface on a component implementation class
 * causes {@link #activate()} to be invoked when the owning component
 * transitions to the {@code activated} state, either from
 * {@code initialized} or back from {@code passivated}. Unlike
 * initialization, activation can happen repeatedly over a component's
 * lifetime and publishes its ports (services, subscriptions), so it should
 * execute quickly.
 */
public interface Activable {

	/**
	 * Called on every transition into the {@code activated} state.
	 */
	void activate();
}
