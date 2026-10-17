/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the SequenceDiagram MVIC (Model/View/Controller/
 * Interaction): a single persistent singleton
 * reconstructing live call sequences from the observed container's runtime
 * instrumentation. Each recording session becomes its own entry, keyed by
 * its own {@code ViewKey}, and its own tab, inside this one instance.
 */
@Component
public interface SequenceDiagram {

}
