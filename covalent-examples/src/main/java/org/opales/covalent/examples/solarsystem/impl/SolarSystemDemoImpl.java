/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.impl;

import org.opales.covalent.examples.solarsystem.SolarSystemDemo;
import org.opales.covalent.examples.solarsystem.hazardalert.impl.HazardAlertImpl;
import org.opales.covalent.examples.solarsystem.hmibuilder.impl.HmiBuilderImpl;
import org.opales.covalent.examples.solarsystem.orbitalsystem.impl.OrbitalSystemImpl;
import org.opales.covalent.examples.solarsystem.orbitalwatch.impl.OrbitalWatchImpl;
import org.opales.covalent.examples.solarsystem.simulationrunner.impl.SimulationRunnerImpl;
import org.opales.covalent.examples.solarsystem.userconfiguration.impl.UserConfigurationImpl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.hmi.impl.HmiCoreImpl;

/**
 * The {@code SolarSystemDemo} composite: deploys the whole application as a
 * single {@code @Implementation}. Only this class needs to be passed to
 * {@code container.deployComponents(...)}.
 * <p>
 * Each field is itself {@code @Implementation}-annotated and typed to a
 * concrete implementation class, one per function of the application, in
 * the exact order their activation must proceed: {@code HmiCoreImpl} first
 * (so {@code ActionManagerImpl}, composed inside it, is ready before any
 * function registers actions against it), then each function's own
 * composite ({@link OrbitalSystemImpl}, {@link SimulationRunnerImpl},
 * {@link OrbitalWatchImpl}, {@link HazardAlertImpl},
 * {@link UserConfigurationImpl}), and finally {@link HmiBuilderImpl} (which
 * reads every other function's {@code *ViewParameters} to assemble the
 * final {@code Stage}, so it must activate last) - the same composite
 * shape as {@code NeuralNetworkDemoImpl}. Activation/passivation both
 * proceed children-before-parent, in field-declaration order.
 */
@Implementation
public class SolarSystemDemoImpl implements SolarSystemDemo {

	@Implementation
	private HmiCoreImpl hmiCore;

	@Implementation
	private OrbitalSystemImpl orbitalSystem;

	@Implementation
	private SimulationRunnerImpl simulationRunner;

	@Implementation
	private OrbitalWatchImpl orbitalWatch;

	@Implementation
	private HazardAlertImpl hazardAlert;

	@Implementation
	private UserConfigurationImpl userConfiguration;

	@Implementation
	private HmiBuilderImpl hmiBuilder;
}
