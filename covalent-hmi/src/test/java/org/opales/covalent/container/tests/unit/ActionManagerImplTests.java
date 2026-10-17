/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.tests.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.CovalentContainerParameters;
import org.opales.covalent.hmi.action.ActionEvents;
import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.hmi.action.ActionStatus;
import org.opales.covalent.hmi.action.impl.ActionManagerImpl;

import javafx.scene.layout.Pane;

/**
 * Behavior of the action manager on its own, without a started container:
 * registration, execution (no, one and two arguments), availability, value
 * bindings, JavaFX node bindings, observers and teardown.
 */
public class ActionManagerImplTests {

	private enum Key implements ActionKey {
		A, B
	}

	private enum Other implements ActionKey {
		C
	}

	private CovalentContainer container;

	private ActionManagerImpl manager;

	private void init(final boolean instrumented) throws Exception {
		this.container = Covalent.container();
		CovalentContainerParameters parameters = this.container.getParameters();
		parameters.setInstrumented(instrumented);
		this.manager = new ActionManagerImpl();
		Field field = ActionManagerImpl.class.getDeclaredField("covalentContainerParameters");
		field.setAccessible(true);
		field.set(this.manager, parameters);
	}

	@Before
	public void setUp() throws Exception {
		this.init(false);
	}

	@After
	public void tearDown() {
		this.container.dispose();
	}

	// --- execution ---

	@Test
	public void aNoArgumentActionRunsAndTheManagerIsIdleAfterwards() {
		AtomicInteger runs = new AtomicInteger();
		this.manager.registerActionExecution(Key.A, (Runnable) runs::incrementAndGet);

		assertTrue(this.manager.executeAction(Key.A));

		assertEquals(1, runs.get());
		assertEquals(ActionStatus.idle, this.manager.getActionStatus(Key.A));
	}

	@Test
	public void anActionIsRunningWhileItsImplementationExecutes() {
		List<ActionStatus> seen = new ArrayList<>();
		this.manager.registerActionExecution(Key.A, (Runnable) () -> seen.add(this.manager.getActionStatus(Key.A)));

		this.manager.executeAction(Key.A);

		assertEquals(List.of(ActionStatus.running), seen);
	}

	@Test
	public void anActionThatFailsPropagatesTheFailureAndTheManagerIsIdleAgain() {
		this.manager.registerActionExecution(Key.A, (Runnable) () -> {
			throw new IllegalStateException("boom");
		});
		this.manager.registerActionExecution(Key.B, (Consumer<String>) s -> {
			throw new IllegalStateException("boom");
		}, String.class);

		assertThrows(IllegalStateException.class, () -> this.manager.executeAction(Key.A));
		assertEquals(ActionStatus.idle, this.manager.getActionStatus(Key.A));
		assertThrows(IllegalStateException.class, () -> this.manager.executeAction(Key.B, "x"));
		assertEquals(ActionStatus.idle, this.manager.getActionStatus(Key.B));
	}

	@Test
	public void aTwoArgumentActionThatFailsPropagatesTheFailureAndTheManagerIsIdleAgain() {
		this.manager.registerActionExecution(Key.A, (String s, Integer i) -> {
			throw new IllegalStateException("boom");
		}, String.class, Integer.class);

		assertThrows(IllegalStateException.class, () -> this.manager.executeAction(Key.A, "s", 1));
		assertEquals(ActionStatus.idle, this.manager.getActionStatus(Key.A));
	}

	@Test
	public void aOneArgumentActionReceivesItsInput() {
		List<String> received = new ArrayList<>();
		this.manager.registerActionExecution(Key.A, (Consumer<String>) received::add, String.class);

		assertTrue(this.manager.executeAction(Key.A, "hello"));

		assertEquals(List.of("hello"), received);
	}

	@Test
	public void aTwoArgumentActionReceivesBothInputs() {
		List<String> received = new ArrayList<>();
		this.manager.registerActionExecution(Key.A, (String s, Integer i) -> received.add(s + i), String.class,
				Integer.class);

		assertTrue(this.manager.executeAction(Key.A, "n", 7));

		assertEquals(List.of("n7"), received);
	}

