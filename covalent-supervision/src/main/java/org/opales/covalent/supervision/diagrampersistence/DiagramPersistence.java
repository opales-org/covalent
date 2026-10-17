/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.diagrampersistence;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the DiagramPersistence composite: the views library,
 * where component diagram views are saved, listed, opened, renamed, deleted,
 * exported and imported (see {@code DiagramPersistenceServices}). No panel of
 * its own: {@code ComponentDiagram}'s per-tab toolbar hosts the "Save view..."
 * and "Views library..." buttons, and its View shows the dialogs - this
 * composite is just a Controller providing {@code DiagramPersistenceServices}.
 */
@Component
public interface DiagramPersistence {

}
