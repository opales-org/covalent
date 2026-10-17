/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container;

/**
 * Overall health of a Covalent container operation.
 */
public enum ContainerStatus {
	/** Every internal component activated cleanly and no shutdown residue was detected. */
	healthy,
	/**
	 * No internal component failed, but an application component failed, a requested extension failed to start,
	 * or shutdown left residue behind.
	 */
	degraded,
	/** At least one internal component failed to activate on startup or to passivate on shutdown. */
	failed;
}
