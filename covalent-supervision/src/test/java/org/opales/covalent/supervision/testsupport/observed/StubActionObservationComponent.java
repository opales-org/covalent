/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Marker for a minimal {@code ActionObservationServices} stand-in, deployed
 * alongside {@code SequenceDiagramControllerImpl} so a test can fire a fake
 * action notification without any real HMI action machinery, and can
 * directly inspect whether the Controller is still registered as a listener
 * (its {@code @Activation}/{@code @Passivation} wiring).
 */
@Component
public interface StubActionObservationComponent {

}
