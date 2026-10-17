/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.containermanager.raw;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import org.opales.covalent.supervision.observed.ObservedPortKind;
import org.opales.covalent.supervision.observed.ObservedPortRate;
import org.opales.covalent.supervision.observed.ObservedPortRateEvents;

/**
 * Unit tests for {@link PortRateSampler}: the per-period call/failure/duration counting and
 * the {@code percentile95} helper, exercised directly without any container or observed calls.
 */
public class PortRateSamplerTests {

	private final List<List<ObservedPortRate>> reports = new CopyOnWriteArrayList<>();
	private volatile CountDownLatch latch;

	private final ObservedPortRateEvents sink = (sampleTimeMillis, periodMillis, rates) -> {
		this.reports.add(rates);
		if (this.latch != null) {
			this.latch.countDown();
		}
	};

	private PortRateSampler sampler;

	@After
	public void tearDown() {
		if (this.sampler != null) {
			this.sampler.stop();
		}
	}

	private List<ObservedPortRate> awaitNextReport() throws InterruptedException {
		this.latch = new CountDownLatch(1);
		Assert.assertTrue("no report arrived within the period",
				this.latch.await(PortRateSampler.PERIOD_MILLIS * 3, TimeUnit.MILLISECONDS));
		return this.reports.get(this.reports.size() - 1);
	}

	@Test
	public void countedCallsAreReportedOncePerPeriodThenReset() throws InterruptedException {
		this.sampler = new PortRateSampler(this.sink);
		this.sampler.start();
		this.sampler.count(String.class, Runnable.class, ObservedPortKind.SERVICE, true);
		this.sampler.count(String.class, Runnable.class, ObservedPortKind.SERVICE, true);

		List<ObservedPortRate> first = this.awaitNextReport();
		Assert.assertEquals(1, first.size());
		ObservedPortRate rate = first.get(0);
		Assert.assertEquals(String.class, rate.componentType());
		Assert.assertEquals(Runnable.class, rate.portType());
		Assert.assertEquals(ObservedPortKind.SERVICE, rate.portKind());
		Assert.assertTrue(rate.receiving());
		Assert.assertEquals(2, rate.calls());
		Assert.assertEquals(0, rate.failures());
		Assert.assertEquals(-1, rate.p95Nanos());
		Assert.assertNull(rate.lastFailedMethod());

		// Nothing counted during the second period: it must be reported as empty, not as a
		// stale repeat of the first period's counts.
		List<ObservedPortRate> second = this.awaitNextReport();
		Assert.assertTrue("counts must reset between periods", second.isEmpty());
	}

	@Test
	public void finishedCallsReportFailuresAndLastFailedMethod() throws InterruptedException {
		this.sampler = new PortRateSampler(this.sink);
		this.sampler.start();
		this.sampler.count(String.class, Runnable.class, ObservedPortKind.EVENT, false);
		this.sampler.finished(String.class, Runnable.class, ObservedPortKind.EVENT, false, 1_000L,
				new RuntimeException("boom"), "run");

		List<ObservedPortRate> report = this.awaitNextReport();
		Assert.assertEquals(1, report.size());
		ObservedPortRate rate = report.get(0);
		Assert.assertEquals(1, rate.failures());
		Assert.assertEquals("run", rate.lastFailedMethod());
		Assert.assertEquals(1_000L, rate.p95Nanos());
	}

	@Test
	public void callsAndFailuresWithoutAnyCountAreNotReported() throws InterruptedException {
		this.sampler = new PortRateSampler(this.sink);
		this.sampler.start();
		List<ObservedPortRate> report = this.awaitNextReport();
		Assert.assertTrue("an idle sampler must report no ports at all", report.isEmpty());
	}

	@Test
	public void nullComponentOrPortTypeIsIgnored() {
		this.sampler = new PortRateSampler(this.sink);
		// Neither call should throw, and since start() is never called here, nothing is
		// reported either - this only confirms countsOf()'s null guard doesn't blow up.
		this.sampler.count(null, Runnable.class, ObservedPortKind.SERVICE, true);
		this.sampler.count(String.class, null, ObservedPortKind.SERVICE, true);
		this.sampler.finished(null, Runnable.class, ObservedPortKind.SERVICE, true, 10L, null, "m");
	}

	@Test
	public void stopClearsCountsAndStopsFurtherReports() throws InterruptedException {
		this.sampler = new PortRateSampler(this.sink);
		this.sampler.start();
		this.sampler.count(String.class, Runnable.class, ObservedPortKind.SERVICE, true);
		this.awaitNextReport();

		this.sampler.stop();
		int reportsAtStop = this.reports.size();
		Thread.sleep(PortRateSampler.PERIOD_MILLIS * 2);
		Assert.assertEquals("no report should be produced once stopped", reportsAtStop, this.reports.size());
	}

	@Test
	public void percentile95OfAnEmptySetIsMinusOne() throws Exception {
		Assert.assertEquals(-1L, invokePercentile95(new long[0], 0));
	}

	@Test
	public void percentile95OfASingleValueIsThatValue() throws Exception {
		Assert.assertEquals(42L, invokePercentile95(new long[] { 42L }, 1));
	}

	@Test
	public void percentile95PicksTheExpectedRankOutOfTwenty() throws Exception {
		long[] durations = new long[20];
		for (int i = 0; i < 20; i++) {
			durations[i] = i + 1; // 1..20, unsorted on purpose (ascending here, but the method sorts anyway)
		}
		// ceil(20 * 0.95) = 19th smallest value (1-indexed) = 19.
		Assert.assertEquals(19L, invokePercentile95(durations, 20));
	}

	@Test
	public void percentile95IgnoresEntriesPastCount() throws Exception {
		long[] durations = new long[] { 5L, 1_000_000L, 1_000_000L };
		// Only the first entry is "live"; the rest is leftover array capacity that must be
		// ignored, matching how PortRateSampler itself only ever considers [0, durationCount).
		Assert.assertEquals(5L, invokePercentile95(durations, 1));
	}

	private static long invokePercentile95(final long[] durations, final int count) throws Exception {
		Method method = PortRateSampler.class.getDeclaredMethod("percentile95", long[].class, int.class);
		method.setAccessible(true);
		return (long) method.invoke(null, durations, count);
	}
}
