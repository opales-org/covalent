/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport.observed.impl;

import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.workers.WorkerPoolKind;
import org.opales.covalent.supervision.observed.ObservedParameters;
import org.opales.covalent.supervision.testsupport.observed.StubObservedCallStreamComponent;

@Implementation
public class StubObservedCallStreamComponentImpl implements StubObservedCallStreamComponent, ObservedParameters {

	private volatile boolean callStreamEnabled = false;

	@Override
	public void setCallStreamEnabled(final boolean enabled) {
		this.callStreamEnabled = enabled;
	}

	@Override
	public void setTaskTracking(final boolean tracking) {
		// Only the call stream is recorded by this stub.
	}

	@Override
	public boolean isTaskTracking() {
		return false;
	}

	@Override
	public void setWorkerCount(final WorkerPoolKind kind, final int count) {
		// Only the call stream is recorded by this stub.
	}

	@Override
	public void setQueueCapacity(final WorkerPoolKind kind, final int capacity) {
		// Only the call stream is recorded by this stub.
	}
}
