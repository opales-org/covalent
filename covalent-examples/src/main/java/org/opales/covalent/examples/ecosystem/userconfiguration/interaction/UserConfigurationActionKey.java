/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.userconfiguration.interaction;

import org.opales.covalent.hmi.action.ActionKey;

/**
 * Action identifiers registered by UserConfigurationInteraction with
 * the HMI's ActionManager, and executed from the view instead of calling
 * the Interaction Controller directly.
 */
public enum UserConfigurationActionKey implements ActionKey {

    /**
     * Sets the growth rate of one species; takes the species name and the rate, a {@code
     * Double}.
     */
    SetGrowthRate,
    /** Sets the simulation time scale; takes the time scale, a {@code Double}. */
    SetTimeScale,
    /**
     * Adds a species to the watchlist, or removes it; takes the species name and whether it is
     * to be watched, a {@code Boolean}.
     */
    SetWatchlistSpecies,
    /**
     * Adds a prey species to those watched by the cross-watcher, or removes it; takes the
     * species name and whether it is to be watched, a {@code Boolean}.
     */
    SetCrossWatcherSpecies;
}
