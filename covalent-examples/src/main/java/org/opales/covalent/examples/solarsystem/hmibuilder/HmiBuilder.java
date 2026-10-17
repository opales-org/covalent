/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.hmibuilder;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the demo's pure HMI assembly component: builds the
 * JavaFX scene graph and embeds each function's view root(s), one per leaf
 * function. No business logic, and it consumes no domain event - it only
 * accesses each function's {@code *ViewParameters} to read its already-built
 * root node(s).
 */
@Component
public interface HmiBuilder {

}
