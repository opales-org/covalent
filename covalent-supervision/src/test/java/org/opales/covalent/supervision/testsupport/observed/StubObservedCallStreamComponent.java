/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Marker for a minimal {@code ObservedCallStreamControl} stand-in, deployed
 * alongside {@code SequenceDiagramControllerImpl} so a test can check when
 * the Controller switches the per-call notifications on and off, without a
 * real observed-container bridge.
 */
@Component
public interface StubObservedCallStreamComponent {

}
