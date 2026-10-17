/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.composite.composedb.subb1.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.tests.unit.composite.composeda.suba1.SubA1Services;
import org.opales.covalent.container.tests.unit.composite.composedb.subb1.SubB1;
import org.opales.covalent.container.tests.unit.composite.composedb.subb1.SubB1Services;

@Implementation
public class SubB1Impl implements SubB1, SubB1Services {

	@UsedServices
	private SubA1Services subA1Services;

	public boolean called;

	@Override
	public void subB1Service(final String message) {
		this.called = true;
		this.subA1Services.subA1Service(message);
	}

}
