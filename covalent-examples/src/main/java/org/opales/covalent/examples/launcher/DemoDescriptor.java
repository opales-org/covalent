/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.examples.launcher;

/**
 * Describes one demo the launcher can start: a display name for its button,
 * and the {@link DemoLauncher} that knows how to start and stop it in its
 * own, dedicated {@link org.opales.covalent.container.CovalentContainer} on
 * first launch.
 *
 * @param name     the demo's display name, also used as its managed
 *                 container's name (see {@code Covalent.managedContainer})
 * @param launcher how to start and stop this demo
 */
public record DemoDescriptor(String name, DemoLauncher launcher) {
}
