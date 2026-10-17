/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.extension;

import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.internals.Internal;

/**
 * Starts optional Covalent runtime extensions requested by the application.
 * Its services are in {@link CovalentExtensionsServices}.
 */
@Internal
@Component
public interface CovalentExtensionsManager {

}
