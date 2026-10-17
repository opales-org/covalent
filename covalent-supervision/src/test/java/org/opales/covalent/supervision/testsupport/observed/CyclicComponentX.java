/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed;

import org.opales.covalent.container.component.annotations.Component;

/** Provides {@link SampleServicesA}, uses {@link SampleServicesB} - forms a 2-node circular
 * dependency with {@link CyclicComponentY} (which provides B and uses A) when both are deployed
 * flat (not nested in a composite) at the top level, for {@code DependencyGraph}'s own
 * cycle-detection to find. */
@Component
public interface CyclicComponentX {

}
