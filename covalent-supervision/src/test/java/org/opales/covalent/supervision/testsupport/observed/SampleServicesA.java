/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed;

import org.opales.covalent.container.component.annotations.Services;

/** A trivial services interface, provided by {@code LeafAProviderComponentImpl}. */
@Services
public interface SampleServicesA {

	void pingA();
}
