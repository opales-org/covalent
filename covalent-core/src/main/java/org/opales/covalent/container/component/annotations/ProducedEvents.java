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
 * Declares a field as a produced-events port: the owning component becomes
 * an events producer. Calling a method on the field fires that event to
 * every currently subscribed consumer. {@link Events} can be used on the
 * same field instead, when a project does not need to distinguish
 * "produces" from "declares" in its annotation vocabulary.
 *
 * <pre>{@code
 * @ProducedEvents // or @Events
 * private MyEvents produceMyEvents;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ProducedEvents {

}
