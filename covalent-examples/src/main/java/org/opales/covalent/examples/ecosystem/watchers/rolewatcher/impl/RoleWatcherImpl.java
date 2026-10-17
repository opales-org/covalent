/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.ecosystem.watchers.rolewatcher.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.examples.ecosystem.watchers.rolewatcher.RoleWatcher;
import org.opales.covalent.examples.ecosystem.watchers.rolewatcher.controller.impl.RoleWatcherControllerImpl;
import org.opales.covalent.examples.ecosystem.watchers.rolewatcher.interaction.impl.RoleWatcherInteractionImpl;
import org.opales.covalent.examples.ecosystem.watchers.rolewatcher.model.impl.RoleWatcherModelImpl;
import org.opales.covalent.examples.ecosystem.watchers.rolewatcher.view.impl.RoleWatcherViewImpl;

/**
 * The RoleWatcher composite: deploys its four MVIC parts together.
 */
@Implementation
public class RoleWatcherImpl implements RoleWatcher {

    @Implementation
    private RoleWatcherModelImpl model;

    @Implementation
    private RoleWatcherControllerImpl controller;

    @Implementation
    private RoleWatcherViewImpl view;

    @Implementation
    private RoleWatcherInteractionImpl interaction;
}
