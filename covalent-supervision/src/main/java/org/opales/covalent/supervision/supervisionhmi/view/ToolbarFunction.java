/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.supervisionhmi.view;

/**
 * One kind of shared-toolbar widget that a {@link SupervisorTabDescriptor}
 * may declare support for.
 */
public enum ToolbarFunction {

	/** The Record/Stop toggle button of the shared toolbar. */
	RECORD_TOGGLE,
	/** The zoom slider of the shared toolbar. */
	ZOOM_SLIDER;
}
