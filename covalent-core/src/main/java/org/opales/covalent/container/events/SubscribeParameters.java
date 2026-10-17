/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.events;

/**
 * Carries options used when subscribing a consumer to an events interface.
 */
public interface SubscribeParameters {

	/**
	 * Sets the event filter object used by the subscription.
	 *
	 * @param <EVENT_FILTER> event filter type
	 * @param anEventFilter  event filter instance
	 */
	<EVENT_FILTER> void setEventFilter(EVENT_FILTER anEventFilter);

	/**
	 * Returns the event filter object used by the subscription.
	 *
	 * @param <EVENT_FILTER> event filter type
	 * @return event filter instance
	 */
	<EVENT_FILTER> EVENT_FILTER getEventFilter();
}
