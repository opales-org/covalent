/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.controller.impl;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.container.registry.RegistryReadServices;
import org.opales.covalent.supervision.exclusionpolicy.controller.ExclusionPolicyControllerServices;
import org.opales.covalent.supervision.exclusionpolicy.controller.impl.ExclusionPolicyControllerImpl;
import org.opales.covalent.supervision.exclusionpolicy.model.impl.ExclusionPolicyModelImpl;
import org.opales.covalent.supervision.stereotypepolicy.controller.impl.StereotypePolicyControllerImpl;
import org.opales.covalent.supervision.stereotypepolicy.model.impl.StereotypePolicyModelImpl;
import org.opales.covalent.supervision.observed.CallOutcome;
import org.opales.covalent.container.component.internals.Internal;
import org.opales.covalent.supervision.componentdiagram.controller.ComponentDiagramController;
import org.opales.covalent.supervision.componentdiagram.interaction.ComponentDiagramInteraction;
import org.opales.covalent.supervision.observed.ObservedEventsManagerEvents;
import org.opales.covalent.supervision.observed.ObservedParametersManagerEvents;
import org.opales.covalent.supervision.observed.ObservedServicesManagerEvents;
import org.opales.covalent.supervision.testsupport.observed.SampleEvents;
import org.opales.covalent.supervision.testsupport.observed.SampleParameters;
import org.opales.covalent.supervision.componentview.sequence.SequenceTraceMessage;
import org.opales.covalent.supervision.sequencediagram.controller.RecordingStateEvents;
import org.opales.covalent.supervision.sequencediagram.controller.SequenceDiagramController;
import org.opales.covalent.supervision.sequencediagram.controller.SequenceDiagramControllerServices;
import org.opales.covalent.supervision.sequencediagram.controller.SequenceDiagramServices;
import org.opales.covalent.supervision.sequencediagram.model.SequenceDiagramEvents;
import org.opales.covalent.supervision.sequencediagram.model.impl.SequenceDiagramModelImpl;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;
import org.opales.covalent.supervision.testsupport.observed.LeafAUserComponent;
import org.opales.covalent.supervision.testsupport.observed.SampleServicesA;
import org.opales.covalent.supervision.testsupport.observed.StubActionObservationComponent;
import org.opales.covalent.supervision.testsupport.observed.impl.StubActionObservationComponentImpl;
import org.opales.covalent.supervision.testsupport.observed.impl.StubObservedCallStreamComponentImpl;
import org.opales.covalent.supervision.testsupport.observed.impl.StubObservedMetamodelComponentImpl;
import org.opales.covalent.hmi.action.ActionKey;

/**
 * Covers {@link SequenceDiagramControllerImpl}: the recording-session
 * lifecycle (start/stop, the "only one at a time" guard), actor-selection
 * and session-close forwarding to the Model, the {@code @Activation}/{@code
 * @Passivation} wiring to {@code ActionObservationServices}, and the
 * exclusion-policy filtering applied to live call activity before it
 * reaches the reconciler. {@link
 * org.opales.covalent.supervision.componentview.sequence.SequenceTraceReconcilerTest}
 * already covers the reconciler's own call/return pairing logic in
 * isolation, so here call activity is only exercised far enough to confirm
 * this Controller wires it through (and through the real exclusion policy)
 * correctly - not to re-verify the reconciler's internals.
 */
public class SequenceDiagramControllerImplTests {

	private CovalentContainer container;

	private SequenceDiagramServices sequenceDiagramServices;

	private SequenceDiagramControllerServices sequenceDiagramControllerServices;

	private ExclusionPolicyControllerServices exclusionPolicyControllerServices;

	private StubActionObservationComponentImpl stubActionObservation;

	private ObservedServicesManagerEvents controllerAsServicesManagerEvents;

	private final List<ViewKey> sessionCreatedKeys = new CopyOnWriteArrayList<>();

	private final List<SequenceTraceMessage> appendedMessages = Collections.synchronizedList(new ArrayList<>());

