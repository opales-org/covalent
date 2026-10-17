/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed;

import org.opales.covalent.container.component.annotations.Component;

/**
 * A composite component type, deployed in the observed container, composing
 * {@code LeafAProviderComponent} (provides {@link SampleServicesA}) and
 * {@code LeafAUserComponent} (uses both {@link SampleServicesA} - a same-composite
 * dependency - and {@link SampleServicesB}, which crosses into {@link CompositeBComponent}).
 */
@Component
public interface CompositeAComponent {

}
