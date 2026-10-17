/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.observed;

import org.opales.covalent.container.component.annotations.Events;

/**
 * Coalesced live activity of the observed container's ports, one component
 * and port kind at a time - the lightweight counterpart of the per-call
 * notifications of {@link ObservedServicesManagerEvents},
 * {@link ObservedParametersManagerEvents} and
 * {@link ObservedEventsManagerEvents}, for a consumer that only shows
 * whether a port is busy. A port turns active as soon as a call starts
 * through it, and inactive only once no call has gone through it for a
 * short while, so a burst of calls produces a single pair of
 * notifications.
 */
@Events
public interface ObservedPortActivityEvents {

	/**
	 * Notifies that the activity of a component's ports of one kind and type started or stopped.
	 *
	 * @param componentType the component owning the ports
	 * @param portType      the specific services/parameters/events interface this activity
	 *                      concerns - distinct interfaces of the same kind never share a report,
	 *                      so one busy interface never lights up another, unrelated one
	 * @param portKind      the kind of its ports this activity concerns
	 * @param active        whether at least one call is going through them
	 */
	void onPortActivityChanged(Class<?> componentType, Class<?> portType, ObservedPortKind portKind, boolean active);

}
