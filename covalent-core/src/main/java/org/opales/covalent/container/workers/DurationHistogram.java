/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.workers;

import java.util.Arrays;

import org.opales.covalent.container.util.RuntimeCheck;

/**
 * Durations counted by powers of two: bucket 0 holds those under one
 * microsecond, bucket {@code i} those from {@code 2^(i-1)} to {@code 2^i}
 * microseconds, the last bucket everything longer. Cheap to update from
 * several threads, and precise enough for percentiles within a factor two.
 *
 * @param counts how many durations fell in each bucket, copied
 */
public record DurationHistogram(long[] counts) {

	/** The number of buckets: the last one starts at about 3 days. */
	public static final int BUCKETS = 40;

	/** An empty histogram. */
	public static final DurationHistogram EMPTY = new DurationHistogram(new long[BUCKETS]);

	/**
	 * Creates a histogram holding a copy of the given counts.
	 *
	 * @param counts how many durations fell in each bucket, copied
	 */
	public DurationHistogram {
		if (counts.length != BUCKETS) {
			throw new IllegalArgumentException("Expected " + BUCKETS + " buckets, got " + counts.length);
		}
		counts = counts.clone();
	}

	/**
	 * Finds the bucket a duration falls in.
	 *
	 * @param nanos a duration
	 * @return the bucket it falls in
	 */
	public static int bucketOf(final long nanos) {
		long micros = nanos / 1000;
		if (micros <= 0) {
			return 0;
		}
		return Math.min(BUCKETS - 1, 64 - Long.numberOfLeadingZeros(micros));
	}

	/**
	 * Gives the longest duration a bucket holds.
	 *
	 * @param bucket a bucket
	 * @return the longest duration it holds, in nanoseconds; for the last
	 *         bucket, where it starts
	 */
	public static long upperBoundNanos(final int bucket) {
		int last = Math.min(bucket, BUCKETS - 2);
		return (1L << last) * 1000;
	}

	/**
	 * Counts the durations the histogram holds.
	 *
	 * @return how many durations it holds
	 */
	public long total() {
		long total = 0;
		for (long count : this.counts) {
			total += count;
		}
		return total;
	}

	/**
	 * Estimates a percentile of the durations, as the upper bound of the bucket that holds it.
	 *
	 * @param percent a percentile, from 0 to 100
	 * @return the upper bound of the bucket holding that percentile, in
	 *         nanoseconds; {@code -1} when it holds nothing
	 */
	public long percentileNanos(final double percent) {
		long total = this.total();
		if (total == 0) {
			return -1;
		}
		long rank = (long) Math.ceil(total * Math.max(0, Math.min(100, percent)) / 100.0);
		long seen = 0;
		for (int bucket = 0; bucket < BUCKETS; bucket++) {
			seen += this.counts[bucket];
			if (seen >= Math.max(1, rank)) {
				return upperBoundNanos(bucket);
			}
		}
		return upperBoundNanos(BUCKETS - 1);
	}

	/**
	 * Subtracts an earlier reading of the same histogram, leaving what was counted in between.
	 *
	 * @param earlier the same histogram read before
	 * @return what was counted since
	 */
	public DurationHistogram since(final DurationHistogram earlier) {
		RuntimeCheck.notNull(earlier, "earlier");
		long[] difference = new long[BUCKETS];
		for (int bucket = 0; bucket < BUCKETS; bucket++) {
			difference[bucket] = Math.max(0, this.counts[bucket] - earlier.counts[bucket]);
		}
		return new DurationHistogram(difference);
	}

	/**
	 * Returns the number of durations in each bucket.
	 *
	 * @return the counts, copied
	 */
	@Override
	public long[] counts() {
		return this.counts.clone();
	}

	@Override
	public boolean equals(final Object other) {
		return other instanceof DurationHistogram histogram && Arrays.equals(this.counts, histogram.counts);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(this.counts);
	}

	@Override
	public String toString() {
		return "DurationHistogram" + Arrays.toString(this.counts);
	}
}
