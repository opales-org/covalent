/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.threads.view.impl;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.opales.covalent.container.workers.WorkerPoolKind;
import org.opales.covalent.supervision.threads.model.Contention;
import org.opales.covalent.supervision.threads.model.ThreadActivity;

/** How the Threads panel writes times, durations and states. */
final class ThreadsFormat {

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

	private ThreadsFormat() {
	}

	static String time(final long millis) {
		return TIME.format(Instant.ofEpochMilli(millis));
	}

	/** A duration such as {@code 850 µs}, {@code 12 ms}, {@code 1.4 s} or {@code 00:02:03}. */
	static String duration(final long nanos) {
		if (nanos < 0) {
			return "–";
		}
		if (nanos < 1_000_000) {
			return Math.max(1, nanos / 1000) + " µs";
		}
		if (nanos < 1_000_000_000) {
			return nanos / 1_000_000 + " ms";
		}
		long seconds = nanos / 1_000_000_000;
		if (seconds < 60) {
			return String.format(Locale.ROOT, "%.1f s", nanos / 1e9);
		}
		return clock(seconds);
	}

	/** A number of seconds as {@code hh:mm:ss}. */
	static String clock(final long seconds) {
		return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
	}

	/** How long ago, in whole seconds, then minutes, then hours: {@code 4 s}, {@code 2 min}. */
	static String age(final long seconds) {
		return seconds < 60 ? Math.max(1, seconds) + " s" : seconds < 3600 ? seconds / 60 + " min" : seconds / 3600 + " h";
	}

	static String rate(final double perSecond) {
		return perSecond < 10 ? String.format(Locale.ROOT, "%.1f", perSecond) : String.valueOf(Math.round(perSecond));
	}

	/** A number of workers on average, such as {@code 1.5}: {@code <0.1} when some, yet fewer. */
	static String workers(final double workers) {
		return workers > 0 && workers < 0.05 ? "<0.1" : rate(workers);
	}

	/** A share with one decimal below 10 %, such as {@code 0.4 %}. */
	static String share(final double share) {
		double percent = share * 100;
		return percent < 10 ? String.format(Locale.ROOT, "%.1f %%", percent) : Math.round(percent) + " %";
	}

	static String percent(final double share) {
		return Math.round(share * 100) + " %";
	}

	static String label(final ThreadActivity activity) {
		return ThreadsTexts.get("activity." + activity.name());
	}

	static String label(final Contention contention) {
		return ThreadsTexts.get("contention." + contention.name());
	}

	static String styleSuffix(final ThreadActivity activity) {
		return activity.name().toLowerCase(Locale.ROOT);
	}

	static String kindBadge(final WorkerPoolKind kind) {
		return switch (kind) {
			case EVENTS -> ThreadsTexts.get("badge.fixedPool");
			case GENERAL -> ThreadsTexts.get("badge.sharedPool");
			case DEDICATED -> ThreadsTexts.get("badge.dedicatedPool");
		};
	}

	/** The name a kind of pool is shown with: "Events", "Async Services", "Dedicated", translated. */
	static String kindLabel(final WorkerPoolKind kind) {
		return ThreadsTexts.get("kind." + kind.name());
	}

	/**
	 * The suffix of the style classes, and the palette name, of a kind of pool's color: events take the
	 * events color, the general pool, which runs the async services, the services color.
	 */
	static String kindStyle(final WorkerPoolKind kind) {
		return switch (kind) {
			case EVENTS -> "events";
			case GENERAL -> "general";
			case DEDICATED -> "dedicated";
		};
	}
}
