/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.container.tests.unit.support.CapturingLogHandler;
import org.opales.covalent.container.util.CallerFinder;
import org.opales.covalent.container.util.Reflection;

/**
 * Verifies how {@link CallerFinder} resolves the class that called a method of
 * the container: frames of invocation handlers, of the JDK reflection and
 * proxy machinery and of excluded classes are skipped, the immediate caller
 * only skips the first two kinds, and an exhausted stack is reported.
 */
public class CallerFinderTests {

	/** Asks the finder from its own frame, like a container class does. */
	public static class Probe {
		public Class<?> ask() {
			return CallerFinder.getCaller();
		}

		public Class<?> askExcluding(final List<Class<?>> excluded) {
			return CallerFinder.getCaller(excluded);
		}

		public Class<?> askImmediate() {
			return CallerFinder.getImmediateCaller();
		}
	}

	/** The class the finder is expected to report. */
	public static class Middle {
		public Class<?> call(final Probe probe) {
			return probe.ask();
		}

		public Class<?> callExcluding(final Probe probe, final List<Class<?>> excluded) {
			return probe.askExcluding(excluded);
		}

		public Class<?> callImmediate(final Probe probe) {
			return probe.askImmediate();
		}

		public Class<?> callThroughHandler(final Probe probe) {
			return new RelayInvocationHandler().relay(probe);
		}

		public Class<?> callImmediateThroughHandler(final Probe probe) {
			return new RelayInvocationHandler().relayImmediate(probe);
		}

		public Class<?> callThroughReflection(final Probe probe, final String methodName) throws Exception {
			return (Class<?>) Probe.class.getMethod(methodName).invoke(probe);
		}

		public Class<?> callThroughReflectionHelper(final Probe probe, final String methodName) throws Exception {
			Method method = Probe.class.getMethod(methodName);
			return (Class<?>) Reflection.invokeMethod(method, probe);
		}
	}

	/** Its name ends like an invocation handler, so its frames are skipped. */
	public static class RelayInvocationHandler {
		public Class<?> relay(final Probe probe) {
			return probe.ask();
		}

		public Class<?> relayImmediate(final Probe probe) {
			return probe.askImmediate();
		}
	}

	/** Fills the stack with excluded frames. */
	public static class Deep {
		public Class<?> descend(final int remaining, final Probe probe, final List<Class<?>> excluded) {
			if (remaining > 1) {
				return this.descend(remaining - 1, probe, excluded);
			}
			return probe.askExcluding(excluded);
		}
	}

	private CapturingLogHandler callerFinderLogs;

	@Before
	public void setUp() {
		this.callerFinderLogs = CapturingLogHandler.install(CallerFinder.class);
	}

	@After
	public void tearDown() {
		this.callerFinderLogs.restore();
	}

	@Test
	public void theCallerIsTheClassThatCalledTheMethodAskingForIt() {
		Assert.assertEquals(Middle.class, new Middle().call(new Probe()));
	}

	@Test
	public void framesOfInvocationHandlersAreSkipped() {
		Assert.assertEquals(Middle.class, new Middle().callThroughHandler(new Probe()));
		Assert.assertEquals(Middle.class, new Middle().callImmediateThroughHandler(new Probe()));
	}

	@Test
	public void framesOfTheJdkReflectionMachineryAreSkipped() throws Exception {
		Assert.assertEquals(Middle.class, new Middle().callThroughReflection(new Probe(), "ask"));
		Assert.assertEquals(Middle.class, new Middle().callThroughReflection(new Probe(), "askImmediate"));
	}

	@Test
	public void excludedClassesAreSkipped() {
		Assert.assertEquals(CallerFinderTests.class,
				new Middle().callExcluding(new Probe(), List.of(Middle.class)));
		Assert.assertEquals(Middle.class, new Middle().callExcluding(new Probe(), List.of(String.class)));
	}

	@Test
	public void theCallerSkipsTheContainerHelpersWhereasTheImmediateCallerStopsAtThem() throws Exception {
		Assert.assertEquals(Middle.class, new Middle().callThroughReflectionHelper(new Probe(), "ask"));
		Assert.assertEquals(Reflection.class, new Middle().callThroughReflectionHelper(new Probe(), "askImmediate"));
	}

	@Test
	public void theImmediateCallerIsTheClassThatCalledTheMethodAskingForIt() {
		Assert.assertEquals(Middle.class, new Middle().callImmediate(new Probe()));
	}

	@Test
	public void aCallerFoundOnTheLastInspectedFrameIsNotReportedAsAnExhaustedStack() {
		// 23 excluded frames, then this test: the caller is the 25th and last frame inspected
		Class<?> caller = new Deep().descend(22, new Probe(), List.of(Deep.class, Probe.class));

		Assert.assertEquals(CallerFinderTests.class, caller);
		Assert.assertTrue(this.callerFinderLogs.messages(Level.SEVERE).isEmpty());
	}

	@Test
	public void anExhaustedStackIsReportedAndAnswersTheLastFrame() {
		Class<?> caller = new Deep().descend(40, new Probe(), List.of(Deep.class, Probe.class));

		Assert.assertEquals(Deep.class, caller);
		List<String> severe = this.callerFinderLogs.messages(Level.SEVERE);
		Assert.assertEquals(1, severe.size());
		Assert.assertTrue(severe.get(0), severe.get(0).contains("Limit stack size reached"));
	}
}
