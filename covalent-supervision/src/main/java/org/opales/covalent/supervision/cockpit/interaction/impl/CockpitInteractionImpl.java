/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.cockpit.interaction.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionProviderServices;
import org.opales.covalent.supervision.cockpit.controller.CockpitControllerServices;
import org.opales.covalent.supervision.cockpit.interaction.CockpitActionKey;
import org.opales.covalent.supervision.cockpit.interaction.CockpitInteraction;
import org.opales.covalent.supervision.cockpit.interaction.CockpitViewKey;
import org.opales.covalent.supervision.cockpit.interaction.LifecycleRequest;
import org.opales.covalent.supervision.cockpit.model.LifecycleCommand;
import org.opales.covalent.supervision.cockpit.view.CockpitViewServices;

/** Registers the cockpit's actions, and opens and closes its panel. */
@Implementation
public class CockpitInteractionImpl implements CockpitInteraction {

	/** Creates the interaction controller of the cockpit function. */
	public CockpitInteractionImpl() {
		// Nothing to initialize.
	}

	@UsedServices
	private ActionProviderServices actionManagerServices;

	@UsedServices
	private CockpitViewServices useViewServices;

	@UsedServices
	private CockpitControllerServices useControllerServices;

	private volatile boolean shown = false;

	/** Registers the cockpit's actions with the action manager when the component activates. */
	@Activation
	public void registerActions() {
		this.actionManagerServices.registerActionExecution(CockpitActionKey.Toggle, this::onToggle);
		this.actionManagerServices.registerActionExecution(CockpitActionKey.Command,
				(LifecycleRequest request) -> this.useControllerServices.execute(request.command(), request.componentType()),
				LifecycleRequest.class);
		this.actionManagerServices.registerActionExecution(CockpitActionKey.ContainerCommand,
				this.useControllerServices::executeOnContainer, LifecycleCommand.class);
		this.actionManagerServices.registerActionExecution(CockpitActionKey.EnterComposite,
				this.useControllerServices::enterComposite, Class.class);
		this.actionManagerServices.registerActionExecution(CockpitActionKey.ShowConsoleLevel,
				this.useControllerServices::showConsoleLevel, Integer.class);
	}

	/** Unregisters the cockpit's actions and closes its panel when the component passivates. */
	@Passivation
	public void unregisterActions() {
		for (CockpitActionKey key : CockpitActionKey.values()) {
			try {
				this.actionManagerServices.unregisterActionExecution(key);
			} catch (IllegalStateException e) {
				// The action manager is already passivated, as part of the same container stop.
			}
		}
		this.onClose();
	}

	private void onToggle() {
		if (this.shown) {
			this.onClose();
		} else {
			this.shown = true;
			this.useViewServices.showView(CockpitViewKey.INSTANCE);
		}
	}

	private void onClose() {
		if (this.shown) {
			this.shown = false;
			this.useViewServices.hideView(CockpitViewKey.INSTANCE);
		}
	}
}
