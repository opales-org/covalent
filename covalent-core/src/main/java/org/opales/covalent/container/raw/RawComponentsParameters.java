/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.raw;

import org.opales.covalent.container.component.annotations.Parameters;
import org.opales.covalent.container.lifecycle.ComponentState;

/**
 * Configures lifecycle behavior applied to newly registered raw components.
 */
@Parameters
public interface RawComponentsParameters {
	/**
	 * Sets the lifecycle state reached by default when a raw component is
	 * registered.
	 *
	 * @param componentStateToReach default raw component target state
	 */
	void setDefaultStateToReach(final ComponentState componentStateToReach);

	/**
	 * Returns the lifecycle state reached by default when a raw component is
	 * registered.
	 *
	 * @return default raw component target state
	 */
	ComponentState getDefaultStateToReach();
}
