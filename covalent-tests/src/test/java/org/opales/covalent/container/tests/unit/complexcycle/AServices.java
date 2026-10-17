/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.complexcycle;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Services provided by {@code AImpl}. Unused by any other fixture component;
 * {@code AImpl} is the entry point of the long gray-path chain
 * A -&gt; B -&gt; X -&gt; Y -&gt; Z -&gt; X.
 */
@Services
public interface AServices {

	String fromA();

}
