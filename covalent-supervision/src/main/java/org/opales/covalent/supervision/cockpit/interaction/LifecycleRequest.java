/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.cockpit.interaction;

import org.opales.covalent.supervision.cockpit.model.LifecycleCommand;

/**
 * The payload of {@link CockpitActionKey#Command}.
 *
 * @param command the transition
 * @param componentType the component or composite it applies to
 */
public record LifecycleRequest(LifecycleCommand command, Class<?> componentType) {
}
