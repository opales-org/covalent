/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.prefs.Preferences;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.supervision.componentview.sequence.SequenceTraceMessage;
import org.opales.covalent.supervision.observed.CallOutcome;
import org.opales.covalent.supervision.supervisionhmi.SupervisorSettings;
import org.opales.covalent.supervision.testsupport.SequenceLogWords;

/**
 * Verifies a recording's log in its three formats: what each entry holds, and how text that
 * would break a CSV row or a JSON line is escaped.
 */
public class SequenceLogTests {

	private static final Instant AT = Instant.parse("2026-10-08T07:47:12.418Z");

	private static SequenceTraceMessage message(final String label, final String details) {
		return new SequenceTraceMessage(String.class, Integer.class, label, details, 12,
				SequenceTraceMessage.Style.RETURN);
	}

	@Test
	public void theScopeIsTheSortedSimpleNames() {
		Assert.assertEquals("Integer + String", SequenceLog.scopeOf(java.util.Set.of(String.class, Integer.class)));
	}

	@Test
	public void theFormatComesFromTheFilesExtension() {
		Assert.assertEquals(SequenceLog.Format.CSV, SequenceLog.formatOf(new File("log.CSV")));
		Assert.assertEquals(SequenceLog.Format.JSON_LINES, SequenceLog.formatOf(new File("log.jsonl")));
		Assert.assertEquals(SequenceLog.Format.JSON_LINES, SequenceLog.formatOf(new File("log.json")));
		Assert.assertEquals(SequenceLog.Format.TEXT, SequenceLog.formatOf(new File("log.txt")));
		Assert.assertEquals(SequenceLog.Format.TEXT, SequenceLog.formatOf(new File("log")));
	}

	@Test
	public void aFileIsNamedAfterTheScopeAndTheStart() {
		String name = SequenceLog.fileName("EcosystemDemo + StatsExporter", AT, SequenceLog.Format.CSV);

		Assert.assertTrue(name, name.matches("EcosystemDemo_StatsExporter-\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}\\.csv"));
		Assert.assertTrue(SequenceLog.fileName(" + ", AT, SequenceLog.Format.TEXT).startsWith("sequence-"));
	}

	@Test
	public void aTextEntryIsTheMessageLineThenItsIndentedDetails() {
		Assert.assertEquals("#3  T0 + 12 ms  String -> Integer  get.\n    first\n    second\n\n",
				SequenceLog.message(SequenceLog.Format.TEXT, "Scope", AT, 3, message("get\n", "first\nsecond")));
	}

	@Test
	public void aCsvRowQuotesWhatWouldBreakIt() {
		String row = SequenceLog.message(SequenceLog.Format.CSV, "A + B", AT, 1, message("say \"hi\", twice", "a\nb"));

		Assert.assertTrue(row, row.matches("(?s)1,2026-10-08 \\d{2}:47:12\\.430,12,A \\+ B,return,,String,Integer,.*"));
		Assert.assertTrue(row, row.endsWith(",\"say \"\"hi\"\", twice\",\"a\nb\"\n"));
		Assert.assertEquals(SequenceLog.CSV_COLUMNS.size() - 1, row.chars().filter(c -> c == ',').count() - 1);
	}

	@Test
	public void aJsonLineEscapesQuotesBackslashesAndLineBreaks() {
		String line = SequenceLog.message(SequenceLog.Format.JSON_LINES, "Scope", AT, 2, message("a \"b\" \\ c", "x\ny"));

		Assert.assertEquals("{\"type\":\"message\",\"n\":2,\"tMs\":12,\"style\":\"return\",\"source\":\"String\","
				+ "\"target\":\"Integer\",\"label\":\"a \\\"b\\\" \\\\ c\",\"details\":\"x\\ny\"}\n", line);
	}

	@Test
	public void aWholeLogHasItsStartItsMessagesAndItsEnd() {
		String log = SequenceLog.whole(SequenceLog.Format.JSON_LINES, "Scope", AT, List.of(message("m", "d")),
				List.of(), "saved", AT);

		List<String> lines = log.lines().toList();
		Assert.assertEquals(3, lines.size());
		Assert.assertEquals("{\"type\":\"start\",\"at\":\"2026-10-08T07:47:12.418Z\",\"scope\":\"Scope\"}", lines.get(0));
		Assert.assertEquals("{\"type\":\"end\",\"reason\":\"saved\",\"at\":\"2026-10-08T07:47:12.418Z\",\"messages\":1,"
				+ "\"failures\":0}", lines.get(2));
		Assert.assertTrue(SequenceLog.whole(SequenceLog.Format.CSV, "Scope", AT, List.of(), List.of(), "saved", AT)
				.startsWith("n,at,t_ms,scope,style,"));
	}

	private static final String SETTINGS_NODE = "org/opales/covalent/supervisor";

