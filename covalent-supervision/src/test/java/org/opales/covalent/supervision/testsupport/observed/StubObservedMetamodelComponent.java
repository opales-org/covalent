/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Marker for a minimal {@code ObservedMetamodelReadServices} stand-in,
 * deployed alongside {@code SequenceDiagramControllerImpl}: it knows no
 * metamodel at all, so every observed component is its own lifeline.
 */
@Component
public interface StubObservedMetamodelComponent {

}
