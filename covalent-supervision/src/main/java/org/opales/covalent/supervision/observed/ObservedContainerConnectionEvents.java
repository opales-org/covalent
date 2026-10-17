/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.observed;

import org.opales.covalent.container.component.annotations.Events;

/**
 * Fired by {@code ObservedContainerBridge} whenever
 * {@code ObservedContainerControl#connect(CovalentContainer)} switches the
 * observed container, and whenever
 * {@code ObservedContainerControl#disconnect()} leaves it. Connecting itself is otherwise silent - no
 * registry/services/parameters/events notification is replayed for state
 * that existed in the target container before the connection - so
 * a consumer that keeps its own rebuilt view of the observed container
 * (rather than reacting to individual deltas) needs this one explicit
 * signal to resynchronize with the newly observed container's current
 * state.
 */
@Events
public interface ObservedContainerConnectionEvents {

	/**
	 * Notifies that a container is now observed: a consumer resynchronizes
	 * with its current state.
	 */
	void onContainerConnected();

	/**
	 * Notifies that no container is observed - the observed one is
	 * stopping or being disposed, for instance: a consumer empties its view,
	 * every read now answering as if nothing were observed.
	 */
	void onContainerDisconnected();

}
