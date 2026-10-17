/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.internalreserved;

import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.internals.Internal;

/**
 * A business component type posing as one of Covalent's own components.
 */
@Component
@Internal
public interface PosingInternal {

}