	@Test
	public void anActionCalledWithTheWrongNumberOfArgumentsDoesNotRun() {
		AtomicInteger runs = new AtomicInteger();
		this.manager.registerActionExecution(Key.A, (Runnable) runs::incrementAndGet);
		this.manager.registerActionExecution(Key.B, (Consumer<String>) s -> runs.incrementAndGet(), String.class);

		assertFalse(this.manager.executeAction(Key.A, "unexpected"));
		assertFalse(this.manager.executeAction(Key.A, "unexpected", "again"));
		assertFalse(this.manager.executeAction(Key.B));
		assertFalse(this.manager.executeAction(Key.B, "x", "y"));

		assertEquals(0, runs.get());
	}

	@Test
	public void anActionThatIsNotRegisteredReportsThatNothingRan() {
		assertFalse(this.manager.executeAction(Key.A));
		assertFalse(this.manager.executeAction(Key.A, "x"));
		assertFalse(this.manager.executeAction(Key.A, "x", "y"));
	}

	@Test
	public void registeringAgainReplacesTheImplementation() {
		List<String> runs = new ArrayList<>();
		this.manager.registerActionExecution(Key.A, (Runnable) () -> runs.add("old"));
		this.manager.registerActionExecution(Key.A, (Runnable) () -> runs.add("new"));

		this.manager.executeAction(Key.A);

		assertEquals(List.of("new"), runs);
	}

	@Test
	public void anUnregisteredActionNoLongerRuns() {
		AtomicInteger runs = new AtomicInteger();
		this.manager.registerActionExecution(Key.A, (Runnable) runs::incrementAndGet);

		this.manager.unregisterActionExecution(Key.A);

		assertFalse(this.manager.executeAction(Key.A));
		assertEquals(0, runs.get());
	}

	@Test
	public void unregisteringAnUnknownActionIsHarmless() {
		this.manager.unregisterActionExecution(Key.B);
	}

	// --- availability ---

	@Test
	public void anActionIsAvailableUntilToldOtherwise() {
		assertTrue(this.manager.isActionAvailable(Key.A));
		this.manager.setActionAvailable(Key.A, false);
		assertFalse(this.manager.isActionAvailable(Key.A));
		this.manager.setActionAvailable(Key.A, true);
		assertTrue(this.manager.isActionAvailable(Key.A));
	}

	@Test
	public void anUnavailableActionDoesNotRunAndReportsIt() {
		AtomicInteger runs = new AtomicInteger();
		this.manager.registerActionExecution(Key.A, (Runnable) runs::incrementAndGet);
		this.manager.registerActionExecution(Key.B, (Consumer<String>) s -> runs.incrementAndGet(), String.class);
		this.manager.setActionAvailable(Key.A, false);
		this.manager.setActionAvailable(Key.B, false);

		assertFalse(this.manager.executeAction(Key.A));
		assertFalse(this.manager.executeAction(Key.B, "x"));
		assertFalse(this.manager.executeAction(Key.B, "x", "y"));
		assertEquals(0, runs.get());

		this.manager.setActionAvailable(Key.A, true);
		assertTrue(this.manager.executeAction(Key.A));
		assertEquals(1, runs.get());
	}

	@Test
	public void anAvailabilityHandlerIsToldTheKnownStatusThenEveryChange() {
		List<Boolean> seen = new ArrayList<>();
		this.manager.setActionAvailable(Key.A, false);

		this.manager.addActionAvailabilityHandler(Key.A, seen::add);
		this.manager.setActionAvailable(Key.A, true);
		this.manager.setActionAvailable(Key.B, false);

		assertEquals(List.of(false, true), seen);
	}

	@Test
	public void anAvailabilityHandlerAddedBeforeAnyStatusIsNotCalledRightAway() {
		List<Boolean> seen = new ArrayList<>();

		this.manager.addActionAvailabilityHandler(Key.A, seen::add);

		assertTrue(seen.isEmpty());
	}

