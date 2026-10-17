/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.opales.covalent.supervision.componentview.sequence.SequenceTraceMessage;
import org.opales.covalent.supervision.supervisionhmi.SupervisorSettings;

/**
 * A recording's log, as plain functions: its start, one entry per message and its end, in one of
 * three formats. The log saved on demand and the one written live while recording are made of the
 * same entries, so that both read alike.
 */
public final class SequenceLog {

	/** A log's format, chosen in the preferences or by the extension of the file saved. */
	public enum Format {
		/** Readable text, one block per message: the default. */
		TEXT("txt"),
		/** One row per message, fixed columns, after a header row. */
		CSV("csv"),
		/** One JSON object per line: a start, the messages, an end; valid at any moment. */
		JSON_LINES("jsonl");

		private final String extension;

		Format(final String extension) {
			this.extension = extension;
		}

		/**
		 * Returns the extension of a file in this format.
		 *
		 * @return the extension, without its dot
		 */
		public String extension() {
			return this.extension;
		}
	}

	/** The columns of a CSV log, in order. */
	static final List<String> CSV_COLUMNS = List.of("n", "at", "t_ms", "scope", "style", "kind", "source", "target", "port",
			"method", "thread", "asynchronous", "duration_ms", "queue_wait_ms", "outcome", "failure", "arguments",
			"label", "details");

	private static final DateTimeFormatter TEXT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

	private SequenceLog() {
	}

	/**
	 * Returns a recording's scope, as its logs and its band show it.
	 *
	 * @param componentTypes the component types it records
	 * @return their simple names, sorted, joined by {@code " + "}
	 */
	public static String scopeOf(final Set<Class<?>> componentTypes) {
		return componentTypes.stream().map(Class::getSimpleName).sorted().collect(Collectors.joining(" + "));
	}

	/**
	 * Returns the format of a file, from its extension.
	 *
	 * @param file a log file
	 * @return CSV for {@code .csv}, JSON Lines for {@code .jsonl} or {@code .json}, text otherwise
	 */
	public static Format formatOf(final File file) {
		String name = file.getName().toLowerCase(Locale.ROOT);
		if (name.endsWith(".csv")) {
			return Format.CSV;
		}
		if (name.endsWith(".jsonl") || name.endsWith(".json")) {
			return Format.JSON_LINES;
		}
		return Format.TEXT;
	}

	/**
	 * Returns the folder live logs are written in.
	 *
	 * @return the folder chosen in the preferences, {@code ~/.covalent/supervisor/sequences} otherwise
	 */
	public static File folder() {
		String chosen = SupervisorSettings.getString(SupervisorSettings.SEQUENCE_LOG_DIRECTORY);
		return chosen != null ? new File(chosen) : new File(System.getProperty("user.home"), ".covalent/supervisor/sequences");
	}

	/**
	 * Returns the format chosen in the preferences.
	 *
	 * @return the log format, text when none was chosen
	 */
	public static Format preferredFormat() {
		return SupervisorSettings.getEnum(SupervisorSettings.SEQUENCE_LOG_FORMAT, Format.TEXT);
	}

	/**
	 * Returns the name of a log file: the recording's scope and the time it starts.
	 *
	 * @param scope  the recording's scope
	 * @param at     when the log starts
	 * @param format the log's format
	 * @return a file name holding only letters, digits, {@code -} and {@code _}, then the extension
	 */
	public static String fileName(final String scope, final Instant at, final Format format) {
		String base = scope.replaceAll("[^A-Za-z0-9_-]+", "_").replaceAll("^_+|_+$", "");
		if (base.isEmpty()) {
			base = "sequence";
		} else if (base.length() > 60) {
			base = base.substring(0, 60);
		}
		return base + "-" + FILE_TIME.format(at.atZone(ZoneId.systemDefault())) + "." + format.extension();
	}

	/**
	 * Returns the start of a log.
	 *
	 * @param format the log's format
	 * @param scope  the recording's scope
	 * @param at     when the recording started
	 * @return the start of the log, ending with a line break
	 */
	public static String start(final Format format, final String scope, final Instant at) {
		return switch (format) {
			case TEXT -> SequenceLogTexts.get("log.title") + "\n" + SequenceLogTexts.get("log.started", textTime(at)) + "\n"
					+ SequenceLogTexts.get("log.scope", scope) + "\n\n";
			case CSV -> String.join(",", CSV_COLUMNS) + "\n";
			case JSON_LINES -> "{\"type\":\"start\",\"at\":" + json(at.toString()) + ",\"scope\":" + json(scope) + "}\n";
		};
	}

