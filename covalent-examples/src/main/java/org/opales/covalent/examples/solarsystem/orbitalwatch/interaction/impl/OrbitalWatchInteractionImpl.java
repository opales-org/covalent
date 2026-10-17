/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalwatch.interaction.impl;

import org.opales.covalent.examples.solarsystem.orbitalwatch.controller.OrbitalWatchControllerServices;
import org.opales.covalent.examples.solarsystem.orbitalwatch.interaction.OrbitalWatchActionKey;
import org.opales.covalent.examples.solarsystem.orbitalwatch.interaction.OrbitalWatchInteraction;
import org.opales.covalent.examples.solarsystem.orbitalwatch.view.OrbitalWatchViewServices;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.hmi.action.ActionProviderServices;

/**
 * The watch's Interaction Controller: registers its "Clear" action with the
 * HMI's {@code ActionManager} at activation.
 */
@Implementation
public class OrbitalWatchInteractionImpl implements OrbitalWatchInteraction {

	@UsedServices
	private ActionProviderServices actionManagerServices;

	@UsedServices
	private OrbitalWatchControllerServices useOrbitalWatchControllerServices;

	@UsedServices
	private OrbitalWatchViewServices useOrbitalWatchViewServices;

	/** Registers the {@link OrbitalWatchActionKey#Clear} action at activation. */
	@Activation
	public void registerActions() {
		this.actionManagerServices.registerActionExecution(OrbitalWatchActionKey.Clear, this::clear);
	}

	/** Unregisters the action registered at activation. */
	@Passivation
	public void unregisterActions() {
		this.safeUnregister(OrbitalWatchActionKey.Clear);
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

	private void clear() {
		this.useOrbitalWatchViewServices.setClearing(true);
		this.useOrbitalWatchControllerServices.clear();
		this.useOrbitalWatchViewServices.setClearing(false);
	}
}
