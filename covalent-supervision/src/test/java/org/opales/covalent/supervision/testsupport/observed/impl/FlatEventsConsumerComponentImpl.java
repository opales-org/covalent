/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.supervision.testsupport.observed.FlatEventsConsumerComponent;
import org.opales.covalent.supervision.testsupport.observed.SampleEvents;

/**
 * Implements {@link SampleEvents} directly - the same auto-subscribe idiom production code
 * uses throughout (e.g. {@code ComponentDiagramControllerImpl implements ... ObservedRegistryEvents}) -
 * so it becomes an autowired consumer with no separate {@code @UsedEventsSubscribe} field needed;
 * that annotation is for injecting a subscribe/unsubscribe delegate to manage SOMEONE ELSE's
 * subscription, not for this component's own passive consumption.
 */
@Implementation
public class FlatEventsConsumerComponentImpl implements FlatEventsConsumerComponent, SampleEvents {

	public boolean called;

	@Override
	public void fired() {
		this.called = true;
	}
}
