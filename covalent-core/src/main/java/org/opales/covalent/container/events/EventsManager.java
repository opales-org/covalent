/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.events;

import org.opales.covalent.container.component.annotations.Component;
import org.opales.covalent.container.component.internals.Internal;

/**
 * <p>
 * The events manager handles Covalent events. Events are a semantics of
 * communication between components which is based on the following features:
 * <ul>
 * <li>Very low coupling.
 * <ul>
 * <li>Events producer and consumers depend only on interfaces</li>
 * <li>Events consumer can subscribe before the producer is activated or even
 * deployed.</li>
 * </ul>
 * </li>
 * <li>The events produced are intended for the benefit of consumers. The
 * producer gains no benefit at all from firing events. Note that, technically,
 * this is reversed compared to services: in services, the caller takes the
 * benefit, while in events, the benefits is for the callee.</li>
 * <li>Based on fire and forget semantics:
 * <ul>
 * <li>An event method never returns an argument (void method), nor can it throw
 * a caught exception.</li>
 * <li>Runtime exceptions thrown by a consumer are ignored by the producer
 * (indeed, in Covalent, the producer is even not aware about any thrown exception,
 * they are caught in the dynamic proxies inserted between both
 * components).</li>
 * <li>In order to avoid slowing the producer, the default link between a
 * producer and its consumers is asynchronous (this can be changed by
 * configuration).</li>
 * <li>The consumer must not modify the objects provided by the notification
 * methods. Ideally, they should be cloned by the producer. in Covalent, this can be
 * done automatically using the @Cloned annotation).</li>
 * </ul>
 * </li>
 * </ul>
 *
 */
@Internal
@Component
public interface EventsManager {

}
