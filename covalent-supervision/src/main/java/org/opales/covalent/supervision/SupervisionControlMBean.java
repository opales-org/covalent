/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision;

/**
 * The management interface of the supervisor, registered as the {@code org.opales.covalent:type=Supervision} MBean
 * of the platform MBean server: a tool such as JConsole or VisualVM, attached to the running JVM, can summon the
 * supervisor from it.
 */
public interface SupervisionControlMBean {

	/**
	 * The state of the supervisor.
	 *
	 * @return {@code DORMANT} until it is first summoned, {@code STARTED} once it has been started (its window may
	 *         be open or closed), {@code UNAVAILABLE: <reason>} when it cannot be shown, for example without a
	 *         display
	 */
	String getState();

	/**
	 * Starts the supervisor if needed and shows its window, in front of the other windows.
	 */
	void openSupervisor();
}
