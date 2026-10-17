/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.observed;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link DataPrinter} describes observed values as {@code Type : text}, guarding the application's own
 * {@code toString()} and cutting what is too long; {@link CallOutcome} renders how a call ended.
 */
public class DataPrinterTests {

	private static final String ELLIPSIS = "…";

	/** An application object whose text cannot be obtained. */
	private static final class Broken {
		@Override
		public String toString() {
			throw new IllegalStateException("no text");
		}
	}

	/** An application object that recurses forever when asked for its text. */
	private static final class Endless {
		@Override
		public String toString() {
			return this.toString() + "!";
		}
	}

	@SuppressWarnings("unused")
	private static void returnsNothing() {
	}

	@SuppressWarnings("unused")
	private static String returnsText() {
		return null;
	}

	private static Method method(final String name) throws NoSuchMethodException {
		return DataPrinterTests.class.getDeclaredMethod(name);
	}

	@Test
	public void aValueIsDescribedByItsTypeAndItsText() {
		Assert.assertEquals("String : abc", DataPrinter.describe("abc"));
		Assert.assertEquals("Integer : 42", DataPrinter.describe(42));
	}

	@Test
	public void nullIsDescribedAsNull() {
		Assert.assertEquals("null", DataPrinter.describe(null));
	}

	@Test
	public void anAnonymousClassIsNamedByItsFullName() {
		Object anonymous = new Object() {
			@Override
			public String toString() {
				return "anon";
			}
		};

		Assert.assertEquals(anonymous.getClass().getName() + " : anon", DataPrinter.describe(anonymous));
	}

	@Test
	public void aLongTextIsCutAndMarked() {
		String described = DataPrinter.describe("x".repeat(500));

		Assert.assertEquals("String : " + "x".repeat(DataPrinter.MAX_VALUE_LENGTH) + ELLIPSIS, described);
	}

	@Test
	public void aTextAtTheLimitIsKept() {
		String text = "x".repeat(DataPrinter.MAX_VALUE_LENGTH);

		Assert.assertEquals("String : " + text, DataPrinter.describe(text));
	}

	@Test
	public void aToStringThatFailsIsReportedNotPropagated() {
		String expected = ObservedTexts.get("toString.failed", "IllegalStateException");

		Assert.assertEquals("Broken : " + expected, DataPrinter.describe(new Broken()));
	}

	@Test
	public void aToStringThatOverflowsTheStackIsReportedToo() {
		String expected = ObservedTexts.get("toString.failed", "StackOverflowError");

		Assert.assertEquals("Endless : " + expected, DataPrinter.describe(new Endless()));
	}

	@Test
	public void aToStringReturningNullIsDescribedAsNull() {
		Object returnsNull = new Object() {
			@Override
			public String toString() {
				return null;
			}
		};

		Assert.assertTrue(DataPrinter.describe(returnsNull).endsWith(" : null"));
	}

	@Test
	public void anArrayIsSummedUpByItsSizeAndItsFirstElements() {
		Assert.assertEquals("int[] : (3) [1, 2, 3]", DataPrinter.describe(new int[] { 1, 2, 3 }));
		Assert.assertEquals("String[] : (7) [a, b, c, d, e, …]",
				DataPrinter.describe(new String[] { "a", "b", "c", "d", "e", "f", "g" }));
		Assert.assertEquals("Object[] : (2) [null, x]", DataPrinter.describe(new Object[] { null, "x" }));
	}

	@Test
	public void aCollectionIsSummedUpByItsSizeAndItsFirstElements() {
		Assert.assertEquals("ArrayList : (2) [a, b]", DataPrinter.describe(new ArrayList<>(List.of("a", "b"))));
		Assert.assertEquals("ArrayList : (6) [1, 2, 3, 4, 5, …]",
				DataPrinter.describe(new ArrayList<>(List.of(1, 2, 3, 4, 5, 6))));
		Assert.assertEquals("ArrayList : (0) []", DataPrinter.describe(new ArrayList<>()));
	}

	@Test
	public void aMapIsSummedUpByItsSizeAndItsFirstEntries() {
		Map<String, Integer> map = new LinkedHashMap<>();
		for (int i = 1; i <= 6; i++) {
			map.put("k" + i, i);
		}

		Assert.assertEquals("LinkedHashMap : (6) {k1=1, k2=2, k3=3, k4=4, k5=5, …}", DataPrinter.describe(map));
	}

	@Test
	public void anElementIsCutShorterThanAWholeValue() {
		String described = DataPrinter.describe(List.of("y".repeat(100)));

		Assert.assertEquals("ListN : (1) [" + "y".repeat(40) + ELLIPSIS + "]", described.replace("List12", "ListN"));
	}

	@Test
	public void anElementWhoseToStringFailsDoesNotSpoilTheSummary() {
		String marker = ObservedTexts.get("toString.failed", "IllegalStateException");

		Assert.assertEquals("ArrayList : (2) [a, " + marker + "]",
				DataPrinter.describe(new ArrayList<>(List.of("a", new Broken()))));
	}

