/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.lifecycle;

/**
 * Marker interface alternative to the {@link Passivation} method
 * annotation: implementing this interface on a component
 * implementation class causes {@link #passivate()} to be invoked when the
 * owning component transitions from {@code activated} to
 * {@code passivated}. Passivation unpublishes the component's ports; a
 * passivated component can later be reactivated (see {@link Activable}).
 */
public interface Passivatable {

	/**
	 * Called on every transition into the {@code passivated} state.
	 */
	void passivate();
}
