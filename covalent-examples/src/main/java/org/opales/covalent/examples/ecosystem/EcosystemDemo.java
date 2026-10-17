/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the whole Ecosystem demo: a stochastic tau-leaping
 * generalized Lotka-Volterra multi-species simulation (resource, prey and
 * predator roles), watched by three independently filtered subscribers.
 */
@Component
public interface EcosystemDemo {
}
