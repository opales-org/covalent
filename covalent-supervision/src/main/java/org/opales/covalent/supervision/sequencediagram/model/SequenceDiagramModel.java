/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.model;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for SequenceDiagram's Model. Holds, per session key, the
 * ordered list of {@code SequenceTraceMessage}s and the selected actor
 * types, keyed by session under this composite's single-singleton shape.
 */
@Component
public interface SequenceDiagramModel {

}
