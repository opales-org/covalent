/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.complexcycle;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Services provided by {@code ZImpl}, which closes the 3-node cycle back to
 * {@link XServices} (Z -&gt; X).
 */
@Services
public interface ZServices {

	String fromZ();

}
