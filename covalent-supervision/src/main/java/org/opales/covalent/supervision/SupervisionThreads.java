/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision;

import java.util.concurrent.ThreadFactory;

/**
 * The threads the supervisor starts for its own work. They share one name prefix, so that a call running on one of
 * them is told apart from the activity of the observed application, which the supervisor keeps out of what it shows.
 */
public final class SupervisionThreads {

	/** What every supervisor thread's name starts with. */
	public static final String PREFIX = "covalent-supervision-";

	private SupervisionThreads() {
		// Not meant to be instantiated.
	}

	/**
	 * Builds a daemon thread named after its purpose.
	 *
	 * @param work    what the thread runs
	 * @param purpose what it is for, appended to the common prefix
	 * @return the thread, not started
	 */
	public static Thread newThread(final Runnable work, final String purpose) {
		Thread thread = new Thread(work, PREFIX + purpose);
		thread.setDaemon(true);
		return thread;
	}

	/**
	 * Builds the threads of an executor.
	 *
	 * @param purpose what the threads are for, appended to the common prefix
	 * @return a factory of daemon threads
	 */
	public static ThreadFactory factory(final String purpose) {
		return work -> newThread(work, purpose);
	}

	/**
	 * Tells whether the calling thread is one of the supervisor's own.
	 *
	 * @return whether the current thread's name starts with the common prefix
	 */
	public static boolean isCurrent() {
		return Thread.currentThread().getName().startsWith(PREFIX);
	}
}
