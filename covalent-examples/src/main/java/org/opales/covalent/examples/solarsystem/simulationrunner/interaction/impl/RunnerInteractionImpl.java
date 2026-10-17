/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.simulationrunner.interaction.impl;

import org.opales.covalent.examples.solarsystem.simulationrunner.controller.RunnerControllerServices;
import org.opales.covalent.examples.solarsystem.simulationrunner.controller.RunnerParameters;
import org.opales.covalent.examples.solarsystem.simulationrunner.interaction.RunnerActionKey;
import org.opales.covalent.examples.solarsystem.simulationrunner.interaction.RunnerInteraction;
import org.opales.covalent.examples.solarsystem.simulationrunner.view.RunnerViewServices;

import org.opales.covalent.container.component.annotations.AccessedParameters;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.hmi.action.ActionProviderServices;

/**
 * The simulation run's Interaction Controller: registers its actions with the
 * HMI's {@code ActionManager} at activation - the view's
 * start/pause/step/reset buttons and time-scale slider trigger them
 * through {@code ActionConsumerServices.executeAction(...)}, not by calling
 * this class directly. Applies the time scale directly through
 * {@link RunnerParameters} - the same pattern
 * {@code TrainingInteractionImpl} uses for batch size / pace.
 */
@Implementation
public class RunnerInteractionImpl implements RunnerInteraction {

	@UsedServices
	private ActionProviderServices actionManagerServices;

	@UsedServices
	private RunnerControllerServices useRunnerControllerServices;

	@UsedServices
	private RunnerViewServices useRunnerViewServices;

	@AccessedParameters
	private RunnerParameters accessRunnerParameters;

	/** Registers the start, pause, step, reset and time-scale actions at activation. */
	@Activation
	public void registerActions() {
		this.actionManagerServices.registerActionExecution(RunnerActionKey.Start, this::start);
		this.actionManagerServices.registerActionExecution(RunnerActionKey.Pause, this::pause);
		this.actionManagerServices.registerActionExecution(RunnerActionKey.Step, this::step);
		this.actionManagerServices.registerActionExecution(RunnerActionKey.Reset, this::reset);
		this.actionManagerServices.registerActionExecution(
				RunnerActionKey.SetTimeScale, this::setTimeScale, Double.class);
	}

	/** Unregisters the actions registered at activation. */
	@Passivation
	public void unregisterActions() {
		this.safeUnregister(RunnerActionKey.Start);
		this.safeUnregister(RunnerActionKey.Pause);
		this.safeUnregister(RunnerActionKey.Step);
		this.safeUnregister(RunnerActionKey.Reset);
		this.safeUnregister(RunnerActionKey.SetTimeScale);
	}

	/**
	 * Unregisters one action, tolerating the {@code ActionProviderServices}
	 * proxy already being invalidated, which happens when the container stop
	 * has already passivated the action manager.
	 *
	 * @param actionKey the action identifier to unregister
	 */
	private void safeUnregister(final ActionKey actionKey) {
		try {
			this.actionManagerServices.unregisterActionExecution(actionKey);
		} catch (IllegalStateException e) {
			// ActionManager already passivated as part of the same
			// container-stop cascade.
		}
	}

	private void start() {
		this.useRunnerControllerServices.start();
	}

	private void pause() {
		this.useRunnerControllerServices.pause();
	}

	private void step() {
		this.useRunnerControllerServices.step();
		this.useRunnerViewServices.pulseStep();
	}

	private void reset() {
		this.useRunnerControllerServices.reset();
	}

	private void setTimeScale(final Double timeScale) {
		this.accessRunnerParameters.setTimeScale(timeScale);
	}
}
