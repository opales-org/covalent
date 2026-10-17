/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.internal.diagram.cells;

/** Role that a port plays for its component, which decides the shape and color it is drawn with. */
public enum ComponentPortRole {
	COMPONENT_TYPE,
	PROVIDED,
	USED,
	PRODUCED,
	CONSUMED,
	OFFERED,
	ACCESSED
}
