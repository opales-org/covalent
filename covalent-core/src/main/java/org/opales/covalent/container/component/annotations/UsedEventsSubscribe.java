/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.component.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a field as a manual event-subscription port: the type is
 * usually an {@link EventsSubscribe}-annotated interface nested in the
 * producer's events interface, or {@link
 * org.opales.covalent.container.events.GenericSubscribe} when the producer
 * does not provide a specific subscribe interface and the consumer consumes
 * only one kind of events. The container injects a proxy into the field;
 * calling {@code subscribe}/{@code unsubscribe} on it is intercepted by
 * Covalent, which manages the actual subscription.
 * <p>
 * Manual subscription is only needed for explicit control over when a
 * consumer receives events (partial-lifecycle consumption, subscribing an
 * owned object instead of the component itself, or repeated
 * subscribe/unsubscribe). By default, Covalent automatically subscribes an
 * event consumer to the producers of the events it consumes during
 * startup, with nothing to declare.
 *
 * <pre>{@code
 * @UsedEventsSubscribe
 * private MyEventsSubscribe useMyEventsSubscribe;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface UsedEventsSubscribe {

}