	// --- node bindings ---

	@Test
	public void aBoundNodeFollowsTheAvailabilityOfItsAction() {
		Pane node = new Pane();
		this.manager.bindActionAvailability(Key.A, node);
		assertFalse(node.isDisable());

		this.manager.setActionAvailable(Key.A, false);
		assertTrue(node.isDisable());

		this.manager.setActionAvailable(Key.A, true);
		assertFalse(node.isDisable());
	}

	@Test
	public void aNodeBoundToAnAlreadyUnavailableActionStartsDisabled() {
		this.manager.setActionAvailable(Key.A, false);
		Pane node = new Pane();

		this.manager.bindActionAvailability(Key.A, node);

		assertTrue(node.isDisable());
	}

	@Test
	public void aBoundNodeCannotBeEnabledWhileItsActionIsUnavailable() {
		Pane node = new Pane();
		this.manager.bindActionAvailability(Key.A, node);
		this.manager.setActionAvailable(Key.A, false);

		node.setDisable(false);

		assertTrue(node.isDisable());
	}

	@Test
	public void aBoundNodeMayBeDisabledByTheApplicationWhileItsActionIsAvailable() {
		Pane node = new Pane();
		this.manager.bindActionAvailability(Key.A, node);

		node.setDisable(true);

		assertTrue(node.isDisable());
	}

	@Test
	public void anUnboundNodeIsLeftAlone() {
		Pane node = new Pane();
		this.manager.bindActionAvailability(Key.A, node);
		this.manager.unbindActionAvailability(Key.A, node);

		this.manager.setActionAvailable(Key.A, false);
		assertFalse(node.isDisable());

		node.setDisable(true);
		node.setDisable(false);
		assertFalse(node.isDisable());
	}

	@Test
	public void unbindingANodeThatWasNeverBoundIsHarmless() {
		this.manager.unbindActionAvailability(Key.A, new Pane());
		this.manager.bindActionAvailability(Key.B, new Pane());
		this.manager.unbindActionAvailability(Key.B, new Pane());
	}

	@Test
	public void aNodeCannotBeBoundToASecondAction() {
		Pane node = new Pane();
		this.manager.bindActionAvailability(Key.A, node);

		assertThrows(IllegalStateException.class, () -> this.manager.bindActionAvailability(Key.B, node));

		this.manager.setActionAvailable(Key.B, false);
		assertFalse(node.isDisable());
		this.manager.setActionAvailable(Key.A, false);
		assertTrue(node.isDisable());
	}

	@Test
	public void aNodeCanBeBoundToAnotherActionOnceUnbound() {
		Pane node = new Pane();
		this.manager.bindActionAvailability(Key.A, node);
		this.manager.unbindActionAvailability(Key.A, node);

		this.manager.bindActionAvailability(Key.B, node);

		this.manager.setActionAvailable(Key.A, false);
		assertFalse(node.isDisable());
		this.manager.setActionAvailable(Key.B, false);
		assertTrue(node.isDisable());
	}

	@Test
	public void bindingTheSameNodeToTheSameActionTwiceChangesNothing() {
		Pane node = new Pane();
		this.manager.bindActionAvailability(Key.A, node);
		this.manager.bindActionAvailability(Key.A, node);

		this.manager.unbindActionAvailability(Key.A, node);

		this.manager.setActionAvailable(Key.A, false);
		assertFalse(node.isDisable());
		node.setDisable(true);
		node.setDisable(false);
		assertFalse(node.isDisable());
	}

	@Test
	public void unbindingFromAnActionTheNodeIsNotBoundToLeavesItsBindingAlone() {
		Pane node = new Pane();
		this.manager.bindActionAvailability(Key.A, node);

		this.manager.unbindActionAvailability(Key.B, node);

		this.manager.setActionAvailable(Key.A, false);
		assertTrue(node.isDisable());
		node.setDisable(false);
		assertTrue(node.isDisable());
	}

	// --- value bindings ---

