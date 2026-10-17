/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalsystem.interaction.impl;

import org.opales.covalent.examples.solarsystem.orbitalsystem.controller.BodyDetails;
import org.opales.covalent.examples.solarsystem.orbitalsystem.controller.OrbitalControllerServices;
import org.opales.covalent.examples.solarsystem.orbitalsystem.interaction.OrbitalActionKey;
import org.opales.covalent.examples.solarsystem.orbitalsystem.interaction.OrbitalInteraction;
import org.opales.covalent.examples.solarsystem.orbitalsystem.view.OrbitalViewServices;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.hmi.action.ActionProviderServices;

/**
 * The orbital system's Interaction Controller: registers its actions with the
 * HMI's {@code ActionManager} at activation - the view's "click a body to
 * select it" gesture and its "Add Comet" button trigger them through
 * {@code ActionConsumerServices.executeAction(...)}, not by calling this
 * class directly. The body details are pushed back to the view rather than
 * returned, since an executed action has no return value.
 */
@Implementation
public class OrbitalInteractionImpl implements OrbitalInteraction {

	@UsedServices
	private ActionProviderServices actionManagerServices;

	@UsedServices
	private OrbitalViewServices useOrbitalViewServices;

	@UsedServices
	private OrbitalControllerServices useOrbitalControllerServices;

	/**
	 * Registers the {@link OrbitalActionKey#SelectBody} and {@link OrbitalActionKey#AddComet} actions at activation.
	 */
	@Activation
	public void registerActions() {
		this.actionManagerServices.registerActionExecution(
				OrbitalActionKey.SelectBody, this::selectBody, String.class);
		this.actionManagerServices.registerActionExecution(
				OrbitalActionKey.AddComet, this::addComet);
	}

	/** Unregisters the actions registered at activation. */
	@Passivation
	public void unregisterActions() {
		this.safeUnregister(OrbitalActionKey.SelectBody);
		this.safeUnregister(OrbitalActionKey.AddComet);
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

	/**
	 * Computes an interactive body-details preview and pushes the result to
	 * the view.
	 *
	 * @param bodyName the selected body's name
	 */
	private void selectBody(final String bodyName) {
		BodyDetails details = this.useOrbitalControllerServices.getBodyDetails(bodyName);
		if (details != null) {
			this.useOrbitalViewServices.showBodyDetails(details);
		}
	}

	private void addComet() {
		this.useOrbitalControllerServices.addComet();
	}
}
