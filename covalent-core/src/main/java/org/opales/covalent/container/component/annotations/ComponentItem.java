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
 * Alternative to {@link Item} for the field, in the owning component
 * implementation, that aggregates a component item instance; the item
 * class declaration itself is always annotated with {@code @Item}. Using
 * two distinct annotations - {@code @Item} on the class,
 * {@code @ComponentItem} on the field - makes the distinction between
 * "implementing an item" and "aggregating an item" explicit in the source;
 * using {@code @Item} for both is equally valid and more common.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ComponentItem {

}
