/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.simulationrunner.impl;

import org.opales.covalent.examples.ecosystem.simulationrunner.SimulationRunner;
import org.opales.covalent.examples.ecosystem.simulationrunner.controller.impl.RunnerControllerImpl;
import org.opales.covalent.examples.ecosystem.simulationrunner.interaction.impl.RunnerInteractionImpl;
import org.opales.covalent.examples.ecosystem.simulationrunner.model.impl.RunnerModelImpl;
import org.opales.covalent.examples.ecosystem.simulationrunner.view.impl.RunnerViewImpl;

import org.opales.covalent.container.component.annotations.Implementation;

/**
 * The {@code SimulationRunner} composite: deploys its four MVIC parts
 * together. Only this class needs to be passed to
 * {@code container.deployComponents(...)}.
 */
@Implementation
public class SimulationRunnerImpl implements SimulationRunner {

	@Implementation
	private RunnerModelImpl model;

	@Implementation
	private RunnerControllerImpl controller;

	@Implementation
	private RunnerViewImpl view;

	@Implementation
	private RunnerInteractionImpl interaction;
}
