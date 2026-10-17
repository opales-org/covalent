/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.userconfiguration.interaction.impl;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.opales.covalent.container.component.annotations.AccessedParameters;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.examples.ecosystem.ecosystem.controller.EcosystemParameters;
import org.opales.covalent.examples.ecosystem.simulationrunner.controller.RunnerParameters;
import org.opales.covalent.examples.ecosystem.userconfiguration.interaction.UserConfigurationActionKey;
import org.opales.covalent.examples.ecosystem.userconfiguration.interaction.UserConfigurationInteraction;
import org.opales.covalent.examples.ecosystem.userconfiguration.view.UserConfigurationViewServices;
import org.opales.covalent.examples.ecosystem.watchers.crosswatcher.controller.CrossWatcherParameters;
import org.opales.covalent.examples.ecosystem.watchers.watchlistwatcher.controller.WatchlistParameters;
import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.hmi.action.ActionProviderServices;

/**
 * The configuration composite's Interaction Controller: registers one
 * ActionManager action per user-editable parameter, and applies each one
 * directly to whichever function's Parameters interface actually owns it.
 */
@Implementation
public class UserConfigurationInteractionImpl implements UserConfigurationInteraction {

    @UsedServices
    private ActionProviderServices actionManagerServices;

    @UsedServices
    private UserConfigurationViewServices viewServices;

    @AccessedParameters
    private EcosystemParameters ecosystemParameters;

    @AccessedParameters
    private RunnerParameters runnerParameters;

    @AccessedParameters
    private WatchlistParameters watchlistParameters;

    @AccessedParameters
    private CrossWatcherParameters crossWatcherParameters;

    /**
     * Registers the four configuration actions, then shows the current growth rates, time
     * scale and
     * watched-species sets in the view.
     */
    @Activation
    public void registerActions() {
        actionManagerServices.registerActionExecution(
                UserConfigurationActionKey.SetGrowthRate, this::setGrowthRate, String.class, Double.class);
        actionManagerServices.registerActionExecution(
                UserConfigurationActionKey.SetTimeScale, this::setTimeScale, Double.class);
        actionManagerServices.registerActionExecution(
                UserConfigurationActionKey.SetWatchlistSpecies, this::setWatchlistSpecies, String.class, Boolean.class);
        actionManagerServices.registerActionExecution(
                UserConfigurationActionKey.SetCrossWatcherSpecies, this::setCrossWatcherSpecies, String.class,
                Boolean.class);

        viewServices.displayGrowthRates(ecosystemParameters.getGrowthRates());
        viewServices.displayTimeScale(runnerParameters.getTimeScale());
        viewServices.displayWatchlistSpecies(watchlistParameters.getWatchedSpecies());
        viewServices.displayPreySpecies(preySpecies());
        viewServices.displayCrossWatcherSpecies(crossWatcherParameters.getWatchedSpecies());
    }

    /** Unregisters the four configuration actions. */
    @Passivation
    public void unregisterActions() {
        safeUnregister(UserConfigurationActionKey.SetGrowthRate);
        safeUnregister(UserConfigurationActionKey.SetTimeScale);
        safeUnregister(UserConfigurationActionKey.SetWatchlistSpecies);
        safeUnregister(UserConfigurationActionKey.SetCrossWatcherSpecies);
    }

    private void safeUnregister(ActionKey actionKey) {
        try {
            actionManagerServices.unregisterActionExecution(actionKey);
        } catch (IllegalStateException e) {
            // ActionManager already passivated as part of the same
            // container-stop cascade.
        }
    }

    private void setGrowthRate(String species, Double rate) {
        Map<String, Double> updated = new LinkedHashMap<>(ecosystemParameters.getGrowthRates());
        updated.put(species, rate);
        ecosystemParameters.setGrowthRates(updated);
    }

    private void setTimeScale(Double timeScale) {
        runnerParameters.setTimeScale(timeScale);
    }

    private void setWatchlistSpecies(String species, Boolean watched) {
        watchlistParameters.setWatchedSpecies(withSpecies(watchlistParameters.getWatchedSpecies(), species, watched));
    }

    private void setCrossWatcherSpecies(String species, Boolean watched) {
        crossWatcherParameters
                .setWatchedSpecies(withSpecies(crossWatcherParameters.getWatchedSpecies(), species, watched));
    }

    /**
     * The species some other species consumes, found as EcosystemController does when it raises
     * a predation: a negative coefficient, other than the species' own, in the species' row of
     * the interaction matrix.
     */
    private Set<String> preySpecies() {
        Set<String> prey = new LinkedHashSet<>();
        for (Map.Entry<String, Map<String, Double>> row : ecosystemParameters.getInteractionMatrix().entrySet()) {
            for (Map.Entry<String, Double> coefficient : row.getValue().entrySet()) {
                if (!coefficient.getKey().equals(row.getKey()) && coefficient.getValue() < 0.0) {
                    prey.add(row.getKey());
                    break;
                }
            }
        }
        return prey;
    }

    private static Set<String> withSpecies(Set<String> watchedSpecies, String species, boolean watched) {
        Set<String> updated = new LinkedHashSet<>(watchedSpecies);
        if (watched) {
            updated.add(species);
        } else {
            updated.remove(species);
        }
        return updated;
    }
}
