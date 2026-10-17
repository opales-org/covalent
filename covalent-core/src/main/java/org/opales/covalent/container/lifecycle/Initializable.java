/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.lifecycle;

/**
 * Marker interface alternative to the {@link Initialization} method
 * annotation: implementing this interface on a component
 * implementation class causes {@link #initialize()} to be invoked once,
 * when the owning component transitions from the {@code instantiated} to
 * the {@code initialized} state. This is the point at which offered and
 * accessed parameters first become available.
 */
public interface Initializable {

	/**
	 * Called once, during the {@code instantiated} to {@code initialized}
	 * state transition.
	 */
	void initialize();
}
