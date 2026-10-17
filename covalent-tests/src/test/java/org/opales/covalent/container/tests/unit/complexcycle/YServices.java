/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.complexcycle;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Services provided by {@code YImpl}, the middle link of the 3-node cycle
 * X -&gt; Y -&gt; Z -&gt; X.
 */
@Services
public interface YServices {

	String fromY();

}
