/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.observed;

import java.util.Set;

/**
 * A thread that ran a call of an observed component - a provided service, an offered
 * parameter, an event delivery - at the moment it was read. Known only while the observed
 * container is instrumented (data flows on).
 *
 * @param threadId   the thread's id
 * @param threadName the thread's name
 * @param inCall     whether it is in a call now
 * @param callNanos  how long its outermost current call has run, {@code -1} when in none
 * @param call       its innermost current call, as {@code Component.method}, {@code null} when in none
 * @param component  the component of its outermost current call, {@code null} when in none
 * @param busyNanos  the time it spent in calls since first seen, its current one included
 * @param calls      the calls it ran since first seen, nested ones included
 * @param lastComponent the component of its latest outermost call, {@code null} before its first
 * @param components the components of the outermost calls it entered since it was last read, or
 *                   still in: however short, a call is never missed
 */
public record ObservedCallingThread(long threadId, String threadName, boolean inCall, long callNanos, String call,
		Class<?> component, long busyNanos, long calls, Class<?> lastComponent, Set<Class<?>> components) {
}
