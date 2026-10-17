/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Optionally strips JDK reflection/dynamic-proxy frames and Covalent's own
 * internal dispatch frames (see {@link Reflection#isJdkReflectionOrProxyFrame}
 * and {@link Reflection#isCovalentDispatchFrame}) from an exception's stack
 * trace, mutating it in place. Off by default ({@link #isEnabled()} returns
 * {@code false}), since it mutates the throwable and is purely cosmetic;
 * {@link #setEnabled(boolean)} turns it on. When disabled,
 * {@link #cleanIfEnabled} is a no-op, so call sites can unconditionally
 * route exceptions through it without checking the flag themselves.
 */
public final class StackTraceCleaner {

	private static volatile boolean enabled = false;

	private StackTraceCleaner() {
	}

	/**
	 * Enables or disables stack-trace cleaning.
	 *
	 * @param enabled {@code true} to enable cleaning
	 */
	public static void setEnabled(final boolean enabled) {
		StackTraceCleaner.enabled = enabled;
	}

	/**
	 * True if stack-trace cleaning is currently enabled.
	 *
	 * @return {@code true} if enabled
	 */
	public static boolean isEnabled() {
		return enabled;
	}

	/**
	 * Cleans the given throwable's stack trace, its cause chain, and its
	 * suppressed exceptions, but only if cleaning is currently enabled;
	 * otherwise returns it unchanged. This is the method call sites should
	 * use.
	 *
	 * @param <T>       the throwable's type
	 * @param throwable the throwable to clean, possibly {@code null}
	 * @return the same throwable, mutated in place if cleaning was enabled
	 */
	public static <T extends Throwable> T cleanIfEnabled(final T throwable) {
		if (!enabled) {
			return throwable;
		}
		return clean(throwable);
	}

	/**
	 * Unconditionally cleans the given throwable's stack trace, its cause
	 * chain, and its suppressed exceptions, mutating it in place. Cycles in
	 * the cause/suppressed graph are tolerated (each throwable is visited at
	 * most once).
	 *
	 * @param <T>       the throwable's type
	 * @param throwable the throwable to clean
	 * @return the same throwable, mutated in place
	 */
	public static <T extends Throwable> T clean(final T throwable) {
		Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		clean(throwable, visited);
		return throwable;
	}

	private static void clean(final Throwable throwable, final Set<Throwable> visited) {
		if (throwable == null || visited.contains(throwable)) {
			return;
		}
		visited.add(throwable);

		throwable.setStackTrace(clean(throwable.getStackTrace()));
		for (Throwable suppressed : throwable.getSuppressed()) {
			clean(suppressed, visited);
		}
		clean(throwable.getCause(), visited);
	}

	/**
	 * Returns a copy of the given stack trace with every JDK
	 * reflection/dynamic-proxy frame and Covalent internal dispatch frame
	 * removed.
	 *
	 * @param stackTrace the stack trace to filter
	 * @return the filtered stack trace
	 */
	public static StackTraceElement[] clean(final StackTraceElement[] stackTrace) {
		return Arrays.stream(stackTrace)
				.filter(stackTraceElement -> !isTechnicalFrame(stackTraceElement))
				.toArray(StackTraceElement[]::new);
	}

	/**
	 * True if the given stack trace element should be stripped: a JDK
	 * reflection/dynamic-proxy frame, a Covalent internal dispatch frame, or
	 * one of this class's own helper frames ({@code Reflection} itself, or
	 * an {@code AsyncManagerImpl} inner-class frame).
	 *
	 * @param stackTraceElement the stack trace element to check
	 * @return {@code true} if it should be stripped
	 */
	private static boolean isTechnicalFrame(final StackTraceElement stackTraceElement) {
		String className = stackTraceElement.getClassName();

		// JDK reflection/proxy frames and Covalent's own dispatch machinery are
		// classified by Reflection, so this stays the single place both this
		// class and CallerFinder rely on for that decision.
		return Reflection.isJdkReflectionOrProxyFrame(className) || Reflection.isCovalentDispatchFrame(className)
				|| className.equals("org.opales.covalent.container.util.Reflection")
				|| className.startsWith("org.opales.covalent.container.async.impl.AsyncManagerImpl$");
	}
}
