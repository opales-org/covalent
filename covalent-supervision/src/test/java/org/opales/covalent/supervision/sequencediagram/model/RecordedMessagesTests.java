/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.model;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assert;
import org.junit.Test;
import org.opales.covalent.supervision.componentview.sequence.SequenceTraceMessage;

/**
 * Verifies a session's messages: kept in order, read as they are at the moment of the read, and
 * readable while they are added.
 */
public class RecordedMessagesTests {

	private static SequenceTraceMessage message(final int number) {
		return new SequenceTraceMessage(String.class, Integer.class, "m" + number, "", number,
				SequenceTraceMessage.Style.CALL);
	}

	@Test
	public void theMessagesAreKeptInTheOrderTheyAreAddedAcrossBlocks() {
		RecordedMessages recorded = new RecordedMessages();
		for (int number = 0; number < 3000; number++) {
			recorded.add(message(number));
		}

		List<SequenceTraceMessage> messages = recorded.upToNow();
		Assert.assertEquals(3000, messages.size());
		Assert.assertEquals("m0", messages.get(0).getLabel());
		Assert.assertEquals("m1024", messages.get(1024).getLabel());
		Assert.assertEquals("m2999", messages.get(2999).getLabel());
	}

	@Test
	public void aReadOutsideTheMessagesThereAreIsRefused() {
		RecordedMessages recorded = new RecordedMessages();
		recorded.add(message(0));
		recorded.add(message(1));
		List<SequenceTraceMessage> messages = recorded.upToNow();
		recorded.add(message(2));

		Assert.assertThrows(IndexOutOfBoundsException.class, () -> messages.get(-1));
		Assert.assertThrows(IndexOutOfBoundsException.class, () -> messages.get(2));
		Assert.assertEquals("m1", messages.get(1).getLabel());
	}

	@Test
	public void aReadHoldsTheMessagesThereWereWhenItWasMade() {
		RecordedMessages recorded = new RecordedMessages();
		recorded.add(message(0));
		List<SequenceTraceMessage> before = recorded.upToNow();

		recorded.add(message(1));

		Assert.assertEquals(1, before.size());
		Assert.assertEquals(2, recorded.upToNow().size());
		Assert.assertThrows(IndexOutOfBoundsException.class, () -> before.get(1));
	}

	@Test(expected = UnsupportedOperationException.class)
	public void aReadCannotChangeTheMessages() {
		RecordedMessages recorded = new RecordedMessages();
		recorded.upToNow().add(message(0));
	}

	@Test
	public void theMessagesCanBeReadWhileTheyAreAdded() throws Exception {
		RecordedMessages recorded = new RecordedMessages();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		Thread writer = new Thread(() -> {
			for (int number = 0; number < 50_000; number++) {
				recorded.add(message(number));
			}
		});
		Thread reader = new Thread(() -> {
			try {
				while (recorded.size() < 50_000) {
					List<SequenceTraceMessage> messages = recorded.upToNow();
					for (int index = 0; index < messages.size(); index++) {
						if (messages.get(index).getRelativeTimeMillis() != index) {
							throw new AssertionError("message " + index + " out of place");
						}
					}
				}
			} catch (Throwable e) {
				failure.set(e);
			}
		});
		writer.start();
		reader.start();
		writer.join();
		reader.join();

		Assert.assertNull(failure.get());
		Assert.assertEquals(50_000, recorded.size());
	}
}
