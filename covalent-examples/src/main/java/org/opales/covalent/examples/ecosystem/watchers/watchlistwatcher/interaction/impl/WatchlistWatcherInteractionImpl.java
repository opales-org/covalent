/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.interaction.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.controller.WatchlistWatcherControllerServices;
import org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.interaction.WatchlistWatcherActionKey;
import org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.interaction.WatchlistWatcherInteraction;
import org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.view.WatchlistWatcherViewServices;
import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.hmi.action.ActionProviderServices;

/** WatchlistWatcher's Interaction Controller: registers the "clear" action at activation. */
@Implementation
public class WatchlistWatcherInteractionImpl implements WatchlistWatcherInteraction {

    @UsedServices
    private ActionProviderServices actionManagerServices;

    @UsedServices
    private WatchlistWatcherControllerServices controllerServices;

    @UsedServices
    private WatchlistWatcherViewServices viewServices;

    /** Registers the clear action with the action manager. */
    @Activation
    public void registerActions() {
        actionManagerServices.registerActionExecution(WatchlistWatcherActionKey.Clear, this::clear);
    }

    /** Unregisters the clear action. */
    @Passivation
    public void unregisterActions() {
        safeUnregister(WatchlistWatcherActionKey.Clear);
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
