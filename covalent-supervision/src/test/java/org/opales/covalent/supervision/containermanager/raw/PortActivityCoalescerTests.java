/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.containermanager.raw;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import org.opales.covalent.supervision.observed.ObservedPortActivityEvents;
import org.opales.covalent.supervision.observed.ObservedPortKind;

/**
 * Unit tests for {@link PortActivityCoalescer}, exercising the debounced active/inactive
 * coalescing logic directly - no container, no observed calls, just the call-start/call-finish
 * API and the recorded {@link ObservedPortActivityEvents} notifications.
 */
public class PortActivityCoalescerTests {

	private interface PortA {
	}

	private interface PortB {
	}

	private static final class Change {
		final Class<?> componentType;
		final Class<?> portType;
		final ObservedPortKind portKind;
		final boolean active;

		Change(final Class<?> componentType, final Class<?> portType, final ObservedPortKind portKind,
				final boolean active) {
			this.componentType = componentType;
			this.portType = portType;
			this.portKind = portKind;
			this.active = active;
		}
	}

	private final List<Change> changes = new CopyOnWriteArrayList<>();
	private volatile CountDownLatch latch;

	private final ObservedPortActivityEvents sink = (componentType, portType, portKind, active) -> {
		this.changes.add(new Change(componentType, portType, portKind, active));
		if (this.latch != null) {
			this.latch.countDown();
		}
	};

	private PortActivityCoalescer coalescer;

	@After
	public void tearDown() {
		if (this.coalescer != null) {
			this.coalescer.shutdown();
		}
	}

	@Test
	public void firstCallStartedReportsActiveImmediately() {
		this.coalescer = new PortActivityCoalescer(this.sink);
		this.coalescer.callStarted(String.class, PortA.class, ObservedPortKind.SERVICE);
		Assert.assertEquals(1, this.changes.size());
		Assert.assertEquals(String.class, this.changes.get(0).componentType);
		Assert.assertEquals(PortA.class, this.changes.get(0).portType);
		Assert.assertEquals(ObservedPortKind.SERVICE, this.changes.get(0).portKind);
		Assert.assertTrue(this.changes.get(0).active);
	}

	@Test
	public void furtherCallsStartedWhileActiveDoNotReportAgain() {
		this.coalescer = new PortActivityCoalescer(this.sink);
		this.coalescer.callStarted(String.class, PortA.class, ObservedPortKind.SERVICE);
		this.coalescer.callStarted(String.class, PortA.class, ObservedPortKind.SERVICE);
		this.coalescer.callStarted(String.class, PortA.class, ObservedPortKind.SERVICE);
		Assert.assertEquals("only the first call should have reported anything", 1, this.changes.size());
	}

	@Test
	public void differentComponentsOrPortKindsAreTrackedIndependently() {
		this.coalescer = new PortActivityCoalescer(this.sink);
		this.coalescer.callStarted(String.class, PortA.class, ObservedPortKind.SERVICE);
		this.coalescer.callStarted(String.class, PortA.class, ObservedPortKind.PARAMETER);
		this.coalescer.callStarted(Integer.class, PortA.class, ObservedPortKind.SERVICE);
		Assert.assertEquals(3, this.changes.size());
		Assert.assertTrue(this.changes.stream().allMatch(c -> c.active));
	}

	@Test
	public void differentPortTypesOfTheSameComponentAndKindAreTrackedIndependently() {
		this.coalescer = new PortActivityCoalescer(this.sink);
		this.coalescer.callStarted(String.class, PortA.class, ObservedPortKind.SERVICE);
		this.coalescer.callStarted(String.class, PortB.class, ObservedPortKind.SERVICE);
		// A call on one interface must not be coalesced with, or suppressed by, the other.
		Assert.assertEquals(2, this.changes.size());
		Assert.assertEquals(PortA.class, this.changes.get(0).portType);
		Assert.assertEquals(PortB.class, this.changes.get(1).portType);

		this.coalescer.callStarted(String.class, PortA.class, ObservedPortKind.SERVICE);
		Assert.assertEquals("PortA was already reported active; a second call on it must not report again", 2,
				this.changes.size());
	}

