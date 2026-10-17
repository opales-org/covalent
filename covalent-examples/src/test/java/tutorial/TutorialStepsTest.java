/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package tutorial;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

/**
 * Runs every tutorial step exactly the way a reader is told to run it in
 * {@code doc/tutorial/}: as {@code java tutorial.StepN}, its own process,
 * started fresh. Each step's documented output is then checked line by line.
 * <p>
 * This is not a test of the framework: {@code covalent-tests} already covers
 * the framework itself. It exists so that the tutorial cannot silently drift
 * from the code it claims to run - if a step stops compiling, or starts
 * printing something else, this suite turns red.
 * <p>
 * Each step runs in its own forked JVM, one process per test, instead of
 * calling {@code StepN.main(...)} directly in-process. Two reasons: it is a
 * more faithful reproduction of what the tutorial actually tells a reader to
 * do (run each program on its own), and it avoids sharing the {@code
 * Covalent.main} singleton container across steps, which does not reflect
 * how any of these programs are meant to be used and would make the suite
 * depend on JUnit's (unspecified) test method order.
 * <p>
 * Steps 1, 2, 4, 5, 7 and 8 never touch events, so their output is fully
 * deterministic and compared line by line, in order. Steps 3 and 6 dispatch
 * events (synchronously, so each run is itself single-threaded and
 * deterministic), but - as both tutorial pages point out - the order in
 * which independent consumers of the same event are called is not a
 * contract; their output is compared as an unordered multiset of lines
 * instead of a fixed sequence, so this test does not depend on an ordering
 * detail the framework never promised.
 */
public class TutorialStepsTest {

	@Test
	public void step1PrintsGreetingStatusAndFarewell() throws Exception {
		List<String> lines = this.runStep("Step1");
		this.assertLinesInOrder(
				lines,
				"Hello from a Covalent component!",
				"Startup status: healthy",
				"Goodbye from a Covalent component!");
	}

	@Test
	public void step2PrintsThePriceOfTwoCoffees() throws Exception {
		List<String> lines = this.runStep("Step2");
		this.assertLinesInOrder(
				lines,
				"Two coffees cost 500 cents.");
	}

	@Test
	public void step3PrintsOneOrderAndTwoConsumersPerPlacedOrder() throws Exception {
		List<String> lines = this.runStep("Step3");
		this.assertSameLines(
				lines,
				"Orders: placing 2 x coffee",
				"Audit: order recorded (2 x coffee)",
				"Kitchen: preparing 2 x coffee",
				"Orders: placing 1 x croissant",
				"Audit: order recorded (1 x croissant)",
				"Kitchen: preparing 1 x croissant");
	}

	@Test
	public void step4PrintsTheConfiguredTaxRateAndThePriceWithTax() throws Exception {
		List<String> lines = this.runStep("Step4");
		this.assertLinesInOrder(
				lines,
				"Tax rate configured: 10%",
				"Two coffees cost 550 cents, tax included.");
	}

	@Test
	public void step5PrintsEveryLifecycleTransitionInOrder() throws Exception {
		List<String> lines = this.runStep("Step5");
		this.assertLinesInOrder(
				lines,
				"State after deployment: deployed",
				"  [Ticker] initialization: prepare resources (once)",
				"State after initializeComponent: initialized",
				"  [Ticker] activation: ports are published",
				"State after start: activated",
				"  [Ticker] passivation: ports are unpublished",
				"State after passivateComponent: passivated",
				"  [Ticker] activation: ports are published",
				"State after activateComponent: activated",
				"  [Ticker] passivation: ports are unpublished",
				"  [Ticker] removal: release resources");
	}

	@Test
	public void step6PrintsSubscriptionsFilteringAndCloneIsolation() throws Exception {
		List<String> lines = this.runStep("Step6");
		this.assertSameLines(
				lines,
				"Orders: a subscriber joined (0 orders so far)",
				"Orders: a filtered subscriber joined (0 orders so far)",
				"Audit: 2 x coffee",
				"Orders: my own order is unchanged: 2 x coffee",
				"Audit: 12 x croissant",
				"Alert: a croissant order arrived, product = croissant",
				"Orders: my own order is unchanged: 12 x croissant");
	}

	@Test
	public void step7PrintsOneReceiptLinePerCheckout() throws Exception {
		List<String> lines = this.runStep("Step7");
		this.assertLinesInOrder(
				lines,
				"2 x coffee = 500 cents",
				"3 x croissant = 540 cents");
	}

	@Test
	public void step8PrintsTheExistingStockQuantity() throws Exception {
		List<String> lines = this.runStep("Step8");
		this.assertLinesInOrder(
				lines,
				"Coffee in stock: 42");
	}

	/**
	 * Runs {@code tutorial.<stepClassName>} as its own {@code java} process,
	 * on the same classpath this test itself runs on, and returns what it
	 * printed on standard output as a list of lines. Standard error (the
	 * container's {@code java.util.logging} INFO lines) is drained on a
	 * separate thread so it can never fill its pipe and deadlock the process,
	 * and is otherwise discarded: the tutorial pages document standard
	 * output only.
	 */
	private List<String> runStep(final String stepClassName) throws IOException, InterruptedException {
		String javaBinary = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
		ProcessBuilder processBuilder = new ProcessBuilder(
				javaBinary, "-cp", System.getProperty("java.class.path"), "tutorial." + stepClassName);
		Process process = processBuilder.start();

		ByteArrayOutputStream discardedStderr = new ByteArrayOutputStream();
		Thread stderrDrain = new Thread(() -> {
			try {
				process.getErrorStream().transferTo(discardedStderr);
			} catch (final IOException ignored) {
				// Best-effort drain; nothing to do if the pipe breaks on process exit.
			}
		});
		stderrDrain.start();

		byte[] stdoutBytes = process.getInputStream().readAllBytes();
		int exitCode = process.waitFor();
		stderrDrain.join();

		Assert.assertEquals(
				"tutorial." + stepClassName + " should exit successfully; stderr was:\n" + discardedStderr,
				0, exitCode);

		String output = new String(stdoutBytes, StandardCharsets.UTF_8);
		return output.isEmpty() ? Collections.emptyList() : output.lines().toList();
	}

	private void assertLinesInOrder(final List<String> actual, final String... expected) {
		Assert.assertEquals(Arrays.asList(expected), actual);
	}

	private void assertSameLines(final List<String> actual, final String... expected) {
		List<String> sortedActual = new ArrayList<>(actual);
		List<String> sortedExpected = new ArrayList<>(Arrays.asList(expected));
		Collections.sort(sortedActual);
		Collections.sort(sortedExpected);
		Assert.assertEquals(sortedExpected, sortedActual);
	}
}
