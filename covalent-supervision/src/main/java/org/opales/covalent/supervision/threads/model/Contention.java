/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.model;

/**
 * How hard a pool is pressed, over the last seconds: from its workers'
 * occupancy, its queue and its rejections.
 */
public enum Contention {

	/** Room to spare. */
	FLUID,

	/** Most workers busy, or tasks queuing. */
	UNDER_STRAIN,

	/** Nearly every worker busy with tasks queuing, or tasks rejected. */
	SATURATED
}
