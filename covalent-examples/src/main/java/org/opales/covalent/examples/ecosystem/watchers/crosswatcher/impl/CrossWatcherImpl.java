/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.crosswatcher.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.examples.ecosystem.watchers.crosswatcher.CrossWatcher;
import org.opales.covalent.examples.ecosystem.watchers.crosswatcher.controller.impl.CrossWatcherControllerImpl;
import org.opales.covalent.examples.ecosystem.watchers.crosswatcher.interaction.impl.CrossWatcherInteractionImpl;
import org.opales.covalent.examples.ecosystem.watchers.crosswatcher.model.impl.CrossWatcherModelImpl;
import org.opales.covalent.examples.ecosystem.watchers.crosswatcher.view.impl.CrossWatcherViewImpl;

/** The CrossWatcher composite: deploys its four MVIC parts together. */
@Implementation
public class CrossWatcherImpl implements CrossWatcher {

    @Implementation
    private CrossWatcherModelImpl model;

    @Implementation
    private CrossWatcherControllerImpl controller;

    @Implementation
    private CrossWatcherViewImpl view;

    @Implementation
    private CrossWatcherInteractionImpl interaction;
}
