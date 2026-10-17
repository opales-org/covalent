/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.sequence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.opales.covalent.supervision.componentview.sequence.SequenceTraceMessage.Style;
import org.opales.covalent.supervision.componentview.sequence.SequenceTraceReconciler.InteractionKind;
import org.opales.covalent.supervision.observed.CallOutcome;

public class SequenceTraceReconcilerTest {

	private static final long NOW = System.nanoTime();

	private static final Thread THREAD_A = new Thread("sequence-a");

	private static final Thread THREAD_B = new Thread("sequence-b");

	@Test
	public void shouldEmitServiceCallOnProviderStartAndReturnOnCallerFinish() throws Exception {
		List<SequenceTraceMessage> messages = new ArrayList<>();
		SequenceTraceReconciler reconciler = new SequenceTraceReconciler(Set.of(ComponentA.class, ComponentB.class),
				ignored -> false, messages::add);
		Method method = SampleService.class.getMethod("execute", String.class);

		reconciler.onCallerSideStarted(InteractionKind.SERVICE, THREAD_A, NOW, SampleService.class, method, ComponentA.class,
				"payload");
		assertEquals(0, messages.size());

		reconciler.onProviderSideStarted(InteractionKind.SERVICE, THREAD_A, NOW, SampleService.class, method,
				ComponentB.class);
		assertEquals(1, messages.size());
		assertEquals(ComponentA.class, messages.get(0).getSourceType());
		assertEquals(ComponentB.class, messages.get(0).getTargetType());
		assertEquals(Style.CALL, messages.get(0).getStyle());

		reconciler.onProviderSideFinished(InteractionKind.SERVICE, THREAD_A, SampleService.class, method,
				ComponentB.class, 12_000_000L, null, CallOutcome.value("int : 42"));
		assertEquals(1, messages.size());

		reconciler.onCallerSideFinished(InteractionKind.SERVICE, THREAD_A, NOW, SampleService.class, method, ComponentA.class,
				14_000_000L, null, CallOutcome.value("int : 42"));
		assertEquals(2, messages.size());
		assertEquals(ComponentB.class, messages.get(1).getSourceType());
		assertEquals(CallOutcome.value("int : 42"), messages.get(1).getFacts().outcome());
		assertEquals(ComponentA.class, messages.get(1).getTargetType());
		assertEquals(Style.RETURN, messages.get(1).getStyle());
	}

	@Test
	public void shouldHandleParameterCallsLikeBidirectionalInteractions() throws Exception {
		List<SequenceTraceMessage> messages = new ArrayList<>();
		SequenceTraceReconciler reconciler = new SequenceTraceReconciler(Set.of(ComponentA.class, ComponentB.class),
				ignored -> false, messages::add);
		Method method = SampleParameters.class.getMethod("readSetting");

		reconciler.onCallerSideStarted(InteractionKind.PARAMETER, THREAD_A, NOW, SampleParameters.class, method,
				ComponentA.class, "<no-args>");
		reconciler.onProviderSideStarted(InteractionKind.PARAMETER, THREAD_A, NOW, SampleParameters.class, method,
				ComponentB.class);
		reconciler.onProviderSideFinished(InteractionKind.PARAMETER, THREAD_A, SampleParameters.class, method,
				ComponentB.class, 5_000_000L, null, CallOutcome.value("int : 42"));
		reconciler.onCallerSideFinished(InteractionKind.PARAMETER, THREAD_A, NOW, SampleParameters.class, method,
				ComponentA.class, 6_000_000L, null, CallOutcome.value("int : 42"));

		assertEquals(2, messages.size());
		assertEquals("SampleParameters\nreadSetting()", messages.get(0).getLabel());
		assertEquals(SequenceTexts.get("return"), messages.get(1).getLabel());
	}

	@Test
	public void shouldMatchNestedInvocationsInLifoOrderWithinOneThread() throws Exception {
		List<SequenceTraceMessage> messages = new ArrayList<>();
		SequenceTraceReconciler reconciler = new SequenceTraceReconciler(
				Set.of(ComponentA.class, ComponentB.class, ComponentC.class), ignored -> false, messages::add);
		Method outerMethod = SampleService.class.getMethod("execute", String.class);
		Method innerMethod = SecondaryService.class.getMethod("compute", int.class);

		reconciler.onCallerSideStarted(InteractionKind.SERVICE, THREAD_A, NOW, SampleService.class, outerMethod,
				ComponentA.class, "outer");
		reconciler.onProviderSideStarted(InteractionKind.SERVICE, THREAD_A, NOW, SampleService.class, outerMethod,
				ComponentB.class);

		reconciler.onCallerSideStarted(InteractionKind.SERVICE, THREAD_A, NOW, SecondaryService.class, innerMethod,
				ComponentB.class, "inner");
		reconciler.onProviderSideStarted(InteractionKind.SERVICE, THREAD_A, NOW, SecondaryService.class, innerMethod,
				ComponentC.class);
		reconciler.onProviderSideFinished(InteractionKind.SERVICE, THREAD_A, SecondaryService.class, innerMethod,
				ComponentC.class, 3_000_000L, null, CallOutcome.value("int : 42"));
		reconciler.onCallerSideFinished(InteractionKind.SERVICE, THREAD_A, NOW, SecondaryService.class, innerMethod,
				ComponentB.class, 4_000_000L, null, CallOutcome.value("int : 42"));

		reconciler.onProviderSideFinished(InteractionKind.SERVICE, THREAD_A, SampleService.class, outerMethod,
				ComponentB.class, 7_000_000L, null, CallOutcome.value("int : 42"));
		reconciler.onCallerSideFinished(InteractionKind.SERVICE, THREAD_A, NOW, SampleService.class, outerMethod,
				ComponentA.class, 8_000_000L, null, CallOutcome.value("int : 42"));

		assertEquals(4, messages.size());
		assertEquals("SampleService\nexecute()", messages.get(0).getLabel());
		assertEquals("SecondaryService\ncompute()", messages.get(1).getLabel());
		assertEquals(SequenceTexts.get("return"), messages.get(2).getLabel());
		assertEquals(ComponentC.class, messages.get(2).getSourceType());
		assertEquals(ComponentB.class, messages.get(2).getTargetType());
		assertEquals(ComponentB.class, messages.get(3).getSourceType());
		assertEquals(ComponentA.class, messages.get(3).getTargetType());
	}

