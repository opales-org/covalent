/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.model.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.supervision.threads.model.ThreadsEvents;
import org.opales.covalent.supervision.threads.model.ThreadsModel;
import org.opales.covalent.supervision.threads.model.ThreadsModelReadServices;
import org.opales.covalent.supervision.threads.model.ThreadsModelServices;
import org.opales.covalent.supervision.threads.model.ThreadStack;
import org.opales.covalent.supervision.threads.model.ThreadsSnapshot;

/** Holds the latest sample and the last Java stack read, and passes them on. */
@Implementation
public class ThreadsModelImpl implements ThreadsModel, ThreadsModelServices, ThreadsModelReadServices {

	/** Creates the Model; the container injects the producer of its events. */
	public ThreadsModelImpl() {
		// Nothing to initialize.
	}

	@ProducedEvents
	private ThreadsEvents produceThreadsEvents;

	private volatile ThreadsSnapshot snapshot = ThreadsSnapshot.EMPTY;

	private volatile ThreadStack stack = ThreadStack.NONE;

	@Override
	public void setSnapshot(final ThreadsSnapshot snapshot) {
		this.snapshot = snapshot;
		this.produceThreadsEvents.onThreadsSampled();
	}

	@Override
	public void setStack(final ThreadStack stack) {
		this.stack = stack;
		this.produceThreadsEvents.onStackRead(stack.threadId());
	}

	@Override
	public ThreadsSnapshot getSnapshot() {
		return this.snapshot;
	}

	@Override
	public ThreadStack getStack(final long threadId) {
		ThreadStack last = this.stack;
		return threadId == last.threadId() ? last : ThreadStack.NONE;
	}
}
