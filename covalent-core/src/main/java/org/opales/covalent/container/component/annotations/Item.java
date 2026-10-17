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
 * Declares a component item class, or the item field on the owning
 * component implementation.
 * <p>
 * An item is an internal part of a segmented component: it is not an
 * independent component, but it follows its owning component's lifecycle
 * and can carry part of that component's ports. Items are instantiated (or,
 * if the field is already initialized, adopted) when the owning component
 * is instantiated.
 * <p>
 * {@link ComponentItem} is an alternative annotation for the field, in the
 * owning implementation, that aggregates the item instance, for projects
 * that prefer to distinguish "aggregates an item instance" from
 * "implements the item class" in their source; the item class declaration
 * itself is always annotated with {@code @Item}. Component items are not
 * allowed in raw components.
 *
 * <pre>{@code
 * @Implementation
 * public class MyComponentImpl implements MyComponentType {
 *     @ComponentItem
 *     private MyItem myItem;
 * }
 *
 * @Item
 * public class MyItem {
 *     // ...
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.FIELD })
public @interface Item {

}
