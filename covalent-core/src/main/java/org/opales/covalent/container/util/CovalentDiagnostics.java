/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.util;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thin logging facade used throughout the container instead of calling
 * {@link Logger} directly, so that any logged {@link Throwable} is passed
 * through {@link StackTraceCleaner#cleanIfEnabled} first - keeping stack
 * traces free of JDK reflection/proxy and Covalent dispatch frames when
 * cleaning is enabled, without every call site having to remember to do it.
 */
public final class CovalentDiagnostics {

	private CovalentDiagnostics() {
	}

	/**
	 * Logs a message at {@link Level#SEVERE}.
	 *
	 * @param logger  the logger to log to
	 * @param message the message to log
	 */
	public static void severe(final Logger logger, final String message) {
		log(logger, Level.SEVERE, message);
	}

	/**
	 * Logs a message and a (stack-trace-cleaned) failure at
	 * {@link Level#SEVERE}.
	 *
	 * @param logger  the logger to log to
	 * @param message the message to log
	 * @param failure the associated failure
	 */
	public static void severe(final Logger logger, final String message, final Throwable failure) {
		log(logger, Level.SEVERE, message, failure);
	}

	/**
	 * Logs a message at {@link Level#WARNING}.
	 *
	 * @param logger  the logger to log to
	 * @param message the message to log
	 */
	public static void warning(final Logger logger, final String message) {
		log(logger, Level.WARNING, message);
	}

	/**
	 * Logs a message and a (stack-trace-cleaned) failure at
	 * {@link Level#WARNING}.
	 *
	 * @param logger  the logger to log to
	 * @param message the message to log
	 * @param failure the associated failure
	 */
	public static void warning(final Logger logger, final String message, final Throwable failure) {
		log(logger, Level.WARNING, message, failure);
	}

	/**
	 * Logs a message at {@link Level#INFO}.
	 *
	 * @param logger  the logger to log to
	 * @param message the message to log
	 */
	public static void info(final Logger logger, final String message) {
		log(logger, Level.INFO, message);
	}

	/**
	 * Logs a message and a (stack-trace-cleaned) failure at
	 * {@link Level#INFO}.
	 *
	 * @param logger  the logger to log to
	 * @param message the message to log
	 * @param failure the associated failure
	 */
	public static void info(final Logger logger, final String message, final Throwable failure) {
		log(logger, Level.INFO, message, failure);
	}

	/**
	 * Logs a message at {@link Level#FINE}.
	 *
	 * @param logger  the logger to log to
	 * @param message the message to log
	 */
	public static void fine(final Logger logger, final String message) {
		log(logger, Level.FINE, message);
	}

	/**
	 * Logs a message and a (stack-trace-cleaned) failure at
	 * {@link Level#FINE}.
	 *
	 * @param logger  the logger to log to
	 * @param message the message to log
	 * @param failure the associated failure
	 */
	public static void fine(final Logger logger, final String message, final Throwable failure) {
		log(logger, Level.FINE, message, failure);
	}

	/**
	 * Logs a message at the given level.
	 *
	 * @param logger  the logger to log to
	 * @param level   the level to log at
	 * @param message the message to log
	 */
	public static void log(final Logger logger, final Level level, final String message) {
		logger.log(level, message);
	}

	/**
	 * Logs a message and a failure at the given level, after passing the
	 * failure through {@link StackTraceCleaner#cleanIfEnabled}.
	 *
	 * @param logger  the logger to log to
	 * @param level   the level to log at
	 * @param message the message to log
	 * @param failure the associated failure
	 */
	public static void log(final Logger logger, final Level level, final String message, final Throwable failure) {
		logger.log(level, message, StackTraceCleaner.cleanIfEnabled(failure));
	}
}
