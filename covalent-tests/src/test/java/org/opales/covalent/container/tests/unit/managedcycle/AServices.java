/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.managedcycle;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Services provided by {@code AImpl}, used by {@code BImpl}: one leg of the
 * two-component cycle fixture (A -&gt; B -&gt; A).
 */
@Services
public interface AServices {

	String fromA();

}
