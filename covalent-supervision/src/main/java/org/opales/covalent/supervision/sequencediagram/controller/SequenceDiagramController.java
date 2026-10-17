/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.controller;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for SequenceDiagram's Controller. The sole provider of the
 * external {@code SequenceDiagramServices} and sole producer of
 * {@code RecordingStateEvents}. Reacts to the observed container's live
 * services/parameters/events call activity and {@code ActionObservationServices},
 * and reads the observed metamodel only to describe the actors of a message.
 */
@Component
public interface SequenceDiagramController {

}
