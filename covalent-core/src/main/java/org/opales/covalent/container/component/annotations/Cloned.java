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
 * Requests automatic cloning of event arguments before they are delivered
 * to consumers, protecting the fire-and-forget model by preventing a
 * consumer from being able to modify objects it received from the producer.
 * Cloning only works for arguments whose runtime type implements the JDK
 * {@link Cloneable} interface and exposes a usable clone operation; if
 * cloning fails, dispatch still proceeds unless dynamic rule verification is
 * set to its strictest level. Arrays are cloned as well; like the clone of
 * any Java array, the copy is shallow, so the elements of a received array
 * are shared with the producer's array.
 * <p>
 * Can be applied at three levels: on an {@link Events}-annotated interface,
 * to clone every argument of every event method; on an event method, to
 * clone every argument of that method; or on an individual event method
 * argument, to clone only that argument.
 *
 * <pre>{@code
 * @Events
 * public interface MyEvents {
 *     void onEvents(@Cloned CloneableData data);
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.METHOD, ElementType.PARAMETER })
public @interface Cloned {

}
