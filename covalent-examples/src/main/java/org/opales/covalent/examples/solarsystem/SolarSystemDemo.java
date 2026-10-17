/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the whole Solar System Simulation demo: a single
 * top-level composite grouping every function of the application (the
 * orbital system, the simulation runner, the orbital watch, the hazard
 * alert, and the user configuration front-end) together with the HMI
 * assembly, under one {@code @Implementation}.
 * <p>
 * {@code SolarSystemDemoImpl} groups the whole application together for
 * deployment.
 */
@Component
public interface SolarSystemDemo {

}
