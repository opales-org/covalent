/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.supervisionhmi.interaction.impl;

import java.util.EnumMap;
import java.util.Map;

import org.opales.covalent.supervision.supervisionhmi.interaction.SupervisionHmiActionKey;
import org.opales.covalent.supervision.supervisionhmi.interaction.SupervisionHmiInteraction;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisionHmiViewServices;
import org.opales.covalent.supervision.supervisionhmi.view.SupervisorTabDescriptor;
import org.opales.covalent.supervision.supervisionhmi.view.ToolbarFunction;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.hmi.action.ActionProviderServices;

/**
 * SupervisionHmi's Interaction Controller: registers
 * {@link SupervisionHmiActionKey#TabSelected} and {@link SupervisionHmiActionKey#ShowWindow} with the HMI's
 * {@code ActionManager} at activation - {@code SupervisionHmiView}'s tab
 * pane selection listener triggers it through
 * {@code ActionConsumerServices.executeAction(...)}, not by calling this
 * class directly. Never touches JavaFX itself: it drives the shared
 * toolbar's bind/unbind cycle through {@link SupervisionHmiViewServices},
 * which is the only place that actually calls JavaFX-shaped APIs on the
 * toolbar widgets.
 */
@Implementation
public class SupervisionHmiInteractionImpl implements SupervisionHmiInteraction {

	/** Creates the interaction controller of the supervisor's window. */
	public SupervisionHmiInteractionImpl() {
		// Nothing to initialize.
	}

	@UsedServices
	private ActionProviderServices actionManagerServices;

	@UsedServices
	private SupervisionHmiViewServices useSupervisionHmiViewServices;

	/** The ActionKey each toolbar function is currently bound to - absent while unbound. */
	private final Map<ToolbarFunction, ActionKey> boundKeys = new EnumMap<>(ToolbarFunction.class);

	/** Registers the tab selection and show window actions with the action manager when the component activates. */
	@Activation
	public void registerActions() {
		this.actionManagerServices.registerActionExecution(
				SupervisionHmiActionKey.TabSelected, this::onTabSelected, SupervisorTabDescriptor.class);
		this.actionManagerServices.registerActionExecution(SupervisionHmiActionKey.ShowWindow, this::onShowWindow);
	}

	/** Unregisters the tab selection and show window actions when the component passivates. */
	@Passivation
	public void unregisterActions() {
		try {
			this.actionManagerServices.unregisterActionExecution(SupervisionHmiActionKey.TabSelected);
			this.actionManagerServices.unregisterActionExecution(SupervisionHmiActionKey.ShowWindow);
		} catch (IllegalStateException e) {
			// ActionManager already passivated as part of the same container-stop cascade.
		}
	}

	/** Asks the view to show the supervisor's window again. */
	private void onShowWindow() {
		this.useSupervisionHmiViewServices.showWindow();
	}

	/**
	 * Rebinds every toolbar function the newly active tab supports: unbinds
	 * that function's previous key, if any, then binds the new tab's. A
	 * function the new tab does not support keeps its current binding, so
	 * the shared Record button keeps controlling the last tab that records
	 * while, for instance, a sequence diagram is on screen.
	 *
	 * @param descriptor the newly active tab's descriptor
	 */
	private void onTabSelected(final SupervisorTabDescriptor descriptor) {
		for (ToolbarFunction function : descriptor.supportedToolbarFunctions()) {
			ActionKey previousKey = this.boundKeys.remove(function);
			if (previousKey != null) {
				this.useSupervisionHmiViewServices.unbindToolbarFunction(function, previousKey);
			}
			ActionKey key = this.actionKeyFor(descriptor, function);
			this.useSupervisionHmiViewServices.bindToolbarFunction(function, key);
			this.boundKeys.put(function, key);
		}
	}

	/**
	 * Resolves the ActionKey to bind/unbind a toolbar widget to, per the
	 * widget kind.
	 *
	 * @param descriptor the active tab's descriptor
	 * @param function   the toolbar widget kind being bound/unbound
	 * @return the matching ActionKey
	 */
	private ActionKey actionKeyFor(final SupervisorTabDescriptor descriptor, final ToolbarFunction function) {
		switch (function) {
			case RECORD_TOGGLE:
				return descriptor.recordActionKey();
			case ZOOM_SLIDER:
				return descriptor.zoomSetScaleActionKey();
			default:
				throw new IllegalArgumentException("Unknown toolbar function: " + function);
		}
	}
}
