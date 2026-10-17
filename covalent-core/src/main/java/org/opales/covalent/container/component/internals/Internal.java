/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.component.internals;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a component type as one of Covalent's own components, as opposed to
 * a component of an application built on top of it. Internal components are
 * active from the container's construction to its disposal: unlike the
 * application components, they are kept across
 * {@link org.opales.covalent.container.CovalentContainer#stop() stop()}.
 * <p>
 * Reserved to Covalent's own types: an application component carrying it is
 * refused, whether deployed or registered as a raw component, and no raw port
 * can be opened on behalf of an internal component. The managed container
 * relay, a raw component registered by {@link org.opales.covalent.Covalent}
 * on each managed container, is the only raw component carrying it.
 * <p>
 * It does not by itself mean that a Java type, or its ports, are outside the
 * public API.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Internal {

}
