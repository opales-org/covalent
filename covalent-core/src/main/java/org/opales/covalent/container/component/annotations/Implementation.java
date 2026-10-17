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
 * Declares a component or sub-component implementation class, or a
 * sub-component field.
 * <p>
 * On a class, identifies it as the implementation of a {@link Component}
 * type: the class must implement the component type interface, and must be
 * {@code public} with a {@code public} no-argument constructor.
 * <p>
 * On a field of a composite implementation, declares a sub-component: an
 * independent component, with its own type, ports, and lifecycle state,
 * owned by the composite. {@link ComposedComponent} is an alternative,
 * equivalent annotation for that same field usage, for projects that prefer
 * to distinguish "sub-component field" from "implementation class" in their
 * source.
 *
 * <pre>{@code
 * @Implementation
 * public class MyComponentImpl implements MyComponentType {
 *     // ...
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.FIELD })
public @interface Implementation {

}
