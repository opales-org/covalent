/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.rolewatcher.interaction.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.examples.ecosystem.ecosystem.model.Role;
import org.opales.covalent.examples.ecosystem.watchers.rolewatcher.controller.RoleWatcherControllerServices;
import org.opales.covalent.examples.ecosystem.watchers.rolewatcher.interaction.RoleWatcherActionKey;
import org.opales.covalent.examples.ecosystem.watchers.rolewatcher.interaction.RoleWatcherInteraction;
import org.opales.covalent.examples.ecosystem.watchers.rolewatcher.view.RoleWatcherViewServices;
import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.hmi.action.ActionProviderServices;

/**
 * RoleWatcher's Interaction Controller: registers the "clear one role's log"
 * action with the HMI's ActionManager at activation.
 */
@Implementation
public class RoleWatcherInteractionImpl implements RoleWatcherInteraction {

    @UsedServices
    private ActionProviderServices actionManagerServices;

    @UsedServices
    private RoleWatcherControllerServices controllerServices;

    @UsedServices
    private RoleWatcherViewServices viewServices;

    /** Registers the clear-role action with the action manager. */
    @Activation
    public void registerActions() {
        actionManagerServices.registerActionExecution(RoleWatcherActionKey.ClearRole, this::clearRole, Role.class);
    }

    /** Unregisters the clear-role action. */
    @Passivation
    public void unregisterActions() {
        safeUnregister(RoleWatcherActionKey.ClearRole);
    }

    private void safeUnregister(ActionKey actionKey) {
        try {
            actionManagerServices.unregisterActionExecution(actionKey);
        } catch (IllegalStateException e) {
            // ActionManager already passivated as part of the same
            // container-stop cascade.
        }
    }

    private void clearRole(Role role) {
        viewServices.setClearing(role, true);
        controllerServices.clear(role);
        viewServices.setClearing(role, false);
    }
}
