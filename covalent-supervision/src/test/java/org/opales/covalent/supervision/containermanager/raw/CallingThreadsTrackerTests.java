/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.containermanager.raw;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.supervision.observed.ObservedCallingThread;

public class CallingThreadsTrackerTests {

	private final CallingThreadsTracker tracker = new CallingThreadsTracker();

	private ObservedCallingThread only() {
		List<ObservedCallingThread> threads = this.tracker.read();
		Assert.assertEquals(1, threads.size());
		return threads.get(0);
	}

	@Test
	public void nestedCallsShowTheInnermostAndTheOutermostComponent() {
		this.tracker.entered(String.class, "outer");
		this.tracker.entered(Integer.class, "inner");
		ObservedCallingThread inCall = this.only();
		Assert.assertTrue(inCall.inCall());
		Assert.assertEquals("Integer.inner", inCall.call());
		Assert.assertEquals(String.class, inCall.component());
		Assert.assertEquals(Thread.currentThread().threadId(), inCall.threadId());
		Assert.assertEquals(2, inCall.calls());

		this.tracker.exited(Integer.class);
		Assert.assertEquals("String.outer", this.only().call());
		this.tracker.exited(String.class);
		ObservedCallingThread after = this.only();
		Assert.assertFalse(after.inCall());
		Assert.assertNull(after.call());
		Assert.assertEquals(-1, after.callNanos());
		Assert.assertTrue(after.busyNanos() > 0);
		// Handed over by the reads made while it was open, the call's component is not again.
		Assert.assertEquals(java.util.Set.of(), after.components());
		Assert.assertEquals(String.class, after.lastComponent());
	}

	@Test
	public void callsBetweenTwoReadsAreEachHandedOverOnce() {
		this.tracker.entered(String.class, "first");
		this.tracker.exited(String.class);
		this.tracker.entered(Integer.class, "second");
		this.tracker.exited(Integer.class);
		ObservedCallingThread read = this.only();
		Assert.assertEquals(java.util.Set.of(String.class, Integer.class), read.components());
		Assert.assertEquals(Integer.class, read.lastComponent());
		Assert.assertEquals(java.util.Set.of(), this.only().components());

		// A call still open counts at every read until it ends.
		this.tracker.entered(Long.class, "long");
		Assert.assertEquals(java.util.Set.of(Long.class), this.only().components());
		Assert.assertEquals(java.util.Set.of(Long.class), this.only().components());
	}

	@Test
	public void unmatchedOrAnonymousCallsAreIgnored() {
		this.tracker.exited(String.class);
		this.tracker.entered(null, "nothing");
		Assert.assertEquals(List.of(), this.tracker.read());
	}

	@Test
	public void resetForgetsTheThreadsUntilTheyCallAgain() {
		this.tracker.entered(String.class, "call");
		this.tracker.reset();
		Assert.assertEquals(List.of(), this.tracker.read());
		// The call open before the reset ends without effect.
		this.tracker.exited(String.class);
		this.tracker.entered(String.class, "again");
		Assert.assertEquals("String.again", this.only().call());
		this.tracker.exited(String.class);
	}
}
