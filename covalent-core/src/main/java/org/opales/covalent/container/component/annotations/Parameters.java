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
 * Declares a parameters interface, or an accessed-parameters field.
 * <p>
 * On a type, marks the interface as a parameters contract: a
 * request-response interaction used to configure the component that offers
 * it, connected earlier in the lifecycle than services so a component can
 * access parameters during initialization, before it is activated. A
 * component that implements the interface offers those parameters.
 * <p>
 * On a field, declares that the owning component accesses that
 * configuration; the container injects the offering component's
 * implementation into the field during startup. {@link AccessedParameters}
 * is an equivalent, more specific annotation for that field usage.
 *
 * <pre>{@code
 * @Parameters
 * public interface MyParameters {
 *     void setParam(Param parameter);
 *     Param getParam();
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.FIELD })
public @interface Parameters {

}
