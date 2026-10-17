/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.interaction.impl;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opales.covalent.supervision.SupervisionThreads;
import org.opales.covalent.supervision.componentview.sequence.SequenceTraceMessage;
import org.opales.covalent.supervision.sequencediagram.SequenceLog;

/**
 * One live log: a file of the logs folder that a recording's entries are appended to as they
 * come, on a writer thread of their own so that neither the recording nor the screen waits for
 * the disk. The file is flushed whenever the writer has nothing left to write, so that it can be
 * read while the recording goes on. It counts the messages it wrote, and their failures, for its
 * end; it is used by one thread at a time.
 */
final class LiveSequenceLog {

	private static final Logger log = Logger.getLogger(LiveSequenceLog.class.getName());

	private final File file;
	private final SequenceLog.Format format;
	private final ThreadPoolExecutor writerThread;
	private final Consumer<IOException> onFailure;
	private Writer writer;
	private boolean failed;
	private volatile int written;
	private volatile int failures;
	private volatile String scope = "";
	private volatile Instant started = Instant.now();

	/**
	 * Opens a live log; nothing is written until its first entries.
	 *
	 * @param file      the file written
	 * @param format    its format
	 * @param onFailure told, on the writer thread, when the file cannot be written: the log then stops
	 */
	LiveSequenceLog(final File file, final SequenceLog.Format format, final Consumer<IOException> onFailure) {
		this.file = file;
		this.format = format;
		this.onFailure = onFailure;
		this.writerThread = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
				SupervisionThreads.factory("sequence-log"));
	}

	File file() {
		return this.file;
	}

	/**
	 * Returns the number of the last message written.
	 *
	 * @return how many messages were written, {@code 0} for none
	 */
	int written() {
		return this.written;
	}

	/**
	 * Writes the log's start.
	 *
	 * @param scope   the recording's scope
	 * @param started when the recording started
	 */
	void begin(final String scope, final Instant started) {
		this.scope = scope;
		this.started = started;
		this.append(SequenceLog.start(this.format, scope, started));
	}

	/**
	 * Writes a message's entry.
	 *
	 * @param number  the message's number in the recording, the one after the last written
	 * @param message the message
	 */
	void write(final int number, final SequenceTraceMessage message) {
		this.append(SequenceLog.message(this.format, this.scope, this.started, number, message));
		this.written = number;
		if (SequenceLog.isFailure(message)) {
			this.failures++;
		}
	}

	/**
	 * Counts a completion's failure: completions are not entries of the log, but its end counts
	 * every failure, as the diagram does.
	 *
	 * @param completion how an invocation without a return ended
	 */
	void complete(final SequenceTraceMessage completion) {
		if (SequenceLog.isFailure(completion)) {
			this.failures++;
		}
	}

	/**
	 * Writes the log's end, then closes the file; nothing is written afterwards.
	 *
	 * @param reason why the log ends, see {@link SequenceLog#end}
	 */
	void finish(final String reason) {
		this.close(SequenceLog.end(this.format, reason, Instant.now(), this.written, this.failures));
	}

	private void append(final String text) {
		if (text.isEmpty()) {
			return;
		}
		this.submit(() -> this.write(text));
	}

	private void close(final String text) {
		this.submit(() -> {
			this.write(text);
			if (this.writer != null) {
				try {
					this.writer.close();
				} catch (IOException e) {
					log.log(Level.WARNING, "Failed to close the sequence log " + this.file, e);
				}
				this.writer = null;
			}
		});
		this.writerThread.shutdown();
	}

	private void write(final String text) {
		if (this.failed || text.isEmpty()) {
			return;
		}
		try {
			if (this.writer == null) {
				Files.createDirectories(this.file.toPath().toAbsolutePath().getParent());
				this.writer = Files.newBufferedWriter(this.file.toPath(), StandardCharsets.UTF_8);
			}
			this.writer.write(text);
			if (this.writerThread.getQueue().isEmpty()) {
				this.writer.flush();
			}
		} catch (IOException e) {
			this.failed = true;
			log.log(Level.WARNING, "Failed to write the sequence log " + this.file, e);
			this.onFailure.accept(e);
			this.abandon();
		}
	}

	/** Hands a task to the writer thread, unless the log has stopped: nothing is written afterwards. */
	private void submit(final Runnable task) {
		try {
			this.writerThread.execute(task);
		} catch (RejectedExecutionException e) {
			// The log stopped after a failure: its entries are dropped.
		}
	}

	/** Ends a log that failed, on the writer thread: its file is closed and its thread is released. */
	private void abandon() {
		if (this.writer != null) {
			try {
				this.writer.close();
			} catch (IOException e) {
				log.log(Level.FINE, "Failed to close the sequence log " + this.file, e);
			}
			this.writer = null;
		}
		this.writerThread.shutdown();
	}
}
