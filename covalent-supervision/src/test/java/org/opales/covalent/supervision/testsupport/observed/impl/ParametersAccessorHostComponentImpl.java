/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed.impl;

import org.opales.covalent.container.component.annotations.ComposedComponent;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.supervision.testsupport.observed.ParametersAccessorHostComponent;

/** The composite, holding FlatParametersAccessorComponentImpl. */
@Implementation
public class ParametersAccessorHostComponentImpl implements ParametersAccessorHostComponent {

	@ComposedComponent
	private final FlatParametersAccessorComponentImpl inner = null;
}
