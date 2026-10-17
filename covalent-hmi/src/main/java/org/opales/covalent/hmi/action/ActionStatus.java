/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.hmi.action;

/**
 * Whether an action is executing at a given moment.
 */
public enum ActionStatus {

	/** The action is executing. */
	running,

	/** The action is not executing. */
	idle;
}
