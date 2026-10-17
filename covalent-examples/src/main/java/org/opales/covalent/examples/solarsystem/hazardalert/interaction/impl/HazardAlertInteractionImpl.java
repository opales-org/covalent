/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.hazardalert.interaction.impl;

import org.opales.covalent.examples.solarsystem.hazardalert.controller.HazardAlertControllerServices;
import org.opales.covalent.examples.solarsystem.hazardalert.interaction.HazardAlertActionKey;
import org.opales.covalent.examples.solarsystem.hazardalert.interaction.HazardAlertInteraction;
import org.opales.covalent.examples.solarsystem.hazardalert.view.HazardAlertViewServices;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.hmi.action.ActionProviderServices;

/**
 * The hazard alert's Interaction Controller: registers its "Acknowledge all"
 * action with the HMI's {@code ActionManager} at activation.
 */
@Implementation
public class HazardAlertInteractionImpl implements HazardAlertInteraction {

	@UsedServices
	private ActionProviderServices actionManagerServices;

	@UsedServices
	private HazardAlertControllerServices useHazardAlertControllerServices;

	@UsedServices
	private HazardAlertViewServices useHazardAlertViewServices;

	/** Registers the {@link HazardAlertActionKey#AcknowledgeAll} action at activation. */
	@Activation
	public void registerActions() {
		this.actionManagerServices.registerActionExecution(
				HazardAlertActionKey.AcknowledgeAll, this::acknowledgeAll);
	}

	/** Unregisters the action registered at activation. */
	@Passivation
	public void unregisterActions() {
		this.safeUnregister(HazardAlertActionKey.AcknowledgeAll);
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

	private void acknowledgeAll() {
		this.useHazardAlertViewServices.setAcknowledging(true);
		this.useHazardAlertControllerServices.acknowledgeAll();
		this.useHazardAlertViewServices.setAcknowledging(false);
	}
}