	@Test
	public void aCollectionChangingWhileDescribedIsReportedNotPropagated() {
		List<String> shrinking = new ArrayList<>(List.of("a", "b", "c")) {
			@Override
			public java.util.Iterator<String> iterator() {
				throw new java.util.ConcurrentModificationException();
			}
		};

		String expected = ObservedTexts.get("toString.failed", "ConcurrentModificationException");

		Assert.assertTrue(DataPrinter.describe(shrinking).endsWith(" : " + expected));
	}

	@Test
	public void noArgumentsAreDescribedByTheirOwnText() {
		String none = ObservedTexts.get("arguments.none");

		Assert.assertEquals(none, DataPrinter.describeArguments(null));
		Assert.assertEquals(none, DataPrinter.describeArguments(new Object[0]));
	}

	@Test
	public void argumentsAreDescribedOnePerLine() {
		Assert.assertEquals("String : a\nnull\nInteger : 3", DataPrinter.describeArguments(new Object[] { "a", null, 3 }));
	}

	@Test
	public void aCallThatFailedIsDescribedByItsFailure() throws Exception {
		CallOutcome outcome = DataPrinter.describeOutcome(method("returnsText"), "ignored",
				new IllegalStateException("boom"));

		Assert.assertEquals(CallOutcome.Kind.FAILED, outcome.kind());
		Assert.assertEquals("java.lang.IllegalStateException: boom", outcome.description());
	}

	@Test
	public void aFailureWhoseToStringFailsIsReportedToo() throws Exception {
		Throwable failure = new RuntimeException() {
			@Override
			public String toString() {
				throw new UnsupportedOperationException();
			}
		};

		CallOutcome outcome = DataPrinter.describeOutcome(method("returnsText"), null, failure);

		Assert.assertEquals(CallOutcome.Kind.FAILED, outcome.kind());
		Assert.assertEquals(ObservedTexts.get("toString.failed", "UnsupportedOperationException"),
				outcome.description());
	}

	@Test
	public void aVoidMethodEndsAsVoid() throws Exception {
		Assert.assertEquals(CallOutcome.VOID, DataPrinter.describeOutcome(method("returnsNothing"), null, null));
	}

	@Test
	public void aNullReturnEndsAsNull() throws Exception {
		Assert.assertEquals(CallOutcome.NULL, DataPrinter.describeOutcome(method("returnsText"), null, null));
	}

	@Test
	public void aReturnedValueIsDescribed() throws Exception {
		Assert.assertEquals(CallOutcome.value("String : ok"),
				DataPrinter.describeOutcome(method("returnsText"), "ok", null));
	}

	@Test
	public void withoutAMethodAValueIsStillDescribed() {
		Assert.assertEquals(CallOutcome.value("Integer : 1"), DataPrinter.describeOutcome(null, 1, null));
		Assert.assertEquals(CallOutcome.NULL, DataPrinter.describeOutcome(null, null, null));
	}

	@Test
	public void anOutcomeRendersAsOneLineOfText() {
		Assert.assertEquals("void", CallOutcome.VOID.toText());
		Assert.assertEquals("null", CallOutcome.NULL.toText());
		Assert.assertEquals("String : ok", CallOutcome.value("String : ok").toText());
		Assert.assertEquals("java.lang.Error", CallOutcome.failed("java.lang.Error").toText());
	}

	@Test
	public void arraysNestedInAnArrayAreSummedUpToo() {
		Assert.assertEquals("int[][] : (2) [(3) [1, 2, 3], (2) [4, 5]]",
				DataPrinter.describe(new int[][] { { 1, 2, 3 }, { 4, 5 } }));
	}

	@Test
	public void collectionsAndMapsNestedInACollectionAreSummedUpToo() {
		Map<String, List<Integer>> map = new LinkedHashMap<>();
		map.put("k", new ArrayList<>(List.of(1, 2)));

		Assert.assertEquals("ArrayList : (2) [(2) [a, b], (1) {k=[1, 2]}]",
				DataPrinter.describe(new ArrayList<>(List.of(new ArrayList<>(List.of("a", "b")), map))));
	}

	@Test
	public void nestingIsSummedUpDownToTwoLevelsOnly() {
		Object[] deep = { new Object[] { new Object[] { "x" } } };

		Assert.assertEquals("Object[] : (1) [(1) [[Ljava.lang.Object;@", DataPrinter.describe(deep)
				.replaceAll("@[0-9a-f]+.*", "@"));
	}

	@Test
	public void aCollectionContainingItselfDoesNotLoop() {
		List<Object> loop = new ArrayList<>();
		loop.add(loop);

		Assert.assertTrue(DataPrinter.describe(loop).startsWith("ArrayList : (1) [(1) ["));
	}

	@Test
	public void aNestedSummaryIsCutLikeAnyElement() {
		String described = DataPrinter.describe(new Object[] { new String[] { "z".repeat(60) } });

		Assert.assertEquals("Object[] : (1) [(1) [" + "z".repeat(35) + ELLIPSIS + "]", described);
	}
}
