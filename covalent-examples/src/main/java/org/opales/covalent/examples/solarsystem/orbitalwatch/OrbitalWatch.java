/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.orbitalwatch;

import org.opales.covalent.examples.solarsystem.hazardalert.HazardAlert;
import org.opales.covalent.examples.solarsystem.orbitalsystem.model.CloseApproachEvents;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the general, unfiltered watch MVIC (Model/Controller/
 * Interaction/View). Consumes every {@link CloseApproachEvents}
 * notification as-is - no decision logic beyond recording. Deliberately a
 * separate MVIC from {@code HazardAlert}, not a second graphical view of the
 * same one: they differ in business logic and purpose, not just
 * presentation.
 */
@Component
public interface OrbitalWatch {

}
