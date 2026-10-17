/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed.impl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.hmi.action.ActionEvents;
import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.hmi.action.ActionObservationServices;
import org.opales.covalent.supervision.testsupport.observed.StubActionObservationComponent;

@Implementation
public class StubActionObservationComponentImpl implements StubActionObservationComponent, ActionObservationServices {

	private final List<ActionEvents> listeners = new CopyOnWriteArrayList<>();

	@Override
	public void addActionEventsListener(final ActionEvents actionEventsSubscribe) {
		this.listeners.add(actionEventsSubscribe);
	}

	@Override
	public void removeActionEventsListener(final ActionEvents actionEventsSubscribe) {
		this.listeners.remove(actionEventsSubscribe);
	}

	public int listenerCount() {
		return this.listeners.size();
	}

	public void fireActionTriggered(final ActionKey actionKey, final Class<?> actionCaller,
			final Class<?> actionImplementer, final Object... inputData) {
		for (ActionEvents listener : this.listeners) {
			listener.onActionTriggered(actionKey, actionCaller, actionImplementer, inputData);
		}
	}
}
