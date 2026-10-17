/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Test;

/**
 * The threads the supervisor starts carry one common name prefix, are daemons, and are recognized by it.
 */
public class SupervisionThreadsTests {

	private static boolean recognizedOn(final Thread thread) throws InterruptedException {
		AtomicBoolean recognized = new AtomicBoolean();
		Thread probe = new Thread(() -> {
			recognized.set(SupervisionThreads.isCurrent());
		}, thread.getName());
		probe.start();
		probe.join();
		return recognized.get();
	}

	@Test
	public void aSupervisorThreadIsADaemonWithTheCommonPrefix() {
		Thread thread = SupervisionThreads.newThread(() -> {
		}, "sampler");

		Assert.assertEquals("covalent-supervision-sampler", thread.getName());
		Assert.assertTrue(thread.isDaemon());
	}

	@Test
	public void theFactoryBuildsSuchThreads() {
		Thread thread = SupervisionThreads.factory("commands").newThread(() -> {
		});

		Assert.assertEquals("covalent-supervision-commands", thread.getName());
		Assert.assertTrue(thread.isDaemon());
	}

	@Test
	public void aSupervisorThreadIsRecognizedByItsName() throws InterruptedException {
		Assert.assertTrue(recognizedOn(SupervisionThreads.newThread(() -> {
		}, "anything")));
	}

	@Test
	public void otherThreadsAreNotRecognized() throws InterruptedException {
		Assert.assertFalse(SupervisionThreads.isCurrent());
		Assert.assertFalse(recognizedOn(new Thread(() -> {
		}, "worker-1")));
		Assert.assertFalse(recognizedOn(new Thread(() -> {
		}, "covalent-supervisor-like")));
	}
}