	private final List<ViewKey> appendedMessageSessionKeys = Collections.synchronizedList(new ArrayList<>());

	private final List<Set<Class<?>>> selectedActorsHistory = new CopyOnWriteArrayList<>();

	private final List<ViewKey> sessionClosedKeys = new CopyOnWriteArrayList<>();

	private final List<Integer> recordingLimits = new CopyOnWriteArrayList<>();

	private final AtomicInteger recordingStartedCount = new AtomicInteger();

	private final AtomicInteger recordingStoppedCount = new AtomicInteger();

	@Before
	public void setUp() {
		this.container = Covalent.container();
		this.container.getParameters().setSynchronousEvents(true);

		RawComponentsServices rcs = this.container.rcs();
		rcs.registerComponent(SequenceDiagramControllerImplTests.class, this);
		rcs.subscribe(SequenceDiagramControllerImplTests.class, SequenceDiagramEvents.class,
				new SequenceDiagramEvents() {
					@Override
					public void onSessionCreated(final ViewKey sessionKey, final Set<Class<?>> observedComponentTypes) {
						SequenceDiagramControllerImplTests.this.sessionCreatedKeys.add(sessionKey);
					}

					@Override
					public void onMessageAppended(final ViewKey sessionKey, final SequenceTraceMessage message) {
						SequenceDiagramControllerImplTests.this.appendedMessageSessionKeys.add(sessionKey);
						SequenceDiagramControllerImplTests.this.appendedMessages.add(message);
					}

					@Override
					public void onSelectedActorsChanged(final ViewKey sessionKey, final Set<Class<?>> actorTypes) {
						SequenceDiagramControllerImplTests.this.selectedActorsHistory.add(actorTypes);
					}

					@Override
					public void onSessionClosed(final ViewKey sessionKey) {
						SequenceDiagramControllerImplTests.this.sessionClosedKeys.add(sessionKey);
					}

					@Override
					public void onRecordingLimitReached(final ViewKey sessionKey, final int limit) {
						SequenceDiagramControllerImplTests.this.recordingLimits.add(limit);
					}
				});
		rcs.subscribe(SequenceDiagramControllerImplTests.class, RecordingStateEvents.class, new RecordingStateEvents() {
			@Override
			public void onRecordingStarted() {
				SequenceDiagramControllerImplTests.this.recordingStartedCount.incrementAndGet();
			}

			@Override
			public void onRecordingStopped() {
				SequenceDiagramControllerImplTests.this.recordingStoppedCount.incrementAndGet();
			}
		});

		this.container.deployComponents(StubActionObservationComponentImpl.class,
				StubObservedCallStreamComponentImpl.class, StubObservedMetamodelComponentImpl.class,
				ExclusionPolicyModelImpl.class,
				ExclusionPolicyControllerImpl.class, StereotypePolicyModelImpl.class,
				StereotypePolicyControllerImpl.class, SequenceDiagramModelImpl.class,
				SequenceDiagramControllerImpl.class);
		this.container.start();

		this.sequenceDiagramServices = rcs.useServices(SequenceDiagramServices.class);
		this.sequenceDiagramControllerServices = rcs.useServices(SequenceDiagramControllerServices.class);
		this.exclusionPolicyControllerServices = rcs.useServices(ExclusionPolicyControllerServices.class);
		this.stubActionObservation = rcs.useServices(RegistryReadServices.class)
				.getComponentInstance(StubActionObservationComponent.class);
		this.controllerAsServicesManagerEvents = rcs.useServices(RegistryReadServices.class)
				.getComponentInstance(SequenceDiagramController.class);
	}

	@After
	public void tearDown() {
		if (this.container != null) {
			this.container.dispose();
		}
	}

	@Test
	public void startRecordingCreatesASessionAndFiresOnRecordingStarted() {
		this.sequenceDiagramServices.startRecording(Set.of(LeafAUserComponent.class));

		Assert.assertEquals(1, this.sessionCreatedKeys.size());
		Assert.assertEquals(1, this.recordingStartedCount.get());
		Assert.assertEquals(0, this.recordingStoppedCount.get());
	}

