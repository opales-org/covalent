/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.model;

import org.opales.covalent.container.component.annotations.Services;

/** Reads the Threads figures. */
@Services
public interface ThreadsModelReadServices {

	/**
	 * Returns the latest sample of the pools and their threads.
	 *
	 * @return the latest sample, {@link ThreadsSnapshot#EMPTY} before the first one
	 */
	ThreadsSnapshot getSnapshot();

	/**
	 * Returns the Java stack read last, when it is the one of a given thread: only one stack is kept.
	 *
	 * @param threadId a thread
	 * @return the stack read last if it belongs to that thread, {@link ThreadStack#NONE} when no stack
	 *         was read or the last one read belongs to another thread
	 */
	ThreadStack getStack(long threadId);
}
