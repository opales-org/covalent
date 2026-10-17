/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit.rawmanagedcircular;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Services provided by the managed component fixture, used by the raw
 * component fixture through {@link org.opales.covalent.container.raw.RawComponentsServices#useServices(Class)}.
 */
@Services
public interface ManagedSideServices {

	String pong();

}
