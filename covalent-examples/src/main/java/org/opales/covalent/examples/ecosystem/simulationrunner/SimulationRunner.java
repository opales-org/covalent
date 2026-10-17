/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.simulationrunner;

import org.opales.covalent.examples.ecosystem.ecosystem.controller.EcosystemServices;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the simulation pacing MVIC (Model/Controller/
 * Interaction/View). Drives simulation time at a controllable pace by
 * calling {@link EcosystemServices} repeatedly - the same role
 * {@code TrainingMonitor} plays for {@code NetworkServices} in the neural
 * network demo.
 */
@Component
public interface SimulationRunner {

}
