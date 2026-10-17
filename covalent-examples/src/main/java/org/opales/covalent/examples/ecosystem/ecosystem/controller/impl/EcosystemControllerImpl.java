/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */

package org.opales.covalent.examples.ecosystem.ecosystem.controller.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.lifecycle.Initialization;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.examples.ecosystem.ecosystem.controller.EcosystemController;
import org.opales.covalent.examples.ecosystem.ecosystem.controller.EcosystemParameters;
import org.opales.covalent.examples.ecosystem.ecosystem.controller.EcosystemServices;
import org.opales.covalent.examples.ecosystem.ecosystem.model.EcosystemModelServices;
import org.opales.covalent.examples.ecosystem.ecosystem.model.PopulationEvents;
import org.opales.covalent.examples.ecosystem.ecosystem.model.Role;
import org.opales.covalent.examples.ecosystem.ecosystem.model.Species;

/**
 * Business controller for the ecosystem: holds the generalized
 * Lotka-Volterra growth rates and interaction matrix as its own fields
 * (like OrbitalController's gravitationalConstant), and runs one
 * stochastic tau-leaping draw per advance(dt), recording the outcome into
 * EcosystemModel and raising a matching PopulationEvents notification.
 */
@Implementation
public class EcosystemControllerImpl implements EcosystemController, EcosystemServices, EcosystemParameters {

    @UsedServices
    private EcosystemModelServices modelServices;

    private final Random random = new Random();

    @ProducedEvents
    private PopulationEvents populationEvents;

    private List<Species> species;
    private Map<String, Double> growthRates;
    private Map<String, Map<String, Double>> interactionMatrix;
    private Map<String, Double> populations;
    private double simulationTime;

    /**
     * Sets the default species, growth rates and interaction matrix, hands the species to the
     * model,
     * then resets the populations.
     */
    @Initialization
    public void initialize() {
        species = defaultSpecies();
        growthRates = defaultGrowthRates();
        interactionMatrix = defaultInteractionMatrix();
        modelServices.setSpecies(species);
        reset();
    }

    @Override
    public void advance(double dt) {
        Map<String, Double> nextPopulations = new LinkedHashMap<>(populations);

        for (Species s : species) {
            String name = s.name();
            double n = populations.getOrDefault(name, 0.0);
            if (n <= 0.0) {
                continue;
            }

            double netRate = growthRates.getOrDefault(name, 0.0);
            Map<String, Double> row = interactionMatrix.getOrDefault(name, Map.of());
            for (Map.Entry<String, Double> entry : row.entrySet()) {
                netRate += entry.getValue() * populations.getOrDefault(entry.getKey(), 0.0);
            }

            double lambda = Math.abs(netRate) * n * dt;
            int count = poisson(lambda);
            if (count == 0) {
                continue;
            }

            if (netRate >= 0.0) {
                nextPopulations.put(name, n + count);
                populationEvents.onBirth(name, s.role());
            } else {
                double after = Math.max(0.0, n - count);
                nextPopulations.put(name, after);
                String cause = s.role() == Role.PREDATOR ? "starvation" : "natural";
                populationEvents.onDeath(name, s.role(), cause);
                firePredationIfApplicable(s, row);
            }
        }

        simulationTime += dt;
        populations = nextPopulations;
        modelServices.updatePopulations(populations, simulationTime);
    }

    private void firePredationIfApplicable(Species prey, Map<String, Double> preyInteractionRow) {
        // Any negative coefficient in this species' own row, other than
        // its self-limiting term, identifies a consumer of this species:
        // Prey-eats-Resource and Predator-eats-Prey are both, structurally,
        // the same kind of consumption event.
        for (Map.Entry<String, Double> entry : preyInteractionRow.entrySet()) {
            if (entry.getKey().equals(prey.name())) {
                continue;
            }
            if (entry.getValue() < 0.0) {
                populationEvents.onPredation(entry.getKey(), prey.name(), simulationTime);
            }
        }
    }

