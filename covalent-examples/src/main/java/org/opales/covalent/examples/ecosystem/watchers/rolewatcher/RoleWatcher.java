/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.rolewatcher;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the role-partitioned watcher MVIC: one Implementation
 * subscribing to PopulationEvents twice, with two fixed filter instances
 * (prey, predator), logging each role's births and deaths into its own
 * {@code @Item}.
 */
@Component
public interface RoleWatcher {
}
