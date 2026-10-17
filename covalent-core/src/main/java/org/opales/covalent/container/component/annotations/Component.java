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
 * Declares a component type. A component type is an empty tag interface
 * identifying a mono-instantiated Covalent component: at runtime, there can
 * be only one instance of a given component type.
 * <p>
 * The implementation class for this component type is declared separately,
 * with {@link Implementation}.
 *
 * <pre>{@code
 * @Component
 * interface MyComponentType { }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Component {

}
