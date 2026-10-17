/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.dependencymanagement.model;

/** What a component of the dependency graph is, which decides how it is drawn. */
public enum ComponentKind {

	/** A component made of no other. */
	COMPONENT,

	/** A component made of others. */
	COMPOSITE,

	/** A component the container does not manage. */
	RAW
}
