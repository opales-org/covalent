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
 * Declares an events interface, or a produced-events field.
 * <p>
 * On a type, marks the interface as an events contract: a fire-and-forget
 * notification, where every method returns {@code void} and must not
 * declare exceptions, dispatched asynchronously by default to every
 * subscribed consumer. A component that implements the interface becomes an
 * events consumer.
 * <p>
 * On a field, declares that the owning component produces those events; the
 * container injects a dispatch proxy into the field, and calling a method
 * on it fires the event to every current subscriber. {@link ProducedEvents}
 * is an equivalent, more specific annotation for that field usage.
 *
 * <pre>{@code
 * @Events
 * public interface MyEvents {
 *     void onSomethingChanged(Object someEventAttribute);
 * }
 * }</pre>
 *
 * @see EventsSubscribe
 * @see EventsFilter
 * @see Cloned
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.FIELD })
public @interface Events {

}
