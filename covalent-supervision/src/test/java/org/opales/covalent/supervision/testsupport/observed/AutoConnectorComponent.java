/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Deployed right before a Controller that reads its data only once at its own {@code
 * @Activation} (e.g. {@code DependencyManagementControllerImpl}, unlike {@code
 * ComponentDiagramControllerImpl}, which also reacts to {@code onContainerConnected()}): vertical
 * start mode activates each deployed root (and its full composed tree) in deployment order, so
 * connecting here - mirroring production's own {@code ContainerManagerInteractionImpl},
 * see {@code SupervisionApplication}'s deployment-order comment - guarantees the bridge already
 * has real data by the time a Controller deployed after this one activates.
 */
@Component
public interface AutoConnectorComponent {

}
