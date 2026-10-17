/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.complexcycle.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.tests.unit.complexcycle.BServices;
import org.opales.covalent.container.tests.unit.complexcycle.SiblingType;

/**
 * Independent component that also depends on {@link BServices}. Deployed and
 * traversed after {@code AImpl}'s branch has already fully processed B: a
 * correct three-color DFS must treat this as an ordinary revisit of a
 * finished (black) node, not as a second cycle.
 */
@Implementation
public class SiblingImpl implements SiblingType {

	@UsedServices
	private BServices useBServices;

}
