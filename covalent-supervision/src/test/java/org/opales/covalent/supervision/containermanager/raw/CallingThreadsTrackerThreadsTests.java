/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.containermanager.raw;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.container.component.internals.Internal;
import org.opales.covalent.supervision.observed.ObservedCallingThread;

/**
 * {@link CallingThreadsTracker} across threads and for components that are not the application's: Covalent's own
 * components leave no trace, a call ended twice is counted once, several threads are listed by thread id while they
 * live, and a thread that ended is forgotten at the next read.
 */
public class CallingThreadsTrackerThreadsTests {

	/** A component of Covalent's own. */
	@Internal
	private static final class InternalComponent {
	}

	private final CallingThreadsTracker tracker = new CallingThreadsTracker();

	/** Runs a call to completion on a new thread that then waits to be released, and returns it started. */
	private Thread callingThread(final Class<?> component, final CountDownLatch release) throws InterruptedException {
		CountDownLatch called = new CountDownLatch(1);
		Thread thread = new Thread(() -> {
			this.tracker.entered(component, "work");
			this.tracker.exited(component);
			called.countDown();
			try {
				release.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}, "calling-threads-tracker-tests");
		thread.start();
		called.await();
		return thread;
	}

	@Test
	public void callsOfCovalentsOwnComponentsLeaveNoTrace() {
		this.tracker.entered(InternalComponent.class, "work");
		this.tracker.exited(InternalComponent.class);

		Assert.assertEquals(List.of(), this.tracker.read());
	}

	@Test
	public void anInternalCallDoesNotEndTheApplicationCallOpenOnTheThread() {
		this.tracker.entered(String.class, "outer");
		this.tracker.entered(InternalComponent.class, "plumbing");
		this.tracker.exited(InternalComponent.class);

		List<ObservedCallingThread> read = this.tracker.read();

		Assert.assertEquals(1, read.size());
		Assert.assertTrue(read.get(0).inCall());
		Assert.assertEquals("String.outer", read.get(0).call());
		Assert.assertEquals(1, read.get(0).calls());
		this.tracker.exited(String.class);
	}

	@Test
	public void aCallEndedTwiceIsEndedOnceAndItsBusyTimeCountedOnce() {
		this.tracker.entered(String.class, "work");
		this.tracker.exited(String.class);
		long busy = this.tracker.read().get(0).busyNanos();

		this.tracker.exited(String.class);

		ObservedCallingThread after = this.tracker.read().get(0);
		Assert.assertEquals(busy, after.busyNanos());
		Assert.assertFalse(after.inCall());
		Assert.assertEquals(1, after.calls());
	}

	@Test
	public void severalThreadsAreListedByThreadIdWhileTheyLive() throws InterruptedException {
		CountDownLatch release = new CountDownLatch(1);
		Thread first = this.callingThread(String.class, release);
		Thread second = this.callingThread(Integer.class, release);
		try {
			List<ObservedCallingThread> read = this.tracker.read();

			Assert.assertEquals(2, read.size());
			Assert.assertTrue(read.get(0).threadId() < read.get(1).threadId());
			Assert.assertEquals(first.threadId(), read.get(0).threadId());
			Assert.assertEquals(second.threadId(), read.get(1).threadId());
			Assert.assertEquals("calling-threads-tracker-tests", read.get(0).threadName());
			Assert.assertEquals(String.class, read.get(0).lastComponent());
			Assert.assertEquals(Integer.class, read.get(1).lastComponent());
		} finally {
			release.countDown();
			first.join();
			second.join();
		}
	}

	@Test
	public void aThreadThatEndedIsForgottenAtTheNextRead() throws InterruptedException {
		CountDownLatch release = new CountDownLatch(1);
		Thread ending = this.callingThread(String.class, release);
		this.tracker.entered(Integer.class, "main");
		this.tracker.exited(Integer.class);
		Assert.assertEquals(2, this.tracker.read().size());

		release.countDown();
		ending.join();

		List<ObservedCallingThread> read = this.tracker.read();
		Assert.assertEquals(1, read.size());
		Assert.assertEquals(Thread.currentThread().threadId(), read.get(0).threadId());
	}
}