	@Test
	public void shouldKeepSameMethodCallsSeparatedAcrossThreads() throws Exception {
		List<SequenceTraceMessage> messages = new ArrayList<>();
		SequenceTraceReconciler reconciler = new SequenceTraceReconciler(
				Set.of(ComponentA.class, ComponentB.class, ComponentC.class, ComponentD.class), ignored -> false,
				messages::add);
		Method method = SampleService.class.getMethod("execute", String.class);

		reconciler.onCallerSideStarted(InteractionKind.SERVICE, THREAD_A, NOW, SampleService.class, method, ComponentA.class,
				"a");
		reconciler.onCallerSideStarted(InteractionKind.SERVICE, THREAD_B, NOW, SampleService.class, method, ComponentC.class,
				"b");

		reconciler.onProviderSideStarted(InteractionKind.SERVICE, THREAD_B, NOW, SampleService.class, method,
				ComponentD.class);
		reconciler.onProviderSideStarted(InteractionKind.SERVICE, THREAD_A, NOW, SampleService.class, method,
				ComponentB.class);

		assertEquals(2, messages.size());
		assertEquals(ComponentC.class, messages.get(0).getSourceType());
		assertEquals(ComponentD.class, messages.get(0).getTargetType());
		assertEquals(ComponentA.class, messages.get(1).getSourceType());
		assertEquals(ComponentB.class, messages.get(1).getTargetType());
	}

	@Test
	public void shouldEmitDirectEventCallWithCompletionInsteadOfReturn() throws Exception {
		List<SequenceTraceMessage> messages = new ArrayList<>();
		SequenceTraceReconciler reconciler = new SequenceTraceReconciler(Set.of(ComponentA.class, ComponentB.class),
				ignored -> false, messages::add);
		Method method = SampleEvents.class.getMethod("onChanged", String.class);

		reconciler.onDirectInteractionStarted(InteractionKind.EVENT, THREAD_A, NOW, SampleEvents.class, method,
				ComponentA.class, ComponentB.class, "event", null, true, 3_000_000L);
		reconciler.onDirectInteractionFinished(InteractionKind.EVENT, THREAD_A, NOW, SampleEvents.class, method,
				ComponentA.class, ComponentB.class, 2_000_000L, new IllegalStateException("boom"));

		assertEquals(2, messages.size());
		assertEquals(Style.CALL, messages.get(0).getStyle());
		assertEquals("SampleEvents\nonChanged()", messages.get(0).getLabel());
		assertEquals(Style.COMPLETION, messages.get(1).getStyle());
		assertEquals(messages.get(0).getFacts().invocationId(), messages.get(1).getFacts().invocationId());
		assertEquals(2_000_000L, messages.get(1).getFacts().durationNanos());
		assertTrue(messages.get(1).getFacts().isFailure());
		assertEquals(3_000_000L, messages.get(0).getFacts().queueWaitNanos());
	}

	@Test
	public void shouldEmitSynchronousEventReturn() throws Exception {
		List<SequenceTraceMessage> messages = new ArrayList<>();
		SequenceTraceReconciler reconciler = new SequenceTraceReconciler(Set.of(ComponentA.class, ComponentB.class),
				ignored -> false, messages::add);
		Method method = SampleEvents.class.getMethod("onChanged", String.class);

		reconciler.onDirectInteractionStarted(InteractionKind.EVENT, THREAD_A, NOW, SampleEvents.class, method,
				ComponentA.class, ComponentB.class, "event", null, false, 0L);
		reconciler.onDirectInteractionFinished(InteractionKind.EVENT, THREAD_A, NOW, SampleEvents.class, method,
				ComponentA.class, ComponentB.class, 1_000_000L, null);

		assertEquals(2, messages.size());
		assertEquals(Style.CALL, messages.get(0).getStyle());
		assertEquals(Style.RETURN, messages.get(1).getStyle());
		assertEquals(ComponentB.class, messages.get(1).getSourceType());
		assertEquals(1_000_000L, messages.get(1).getFacts().durationNanos());
	}

	private interface SampleService {
		void execute(String value);
	}

	private interface SecondaryService {
		void compute(int value);
	}

	private interface SampleParameters {
		String readSetting();
	}

	private interface SampleEvents {
		void onChanged(String value);
	}

	private static final class ComponentA {
	}

	private static final class ComponentB {
	}

	private static final class ComponentC {
	}

	private static final class ComponentD {
	}
}
