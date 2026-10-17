/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.lifecycle;

/**
 * Marker interface alternative to the {@link Removal} method annotation:
 * implementing this interface on a component implementation class
 * causes {@link #remove()} to be invoked when the owning component
 * transitions to the {@code removed} state, to release any
 * resources it holds. The removed instance is not reactivated: a removed
 * component stays deployed, and starting it again creates a new instance.
 */
public interface Removable {

	/**
	 * Called once, on transition into the {@code removed} state.
	 */
	void remove();
}