	private String savedDirectory;

	private String savedFormat;

	@Before
	public void saveTheLogPreferences() {
		this.savedDirectory = SupervisorSettings.getString(SupervisorSettings.SEQUENCE_LOG_DIRECTORY);
		this.savedFormat = SupervisorSettings.getString(SupervisorSettings.SEQUENCE_LOG_FORMAT);
	}

	@After
	public void restoreTheLogPreferences() {
		restore(SupervisorSettings.SEQUENCE_LOG_DIRECTORY, this.savedDirectory);
		restore(SupervisorSettings.SEQUENCE_LOG_FORMAT, this.savedFormat);
	}

	private static void restore(final String key, final String value) {
		if (value != null) {
			SupervisorSettings.putString(key, value);
		} else {
			Preferences.userRoot().node(SETTINGS_NODE).remove(key);
		}
	}

	private static void clear(final String key) {
		Preferences.userRoot().node(SETTINGS_NODE).remove(key);
	}

	private static SequenceTraceMessage described(final String label, final SequenceTraceMessage.Facts facts) {
		return new SequenceTraceMessage(String.class, Integer.class, label, "details", 12,
				SequenceTraceMessage.Style.RETURN, facts);
	}

	private static SequenceTraceMessage.Facts facts(final CallOutcome outcome, final long durationNanos,
			final String failure, final long queueWaitNanos) {
		return new SequenceTraceMessage.Facts(1L, "service call", "EchoServices", "echo", "worker-1", "Impl@1",
				"(hello)", outcome, durationNanos, failure, true, queueWaitNanos);
	}

	@Test
	public void aCsvRowOfADescribedMessageHoldsEveryFact() {
		SequenceTraceMessage described = described("m", facts(CallOutcome.value("hello"), 2_500_000L, null, 1_000_000L));

		String row = SequenceLog.message(SequenceLog.Format.CSV, "Scope", AT, 1, described);

		Assert.assertTrue(row, row.startsWith("1,"));
		Assert.assertTrue(row, row.endsWith(",12,Scope,return,service call,String,Integer,EchoServices,echo,worker-1,"
				+ "true,2.500,1.000,hello,,(hello),m,\n"));
	}

	@Test
	public void aCsvRowLeavesTheDurationsAndTheOutcomeEmptyWhenUnknown() {
		SequenceTraceMessage described = described("m", facts(null, 0L, "IllegalStateException: boom", -1L));

		String row = SequenceLog.message(SequenceLog.Format.CSV, "Scope", AT, 3, described);

		Assert.assertTrue(row, row.endsWith("worker-1,true,,,,IllegalStateException: boom,(hello),m,\n"));
	}

	@Test
	public void aJsonLineOfADescribedMessageHoldsEveryFact() {
		SequenceTraceMessage described = described("m", facts(CallOutcome.value("hello"), 2_500_000L, null, 1_000_000L));

		String line = SequenceLog.message(SequenceLog.Format.JSON_LINES, "Scope", AT, 2, described);

		Assert.assertTrue(line, line.startsWith("{\"type\":\"message\",\"n\":2,\"tMs\":12,\"style\":\"return\","));
		for (String expected : List.of("\"kind\":\"service call\"", "\"port\":\"EchoServices\"",
				"\"method\":\"echo\"", "\"thread\":\"worker-1\"", "\"receiver\":\"Impl@1\"",
				"\"arguments\":\"(hello)\"", "\"asynchronous\":true", "\"durationMs\":2.500",
				"\"queueWaitMs\":1.000", "\"outcome\":\"hello\"")) {
			Assert.assertTrue(expected + " in " + line, line.contains(expected));
		}
		Assert.assertFalse(line, line.contains("\"failure\""));
		Assert.assertFalse(line, line.contains("\"details\""));
	}

	@Test
	public void aJsonLineOmitsTheUnknownDurationsAndOutcomeAndShowsTheFailure() {
		SequenceTraceMessage described = described("m", facts(null, 0L, "IllegalStateException: boom", -1L));

		String line = SequenceLog.message(SequenceLog.Format.JSON_LINES, "Scope", AT, 2, described);

		Assert.assertTrue(line, line.contains("\"failure\":\"IllegalStateException: boom\""));
		Assert.assertFalse(line, line.contains("durationMs"));
		Assert.assertFalse(line, line.contains("queueWaitMs"));
		Assert.assertFalse(line, line.contains("\"outcome\""));
	}

