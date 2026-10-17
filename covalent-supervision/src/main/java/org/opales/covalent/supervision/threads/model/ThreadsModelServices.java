/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.model;

import org.opales.covalent.container.component.annotations.Services;

/** Publishes the Threads figures. */
@Services
public interface ThreadsModelServices {

	/**
	 * Publishes a sample via {@link ThreadsEvents#onThreadsSampled()}.
	 *
	 * @param snapshot the sample
	 */
	void setSnapshot(ThreadsSnapshot snapshot);

	/**
	 * Publishes a thread's stack via {@link ThreadsEvents#onStackRead(long)}.
	 *
	 * @param stack the stack
	 */
	void setStack(ThreadStack stack);
}
