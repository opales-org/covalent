/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.model.impl;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.opales.covalent.supervision.threads.model.ThreadStack;
import org.opales.covalent.supervision.threads.model.ThreadsEvents;
import org.opales.covalent.supervision.threads.model.ThreadsSnapshot;

/** {@link ThreadsModelImpl} keeps the last sampling and the last stack read, and tells when they change. */
public class ThreadsModelImplTests {

	private final List<String> events = new ArrayList<>();

	private ThreadsModelImpl model;

	@Before
	public void setUp() throws Exception {
		this.model = new ThreadsModelImpl();
		Field field = ThreadsModelImpl.class.getDeclaredField("produceThreadsEvents");
		field.setAccessible(true);
		field.set(this.model, new ThreadsEvents() {
			@Override
			public void onThreadsSampled() {
				ThreadsModelImplTests.this.events.add("sampled");
			}

			@Override
			public void onStackRead(final long threadId) {
				ThreadsModelImplTests.this.events.add("stack " + threadId);
			}
		});
	}

	@Test
	public void itStartsEmpty() {
		Assert.assertSame(ThreadsSnapshot.EMPTY, this.model.getSnapshot());
		Assert.assertSame(ThreadStack.NONE, this.model.getStack(7));
		Assert.assertTrue(this.events.isEmpty());
	}

	@Test
	public void aSnapshotIsKeptAndAnnounced() {
		ThreadsSnapshot snapshot = new ThreadsSnapshot(5, true, true, List.of(), 0, List.of(), List.of(),
				ThreadsSnapshot.Callers.NONE, java.util.Map.of());

		this.model.setSnapshot(snapshot);

		Assert.assertSame(snapshot, this.model.getSnapshot());
		Assert.assertEquals(List.of("sampled"), this.events);
	}

	@Test
	public void aStackIsKeptAndAnnouncedWithItsThread() {
		ThreadStack stack = new ThreadStack(7, List.of("a.b"), List.of());

		this.model.setStack(stack);

		Assert.assertSame(stack, this.model.getStack(7));
		Assert.assertEquals(List.of("stack 7"), this.events);
	}

	@Test
	public void theStackOfAnotherThreadIsNotAnswered() {
		this.model.setStack(new ThreadStack(7, List.of("a.b"), List.of()));

		Assert.assertSame(ThreadStack.NONE, this.model.getStack(8));
	}

	@Test
	public void onlyTheLastStackIsKept() {
		this.model.setStack(new ThreadStack(7, List.of("a.b"), List.of()));
		ThreadStack latest = new ThreadStack(8, List.of("c.d"), List.of());
		this.model.setStack(latest);

		Assert.assertSame(ThreadStack.NONE, this.model.getStack(7));
		Assert.assertSame(latest, this.model.getStack(8));
	}
}