	@Test
	public void theEndOfATextLogCountsMessagesAndFailuresInTheSingularAndThePlural() {
		String one = SequenceLog.end(SequenceLog.Format.TEXT, "stopped", AT, 1, 1);
		String many = SequenceLog.end(SequenceLog.Format.TEXT, "saved", AT, 2, 0);

		Assert.assertTrue(one, one.startsWith(SequenceLogWords.endOfLog("stopped")));
		Assert.assertTrue(one, one.endsWith(" · " + SequenceLogWords.get("log.messages.one") + " · "
				+ SequenceLogWords.get("log.failures.one") + "\n"));
		Assert.assertTrue(many, many.startsWith(SequenceLogWords.endOfLog("saved")));
		Assert.assertTrue(many, many.endsWith(" · " + SequenceLogWords.get("log.messages.other", 2) + " · "
				+ SequenceLogWords.get("log.failures.zero") + "\n"));
		Assert.assertEquals("", SequenceLog.end(SequenceLog.Format.CSV, "stopped", AT, 2, 0));
	}

	@Test
	public void theStartOfATextLogNamesTheTraceTheStartAndTheScope() {
		String start = SequenceLog.start(SequenceLog.Format.TEXT, "Integer + String", AT);

		String[] lines = start.split("\n", -1);
		Assert.assertEquals(SequenceLogWords.get("log.title"), lines[0]);
		Assert.assertTrue(start, lines[1].startsWith(SequenceLogWords.get("log.started", "").trim()));
		Assert.assertEquals(SequenceLogWords.get("log.scope", "Integer + String"), lines[2]);
		Assert.assertTrue(start, start.endsWith("\n\n"));
	}

	@Test
	public void noCountIsWordedAsAZeroOneOrManyOfItsLanguage() {
		String none = SequenceLog.end(SequenceLog.Format.TEXT, "closed", AT, 0, 0);

		Assert.assertTrue(none, none.startsWith(SequenceLogWords.endOfLog("closed")));
		Assert.assertTrue(none, none.endsWith(" · " + SequenceLogWords.get("log.messages.zero") + " · "
				+ SequenceLogWords.get("log.failures.zero") + "\n"));
	}

	@Test
	public void aMessageWithoutFactsIsNotAFailure() {
		Assert.assertFalse(SequenceLog.isFailure(message("m", "d")));
		Assert.assertFalse(SequenceLog.isFailure(described("m", facts(CallOutcome.VOID, 1L, null, -1L))));
		Assert.assertTrue(SequenceLog.isFailure(described("m", facts(null, 1L, "boom", -1L))));
	}

	@Test
	public void aWholeLogCountsTheFailuresOfItsMessagesAndOfTheInvocationsWithoutReturn() {
		SequenceTraceMessage failed = described("failed", facts(null, 1L, "boom", -1L));
		SequenceTraceMessage fine = described("fine", facts(CallOutcome.VOID, 1L, null, -1L));
		SequenceTraceMessage lateFailure = described("late", facts(null, 1L, "late boom", -1L));
		SequenceTraceMessage lateFine = described("lateFine", facts(CallOutcome.VOID, 1L, null, -1L));

		String log = SequenceLog.whole(SequenceLog.Format.JSON_LINES, "Scope", AT, List.of(failed, fine),
				List.of(lateFailure, lateFine), "closed", AT);

		List<String> lines = log.lines().toList();
		Assert.assertTrue(lines.get(lines.size() - 1), lines.get(lines.size() - 1).endsWith("\"messages\":2,\"failures\":2}"));
	}

	@Test
	public void aLongScopeIsCutAndAnEmptyOneIsNamedSequence() {
		String longScope = "A".repeat(80);

		String cut = SequenceLog.fileName(longScope, AT, SequenceLog.Format.CSV);
		String symbols = SequenceLog.fileName("+ / +", AT, SequenceLog.Format.JSON_LINES);

		Assert.assertTrue(cut, cut.startsWith("A".repeat(60) + "-"));
		Assert.assertTrue(cut, cut.endsWith(".csv"));
		Assert.assertTrue(symbols, symbols.startsWith("sequence-"));
		Assert.assertTrue(symbols, symbols.endsWith(".jsonl"));
	}

	@Test
	public void theFolderAndTheFormatComeFromThePreferencesWithDefaults() {
		clear(SupervisorSettings.SEQUENCE_LOG_DIRECTORY);
		clear(SupervisorSettings.SEQUENCE_LOG_FORMAT);

		Assert.assertEquals(new File(System.getProperty("user.home"), ".covalent/supervisor/sequences"),
				SequenceLog.folder());
		Assert.assertEquals(SequenceLog.Format.TEXT, SequenceLog.preferredFormat());

		SupervisorSettings.putString(SupervisorSettings.SEQUENCE_LOG_DIRECTORY, "target/chosen-logs");
		SupervisorSettings.putEnum(SupervisorSettings.SEQUENCE_LOG_FORMAT, SequenceLog.Format.JSON_LINES);

		Assert.assertEquals(new File("target/chosen-logs"), SequenceLog.folder());
		Assert.assertEquals(SequenceLog.Format.JSON_LINES, SequenceLog.preferredFormat());
	}
}
