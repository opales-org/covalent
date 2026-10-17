/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.cells;

/**
 * Runtime activity displayed on a component port slot.
 */
public enum ComponentPortActivity {
	IDLE,
	SERVICE_RUNNING,
	SERVICE_FAILED,
	EVENT_RUNNING,
	PARAMETER_RUNNING,
	PARAMETER_FAILED
}
