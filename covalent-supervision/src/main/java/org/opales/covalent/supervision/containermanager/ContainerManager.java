/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.containermanager;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the ContainerManager composite: the managed-container
 * selector widget and the connection of the tool to the selected container. No Model/Controller -
 * pure View + Interaction Controller. Deployed alongside
 * {@code SupervisionHmi} in this tool's own dedicated container - see
 * {@code SupervisionApplication}.
 */
@Component
public interface ContainerManager {

}
