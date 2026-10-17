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
 * Declares a filter interface for one events contract, nested inside the
 * corresponding {@link Events}-annotated events interface. A filter is
 * evaluated for one subscriber and decides whether a produced event should
 * actually be delivered to it; the event is delivered only when the filter
 * method returns {@code false}.
 * <p>
 * By convention, the filter interface name is the events interface name
 * suffixed with {@code Filter} - mandatory for managed components. Each
 * filter method must match one event method: its name is the event method
 * name with {@code on} replaced by {@code filter}, with the exact same
 * arguments. Using a filter requires explicit (manual) subscription through
 * a subscribe method whose second argument is the filter type; automatic
 * subscription cannot pass a filter instance.
 *
 * <pre>{@code
 * @Events
 * public interface MyEvents {
 *     void onEvent(Data data);
 *
 *     @EventsSubscribe
 *     interface MyEventsSubscribe {
 *         void subscribe(MyEvents observer);
 *         void subscribe(MyEvents observer, MyEventsFilter filter);
 *         void unsubscribe(MyEvents observer);
 *     }
 *
 *     @EventsFilter
 *     interface MyEventsFilter {
 *         boolean filterEvent(Data data);
 *     }
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EventsFilter {

}
