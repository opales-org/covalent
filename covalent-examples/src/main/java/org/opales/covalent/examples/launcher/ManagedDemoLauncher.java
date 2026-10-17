/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.launcher;

import org.opales.covalent.container.CovalentContainer;

/**
 * The ordinary {@link DemoLauncher}: deploy a fixed set of managed
 * component implementations, then start or stop the container. Every demo
 * in this module uses this, except one that also wires raw components
 * (see {@code EcosystemStatsExportDemoLauncher}).
 *
 * @param implementations the managed component implementation classes deployed to start the demo
 */
public record ManagedDemoLauncher(Class<?>[] implementations) implements DemoLauncher {

    @Override
    public void start(final CovalentContainer container) {
        container.deployComponents(this.implementations);
        container.start();
    }

    @Override
    public void stop(final CovalentContainer container) {
        container.stop();
    }
}
