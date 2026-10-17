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
 * Declares a field as an accessed-parameters port: the owning component
 * becomes a parameters accessor, and the container injects the offering
 * component's implementation into the field during startup. {@link
 * Parameters} can be used on the same field instead, when a project does
 * not need to distinguish "offers" from "accesses" in its annotation
 * vocabulary.
 *
 * <pre>{@code
 * @AccessedParameters // or @Parameters
 * private MyParameters accessMyParameters;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface AccessedParameters {

}
