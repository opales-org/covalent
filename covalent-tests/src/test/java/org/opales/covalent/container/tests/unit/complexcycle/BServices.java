/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.complexcycle;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Services provided by {@code BImpl}, used both by {@code AImpl} (the long
 * gray-path chain) and by {@code SiblingImpl}, deployed and reached only
 * after A's branch already fully processed B - a black-node revisit that
 * must not be mistaken for a cycle.
 */
@Services
public interface BServices {

	String fromB();

}
