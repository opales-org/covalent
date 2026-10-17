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
 * Declares a subscribe interface for manual event subscription. It must be
 * nested inside the corresponding {@link Events}-annotated events interface,
 * and the first parameter of each of its subscribe and unsubscribe methods
 * (the observer) must be that events interface.
 * Subscribe methods must be prefixed with {@code subscribe}, and unsubscribe
 * methods must be prefixed with {@code unsubscribe}; Covalent intercepts
 * these calls and manages the actual subscription. A subscribe method can
 * optionally accept an {@link EventsFilter}-annotated filter, or a
 * {@link org.opales.covalent.container.events.SubscribeParameters}, as its
 * second argument; either is only recognized as the second argument of a
 * two-argument call. Any other argument is free, including more than two and
 * primitive ones: it reaches the subscribe methods of a producer implementing
 * this interface unchanged, and does not change which events are delivered.
 * <p>
 * A subscription is identified by its observer and its other arguments,
 * compared with {@code equals}: an unsubscribe method must be called with the
 * same arguments as the subscribe call it ends, so this interface must declare
 * an unsubscribe method taking them. A producer implementing this interface
 * receives the very call the consumer made, and the same call again when it is
 * activated after a passivation.
 * <p>
 * A consumer that needs manual subscription control declares a field of
 * this interface type and annotates it with {@link UsedEventsSubscribe}.
 *
 * <pre>{@code
 * @Events
 * public interface MyEvents {
 *     void onEvent(String message);
 *
 *     @EventsSubscribe
 *     interface MyEventsSubscribe {
 *         void subscribe(MyEvents observer);
 *         void unsubscribe(MyEvents observer);
 *     }
 * }
 * }</pre>
 *
 * @see org.opales.covalent.container.events.GenericSubscribe
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EventsSubscribe {

}
