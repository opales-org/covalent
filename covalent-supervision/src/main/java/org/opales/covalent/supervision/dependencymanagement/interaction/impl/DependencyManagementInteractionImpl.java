/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.dependencymanagement.interaction.impl;

import org.opales.covalent.supervision.dependencymanagement.controller.DependencyManagementControllerServices;
import org.opales.covalent.supervision.dependencymanagement.interaction.DependencyManagementActionKey;
import org.opales.covalent.supervision.dependencymanagement.interaction.DependencyManagementInteraction;
import org.opales.covalent.supervision.dependencymanagement.interaction.DependencyManagementViewKey;
import org.opales.covalent.supervision.dependencymanagement.model.DependencyManagementEvents;
import org.opales.covalent.supervision.dependencymanagement.view.DependencyManagementViewServices;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionProviderServices;

/**
 * DependencyManagement's Interaction Controller: registers this function's
 * ActionKeys at its own {@code @Activation}, panel closed by default.
 * {@code Toggle} is SupervisionHmi's own permanent toolbar button, wired
 * here rather than a Record/Stop pair - see
 * {@code DependencyManagementActionKey}'s javadoc.
 */
@Implementation
public class DependencyManagementInteractionImpl implements DependencyManagementInteraction, DependencyManagementEvents {

	/** Creates the Interaction Controller; the container injects the services it uses. */
	public DependencyManagementInteractionImpl() {
		// Nothing to initialize.
	}

	@UsedServices
	private ActionProviderServices actionManagerServices;

	@UsedServices
	private DependencyManagementViewServices useViewServices;

	@UsedServices
	private DependencyManagementControllerServices useControllerServices;

	private boolean shown = false;

	/** Registers the zoom, recenter, refresh, export and toggle actions with the HMI's action manager. */
	@Activation
	public void registerActions() {
		this.actionManagerServices.registerActionExecution(DependencyManagementActionKey.ZoomSetScale,
				this::onZoomSetScale, Double.class);
		this.actionManagerServices.registerActionExecution(DependencyManagementActionKey.ZoomChanged,
				this::onZoomChanged, Double.class);
		this.actionManagerServices.registerActionExecution(DependencyManagementActionKey.Recenter, this::onRecenter);
		this.actionManagerServices.registerActionExecution(DependencyManagementActionKey.Refresh,
				this.useControllerServices::refreshGraph);
		this.actionManagerServices.registerActionExecution(DependencyManagementActionKey.Export, this::onExport);
		this.actionManagerServices.registerActionExecution(DependencyManagementActionKey.Toggle, this::onToggle);
	}

	/** Unregisters the actions and closes the panel if it is open. */
	@Passivation
	public void unregisterActions() {
		for (DependencyManagementActionKey key : new DependencyManagementActionKey[] {
				DependencyManagementActionKey.ZoomSetScale, DependencyManagementActionKey.ZoomChanged,
				DependencyManagementActionKey.Recenter, DependencyManagementActionKey.Refresh,
				DependencyManagementActionKey.Export,
				DependencyManagementActionKey.Toggle }) {
			try {
				this.actionManagerServices.unregisterActionExecution(key);
			} catch (IllegalStateException e) {
				// The ActionManager was passivated by the same container-stop cascade.
			}
		}
		if (this.shown) {
			this.useViewServices.hideView(DependencyManagementViewKey.INSTANCE);
			this.shown = false;
		}
	}

	private void onToggle() {
		this.shown = !this.shown;
		if (this.shown) {
			this.useViewServices.showView(DependencyManagementViewKey.INSTANCE);
			// The Controller's own activation-time publication may predate the View
			// being ready to render it.
			this.useControllerServices.refreshGraph();
		} else {
			this.useViewServices.hideView(DependencyManagementViewKey.INSTANCE);
		}
	}

	private void onZoomSetScale(final Double scale) {
		this.useViewServices.setZoomScale(scale);
	}

	private void onZoomChanged(final Double scale) {
		this.actionManagerServices.setActionValue(DependencyManagementActionKey.ZoomCurrentScale, scale);
	}

	private void onRecenter() {
		this.useViewServices.recenter();
	}

	private void onExport() {
		this.useViewServices.exportSnapshot();
	}

	// --- DependencyManagementEvents: the View renders directly; nothing here reacts to
	// graph content. ---

	@Override
	public void onGraphChanged() {
		// Nothing to do: this Interaction does not react to graph content.
	}
}