	/**
	 * Returns the entry of one message.
	 *
	 * @param format  the log's format
	 * @param scope   the recording's scope, written on every CSV row
	 * @param started when the recording started: a CSV row gives the message's time of day from it
	 * @param number  the message's number in the recording, from 1
	 * @param message the message
	 * @return the message's entry, ending with a line break
	 */
	public static String message(final Format format, final String scope, final Instant started, final int number,
			final SequenceTraceMessage message) {
		return switch (format) {
			case TEXT -> textMessage(number, message);
			case CSV -> csvMessage(scope, started, number, message);
			case JSON_LINES -> jsonMessage(number, message);
		};
	}

	/**
	 * Returns the end of a log.
	 *
	 * @param format   the log's format
	 * @param reason   why it ends: {@code stopped} when the recording stops, {@code saved} for a log saved
	 *                 on demand, {@code closed} when the session closes while recording
	 * @param at       when it ends
	 * @param messages how many messages the recording holds
	 * @param failures how many of them are failures
	 * @return the end of the log, ending with a line break; nothing for CSV, which keeps to its rows
	 */
	public static String end(final Format format, final String reason, final Instant at, final int messages,
			final int failures) {
		return switch (format) {
			case TEXT -> SequenceLogTexts.get("log.end", SequenceLogTexts.get("log.end." + reason), textTime(at),
					SequenceLogTexts.counted("messages", messages), SequenceLogTexts.counted("failures", failures)) + "\n";
			case CSV -> "";
			case JSON_LINES -> "{\"type\":\"end\",\"reason\":" + json(reason) + ",\"at\":" + json(at.toString())
					+ ",\"messages\":" + messages + ",\"failures\":" + failures + "}\n";
		};
	}

	/**
	 * Tells whether a message is a failure, as the recording counts them.
	 *
	 * @param message a message
	 * @return whether its call failed
	 */
	public static boolean isFailure(final SequenceTraceMessage message) {
		SequenceTraceMessage.Facts facts = message.getFacts();
		return facts != null && facts.isFailure();
	}

	/**
	 * Returns a whole log at once: its start, every message and its end.
	 *
	 * @param format      the log's format
	 * @param scope       the recording's scope
	 * @param started     when the recording started
	 * @param messages    the calls and returns, in order: the entries
	 * @param completions how the invocations without a return ended: not entries, but their
	 *                    failures are counted, as the diagram counts them
	 * @param reason      why the log ends, see {@link #end}
	 * @param at          when it ends
	 * @return the whole log
	 */
	public static String whole(final Format format, final String scope, final Instant started,
			final List<SequenceTraceMessage> messages, final List<SequenceTraceMessage> completions,
			final String reason, final Instant at) {
		StringBuilder log = new StringBuilder(start(format, scope, started));
		int failures = 0;
		for (int index = 0; index < messages.size(); index++) {
			log.append(message(format, scope, started, index + 1, messages.get(index)));
			if (isFailure(messages.get(index))) {
				failures++;
			}
		}
		for (SequenceTraceMessage completion : completions) {
			if (isFailure(completion)) {
				failures++;
			}
		}
		return log.append(end(format, reason, at, messages.size(), failures)).toString();
	}

	private static String textTime(final Instant at) {
		return TEXT_TIME.format(at.atZone(ZoneId.systemDefault()));
	}

	private static String textMessage(final int number, final SequenceTraceMessage message) {
		StringBuilder text = new StringBuilder();
		text.append('#').append(number).append("  T0 + ").append(message.getRelativeTimeMillis()).append(" ms  ")
				.append(message.getSourceType().getSimpleName()).append(" -> ")
				.append(message.getTargetType().getSimpleName()).append("  ")
				.append(message.getLabel().replace('\n', '.')).append('\n');
		for (String line : message.getDetails().split("\n")) {
			text.append("    ").append(line).append('\n');
		}
		return text.append('\n').toString();
	}