    @Override
    public void reset() {
        populations = defaultPopulations();
        simulationTime = 0.0;
        modelServices.updatePopulations(populations, simulationTime);
    }

    @Override
    public Map<String, Double> getGrowthRates() {
        return new LinkedHashMap<>(growthRates);
    }

    @Override
    public void setGrowthRates(Map<String, Double> growthRates) {
        this.growthRates = new LinkedHashMap<>(growthRates);
    }

    @Override
    public Map<String, Map<String, Double>> getInteractionMatrix() {
        Map<String, Map<String, Double>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Double>> entry : interactionMatrix.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        return copy;
    }

    @Override
    public void setInteractionMatrix(Map<String, Map<String, Double>> interactionMatrix) {
        this.interactionMatrix = interactionMatrix;
    }

    private int poisson(double lambda) {
        if (lambda <= 0.0) {
            return 0;
        }
        double l = Math.exp(-lambda);
        int k = 0;
        double p = 1.0;
        do {
            k++;
            p *= random.nextDouble();
        } while (p > l);
        return k - 1;
    }

    private static List<Species> defaultSpecies() {
        List<Species> list = new ArrayList<>();
        list.add(new Species("Grass", Role.RESOURCE));
        list.add(new Species("Rabbit", Role.PREY));
        list.add(new Species("Deer", Role.PREY));
        list.add(new Species("Fox", Role.PREDATOR));
        list.add(new Species("Wolf", Role.PREDATOR));
        return list;
    }

    private static Map<String, Double> defaultGrowthRates() {
        // Grass (Resource) is the only species with a positive baseline:
        // it grows on its own. Every Prey and Predator has a NEGATIVE
        // baseline - it declines and eventually starves without its own
        // food source - and only turns net-positive through its
        // interaction-matrix coefficient with what it eats. Without this,
        // a Prey species keeps growing even after its Resource goes
        // extinct, since its baseline alone would stay positive.
        Map<String, Double> rates = new LinkedHashMap<>();
        rates.put("Grass", 0.8);
        rates.put("Rabbit", -0.3);
        rates.put("Deer", -0.25);
        rates.put("Fox", -0.5);
        rates.put("Wolf", -0.4);
        return rates;
    }

    private static Map<String, Map<String, Double>> defaultInteractionMatrix() {
        Map<String, Map<String, Double>> matrix = new LinkedHashMap<>();

        Map<String, Double> grass = new LinkedHashMap<>();
        grass.put("Grass", -0.01);
        grass.put("Rabbit", -0.02);
        grass.put("Deer", -0.015);
        matrix.put("Grass", grass);

        Map<String, Double> rabbit = new LinkedHashMap<>();
        rabbit.put("Grass", 0.01);
        rabbit.put("Rabbit", -0.001);
        rabbit.put("Fox", -0.02);
        matrix.put("Rabbit", rabbit);

        Map<String, Double> deer = new LinkedHashMap<>();
        deer.put("Grass", 0.008);
        deer.put("Deer", -0.001);
        deer.put("Wolf", -0.015);
        matrix.put("Deer", deer);

        Map<String, Double> fox = new LinkedHashMap<>();
        fox.put("Rabbit", 0.015);
        fox.put("Fox", -0.01);
        matrix.put("Fox", fox);

        Map<String, Double> wolf = new LinkedHashMap<>();
        wolf.put("Deer", 0.01);
        wolf.put("Wolf", -0.01);
        matrix.put("Wolf", wolf);

        return matrix;
    }

    private static Map<String, Double> defaultPopulations() {
        Map<String, Double> pops = new LinkedHashMap<>();
        pops.put("Grass", 200.0);
        pops.put("Rabbit", 50.0);
        pops.put("Deer", 30.0);
        pops.put("Fox", 10.0);
        pops.put("Wolf", 8.0);
        return pops;
    }
}
