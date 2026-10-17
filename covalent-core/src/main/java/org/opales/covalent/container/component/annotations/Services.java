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
 * Declares a services interface, or a used-services field.
 * <p>
 * On a type, marks the interface as a services contract: a request-response
 * interaction where the caller depends on the interface, not on a concrete
 * provider. A component that implements the interface becomes a services
 * provider.
 * <p>
 * On a field, declares that the owning component uses that service; the
 * container injects the provider's implementation into the field during
 * startup. {@link UsedServices} is an equivalent, more specific annotation
 * for that field usage.
 *
 * <pre>{@code
 * @Services
 * public interface MyServices {
 *     void doSomething(Data someData);
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.FIELD })
public @interface Services {

}
