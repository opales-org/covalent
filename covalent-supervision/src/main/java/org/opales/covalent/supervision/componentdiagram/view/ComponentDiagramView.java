/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentdiagram.view;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for ComponentDiagram's View. Renders whatever
 * {@code ComponentDiagramModel} holds - the diagram canvas
 * (nodes/edges/cells), the component tree panel, and PNG/clipboard export -
 * and reports gestures to {@code ComponentDiagramInteraction} through
 * {@code ActionManager}, never deciding anything itself.
 */
@Component
public interface ComponentDiagramView {

}
