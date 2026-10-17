/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.composite.composedc.subc1.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.tests.unit.composite.composedc.subc1.SubC1;
import org.opales.covalent.container.tests.unit.composite.composedc.subc1.SubC1Events;
import org.opales.covalent.container.tests.unit.composite.composedc.subc1.SubC1Events.SubC1EventsSubscribe;
import org.opales.covalent.container.tests.unit.composite.composedc.subc1.SubC1Services;

@Implementation
public class SubC1Impl implements SubC1, SubC1Services, SubC1EventsSubscribe {

	public boolean called;

	public boolean delegated;

	@ProducedEvents
	private SubC1Events produceSubC1Events;

	@Override
	public void subC1Service(final String message) {
		this.called = true;
	}

	@Override
	public void produceEvent(final String message) {
		this.produceSubC1Events.onEvent(message);
	}

	@Override
	public void subscribe(final SubC1Events observer) {
		this.delegated = true;
	}

	@Override
	public void unsubscribe(final SubC1Events observer) {

	}

	@Override
	public boolean isDelegated() {
		return this.delegated;
	}

}
