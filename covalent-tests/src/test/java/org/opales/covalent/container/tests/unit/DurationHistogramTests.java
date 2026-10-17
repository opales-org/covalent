/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.opales.covalent.container.workers.DurationHistogram;

/**
 * Verifies {@link DurationHistogram}: bucket selection by power of two, bucket bounds, totals,
 * percentile estimation, subtraction of earlier readings, defensive copies and value semantics.
 */
public class DurationHistogramTests {

	private static long[] counts(final int bucket, final long value) {
		long[] counts = new long[DurationHistogram.BUCKETS];
		counts[bucket] = value;
		return counts;
	}

	@Test
	public void rejectsAWrongNumberOfBuckets() {
		try {
			new DurationHistogram(new long[3]);
			throw new AssertionError("expected IllegalArgumentException");
		} catch (IllegalArgumentException expected) {
			assertTrue(expected.getMessage().contains("3"));
		}
	}

	@Test
	public void bucketOfPutsSubMicrosecondAndNegativeDurationsInBucketZero() {
		assertEquals(0, DurationHistogram.bucketOf(-5));
		assertEquals(0, DurationHistogram.bucketOf(0));
		assertEquals(0, DurationHistogram.bucketOf(999));
	}

	@Test
	public void bucketOfGroupsDurationsByPowerOfTwoMicroseconds() {
		assertEquals(1, DurationHistogram.bucketOf(1_000));
		assertEquals(2, DurationHistogram.bucketOf(2_000));
		assertEquals(2, DurationHistogram.bucketOf(3_999));
		assertEquals(3, DurationHistogram.bucketOf(4_000));
	}

	@Test
	public void bucketOfCapsAtTheLastBucket() {
		assertEquals(DurationHistogram.BUCKETS - 1, DurationHistogram.bucketOf(Long.MAX_VALUE));
	}

	@Test
	public void upperBoundIsAPowerOfTwoMicrosecondsAndCapsBeforeTheLastBucket() {
		assertEquals(1_000L, DurationHistogram.upperBoundNanos(0));
		assertEquals(8_000L, DurationHistogram.upperBoundNanos(3));
		assertEquals(DurationHistogram.upperBoundNanos(DurationHistogram.BUCKETS - 2),
				DurationHistogram.upperBoundNanos(DurationHistogram.BUCKETS - 1));
	}

	@Test
	public void emptyHistogramHasNoDurationAndNoPercentile() {
		assertEquals(0, DurationHistogram.EMPTY.total());
		assertEquals(-1, DurationHistogram.EMPTY.percentileNanos(50));
	}

	@Test
	public void totalSumsAllBuckets() {
		long[] c = counts(2, 3);
		c[5] = 4;
		assertEquals(7, new DurationHistogram(c).total());
	}

	@Test
	public void percentileReturnsTheUpperBoundOfTheBucketHoldingTheRank() {
		long[] c = counts(1, 9);
		c[4] = 1;
		DurationHistogram histogram = new DurationHistogram(c);
		assertEquals(DurationHistogram.upperBoundNanos(1), histogram.percentileNanos(50));
		assertEquals(DurationHistogram.upperBoundNanos(1), histogram.percentileNanos(90));
		assertEquals(DurationHistogram.upperBoundNanos(4), histogram.percentileNanos(95));
		assertEquals(DurationHistogram.upperBoundNanos(4), histogram.percentileNanos(100));
	}

	@Test
	public void percentileIsClampedToTheZeroToHundredRange() {
		long[] c = counts(1, 1);
		c[6] = 1;
		DurationHistogram histogram = new DurationHistogram(c);
		assertEquals(DurationHistogram.upperBoundNanos(1), histogram.percentileNanos(-10));
		assertEquals(DurationHistogram.upperBoundNanos(1), histogram.percentileNanos(0));
		assertEquals(DurationHistogram.upperBoundNanos(6), histogram.percentileNanos(1000));
	}

	@Test
	public void sinceKeepsOnlyWhatWasCountedAfterTheEarlierReading() {
		long[] early = counts(2, 3);
		long[] late = counts(2, 5);
		late[7] = 2;
		DurationHistogram diff = new DurationHistogram(late).since(new DurationHistogram(early));
		assertEquals(2, diff.counts()[2]);
		assertEquals(2, diff.counts()[7]);
		assertEquals(4, diff.total());
	}

	@Test
	public void sinceNeverGoesNegative() {
		DurationHistogram diff = new DurationHistogram(counts(2, 1)).since(new DurationHistogram(counts(2, 9)));
		assertEquals(0, diff.total());
	}

	@Test
	public void constructorAndAccessorCopyTheArray() {
		long[] source = counts(1, 1);
		DurationHistogram histogram = new DurationHistogram(source);
		source[1] = 99;
		assertEquals(1, histogram.counts()[1]);
		histogram.counts()[1] = 77;
		assertEquals(1, histogram.counts()[1]);
		assertArrayEquals(counts(1, 1), histogram.counts());
	}

	@Test
	public void equalityHashCodeAndToStringFollowTheCounts() {
		DurationHistogram a = new DurationHistogram(counts(1, 2));
		DurationHistogram b = new DurationHistogram(counts(1, 2));
		DurationHistogram c = new DurationHistogram(counts(1, 3));
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
		assertNotEquals(a, c);
		assertNotEquals(a, "text");
		assertTrue(a.toString().startsWith("DurationHistogram["));
	}
}
