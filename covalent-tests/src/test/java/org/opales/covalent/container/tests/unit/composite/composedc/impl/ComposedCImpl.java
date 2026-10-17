/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.composite.composedc.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.tests.unit.composite.composedc.ComposedC;
import org.opales.covalent.container.tests.unit.composite.composedc.subc1.SubC1Events;
import org.opales.covalent.container.tests.unit.composite.composedc.subc1.impl.SubC1Impl;
import org.opales.covalent.container.tests.unit.composite.composedc.subc2.impl.SubC2Impl;
import org.opales.covalent.container.tests.unit.composite.composedc.subc3.impl.SubC3Impl;

@Implementation
public class ComposedCImpl implements ComposedC, SubC1Events {

	@Implementation
	private SubC1Impl subC1;

	@Implementation
	private SubC2Impl subC2;

	@Implementation
	private SubC3Impl subC3;

	public boolean eventReceived;

	@Override
	public void onEvent(final String message) {
		this.eventReceived = true;
	}

}