	private static String csvMessage(final String scope, final Instant started, final int number,
			final SequenceTraceMessage message) {
		SequenceTraceMessage.Facts facts = message.getFacts();
		String[] cells = new String[CSV_COLUMNS.size()];
		cells[0] = String.valueOf(number);
		cells[1] = textTime(started.plusMillis(message.getRelativeTimeMillis()));
		cells[2] = String.valueOf(message.getRelativeTimeMillis());
		cells[3] = scope;
		cells[4] = message.getStyle().name().toLowerCase(Locale.ROOT);
		cells[6] = message.getSourceType().getSimpleName();
		cells[7] = message.getTargetType().getSimpleName();
		cells[17] = message.getLabel();
		if (facts == null) {
			cells[18] = message.getDetails();
		} else {
			cells[5] = facts.kind();
			cells[8] = facts.portName();
			cells[9] = facts.methodName();
			cells[10] = facts.threadName();
			cells[11] = String.valueOf(facts.asynchronous());
			cells[12] = facts.durationNanos() > 0 ? millis(facts.durationNanos()) : null;
			cells[13] = facts.queueWaitNanos() > 0 ? millis(facts.queueWaitNanos()) : null;
			cells[14] = facts.outcome() == null ? null : facts.outcome().toText();
			cells[15] = facts.failureDescription();
			cells[16] = facts.argumentsDescription();
		}
		StringBuilder row = new StringBuilder();
		for (int index = 0; index < cells.length; index++) {
			if (index > 0) {
				row.append(',');
			}
			row.append(csv(cells[index]));
		}
		return row.append('\n').toString();
	}

	private static String jsonMessage(final int number, final SequenceTraceMessage message) {
		SequenceTraceMessage.Facts facts = message.getFacts();
		StringBuilder json = new StringBuilder("{\"type\":\"message\",\"n\":").append(number)
				.append(",\"tMs\":").append(message.getRelativeTimeMillis())
				.append(",\"style\":").append(json(message.getStyle().name().toLowerCase(Locale.ROOT)))
				.append(",\"source\":").append(json(message.getSourceType().getSimpleName()))
				.append(",\"target\":").append(json(message.getTargetType().getSimpleName()))
				.append(",\"label\":").append(json(message.getLabel()));
		if (facts == null) {
			json.append(",\"details\":").append(json(message.getDetails()));
		} else {
			field(json, "kind", facts.kind());
			field(json, "port", facts.portName());
			field(json, "method", facts.methodName());
			field(json, "thread", facts.threadName());
			field(json, "receiver", facts.receiverDescription());
			field(json, "arguments", facts.argumentsDescription());
			json.append(",\"asynchronous\":").append(facts.asynchronous());
			if (facts.durationNanos() > 0) {
				json.append(",\"durationMs\":").append(millis(facts.durationNanos()));
			}
			if (facts.queueWaitNanos() > 0) {
				json.append(",\"queueWaitMs\":").append(millis(facts.queueWaitNanos()));
			}
			if (facts.outcome() != null) {
				field(json, "outcome", facts.outcome().toText());
			}
			field(json, "failure", facts.failureDescription());
		}
		return json.append("}\n").toString();
	}

	private static void field(final StringBuilder json, final String name, final String value) {
		if (value != null) {
			json.append(",\"").append(name).append("\":").append(json(value));
		}
	}

	/** Milliseconds with three decimals, a point whatever the locale. */
	private static String millis(final long nanos) {
		return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
	}

	/** A CSV cell: quoted when it holds a comma, a quote or a line break, its quotes doubled. */
	static String csv(final String value) {
		if (value == null) {
			return "";
		}
		if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
			return value;
		}
		return '"' + value.replace("\"", "\"\"") + '"';
	}

	/** A JSON string, quoted and escaped. */
	static String json(final String value) {
		StringBuilder json = new StringBuilder("\"");
		for (int index = 0; index < value.length(); index++) {
			char c = value.charAt(index);
			switch (c) {
				case '"' -> json.append("\\\"");
				case '\\' -> json.append("\\\\");
				case '\n' -> json.append("\\n");
				case '\r' -> json.append("\\r");
				case '\t' -> json.append("\\t");
				default -> {
					if (c < 0x20) {
						json.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
					} else {
						json.append(c);
					}
				}
			}
		}
		return json.append('"').toString();
	}
}
