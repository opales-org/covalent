/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.lifecycle;

import org.opales.covalent.container.component.annotations.Parameters;
import org.opales.covalent.container.lifecycle.LifecycleServices.StartMode;

/**
 * Configures lifecycle transition ordering and lifecycle callback discovery.
 */
@Parameters
public interface LifecycleParameters {
	/**
	 * Sets the startup mode used by the lifecycle manager.
	 *
	 * @param startMode startup transition ordering mode
	 * @throws IllegalArgumentException if {@code startMode} is {@code null}
	 */
	void setStartMode(StartMode startMode);

	/**
	 * Returns the startup mode used by the lifecycle manager.
	 *
	 * @return startup transition ordering mode
	 */
	StartMode getStartMode();

	/**
	 * Enables or disables lifecycle callback discovery by method naming convention.
	 *
	 * @param findLifecycleMethodByNameConvention {@code true} enables naming
	 *                                            convention discovery
	 */
	void setFindLifecycleMethodByNameConvention(final boolean findLifecycleMethodByNameConvention);

	/**
	 * Reports whether lifecycle callback discovery by method naming convention is
	 * enabled.
	 *
	 * @return {@code true} when naming convention discovery is enabled
	 */
	boolean isFindLifecycleMethodByNameConvention();
}
