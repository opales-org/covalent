/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers;

/**
 * One logged occurrence: a species and a short description of what
 * happened. Shared by RoleWatcher and WatchlistWatcher, whose logs are
 * otherwise identically shaped. Carries no simulation time: PopulationEvents'
 * onBirth/onDeath notifications do not carry one (only onPredation does,
 * see CrossWatcher's own Predation record).
 * @param species name of the species concerned
 * @param description what happened, such as {@code birth} or {@code death (starvation)}
 */
public record LogEntry(String species, String description) {
}