	@Test
	public void startRecordingWhileAlreadyRecordingIsIgnored() {
		this.sequenceDiagramServices.startRecording(Set.of(LeafAUserComponent.class));
		this.sequenceDiagramServices.startRecording(Set.of(SampleServicesA.class));

		Assert.assertEquals("a second start while one is already active must not create a second session", 1,
				this.sessionCreatedKeys.size());
		Assert.assertEquals(1, this.recordingStartedCount.get());
	}

	@Test
	public void theRecordingStateIsReadFromTheController() {
		Assert.assertFalse(this.sequenceDiagramServices.isRecording());
		Assert.assertEquals(0, this.sequenceDiagramControllerServices.recordingStartMillis());

		this.sequenceDiagramServices.startRecording(Set.of(LeafAUserComponent.class));
		Assert.assertTrue(this.sequenceDiagramServices.isRecording());
		Assert.assertTrue(this.sequenceDiagramControllerServices.recordingStartMillis() > 0);

		this.sequenceDiagramControllerServices.stopRecording();
		Assert.assertFalse(this.sequenceDiagramServices.isRecording());
		Assert.assertEquals(0, this.sequenceDiagramControllerServices.recordingStartMillis());
	}

	@Test
	public void stopRecordingWithNoActiveSessionIsANoOp() {
		this.sequenceDiagramControllerServices.stopRecording();

		Assert.assertEquals(0, this.recordingStoppedCount.get());
		Assert.assertTrue(this.sessionClosedKeys.isEmpty());
	}

	@Test
	public void stopRecordingEndsTheSessionButDoesNotCloseItsModelEntry() {
		this.sequenceDiagramServices.startRecording(Set.of(LeafAUserComponent.class));
		this.sequenceDiagramServices.stopRecording();

		Assert.assertEquals(1, this.recordingStoppedCount.get());
		Assert.assertTrue("stopRecording() only clears the reconciler - closeSession() is a separate call",
				this.sessionClosedKeys.isEmpty());
	}

	@Test
	public void aNewRecordingIsAcceptedAfterTheFirstOneStops() {
		this.sequenceDiagramServices.startRecording(Set.of(LeafAUserComponent.class));
		this.sequenceDiagramServices.stopRecording();
		this.sequenceDiagramServices.startRecording(Set.of(SampleServicesA.class));

		Assert.assertEquals(2, this.sessionCreatedKeys.size());
		Assert.assertEquals(2, this.recordingStartedCount.get());
		Assert.assertEquals(1, this.recordingStoppedCount.get());
	}

	@Test
	public void selectActorsReplacesTheSelectionWholesaleAndForwardsToTheModel() {
		this.sequenceDiagramServices.startRecording(Set.of(LeafAUserComponent.class));
		ViewKey sessionKey = this.sessionCreatedKeys.get(0);

		this.sequenceDiagramControllerServices.selectActors(sessionKey,
				Set.of(LeafAUserComponent.class, SampleServicesA.class));

		Assert.assertEquals(1, this.selectedActorsHistory.size());
		Assert.assertEquals(Set.of(LeafAUserComponent.class, SampleServicesA.class),
				this.selectedActorsHistory.get(0));
	}

	@Test
	public void toggleActorSelectionAddsThenRemovesASingleActor() {
		this.sequenceDiagramServices.startRecording(Set.of(LeafAUserComponent.class));
		ViewKey sessionKey = this.sessionCreatedKeys.get(0);

		this.sequenceDiagramControllerServices.toggleActorSelection(sessionKey, LeafAUserComponent.class);
		this.sequenceDiagramControllerServices.toggleActorSelection(sessionKey, LeafAUserComponent.class);

		Assert.assertEquals(2, this.selectedActorsHistory.size());
		Assert.assertEquals(Set.of(LeafAUserComponent.class), this.selectedActorsHistory.get(0));
		Assert.assertEquals(Set.of(), this.selectedActorsHistory.get(1));
	}