	@Test
	public void aValueConsumerReceivesTheKnownValueThenEveryChange() {
		List<Object> seen = new ArrayList<>();
		this.manager.setActionValue(Key.A, "one");

		this.manager.bindActionValue(Key.A, seen::add);
		this.manager.setActionValue(Key.A, "two");
		this.manager.setActionValue(Key.B, "other");

		assertEquals(List.of("one", "two"), seen);
	}

	@Test
	public void aNullValueIsAValueLikeAnyOther() {
		List<Object> seen = new ArrayList<>();
		this.manager.setActionValue(Key.A, null);

		this.manager.bindActionValue(Key.A, seen::add);

		assertEquals(1, seen.size());
		assertNull(seen.get(0));
	}

	@Test
	public void aValueConsumerBoundBeforeAnyValueIsNotCalledRightAway() {
		List<Object> seen = new ArrayList<>();

		this.manager.bindActionValue(Key.A, seen::add);

		assertTrue(seen.isEmpty());
	}

	@Test
	public void anUnboundValueConsumerReceivesNothingMore() {
		List<Object> seen = new ArrayList<>();
		Consumer<Object> consumer = seen::add;
		this.manager.bindActionValue(Key.A, consumer);
		this.manager.setActionValue(Key.A, 1);

		this.manager.unbindActionValue(Key.A, consumer);
		this.manager.setActionValue(Key.A, 2);

		assertEquals(List.of(1), seen);
	}

	@Test
	public void unbindingAValueConsumerThatWasNeverBoundIsHarmless() {
		this.manager.unbindActionValue(Key.A, o -> {
		});
	}

	// --- observers ---

	private static final class Recorder implements ActionEvents {
		private final List<Object[]> calls = new ArrayList<>();

		@Override
		public void onActionTriggered(final ActionKey actionKey, final Class<?> actionCaller,
				final Class<?> actionImplementer, final Object... inputData) {
			this.calls.add(new Object[] { actionKey, actionCaller, actionImplementer });
		}
	}

	@Test
	public void anObserverIsToldWhoRanWhatWhenTheContainerIsInstrumented() throws Exception {
		this.tearDown();
		this.init(true);
		Recorder recorder = new Recorder();
		this.manager.addActionEventsListener(recorder);
		this.manager.registerActionExecution(Key.A, (Runnable) () -> {
		});
		this.manager.registerActionExecution(Key.B, (Consumer<String>) s -> {
		}, String.class);

		this.manager.executeAction(Key.A);
		this.manager.executeAction(Key.B, "x");

		assertEquals(2, recorder.calls.size());
		assertSame(Key.A, recorder.calls.get(0)[0]);
		assertSame(ActionManagerImplTests.class, recorder.calls.get(0)[1]);
		assertSame(ActionManagerImplTests.class, recorder.calls.get(0)[2]);
		assertSame(Key.B, recorder.calls.get(1)[0]);
	}

	@Test
	public void theImplementerIsUnknownWhenTheContainerIsNotInstrumented() {
		Recorder recorder = new Recorder();
		this.manager.addActionEventsListener(recorder);
		this.manager.registerActionExecution(Key.A, (Runnable) () -> {
		});

		this.manager.executeAction(Key.A);

		assertEquals(1, recorder.calls.size());
		assertNull(recorder.calls.get(0)[2]);
	}

	@Test
	public void aTwoArgumentActionRegisteredWhileInstrumentedHasItsImplementerRecorded() throws Exception {
		this.tearDown();
		this.init(true);
		Recorder recorder = new Recorder();
		this.manager.addActionEventsListener(recorder);
		this.manager.registerActionExecution(Key.A, (String s, Integer i) -> {
		}, String.class, Integer.class);

		this.manager.executeAction(Key.A, "s", 1);

		assertSame(ActionManagerImplTests.class, recorder.calls.get(0)[2]);
	}

