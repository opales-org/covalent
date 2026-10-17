/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.stereotypepolicy;

/**
 * What a component stands for when no rule of the stereotype table matches its name: what the
 * metamodel says about it. Like every stereotype, they are shown as they are, in any language.
 */
public final class MetamodelStereotypes {

	/** One of Covalent's own components. */
	public static final String INTERNAL_COMPONENT = "Internal";

	/** An interface that is not a component. */
	public static final String INTERFACE = "Interface";

	/** A component the container does not manage. */
	public static final String RAW_COMPONENT = "Raw";

	/** A component made of no other. */
	public static final String COMPONENT = "Component";

	/** A component made of others. */
	public static final String COMPOSITE = "Composite";

	private MetamodelStereotypes() {
	}
}