	@Test
	public void closingTheCurrentlyRecordingSessionAlsoStopsRecording() {
		this.sequenceDiagramServices.startRecording(Set.of(LeafAUserComponent.class));
		ViewKey sessionKey = this.sessionCreatedKeys.get(0);

		this.sequenceDiagramControllerServices.closeSession(sessionKey);

		Assert.assertEquals(1, this.recordingStoppedCount.get());
		Assert.assertEquals(List.of(sessionKey), this.sessionClosedKeys);
	}

	@Test
	public void closingASessionOtherThanTheOneCurrentlyRecordingLeavesRecordingRunning() {
		this.sequenceDiagramServices.startRecording(Set.of(LeafAUserComponent.class));
		ViewKey firstSessionKey = this.sessionCreatedKeys.get(0);
		this.sequenceDiagramServices.stopRecording();
		this.sequenceDiagramServices.startRecording(Set.of(SampleServicesA.class));
		ViewKey secondSessionKey = this.sessionCreatedKeys.get(1);

		this.sequenceDiagramControllerServices.closeSession(firstSessionKey);

		Assert.assertEquals("closing the former (already-stopped) session must not stop the new recording", 1,
				this.recordingStoppedCount.get());
		Assert.assertEquals(List.of(firstSessionKey), this.sessionClosedKeys);
		Assert.assertNotEquals(firstSessionKey, secondSessionKey);
	}

	@Test
	public void onActionTriggeredIsForwardedToTheModelOnlyWhileRecording() throws Exception {
		ActionKey actionKey = new ActionKey() {
		};

		this.stubActionObservation.fireActionTriggered(actionKey, LeafAUserComponent.class, SampleServicesA.class);
		Assert.assertTrue("no recording is active yet, so the action must not be reconciled into a message",
				this.appendedMessages.isEmpty());

		this.sequenceDiagramServices.startRecording(Set.of(LeafAUserComponent.class));
		this.stubActionObservation.fireActionTriggered(actionKey, LeafAUserComponent.class, SampleServicesA.class);

		// Messages reach the model in time order, after a short hold: see the controller.
		this.awaitAppendedMessages(1);
		Assert.assertEquals(1, this.appendedMessages.size());
		Assert.assertEquals(this.sessionCreatedKeys.get(0), this.appendedMessageSessionKeys.get(0));
	}

	@Test
	public void passivationUnregistersTheActionEventsListener() {
		Assert.assertEquals("the @Activation hook must register exactly one listener", 1,
				this.stubActionObservation.listenerCount());

		this.container.dispose();

		Assert.assertEquals("the @Passivation hook must remove it again on dispose", 0,
				this.stubActionObservation.listenerCount());
	}

	@Test
	public void serviceCallActivityForAnObservedNonExcludedComponentIsReconciledIntoCallAndReturnMessages()
			throws Exception {
		Method pingA = SampleServicesA.class.getMethod("pingA");
		this.sequenceDiagramServices.startRecording(Set.of(LeafAUserComponent.class));

		// The controller compares call times with System.nanoTime(): the call started
		// first and finished a millisecond later, but they are delivered in reverse
		// order, and the controller restores the order the calls were made in.
		long startedAtNanos = System.nanoTime();
		long finishedAtNanos = startedAtNanos + 1_000_000L;
		this.controllerAsServicesManagerEvents.onUsedServiceCallFinished(Thread.currentThread(),
				finishedAtNanos, 2L, SampleServicesA.class, pingA,
				LeafAUserComponent.class, 1_000_000L, null, CallOutcome.VOID);
		this.controllerAsServicesManagerEvents.onUsedServiceCallStarted(Thread.currentThread(),
				startedAtNanos, 1L, SampleServicesA.class, pingA,
				LeafAUserComponent.class, "[no arguments]");

		this.awaitAppendedMessages(2);
		Assert.assertEquals(2, this.appendedMessages.size());
		Assert.assertEquals(SequenceTraceMessage.Style.CALL, this.appendedMessages.get(0).getStyle());
		Assert.assertEquals(LeafAUserComponent.class, this.appendedMessages.get(0).getSourceType());
		Assert.assertEquals(SequenceTraceMessage.Style.RETURN, this.appendedMessages.get(1).getStyle());
	}

