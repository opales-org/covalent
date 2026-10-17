/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.crosswatcher.view;

import org.opales.covalent.container.component.annotations.Services;

/** Piloting commands for CrossWatcher's view, used by CrossWatcherInteraction. */
@Services
public interface CrossWatcherViewServices {

    /**
     * Signals that the log is being cleared or that clearing is over; the view needs no visual
     * feedback for it.
     * @param clearing {@code true} while the log is being cleared
     */
    void setClearing(boolean clearing);
}
