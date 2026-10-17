/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.annotatedcomponents.duplicatecomposedtype.impl;

import org.opales.covalent.container.component.annotations.Component;

/**
 * A plain composed-component type meant to be held by two independent
 * {@code @ComposedComponent} fields of the same owning component - see
 * DuplicateComposedTypeComponentImpl, whose two fields (firstCounter,
 * secondCounter) are both declared with this exact same implementation
 * type.
 */
@Component
public interface CounterComponent {

}
