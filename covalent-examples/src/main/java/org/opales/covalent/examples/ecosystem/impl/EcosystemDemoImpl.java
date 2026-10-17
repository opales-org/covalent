/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.examples.ecosystem.EcosystemDemo;
import org.opales.covalent.examples.ecosystem.ecosystem.impl.EcosystemImpl;
import org.opales.covalent.examples.ecosystem.hmibuilder.impl.HmiBuilderImpl;
import org.opales.covalent.examples.ecosystem.simulationrunner.impl.SimulationRunnerImpl;
import org.opales.covalent.examples.ecosystem.userconfiguration.impl.UserConfigurationImpl;
import org.opales.covalent.examples.ecosystem.watchers.impl.WatchersImpl;

import org.opales.covalent.hmi.impl.HmiCoreImpl;

/**
 * The EcosystemDemo composite: deploys the whole application as a single
 * {@code @Implementation}. Only this class needs to be passed to
 * container.deployComponents(...), which {@code EcosystemStatsExportDemoLauncher}
 * does after it has registered the raw stats export widget the HmiBuilder
 * needs.
 * <p>
 * Each field is itself {@code @Implementation}-annotated, in the exact order their
 * activation must proceed: HmiCoreImpl first (so ActionManagerImpl,
 * composed inside it, is ready before any function registers actions
 * against it), then Ecosystem (owns the species state and the tau-leaping
 * step), SimulationRunner (paces it), Watchers (the three filtered
 * subscribers), UserConfiguration, and finally HmiBuilder (which reads
 * every other function's *ViewParameters to assemble the final Stage, so
 * it must activate last).
 */
@Implementation
public class EcosystemDemoImpl implements EcosystemDemo {

    @Implementation
    private HmiCoreImpl hmiCore;

    @Implementation
    private EcosystemImpl ecosystem;

    @Implementation
    private SimulationRunnerImpl simulationRunner;

    @Implementation
    private WatchersImpl watchers;

    @Implementation
    private UserConfigurationImpl userConfiguration;

    @Implementation
    private HmiBuilderImpl hmiBuilder;
}
