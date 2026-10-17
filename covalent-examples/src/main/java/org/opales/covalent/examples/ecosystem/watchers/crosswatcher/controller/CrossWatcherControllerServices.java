/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.crosswatcher.controller;

import org.opales.covalent.container.component.annotations.Services;

/** User-facing "clear" command, provided by CrossWatcherController and used by CrossWatcherInteraction. */
@Services
public interface CrossWatcherControllerServices {

    /** Empties the predation log. */
    void clear();
}
