/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.workers.impl;

/**
 * The component calling {@code AsyncServices} on the current thread, set by that component's
 * used-services proxy for the time of the call: the async manager reads it to tell which
 * component submitted a task. Only {@code AsyncServices} calls set it, at the cost of a
 * thread-local write and restore; it is read only while task tracking is on.
 */
public final class TaskSubmitter {

	private static final ThreadLocal<Class<?>> CURRENT = new ThreadLocal<>();

	private TaskSubmitter() {
	}

	/**
	 * Records the component whose {@code AsyncServices} call starts on this thread.
	 *
	 * @param component the component starting an {@code AsyncServices} call
	 * @return the component set before, to pass back to {@link #exit(Class)}
	 */
	public static Class<?> enter(final Class<?> component) {
		Class<?> previous = CURRENT.get();
		CURRENT.set(component);
		return previous;
	}

	/**
	 * Restores the component recorded before the matching {@link #enter(Class)}.
	 *
	 * @param previous the component {@link #enter(Class)} returned
	 */
	public static void exit(final Class<?> previous) {
		if (previous == null) {
			CURRENT.remove();
		} else {
			CURRENT.set(previous);
		}
	}

	/**
	 * Reads the component recorded for this thread.
	 *
	 * @return the component calling {@code AsyncServices} on this thread, {@code null} if none
	 */
	public static Class<?> current() {
		return CURRENT.get();
	}
}
