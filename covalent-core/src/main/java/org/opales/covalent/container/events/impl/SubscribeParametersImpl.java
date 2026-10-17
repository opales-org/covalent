/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.events.impl;

import org.opales.covalent.container.events.SubscribeParameters;

/**
 * Default {@link SubscribeParameters} implementation: a simple holder for
 * the optional event filter object passed when subscribing to an events
 * interface.
 */
public class SubscribeParametersImpl implements SubscribeParameters {

	/** The optional event filter object, or {@code null} if none was set. */
	private Object eventFilter = null;

	/** Creates a new instance with no event filter set. */
	public SubscribeParametersImpl() {
	}

	@Override
	public <EVENT_FILTER> void setEventFilter(EVENT_FILTER anEventFilter) {
		this.eventFilter = anEventFilter;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <EVENT_FILTER> EVENT_FILTER getEventFilter() {
		return (EVENT_FILTER) this.eventFilter;
	}

}
