/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.model;

import java.util.AbstractList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.opales.covalent.supervision.componentview.sequence.SequenceTraceMessage;

/**
 * One session's messages, in the order they were recorded: a list that is only ever added to.
 * It is written by the recording and read at the same time by the screen and the logs, without
 * a lock and without a copy: a reader takes the number of messages there are, and reads those,
 * which never change; a message added meanwhile is seen by its next read.
 * <p>
 * The messages are kept in blocks of fixed size that are never moved, and the number of
 * messages is raised only once the new one is in its block, so that a reader that sees the
 * number also sees every message it counts.
 */
public final class RecordedMessages {

	private static final int BLOCK_BITS = 10;
	private static final int BLOCK_SIZE = 1 << BLOCK_BITS;
	private static final int BLOCKS = 1 << 16;

	private final AtomicReferenceArray<SequenceTraceMessage[]> blocks = new AtomicReferenceArray<>(BLOCKS);
	private volatile int size;

	/**
	 * Creates an empty list.
	 */
	public RecordedMessages() {
		// nothing to initialize: the blocks are allocated as messages are added
	}

	/**
	 * Adds a message after the others. Called by one writer at a time.
	 *
	 * @param message the message
	 * @throws IllegalStateException when the session already holds the most messages it can
	 */
	public synchronized void add(final SequenceTraceMessage message) {
		int index = this.size;
		int block = index >>> BLOCK_BITS;
		if (block >= BLOCKS) {
			throw new IllegalStateException("A recording holds at most " + BLOCKS * BLOCK_SIZE + " messages");
		}
		SequenceTraceMessage[] messages = this.blocks.get(block);
		if (messages == null) {
			messages = new SequenceTraceMessage[BLOCK_SIZE];
			this.blocks.set(block, messages);
		}
		messages[index & (BLOCK_SIZE - 1)] = message;
		this.size = index + 1;
	}

	/**
	 * Returns how many messages there are.
	 *
	 * @return the number of messages added so far
	 */
	public int size() {
		return this.size;
	}

	/**
	 * Returns the messages there are now.
	 *
	 * @return a read-only list of the messages added so far, in order: those added later are not in it
	 */
	public List<SequenceTraceMessage> upToNow() {
		int count = this.size;
		return new AbstractList<>() {
			@Override
			public SequenceTraceMessage get(final int index) {
				if (index < 0 || index >= count) {
					throw new IndexOutOfBoundsException(index);
				}
				return RecordedMessages.this.blocks.get(index >>> BLOCK_BITS)[index & (BLOCK_SIZE - 1)];
			}

			@Override
			public int size() {
				return count;
			}
		};
	}
}
