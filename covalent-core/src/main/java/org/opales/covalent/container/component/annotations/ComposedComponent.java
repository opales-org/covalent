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
 * Alternative to {@link Implementation} for the field that owns a
 * sub-component in a composite implementation. The sub-component's own
 * implementation class is still annotated with {@code @Implementation}
 * either way; using {@code @ComposedComponent} on the field only makes the
 * "this field is a composed component" intent explicit, where using
 * {@code @Implementation} on both the class and the field is equally valid
 * and more common.
 *
 * <pre>{@code
 * @Implementation
 * public class BillingCompositeImpl implements BillingComposite {
 *     @ComposedComponent // or @Implementation
 *     private InvoiceComponentImpl invoiceComponent;
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ComposedComponent {

}
