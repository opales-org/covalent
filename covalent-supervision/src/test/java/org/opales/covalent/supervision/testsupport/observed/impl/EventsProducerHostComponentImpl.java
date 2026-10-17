/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed.impl;

import org.opales.covalent.container.component.annotations.ComposedComponent;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.supervision.testsupport.observed.EventsProducerHostComponent;

/** The composite, holding FlatEventsProducerComponentImpl. */
@Implementation
public class EventsProducerHostComponentImpl implements EventsProducerHostComponent {

	@ComposedComponent
	private final FlatEventsProducerComponentImpl inner = null;
}