	@Test
	public void anObserverReceivesTheInputDataTheActionWasLaunchedWith() {
		List<List<Object>> seen = new ArrayList<>();
		this.manager.addActionEventsListener(
				(key, caller, implementer, data) -> seen.add(new ArrayList<>(java.util.Arrays.asList(data))));
		this.manager.registerActionExecution(Key.A, (Runnable) () -> {
		});
		this.manager.registerActionExecution(Key.B, (Consumer<String>) s -> {
		}, String.class);
		this.manager.registerActionExecution(Other.C,
				(String s, Integer i) -> {
				}, String.class, Integer.class);

		this.manager.executeAction(Key.A);
		this.manager.executeAction(Key.B, "one");
		this.manager.executeAction(Other.C, "two", 2);

		assertEquals(List.of(List.of(), List.of("one"), List.of("two", 2)), seen);
	}

	@Test
	public void aRemovedObserverIsNotToldAnymore() {
		Recorder recorder = new Recorder();
		this.manager.addActionEventsListener(recorder);
		this.manager.registerActionExecution(Key.A, (Runnable) () -> {
		});
		this.manager.executeAction(Key.A);

		this.manager.removeActionEventsListener(recorder);
		this.manager.executeAction(Key.A);

		assertEquals(1, recorder.calls.size());
	}

	@Test
	public void anObserverMayRemoveItselfWhileBeingNotified() {
		List<String> told = new ArrayList<>();
		ActionEvents[] self = new ActionEvents[1];
		self[0] = (key, caller, implementer, data) -> {
			told.add("once");
			this.manager.removeActionEventsListener(self[0]);
		};
		this.manager.addActionEventsListener(self[0]);
		this.manager.registerActionExecution(Key.A, (Runnable) () -> {
		});

		this.manager.executeAction(Key.A);
		this.manager.executeAction(Key.A);

		assertEquals(List.of("once"), told);
	}

	@Test
	public void anUnavailableActionDoesNotNotifyTheObservers() {
		Recorder recorder = new Recorder();
		this.manager.addActionEventsListener(recorder);
		this.manager.registerActionExecution(Key.A, (Runnable) () -> {
		});
		this.manager.setActionAvailable(Key.A, false);

		this.manager.executeAction(Key.A);

		assertTrue(recorder.calls.isEmpty());
	}

	// --- teardown ---

	@Test
	public void passivatingForgetsEverything() {
		AtomicInteger runs = new AtomicInteger();
		List<Object> values = new ArrayList<>();
		List<Boolean> availabilities = new ArrayList<>();
		Pane node = new Pane();
		this.manager.registerActionExecution(Key.A, (Runnable) runs::incrementAndGet);
		this.manager.setActionAvailable(Key.A, false);
		this.manager.setActionValue(Key.A, "v");
		this.manager.bindActionValue(Key.A, values::add);
		this.manager.addActionAvailabilityHandler(Key.A, availabilities::add);
		this.manager.bindActionAvailability(Key.A, node);
		values.clear();
		availabilities.clear();

		this.manager.passivate();

		assertTrue(this.manager.isActionAvailable(Key.A));
		this.manager.executeAction(Key.A);
		assertEquals(0, runs.get());
		node.setDisable(false);
		assertFalse(node.isDisable());
		this.manager.setActionValue(Key.A, "after");
		this.manager.setActionAvailable(Key.A, false);
		assertTrue(values.toString(), values.isEmpty());
		assertTrue(availabilities.toString(), availabilities.isEmpty());
		List<Object> lateValues = new ArrayList<>();
		this.manager.bindActionValue(Key.B, lateValues::add);
		assertTrue(lateValues.isEmpty());
	}

	@Test
	public void passivatingReleasesTheNodesThatWereBound() {
		Pane node = new Pane();
		this.manager.bindActionAvailability(Key.A, node);

		this.manager.passivate();
		this.manager.setActionAvailable(Key.A, false);
		node.setDisable(true);
		node.setDisable(false);

		assertFalse(node.isDisable());
	}

	@Test
	public void activatingDoesNothingVisible() {
		this.manager.activate();
		assertTrue(this.manager.isActionAvailable(Key.A));
		assertEquals(ActionStatus.idle, this.manager.getActionStatus(Key.A));
	}
}
