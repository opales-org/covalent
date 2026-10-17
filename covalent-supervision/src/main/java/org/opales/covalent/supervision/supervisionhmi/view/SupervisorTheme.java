/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.supervisionhmi.view;

/**
 * The supervision tool's visual theme, selected from SupervisionHmi's View
 * menu. SupervisionHmi swaps its scene's theme stylesheet; every hosted
 * function that draws colors outside CSS (ComponentDiagram's port and wire
 * palette) switches to its own palette for that theme.
 */
public enum SupervisorTheme {

	/** The light theme, the default. */
	LIGHT,
	/** The dark theme. */
	DARK;
}
