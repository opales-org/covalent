/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.supervision.testsupport.observed.FlatEventsProducerComponent;
import org.opales.covalent.supervision.testsupport.observed.SampleEvents;

@Implementation
public class FlatEventsProducerComponentImpl implements FlatEventsProducerComponent {

	@ProducedEvents
	private SampleEvents sampleEvents;

	void fire() {
		this.sampleEvents.fired();
	}
}
