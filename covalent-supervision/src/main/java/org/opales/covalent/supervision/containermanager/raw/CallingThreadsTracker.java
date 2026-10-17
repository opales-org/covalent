/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.containermanager.raw;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.opales.covalent.container.component.ComponentAnnotations;
import org.opales.covalent.supervision.observed.ObservedCallingThread;

/**
 * Keeps, for each thread running calls of the observed components, what it runs and how long:
 * fed on the calls' own threads by the bridge's instrumentation callbacks, read a few times a
 * second by the Thread Supervisor. Covalent's own components' calls are left out. A call costs a thread-local read and a few writes, made by
 * the calling thread only; nothing is published per call.
 */
final class CallingThreadsTracker {

	/** One thread's calls. */
	private static final class Calls {

		private final Thread thread;

		/** The {@link CallingThreadsTracker#epoch} it was registered in. */
		private final int epoch;

		/** The calls open on the thread, innermost last: touched by the thread itself only. */
		private final Deque<String> open = new ArrayDeque<>();

		private volatile String innermost;

		private volatile Class<?> outerComponent;

		/** When the outermost open call started, {@code 0} when none is open. */
		private volatile long outerStart;

		private volatile long busyNanos;

		private volatile long calls;

		/** The component of its latest outermost call. */
		private volatile Class<?> lastComponent;

		/**
		 * The components of the outermost calls it entered since the previous {@link #read()},
		 * replaced by an empty set at each read.
		 */
		private volatile Set<Class<?>> recentComponents = ConcurrentHashMap.newKeySet();

		private Calls(final Thread thread, final int epoch) {
			this.thread = thread;
			this.epoch = epoch;
		}
	}

	/**
	 * Whether a component is one of Covalent's own: its calls - the supervisor reading the
	 * observed container, the container's own plumbing - are not the application's work.
	 */
	private static final ClassValue<Boolean> INTERNAL = new ClassValue<>() {
		@Override
		protected Boolean computeValue(final Class<?> type) {
			return ComponentAnnotations.isInternalComponent(type);
		}
	};

	private final ThreadLocal<Calls> current = new ThreadLocal<>();

	private final Map<Thread, Calls> threads = new ConcurrentHashMap<>();

	/** Incremented by {@link #reset()}: a thread's calls from before are dropped at its next call. */
	private volatile int epoch;

	private Calls calls() {
		Calls calls = this.current.get();
		if (calls == null || calls.epoch != this.epoch) {
			calls = new Calls(Thread.currentThread(), this.epoch);
			this.current.set(calls);
			this.threads.put(calls.thread, calls);
		}
		return calls;
	}

	/**
	 * @param component the component running the call
	 * @param method    the method called
	 */
	void entered(final Class<?> component, final String method) {
		if (component == null || INTERNAL.get(component)) {
			return;
		}
		Calls calls = this.calls();
		String label = component.getSimpleName() + "." + method;
		if (calls.open.isEmpty()) {
			calls.recentComponents.add(component);
			calls.lastComponent = component;
			calls.outerComponent = component;
			calls.outerStart = System.nanoTime();
		}
		calls.open.addLast(label);
		calls.innermost = label;
		calls.calls++;
	}

	/** Ends the innermost call open on the calling thread. */
	void exited(final Class<?> component) {
		if (component == null || INTERNAL.get(component)) {
			return;
		}
		Calls calls = this.current.get();
		if (calls == null || calls.epoch != this.epoch || calls.open.isEmpty()) {
			return;
		}
		calls.open.removeLast();
		calls.innermost = calls.open.peekLast();
		if (calls.open.isEmpty()) {
			// Busy time first, then the end of the call: a read in between counts it at worst once.
			calls.busyNanos += System.nanoTime() - calls.outerStart;
			calls.outerStart = 0;
			calls.outerComponent = null;
		}
	}

	/**
	 * Meant for a single reader, the Thread Supervisor's sampling: the components a thread's
	 * calls entered are each handed over once, at the read following them.
	 *
	 * @return the threads that ran calls and are still alive, by thread id
	 */
	List<ObservedCallingThread> read() {
		long now = System.nanoTime();
		List<ObservedCallingThread> states = new ArrayList<>();
		for (Calls calls : this.threads.values()) {
			if (!calls.thread.isAlive()) {
				this.threads.remove(calls.thread);
				continue;
			}
			long busy = calls.busyNanos;
			long start = calls.outerStart;
			boolean inCall = start != 0;
			Set<Class<?>> recent = calls.recentComponents;
			calls.recentComponents = ConcurrentHashMap.newKeySet();
			Class<?> outer = calls.outerComponent;
			if (inCall && outer != null) {
				// A call still open: its component still works, in this interval as in the ones before.
				recent.add(outer);
			}
			states.add(new ObservedCallingThread(calls.thread.threadId(), calls.thread.getName(), inCall,
					inCall ? Math.max(0, now - start) : -1, inCall ? calls.innermost : null,
					inCall ? outer : null, busy + (inCall ? Math.max(0, now - start) : 0), calls.calls,
					calls.lastComponent, Set.copyOf(recent)));
		}
		states.sort(Comparator.comparingLong(ObservedCallingThread::threadId));
		return states;
	}

	/** Forgets every thread, as when another container is observed. */
	void reset() {
		this.epoch++;
		this.threads.clear();
	}
}
