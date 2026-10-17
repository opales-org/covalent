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
 * Declares a field as a used-services port: the owning component becomes a
 * services user, and the container injects the provider's implementation
 * into the field during startup. {@link Services} can be used on the same
 * field instead, when a project does not need to distinguish "provides" from
 * "uses" in its annotation vocabulary.
 *
 * <pre>{@code
 * @UsedServices // or @Services
 * private MyServices useMyServices;
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface UsedServices {

}
