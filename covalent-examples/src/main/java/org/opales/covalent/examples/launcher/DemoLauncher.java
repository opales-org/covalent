/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.launcher;

import org.opales.covalent.container.CovalentContainer;

/**
 * What a demo row's button actually needs to do to start or stop one demo
 * in its own {@link CovalentContainer}. {@link ManagedDemoLauncher} is the
 * ordinary case - deploy a set of managed component implementations, then
 * start or stop the container - but a demo that also wires raw components
 * (see {@code EcosystemStatsExportDemoLauncher}) needs a richer sequence
 * than a flat set of implementation classes can express, which is why
 * {@link DemoDescriptor} holds one of these instead of a {@code Class<?>[]}
 * directly: {@link LauncherApplication} itself stays demo-agnostic, never
 * importing anything specific to one demo's own wiring.
 */
public interface DemoLauncher {

    /**
     * Starts the demo in the given container.
     * @param container the container dedicated to this demo
     */
    void start(CovalentContainer container);

    /**
     * Stops the demo running in the given container.
     * @param container the container dedicated to this demo
     */
    void stop(CovalentContainer container);
}
