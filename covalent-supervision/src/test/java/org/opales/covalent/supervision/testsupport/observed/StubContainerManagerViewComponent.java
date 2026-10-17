/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Marker for a minimal {@code ContainerManagerViewServices} stand-in,
 * recording what {@code ContainerManagerInteractionImpl} reports, and
 * a minimal {@code ActionProviderServices} one, without any JavaFX node.
 */
@Component
public interface StubContainerManagerViewComponent {

}
