/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.model;

/**
 * The kind of a component's port in the topology graph: a nesting
 * (composition) relationship, or one of the three autowiring kinds. Mirrors
 * {@code componentview.internal.diagram.cells.ComponentPortKind}, kept
 * JavaFX-free here.
 */
public enum ComponentPortKind {
	/** Composition: a component nested inside another one. */
	COMPONENT_TYPE,
	/** A services interface, provided by one component and used by another. */
	SERVICE,
	/** An events interface, produced by one component and consumed by another. */
	EVENT,
	/** A parameters interface, offered by one component and accessed by another. */
	PARAMETER
}
