/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.events;

import org.opales.covalent.container.component.annotations.EventsSubscribe;

/**
 * Fallback subscribe interface used when an events producer declares no
 * {@code @EventsSubscribe}-annotated interface of its own and the consumer
 * consumes exactly one kind of events. It accepts any object as the
 * subscriber, with an optional {@link SubscribeParameters} to customize the
 * subscription.
 */
@EventsSubscribe
public interface GenericSubscribe {

	/**
	 * Subscribes the given object to receive events, using default
	 * subscription parameters.
	 *
	 * @param object the subscriber to register
	 */
	void subscribe(Object object);

	/**
	 * Subscribes the given object to receive events, using the given
	 * subscription parameters.
	 *
	 * @param object              the subscriber to register
	 * @param subscribeParameters the parameters customizing the subscription
	 */
	void subscribe(Object object, SubscribeParameters subscribeParameters);

	/**
	 * Unsubscribes the given object, which then receives no more events.
	 *
	 * @param object the subscriber to unregister
	 */
	void unsubscribe(Object object);
}
