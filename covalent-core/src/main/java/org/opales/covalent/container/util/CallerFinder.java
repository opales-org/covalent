/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.util;

import java.lang.StackWalker.StackFrame;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.opales.covalent.container.events.impl.EventsManagerImpl;
import org.opales.covalent.container.impl.CovalentContainerImpl;
import org.opales.covalent.container.lifecycle.impl.LifecycleManagerImpl;
import org.opales.covalent.container.raw.impl.RawComponentsImpl;
import org.opales.covalent.container.raw.impl.RawServicesAndParametersPort;
import org.opales.covalent.container.raw.impl.RawEventsPort;

/**
 * Walks the current thread's call stack to identify the "real" caller of a
 * container operation - the application or component class that ultimately
 * triggered it - skipping past Covalent's own internal dispatch frames
 * (managers, invocation handlers) and JDK reflection/proxy frames. Used
 * wherever the container needs to know which component is asking (for
 * example, resolving a raw component's own instance from inside a call it
 * made into the container).
 */
public final class CallerFinder {

	/** Not meant to be instantiated; every member is static. */
	private CallerFinder() {
	}

	private static final Logger log = Logger.getLogger(CallerFinder.class.getName());

	private static final StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

	// Upper bound on how many frames are inspected while walking past Covalent's
	// own internal dispatch frames to find the real caller. StackWalker.limit()
	// is used specifically so a deep application stack is never walked further
	// than this regardless of how many frames are actually excluded: this bound
	// only needs headroom over the depth of Covalent's own internal call chains
	// (container/manager delegation, event/RR dispatch, reflective invocation),
	// not over application code. The deepest known chains are those of event
	// dispatch and raw components; checkLimit() logs
	// loudly if this bound is ever exhausted, so an increase in internal dispatch depth is caught rather than silently misresolving the caller.
	private static final int stackLimit = 25;

	private static final List<Class<?>> excludedClasses = new ArrayList<>();

	static {
		excludedClasses.add(EventsManagerImpl.class);
		excludedClasses.add(CovalentContainerImpl.class);
		excludedClasses.add(LifecycleManagerImpl.class);
		excludedClasses.add(RawComponentsImpl.class);
		// RawComponentsImpl's internal port-family collaborators also call
		// getCaller() from methods invoked on behalf of RawComponentsImpl, so each
		// one has to be excluded here too, exactly like RawComponentsImpl itself -
		// otherwise getCaller() stops one frame too early and resolves the
		// collaborator itself as the caller instead of the real raw component.
		excludedClasses.add(RawServicesAndParametersPort.class);
		excludedClasses.add(RawEventsPort.class);
		excludedClasses.add(Reflection.class);
	}

	/**
	 * <p>
	 * Returns the caller class (outside from container), excluding proxy
	 * classes.
	 * </p>
	 * 
	 * @return the caller class outside from container
	 */
	public static Class<?> getCaller() {
		List<StackFrame> stack = walker.walk(s -> s.limit(stackLimit).collect(Collectors.toList()));
		return resolveCaller(stack, frame -> excludedClasses.contains(frame.getDeclaringClass()));
	}

	/**
	 * <p>
	 * Returns the class that called the method which asks for its caller. Only
	 * invocation handler frames and JDK reflection or proxy frames are skipped:
	 * unlike {@link #getCaller()}, the container's own classes are not.
	 * </p>
	 * 
	 * @return the immediate caller class, excluding proxy and dispatch frames
	 */
	public static Class<?> getImmediateCaller() {
		List<StackFrame> stack = walker.walk(s -> s.limit(stackLimit).collect(Collectors.toList()));
		return resolveCaller(stack, frame -> false);
	}

	/**
	 * <p>
	 * Returns the caller class (outside from container). Excluded class are
	 * skipped.
	 * </p>
	 * 
	 * @param excluded a list of classes to exclude.
	 * @return the caller class outside from container
	 */
	public static Class<?> getCaller(final List<Class<?>> excluded) {
		List<StackFrame> stack = walker.walk(s -> s.limit(stackLimit).collect(Collectors.toList()));
		return resolveCaller(stack,
				frame -> excluded.contains(frame.getDeclaringClass())
						|| excludedClasses.contains(frame.getDeclaringClass()));
	}

	/**
	 * Finds the first frame, from the third one on, that is not skipped. The
	 * first two frames are the finder's own method and the container method
	 * that asks for the caller. Invocation handler frames and JDK reflection or
	 * proxy frames are always skipped, on top of the frames the given
	 * condition skips. The stack is read by each public method, not here, so
	 * that these two first frames stay at the same indexes.
	 *
	 * @param stack the frames of the current stack, most recent first
	 * @param skip  tells whether a frame is skipped, in addition to the frames
	 *              that are always skipped
	 * @return the declaring class of the frame found
	 */
	private static Class<?> resolveCaller(final List<StackFrame> stack, final Predicate<StackFrame> skip) {
		// i is only advanced past a frame once that frame itself has been
		// tested at the current index - a frame is never re-tested and never
		// skipped without being examined first. This matters at the boundary:
		// when the real caller turns out to be the last frame in stack, the loop stops on it (i < stack.size()) instead of overshooting to
		// i == stack.size(), which would make checkLimit() log a false "limit reached"
		// failure even though the caller has just been found.
		int i = 2;
		while (i < stack.size() && (skip.test(stack.get(i)) || stack.get(i).getClassName().endsWith("InvocationHandler")
				|| Reflection.isJdkReflectionOrProxyFrame(stack.get(i).getClassName()))) {
			i++;
		}
		checkLimit(i, stack);
		return stack.get(Math.min(i, stack.size() - 1)).getDeclaringClass();
	}

	private static void checkLimit(final int index, final List<StackFrame> stack) {
		if (index == stack.size()) {
			CovalentDiagnostics.severe(log,
					"Limit stack size reached. Covalent container may not work with raw components.",
					new IllegalStateException("Unable to resolve a caller outside Covalent."));
		}
	}

}
