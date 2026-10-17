/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.supervisionhmi;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the SupervisionHmi composite: the window and shared
 * tab pane/toolbar chrome. No Model/Controller - pure View + Interaction
 * Controller. Deployed
 * alongside {@code ContainerManager} in this tool's own dedicated
 * container - see {@code SupervisionApplication}.
 */
@Component
public interface SupervisionHmi {

}
