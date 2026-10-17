/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.stereotypepolicy;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the StereotypePolicy composite: the table that tells which stereotype a
 * component shows, from the end of its name, and the panel to edit it. The sole implementer of
 * {@code StereotypeParameters}, read by ComponentDiagram and SequenceDiagram so that both show
 * the same stereotypes.
 */
@Component
public interface StereotypePolicy {

}
