/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.testsupport;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public final class CapturingLogHandler extends Handler {
	private final Logger logger;

	private final Handler[] previousHandlers;

	private final boolean previousUseParentHandlers;

	private final List<LogRecord> records = new ArrayList<>();

	public static CapturingLogHandler install(final Class<?> loggerClass) {
		CapturingLogHandler logHandler = new CapturingLogHandler(Logger.getLogger(loggerClass.getName()));
		logHandler.install();
		return logHandler;
	}

	private CapturingLogHandler(final Logger logger) {
		this.logger = logger;
		this.previousHandlers = logger.getHandlers();
		this.previousUseParentHandlers = logger.getUseParentHandlers();
	}

	private void install() {
		for (Handler previousHandler : this.previousHandlers) {
			this.logger.removeHandler(previousHandler);
		}
		this.logger.addHandler(this);
		this.logger.setUseParentHandlers(false);
	}

	public void restore() {
		this.logger.removeHandler(this);
		for (Handler previousHandler : this.previousHandlers) {
			this.logger.addHandler(previousHandler);
		}
		this.logger.setUseParentHandlers(this.previousUseParentHandlers);
	}

	@Override
	public void publish(final LogRecord record) {
		this.records.add(record);
	}

	@Override
	public void flush() {
	}

	@Override
	public void close() {
	}

	/**
	 * @param prefix the start of the messages wanted
	 * @return the captured messages starting with {@code prefix}, in order
	 */
	public List<String> messagesStartingWith(final String prefix) {
		List<String> messages = new ArrayList<>();
		for (LogRecord record : this.records) {
			if (record.getMessage() != null && record.getMessage().startsWith(prefix)) {
				messages.add(record.getMessage());
			}
		}
		return messages;
	}

	/**
	 * @param level the level wanted
	 * @return the number of captured records of exactly that level
	 */
	public int count(final Level level) {
		int count = 0;
		for (LogRecord record : this.records) {
			if (level.equals(record.getLevel())) {
				count++;
			}
		}
		return count;
	}

	/**
	 * @param level the level wanted
	 * @return the captured messages of exactly that level, in order, with their parameters filled in
	 */
	public List<String> messages(final Level level) {
		List<String> messages = new ArrayList<>();
		for (LogRecord record : this.records) {
			if (level.equals(record.getLevel())) {
				messages.add(new SimpleFormatter().formatMessage(record));
			}
		}
		return messages;
	}

	/**
	 * @param level the level wanted
	 * @return the failures attached to the captured records of exactly that level, in order (records without a
	 *         failure are left out)
	 */
	public List<Throwable> failures(final Level level) {
		List<Throwable> failures = new ArrayList<>();
		for (LogRecord record : this.records) {
			if (level.equals(record.getLevel()) && record.getThrown() != null) {
				failures.add(record.getThrown());
			}
		}
		return failures;
	}

	public boolean containsWarning(final String expectedMessage) {
		for (LogRecord record : this.records) {
			if (Level.WARNING.equals(record.getLevel()) && expectedMessage.equals(record.getMessage())) {
				return true;
			}
		}
		return false;
	}

	public boolean containsSevereMessageWithFailure(final String expectedMessage,
			final String expectedFailureMessage) {
		for (LogRecord record : this.records) {
			if (Level.SEVERE.equals(record.getLevel()) && expectedMessage.equals(record.getMessage())
					&& this.hasFailureMessage(record.getThrown(), expectedFailureMessage)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasFailureMessage(final Throwable failure, final String expectedFailureMessage) {
		Throwable current = failure;
		while (current != null) {
			if (current.getMessage() != null && current.getMessage().contains(expectedFailureMessage)) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}
}
