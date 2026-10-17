/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.hazardalert;

import org.opales.covalent.examples.solarsystem.orbitalsystem.model.CloseApproachEvents;
import org.opales.covalent.examples.solarsystem.orbitalwatch.OrbitalWatch;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the filtered, decision-facing watch MVIC (Model/
 * Controller/Interaction/View). Subscribes to the very same
 * {@link CloseApproachEvents} as {@code OrbitalWatch} with a distance+size
 * filter, and owns whatever escalation/acknowledgement logic a hazard alert
 * needs.
 */
@Component
public interface HazardAlert {

}