	@Test
	public void callFinishedEventuallyReportsInactiveAfterLinger() throws InterruptedException {
		this.coalescer = new PortActivityCoalescer(this.sink);
		this.latch = new CountDownLatch(2);
		this.coalescer.callStarted(String.class, PortA.class, ObservedPortKind.EVENT);
		this.coalescer.callFinished(String.class, PortA.class, ObservedPortKind.EVENT);

		Assert.assertTrue("inactive report never arrived",
				this.latch.await(PortActivityCoalescer.LINGER_MILLIS * 5, TimeUnit.MILLISECONDS));
		Assert.assertEquals(2, this.changes.size());
		Assert.assertTrue(this.changes.get(0).active);
		Assert.assertFalse(this.changes.get(1).active);
	}

	@Test
	public void callStartedBeforeLingerElapsesCancelsTheInactiveReport() throws InterruptedException {
		this.coalescer = new PortActivityCoalescer(this.sink);
		this.coalescer.callStarted(String.class, PortA.class, ObservedPortKind.PARAMETER);
		this.coalescer.callFinished(String.class, PortA.class, ObservedPortKind.PARAMETER);
		// Restart before the linger window (comfortably, to avoid timing flakiness) expires.
		this.coalescer.callStarted(String.class, PortA.class, ObservedPortKind.PARAMETER);

		// Wait well past the linger window: if the pending inactive report had not been
		// cancelled, it would have fired by now.
		Thread.sleep(PortActivityCoalescer.LINGER_MILLIS * 3);

		Assert.assertEquals("no inactive report should have been produced", 1, this.changes.size());
		Assert.assertTrue(this.changes.get(0).active);

		// Still finish cleanly so tearDown's shutdown() doesn't race a pending task.
		this.coalescer.callFinished(String.class, PortA.class, ObservedPortKind.PARAMETER);
	}

	@Test
	public void callFinishedWithoutAMatchingStartedIsIgnored() {
		this.coalescer = new PortActivityCoalescer(this.sink);
		this.coalescer.callFinished(String.class, PortA.class, ObservedPortKind.SERVICE);
		Assert.assertTrue("an unmatched callFinished must not report or throw", this.changes.isEmpty());
	}

	@Test
	public void nullComponentTypeIsIgnoredOnBothStartAndFinish() {
		this.coalescer = new PortActivityCoalescer(this.sink);
		this.coalescer.callStarted(null, PortA.class, ObservedPortKind.SERVICE);
		this.coalescer.callFinished(null, PortA.class, ObservedPortKind.SERVICE);
		Assert.assertTrue(this.changes.isEmpty());
	}

	@Test
	public void resetClearsStateWithoutNotifying() throws InterruptedException {
		this.coalescer = new PortActivityCoalescer(this.sink);
		this.coalescer.callStarted(String.class, PortA.class, ObservedPortKind.SERVICE);
		this.changes.clear();

		this.coalescer.reset();
		// A pending inactive report (there is none here, since the call never finished) would
		// have been cancelled too; give it a moment either way and confirm silence.
		Thread.sleep(PortActivityCoalescer.LINGER_MILLIS * 2);
		Assert.assertTrue("reset() must not itself produce any notification", this.changes.isEmpty());

		// After a reset, the same component/port pair is tracked as fresh: a new call
		// reports active again instead of being coalesced with the pre-reset state.
		this.coalescer.callStarted(String.class, PortA.class, ObservedPortKind.SERVICE);
		Assert.assertEquals(1, this.changes.size());
		Assert.assertTrue(this.changes.get(0).active);
	}

	@Test
	public void resetCancelsAPendingInactiveReport() throws InterruptedException {
		this.coalescer = new PortActivityCoalescer(this.sink);
		this.coalescer.callStarted(String.class, PortA.class, ObservedPortKind.SERVICE);
		this.coalescer.callFinished(String.class, PortA.class, ObservedPortKind.SERVICE);
		this.changes.clear();

		this.coalescer.reset();
		Thread.sleep(PortActivityCoalescer.LINGER_MILLIS * 3);
		Assert.assertTrue("the pending inactive report must have been cancelled by reset()", this.changes.isEmpty());
	}

	@Test
	public void shutdownIsSafeWithoutAnyPriorActivity() {
		this.coalescer = new PortActivityCoalescer(this.sink);
		this.coalescer.shutdown();
		// Calling shutdown() again (as tearDown() will) must not throw either.
		this.coalescer.shutdown();
	}
}
