/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.solarsystem.userconfiguration;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for the user-facing configuration composite
 * (View/Interaction - it owns no state of its own, so it has no
 * Model/Events): a single place gathering the user-editable Parameters of
 * every function, whichever one actually owns each - the same role as in
 * the neural network demo's own {@code UserConfiguration}.
 */
@Component
public interface UserConfiguration {

}
