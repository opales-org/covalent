/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.container.util.CovalentDiagnostics;

/**
 * Every {@link CovalentDiagnostics} shortcut logs its message, and its
 * failure when it has one, at the level it is named after, on the logger it
 * is given.
 */
public class CovalentDiagnosticsTests {

	private final List<LogRecord> records = new ArrayList<>();

	private Logger logger;

	private Handler handler;

	@Before
	public void setUp() {
		this.logger = Logger.getLogger(CovalentDiagnosticsTests.class.getName());
		this.logger.setUseParentHandlers(false);
		this.logger.setLevel(Level.ALL);
		this.handler = new Handler() {
			@Override
			public void publish(final LogRecord record) {
				CovalentDiagnosticsTests.this.records.add(record);
			}

			@Override
			public void flush() {
				// nothing buffered
			}

			@Override
			public void close() {
				// nothing to release
			}
		};
		this.logger.addHandler(this.handler);
	}

	@After
	public void tearDown() {
		this.logger.removeHandler(this.handler);
		this.logger.setUseParentHandlers(true);
	}

	private void assertSingleRecord(final Level level, final String message, final Throwable failure) {
		Assert.assertEquals(1, this.records.size());
		LogRecord record = this.records.get(0);
		Assert.assertEquals(level, record.getLevel());
		Assert.assertEquals(message, record.getMessage());
		if (failure == null) {
			Assert.assertNull(record.getThrown());
		} else {
			Assert.assertNotNull(record.getThrown());
			Assert.assertEquals(failure.getClass(), record.getThrown().getClass());
			Assert.assertEquals(failure.getMessage(), record.getThrown().getMessage());
		}
	}

	@Test
	public void severeLogsAtSevere() {
		CovalentDiagnostics.severe(this.logger, "severe message");

		this.assertSingleRecord(Level.SEVERE, "severe message", null);
	}

	@Test
	public void severeWithFailureLogsTheFailure() {
		CovalentDiagnostics.severe(this.logger, "severe message", new IllegalStateException("severe failure"));

		this.assertSingleRecord(Level.SEVERE, "severe message", new IllegalStateException("severe failure"));
	}

	@Test
	public void warningLogsAtWarning() {
		CovalentDiagnostics.warning(this.logger, "warning message");

		this.assertSingleRecord(Level.WARNING, "warning message", null);
	}

	@Test
	public void warningWithFailureLogsTheFailure() {
		CovalentDiagnostics.warning(this.logger, "warning message", new IllegalArgumentException("warning failure"));

		this.assertSingleRecord(Level.WARNING, "warning message", new IllegalArgumentException("warning failure"));
	}

	@Test
	public void infoLogsAtInfo() {
		CovalentDiagnostics.info(this.logger, "info message");

		this.assertSingleRecord(Level.INFO, "info message", null);
	}

	@Test
	public void infoWithFailureLogsTheFailure() {
		CovalentDiagnostics.info(this.logger, "info message", new UnsupportedOperationException("info failure"));

		this.assertSingleRecord(Level.INFO, "info message", new UnsupportedOperationException("info failure"));
	}

	@Test
	public void fineLogsAtFine() {
		CovalentDiagnostics.fine(this.logger, "fine message");

		this.assertSingleRecord(Level.FINE, "fine message", null);
	}

	@Test
	public void fineWithFailureLogsTheFailure() {
		CovalentDiagnostics.fine(this.logger, "fine message", new ArithmeticException("fine failure"));

		this.assertSingleRecord(Level.FINE, "fine message", new ArithmeticException("fine failure"));
	}

	@Test
	public void logUsesTheGivenLevel() {
		CovalentDiagnostics.log(this.logger, Level.CONFIG, "config message");

		this.assertSingleRecord(Level.CONFIG, "config message", null);
	}

	@Test
	public void logWithFailureUsesTheGivenLevel() {
		CovalentDiagnostics.log(this.logger, Level.FINER, "finer message", new NullPointerException("finer failure"));

		this.assertSingleRecord(Level.FINER, "finer message", new NullPointerException("finer failure"));
	}

	@Test
	public void aMessageBelowTheLoggerLevelIsNotLogged() {
		this.logger.setLevel(Level.WARNING);

		CovalentDiagnostics.info(this.logger, "ignored");
		CovalentDiagnostics.fine(this.logger, "ignored", new IllegalStateException("ignored"));

		Assert.assertTrue(this.records.isEmpty());
	}
}
