/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.rolewatcher.controller;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.examples.ecosystem.ecosystem.model.Role;

/**
 * User-facing "clear" command for one role's log, provided by
 * RoleWatcherController and used by RoleWatcherInteraction.
 */
@Services
public interface RoleWatcherControllerServices {

    /**
     * Empties the log of one role.
     * @param role the role whose log is cleared
     */
    void clear(Role role);
}
