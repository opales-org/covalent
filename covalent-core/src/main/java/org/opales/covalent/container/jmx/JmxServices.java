/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.jmx;

import org.opales.covalent.container.component.annotations.Services;

/**
 * What the container asks of its JMX manager when it starts and stops.
 */
@Services
public interface JmxServices {

	/**
	 * Registers the container's MBean, when the container is named and JMX is
	 * not disabled; called by the container's {@code start()}. Never throws.
	 */
	void registerContainerMBean();

	/**
	 * Unregisters the container's MBean, if it was registered; called by the
	 * container's {@code stop()} and {@code dispose()}. Never throws.
	 */
	void unregisterContainerMBean();
}
