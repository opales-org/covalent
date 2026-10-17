/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.model;

import org.opales.covalent.container.component.annotations.Events;

/** Tells that the Threads figures changed. */
@Events
public interface ThreadsEvents {

	/** A new sample was taken - see {@link ThreadsModelReadServices#getSnapshot()}. */
	void onThreadsSampled();

	/**
	 * A thread's stack was read - see {@link ThreadsModelReadServices#getStack(long)}.
	 *
	 * @param threadId the thread
	 */
	void onStackRead(long threadId);
}
