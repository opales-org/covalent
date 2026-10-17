/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.rolewatcher.view;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.examples.ecosystem.ecosystem.model.Role;

/**
 * Piloting commands for RoleWatcher's view, provided by RoleWatcherView and
 * used by RoleWatcherInteraction.
 */
@Services
public interface RoleWatcherViewServices {

    /**
     * Signals that the log of a role is being cleared or that clearing is over; the view needs
     * no visual feedback for it.
     * @param role the role whose log is concerned
     * @param clearing {@code true} while the log is being cleared
     */
    void setClearing(Role role, boolean clearing);
}
