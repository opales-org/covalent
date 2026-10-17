/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.crosswatcher.interaction;

import org.opales.covalent.hmi.action.ActionKey;

/** Action identifier registered by CrossWatcherInteraction with the HMI's ActionManager. */
public enum CrossWatcherActionKey implements ActionKey {

    /** Empties the predation log. */
    Clear;
}
