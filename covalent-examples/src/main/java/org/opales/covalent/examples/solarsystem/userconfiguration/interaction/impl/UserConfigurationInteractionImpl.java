/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.userconfiguration.interaction.impl;

import org.opales.covalent.examples.solarsystem.hazardalert.controller.HazardParameters;
import org.opales.covalent.examples.solarsystem.orbitalsystem.controller.OrbitalParameters;
import org.opales.covalent.examples.solarsystem.simulationrunner.controller.RunnerParameters;
import org.opales.covalent.examples.solarsystem.userconfiguration.interaction.UserConfigurationActionKey;
import org.opales.covalent.examples.solarsystem.userconfiguration.interaction.UserConfigurationInteraction;
import org.opales.covalent.examples.solarsystem.userconfiguration.view.UserConfigurationViewServices;

import org.opales.covalent.container.component.annotations.AccessedParameters;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.hmi.action.ActionProviderServices;

/**
 * The configuration composite's Interaction Controller: registers one
 * {@code ActionManager} action per user-editable parameter, and applies
 * each one directly to whichever function's Parameters interface actually
 * owns it: {@link OrbitalParameters} for the gravitational constant,
 * {@link RunnerParameters} for the time scale, {@link HazardParameters} for
 * the hazard thresholds.
 */
@Implementation
public class UserConfigurationInteractionImpl implements UserConfigurationInteraction {

	@UsedServices
	private ActionProviderServices actionManagerServices;

	@UsedServices
	private UserConfigurationViewServices useUserConfigurationViewServices;

	@AccessedParameters
	private OrbitalParameters accessOrbitalParameters;

	@AccessedParameters
	private RunnerParameters accessRunnerParameters;

	@AccessedParameters
	private HazardParameters accessHazardParameters;

	/** Registers one action per parameter, then pushes the current parameter values to the view. */
	@Activation
	public void registerActions() {
		this.actionManagerServices.registerActionExecution(
				UserConfigurationActionKey.SetGravitationalConstant, this::setGravitationalConstant, Double.class);
		this.actionManagerServices.registerActionExecution(
				UserConfigurationActionKey.SetTimeScale, this::setTimeScale, Double.class);
		this.actionManagerServices.registerActionExecution(
				UserConfigurationActionKey.SetDistanceThreshold, this::setDistanceThreshold, Double.class);
		this.actionManagerServices.registerActionExecution(
				UserConfigurationActionKey.SetSizeThreshold, this::setSizeThreshold, Double.class);

		// Push the current values so the view has something to display right away.
		this.useUserConfigurationViewServices
				.displayGravitationalConstant(this.accessOrbitalParameters.getGravitationalConstant());
		this.useUserConfigurationViewServices.displayTimeScale(this.accessRunnerParameters.getTimeScale());
		this.useUserConfigurationViewServices
				.displayDistanceThreshold(this.accessHazardParameters.getDistanceThreshold());
		this.useUserConfigurationViewServices.displaySizeThreshold(this.accessHazardParameters.getSizeThreshold());
	}

	/** Unregisters the actions registered at activation. */
	@Passivation
	public void unregisterActions() {
		this.safeUnregister(UserConfigurationActionKey.SetGravitationalConstant);
		this.safeUnregister(UserConfigurationActionKey.SetTimeScale);
		this.safeUnregister(UserConfigurationActionKey.SetDistanceThreshold);
		this.safeUnregister(UserConfigurationActionKey.SetSizeThreshold);
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

	private void setGravitationalConstant(final Double gravitationalConstant) {
		this.accessOrbitalParameters.setGravitationalConstant(gravitationalConstant);
	}

	private void setTimeScale(final Double timeScale) {
		this.accessRunnerParameters.setTimeScale(timeScale);
	}

	private void setDistanceThreshold(final Double distanceThreshold) {
		this.accessHazardParameters.setDistanceThreshold(distanceThreshold);
	}

	private void setSizeThreshold(final Double sizeThreshold) {
		this.accessHazardParameters.setSizeThreshold(sizeThreshold);
	}
}
