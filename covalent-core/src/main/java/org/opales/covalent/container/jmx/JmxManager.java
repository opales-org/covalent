/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.jmx;

import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.internals.Internal;

/**
 * Owns everything JMX in the container: the registration of its
 * {@link ContainerMXBean} in the platform MBean server. Its services are in
 * {@link JmxServices}.
 */
@Internal
@Component
public interface JmxManager {

}
