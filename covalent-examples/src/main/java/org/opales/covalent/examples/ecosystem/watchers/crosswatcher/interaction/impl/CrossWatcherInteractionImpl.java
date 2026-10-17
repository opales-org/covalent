/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.crosswatcher.interaction.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.examples.ecosystem.watchers.crosswatcher.controller.CrossWatcherControllerServices;
import org.opales.covalent.examples.ecosystem.watchers.crosswatcher.interaction.CrossWatcherActionKey;
import org.opales.covalent.examples.ecosystem.watchers.crosswatcher.interaction.CrossWatcherInteraction;
import org.opales.covalent.examples.ecosystem.watchers.crosswatcher.view.CrossWatcherViewServices;
import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.hmi.action.ActionProviderServices;

/** CrossWatcher's Interaction Controller: registers the "clear" action at activation. */
@Implementation
public class CrossWatcherInteractionImpl implements CrossWatcherInteraction {

    @UsedServices
    private ActionProviderServices actionManagerServices;

    @UsedServices
    private CrossWatcherControllerServices controllerServices;

    @UsedServices
    private CrossWatcherViewServices viewServices;

    /** Registers the clear action with the action manager. */
    @Activation
    public void registerActions() {
        actionManagerServices.registerActionExecution(CrossWatcherActionKey.Clear, this::clear);
    }

    /** Unregisters the clear action. */
    @Passivation
    public void unregisterActions() {
        safeUnregister(CrossWatcherActionKey.Clear);
    }

    private void safeUnregister(ActionKey actionKey) {
        try {
            actionManagerServices.unregisterActionExecution(actionKey);
        } catch (IllegalStateException e) {
            // ActionManager already passivated as part of the same
            // container-stop cascade.
        }
    }

    private void clear() {
        viewServices.setClearing(true);
        controllerServices.clear();
        viewServices.setClearing(false);
    }
}