	@Test
	public void aRecordingStopsAtTheMostMessagesItKeeps() throws Exception {
		Method pingA = SampleServicesA.class.getMethod("pingA");
		this.sequenceDiagramServices.startRecording(Set.of(LeafAUserComponent.class));

		// One call and its return make two messages: 50 001 of them go past the 100 000 kept.
		// Their times are in the past: the controller holds notifications until their time has come.
		long startedAtNanos = System.nanoTime() - 200_000_000_000L;
		for (int call = 0; call < 50_001; call++) {
			long at = startedAtNanos + call * 2_000_000L;
			this.controllerAsServicesManagerEvents.onUsedServiceCallStarted(Thread.currentThread(), at,
					2L * call + 1, SampleServicesA.class, pingA, LeafAUserComponent.class, "[no arguments]");
			this.controllerAsServicesManagerEvents.onUsedServiceCallFinished(Thread.currentThread(), at + 1_000_000L,
					2L * call + 2, SampleServicesA.class, pingA, LeafAUserComponent.class, 1_000_000L, null,
					CallOutcome.VOID);
		}
		long deadline = System.currentTimeMillis() + 30_000;
		while (this.recordingStoppedCount.get() == 0 && System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}

		Assert.assertEquals("the recording was stopped, after " + this.appendedMessages.size() + " messages", 1,
				this.recordingStoppedCount.get());
		Assert.assertFalse(this.sequenceDiagramServices.isRecording());
		Assert.assertEquals("the user is told once, with the limit", List.of(100_000), this.recordingLimits);
		Assert.assertEquals("the messages up to the limit are kept, no more", 100_000, this.appendedMessages.size());
	}

	@Test
	public void serviceCallActivityForAnExcludedComponentIsNotReconciledIntoAMessage() throws Exception {
		Method pingA = SampleServicesA.class.getMethod("pingA");
		this.sequenceDiagramServices.startRecording(Set.of(LeafAUserComponent.class));
		this.exclusionPolicyControllerServices.setExcludedComponents(List.of(LeafAUserComponent.class));

		long startedAtNanos = System.nanoTime();
		this.controllerAsServicesManagerEvents.onUsedServiceCallStarted(Thread.currentThread(),
				startedAtNanos, 1L, SampleServicesA.class, pingA,
				LeafAUserComponent.class, "[no arguments]");
		this.controllerAsServicesManagerEvents.onUsedServiceCallFinished(Thread.currentThread(),
				startedAtNanos + 1_000_000L, 2L, SampleServicesA.class, pingA,
				LeafAUserComponent.class, 1_000_000L, null, CallOutcome.VOID);

		this.awaitAppendedMessages(1);
		Assert.assertTrue("the caller side is excluded, so the real exclusion policy must suppress the message",
				this.appendedMessages.isEmpty());
	}

