/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.rr.services;

import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.internals.Internal;

/**
 * <p>
 * The services manager handles Covalent services. Services are a semantics of
 * communication between components which is based on the following features:
 * <ul>
 * <li>Low coupling. Services providers and users depends only on
 * interfaces</li>
 * <li>The services provided are intended for the benefit of services user.</li>
 * <li>Based on request-response semantics:
 * <ul>
 * <li>A service informs the caller of the successful completion of the
 * request</li>
 * <li>It can throw an exception if it was not able of providing the requested
 * service.</li>
 * <li>Even if there is no returned result (void method), the absence of a
 * thrown exception acknowledge to the user that the service was correctly
 * provided.</li>
 * </ul>
 * </li>
 * </ul>
 * <p>
 * This interface carries no methods of its own; it is the component-model
 * marker interface identifying the internal component implementing the
 * services port family (see {@link org.opales.covalent.container.rr.services.impl.ServicesManagerImpl}).
 * The actual operations are exposed through the collaborating
 * {@link ServicesManagerServices} and {@link ServicesLifecycleServices} ports.
 */
@Internal
@Component
public interface ServicesManager {

}
