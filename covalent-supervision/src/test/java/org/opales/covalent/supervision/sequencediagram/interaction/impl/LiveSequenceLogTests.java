/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.interaction.impl;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opales.covalent.supervision.componentview.sequence.SequenceTraceMessage;
import org.opales.covalent.supervision.observed.CallOutcome;
import org.opales.covalent.supervision.sequencediagram.SequenceLog;
import org.opales.covalent.supervision.testsupport.CapturingLogHandler;

/**
 * The live log of a recording: written on its own thread, entry by entry, with an end that counts the
 * messages written and every failure, and a file that cannot be written ends the log with a warning and a
 * single notice to its owner.
 */
public class LiveSequenceLogTests {

	private static final Instant AT = Instant.parse("2026-10-08T07:47:12.418Z");

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private CapturingLogHandler logs;

	@Before
	public void setUp() {
		this.logs = CapturingLogHandler.install(LiveSequenceLog.class);
	}

	@After
	public void tearDown() {
		this.logs.restore();
	}

	private static SequenceTraceMessage message(final String label, final String failure) {
		SequenceTraceMessage.Facts facts = new SequenceTraceMessage.Facts(1L, "service call", "EchoServices", "echo",
				"worker-1", "Impl@1", "(hello)", failure == null ? CallOutcome.VOID : null, 1_000L, failure, false, -1L);
		return new SequenceTraceMessage(String.class, Integer.class, label, "details", 5,
				SequenceTraceMessage.Style.RETURN, facts);
	}

	private static List<String> awaitLines(final File file, final int count) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < deadline) {
			if (file.isFile()) {
				List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
				if (lines.size() >= count) {
					return lines;
				}
			}
			Thread.sleep(10);
		}
		Assert.fail("fewer than " + count + " lines in " + file);
		return List.of();
	}

	@Test
	public void theLogKnowsItsFileAndHowManyMessagesItWrote() throws Exception {
		File file = new File(this.folder.newFolder("logs"), "live.jsonl");
		LiveSequenceLog live = new LiveSequenceLog(file, SequenceLog.Format.JSON_LINES, failure -> Assert.fail());
		Assert.assertEquals(file, live.file());
		Assert.assertEquals(0, live.written());

		live.begin("Scope", AT);
		live.write(1, message("one", null));
		live.write(2, message("two", null));
		live.finish("stopped");

		Assert.assertEquals(2, live.written());
		List<String> lines = awaitLines(file, 4);
		Assert.assertTrue(lines.get(3), lines.get(3).endsWith("\"messages\":2,\"failures\":0}"));
	}

	@Test
	public void theEndCountsTheFailuresOfTheMessagesAndOfTheCompletions() throws Exception {
		File file = new File(this.folder.newFolder("logs"), "live.jsonl");
		LiveSequenceLog live = new LiveSequenceLog(file, SequenceLog.Format.JSON_LINES, failure -> Assert.fail());

		live.begin("Scope", AT);
		live.write(1, message("failed", "boom"));
		live.complete(message("late failure", "late boom"));
		live.complete(message("late success", null));
		live.finish("closed");

		List<String> lines = awaitLines(file, 3);
		Assert.assertTrue(lines.get(2), lines.get(2).endsWith("\"messages\":1,\"failures\":2}"));
	}

	@Test
	public void aFileThatCannotBeWrittenIsReportedOnceAndTheLogStops() throws Exception {
		// the "folder" of the log file is a regular file: it cannot be created
		File notAFolder = this.folder.newFile("blocker");
		File file = new File(notAFolder, "live.txt");
		AtomicInteger notices = new AtomicInteger();
		CountDownLatch noticed = new CountDownLatch(1);
		LiveSequenceLog live = new LiveSequenceLog(file, SequenceLog.Format.TEXT, failure -> {
			notices.incrementAndGet();
			Assert.assertTrue(failure instanceof IOException);
			noticed.countDown();
		});

		live.begin("Scope", AT);
		live.write(1, message("one", null));
		live.write(2, message("two", null));
		live.finish("stopped");

		Assert.assertTrue("the owner was not told", noticed.await(10, TimeUnit.SECONDS));
		// let the writer thread drain what was queued after the failure
		Thread.sleep(200);
		Assert.assertEquals(1, notices.get());
		Assert.assertEquals(1, this.logs.count(Level.WARNING));
		Assert.assertEquals(1, this.logs.messagesStartingWith("Failed to write the sequence log " + file).size());
		Assert.assertFalse(file.exists());
	}

	private static long writerThreads() {
		return Thread.getAllStackTraces().keySet().stream()
				.filter(thread -> thread.isAlive() && "covalent-supervision-sequence-log".equals(thread.getName())).count();
	}

	@Test
	public void aLogThatFailedReleasesItsWriterThreadWithoutBeingFinished() throws Exception {
		long before = writerThreads();
		File notAFolder = this.folder.newFile("blocker");
		CountDownLatch noticed = new CountDownLatch(1);
		LiveSequenceLog live = new LiveSequenceLog(new File(notAFolder, "live.txt"), SequenceLog.Format.TEXT,
				failure -> noticed.countDown());

		live.begin("Scope", AT);
		Assert.assertTrue("the owner was not told", noticed.await(10, TimeUnit.SECONDS));

		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (writerThreads() > before && System.nanoTime() < deadline) {
			Thread.sleep(20);
		}
		Assert.assertEquals(before, writerThreads());
	}
}