	/** Call notifications are processed on the controller's own thread: waits for them, bounded. */
	private void awaitAppendedMessages(final int expectedCount) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 1_000;
		while (this.appendedMessages.size() < expectedCount && System.currentTimeMillis() < deadline) {
			Thread.sleep(10);
		}
	}

	/** An object receiving the events, of a class nested in this one. */
	private static class InnerReceiver implements SampleEvents {
		@Override
		public void fired() {
		}
	}

	/** An object receiving the events, which is also the consumer it is attributed to. */
	private static class SelfReceiver implements SampleEvents {
		@Override
		public void fired() {
		}
	}

	/** Marked as one of Covalent's own, whatever its name. */
	@Internal
	private interface InternalThing {
	}

	@Test
	public void parameterCallActivityIsReconciledIntoCallAndReturnMessages() throws Exception {
		Method getValue = SampleParameters.class.getMethod("getValue");
		ObservedParametersManagerEvents events = (ObservedParametersManagerEvents) this.controllerAsServicesManagerEvents;
		this.sequenceDiagramServices.startRecording(Set.of(LeafAUserComponent.class));

		events.onAccessedParametersCallStarted(Thread.currentThread(), System.nanoTime(), 1L, SampleParameters.class,
				getValue, LeafAUserComponent.class, "[no arguments]");
		events.onAccessedParametersCallFinished(Thread.currentThread(), System.nanoTime(), 2L, SampleParameters.class,
				getValue, LeafAUserComponent.class, 1_000_000L, null, CallOutcome.VOID);

		this.awaitAppendedMessages(2);
		Assert.assertEquals(2, this.appendedMessages.size());
		Assert.assertEquals(SequenceTraceMessage.Style.CALL, this.appendedMessages.get(0).getStyle());
		Assert.assertEquals(LeafAUserComponent.class, this.appendedMessages.get(0).getSourceType());
		Assert.assertEquals(SequenceTraceMessage.Style.RETURN, this.appendedMessages.get(1).getStyle());
	}

	@Test
	public void providerSideActivityIsHandedToTheReconcilerToo() throws Exception {
		Method getValue = SampleParameters.class.getMethod("getValue");
		Method pingA = SampleServicesA.class.getMethod("pingA");
		ObservedParametersManagerEvents parameters = (ObservedParametersManagerEvents) this.controllerAsServicesManagerEvents;
		this.sequenceDiagramServices.startRecording(Set.of(LeafAUserComponent.class));

		this.controllerAsServicesManagerEvents.onProvidedServiceCallStarted(Thread.currentThread(), System.nanoTime(), 1L,
				SampleServicesA.class, pingA, LeafAUserComponent.class, "[no arguments]");
		this.controllerAsServicesManagerEvents.onProvidedServiceCallFinished(Thread.currentThread(), System.nanoTime(),
				2L, SampleServicesA.class, pingA, LeafAUserComponent.class, 1_000L, null, CallOutcome.VOID);
		parameters.onOfferedParametersCallStarted(Thread.currentThread(), System.nanoTime(), 3L, SampleParameters.class,
				getValue, LeafAUserComponent.class, "[no arguments]");
		parameters.onOfferedParametersCallFinished(Thread.currentThread(), System.nanoTime(), 4L, SampleParameters.class,
				getValue, LeafAUserComponent.class, 1_000L, null, CallOutcome.VOID);

		this.awaitAppendedMessages(1);
		Assert.assertFalse("provider-side notifications reach the reconciler, which displays them",
				this.appendedMessages.isEmpty());
	}

	@Test
	public void anEventNotificationIsAMessageWithItsReceiverAndTheMvicRolesOfItsEnds() throws Exception {
		Method fired = SampleEvents.class.getMethod("fired");
		ObservedEventsManagerEvents events = (ObservedEventsManagerEvents) this.controllerAsServicesManagerEvents;
		this.sequenceDiagramServices.startRecording(Set.of(ComponentDiagramController.class));

		events.onEventNotificationStarted(Thread.currentThread(), System.nanoTime(), 1L, SampleEvents.class, fired,
				ComponentDiagramController.class, ComponentDiagramInteraction.class, InnerReceiver.class, false,
				-1L, "[no arguments]");
		events.onEventNotificationFinished(Thread.currentThread(), System.nanoTime(), 2L, SampleEvents.class, fired,
				ComponentDiagramController.class, ComponentDiagramInteraction.class, InnerReceiver.class,
				1_000L, null);

		this.awaitAppendedMessages(1);
		SequenceTraceMessage message = this.appendedMessages.get(0);
		Assert.assertEquals(ComponentDiagramController.class, message.getSourceType());
		Assert.assertEquals(ComponentDiagramInteraction.class, message.getTargetType());
		Assert.assertEquals("Controller", message.getSourceStereotype());
		Assert.assertEquals("an interaction component takes the Interaction stereotype: its name ends with Interaction",
				"Interaction", message.getTargetStereotype());
		Assert.assertEquals("an inner class is named without its package, nested with dots",
				"SequenceDiagramControllerImplTests.InnerReceiver",
				message.getFacts().receiverDescription());
	}

	@Test
	public void aSubscriberThatIsTheConsumerItselfIsNotDescribedAsAnotherReceiver() throws Exception {
		Method fired = SampleEvents.class.getMethod("fired");
		ObservedEventsManagerEvents events = (ObservedEventsManagerEvents) this.controllerAsServicesManagerEvents;
		this.sequenceDiagramServices.startRecording(Set.of(ComponentDiagramController.class));

		events.onEventNotificationStarted(Thread.currentThread(), System.nanoTime(), 1L, SampleEvents.class, fired,
				ComponentDiagramController.class, SelfReceiver.class,
				SelfReceiver.class, false, -1L, "[no arguments]");

		this.awaitAppendedMessages(1);
		Assert.assertNull(this.appendedMessages.get(0).getFacts().receiverDescription());
	}

	@Test
	public void aCallThatArrivesAheadOfItsPredecessorsIsProcessedOnceTheGapIsGivenUpOn() throws Exception {
		Method fired = SampleEvents.class.getMethod("fired");
		ObservedEventsManagerEvents events = (ObservedEventsManagerEvents) this.controllerAsServicesManagerEvents;
		this.sequenceDiagramServices.startRecording(Set.of(ComponentDiagramController.class));

		// The first call of the thread never arrives: the second one waits for it, then goes.
		events.onEventNotificationStarted(Thread.currentThread(), System.nanoTime(), 2L, SampleEvents.class, fired,
				ComponentDiagramController.class, SelfReceiver.class,
				SelfReceiver.class, false, -1L, "[no arguments]");
		this.awaitAppendedMessages(1);
		Assert.assertEquals(1, this.appendedMessages.size());

		// The missing one finally arrives, after the gap was given up on: processed as it is.
		events.onEventNotificationStarted(Thread.currentThread(), System.nanoTime(), 1L, SampleEvents.class, fired,
				ComponentDiagramController.class, SelfReceiver.class,
				SelfReceiver.class, false, -1L, "[no arguments]");
		this.awaitAppendedMessages(2);
		Assert.assertEquals(2, this.appendedMessages.size());
	}

	@Test
	public void callNotificationsOutsideARecordingAreDropped() throws Exception {
		Method pingA = SampleServicesA.class.getMethod("pingA");

		this.controllerAsServicesManagerEvents.onUsedServiceCallStarted(Thread.currentThread(), System.nanoTime(), 1L,
				SampleServicesA.class, pingA, LeafAUserComponent.class, "[no arguments]");

		Thread.sleep(100);
		Assert.assertTrue(this.appendedMessages.isEmpty());
	}

	@Test
	public void aLifelineStandsForWhatItsNameOrItsKindTells() throws Exception {
		SequenceDiagramControllerImpl controller = (SequenceDiagramControllerImpl) this.controllerAsServicesManagerEvents;
		Method stereotypeOf = SequenceDiagramControllerImpl.class.getDeclaredMethod("stereotypeOf", Class.class);
		stereotypeOf.setAccessible(true);

		Assert.assertNull(stereotypeOf.invoke(controller, (Object) null));
		Assert.assertEquals("Controller", stereotypeOf.invoke(controller, ComponentDiagramController.class));
		Assert.assertEquals("a name ending like a built-in rule takes that rule", "Interaction",
				stereotypeOf.invoke(controller, ComponentDiagramInteraction.class));
		Assert.assertEquals("Internal", stereotypeOf.invoke(controller, InternalThing.class));
		Assert.assertEquals("Interface", stereotypeOf.invoke(controller, LeafAUserComponent.class));
		Assert.assertEquals("Raw", stereotypeOf.invoke(controller, String.class));
		Assert.assertEquals("a registered raw component is raw although the container gives it a metamodel", "Raw",
				stereotypeOf.invoke(controller, SequenceDiagramControllerImplTests.class));
	}
}
