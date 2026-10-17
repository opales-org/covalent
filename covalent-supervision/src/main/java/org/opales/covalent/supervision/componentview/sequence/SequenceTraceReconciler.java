/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.componentview.sequence;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.opales.covalent.supervision.observed.CallOutcome;

/**
 * Rebuilds logical sequence interactions from low-level runtime instrumentation
 * events emitted on caller and provider sides.
 */
public final class SequenceTraceReconciler {

	/** Kind of a port interaction, which decides how it is labeled and whether it has a return. */
	public enum InteractionKind {
		/** A call to a service of a port. */
		SERVICE("Service"),
		/** A call that reads or writes a parameter of a port. */
		PARAMETER("Parameter"),
		/** The notification of an event, which has no return message unless it is delivered synchronously. */
		EVENT("Event");

		private final String label;

		InteractionKind(final String label) {
			this.label = label;
		}

		boolean hasReturnFlow() {
			return this != EVENT;
		}
	}

	private final LinkedHashSet<Class<?>> observedComponentTypes;

	private final Predicate<Class<?>> exclusionPredicate;

	private final Consumer<SequenceTraceMessage> messageConsumer;

	private final Map<Thread, Deque<OpenInvocation>> openInvocationsByThread = new HashMap<>();

	/** The {@link System#nanoTime()} of the recording's first notification, once there is one. */
	private long recordingStartNanos;

	private boolean recordingStarted;

	/**
	 * Creates a reconciler that emits its messages to a consumer.
	 *
	 * @param observedComponentTypes the component types whose interactions are displayed
	 * @param exclusionPredicate tells the types never to display, {@code null} to exclude none
	 * @param messageConsumer receives each message as it is emitted
	 */
	public SequenceTraceReconciler(final Set<Class<?>> observedComponentTypes,
			final Predicate<Class<?>> exclusionPredicate, final Consumer<SequenceTraceMessage> messageConsumer) {
		this.observedComponentTypes = new LinkedHashSet<>(observedComponentTypes);
		this.exclusionPredicate = exclusionPredicate != null ? exclusionPredicate : ignored -> false;
		this.messageConsumer = messageConsumer;
	}

	/**
	 * Records that the caller's side of an interaction started; the message is emitted once a provider side or
	 * the end of the call is known.
	 *
	 * @param kind the kind of interaction
	 * @param thread the thread running the interaction
	 * @param eventTimeNanos the {@link System#nanoTime()} of the notification
	 * @param portType the port interface
	 * @param method the invoked method of the port
	 * @param callerType the type of the calling component
	 * @param argumentsDescription the printed arguments
	 */
	public synchronized void onCallerSideStarted(final InteractionKind kind, final Thread thread, final long eventTimeNanos,
			final Class<?> portType, final Method method, final Class<?> callerType, final String argumentsDescription) {
		this.captureRecordingStart(eventTimeNanos);
		this.getOrCreateThreadInvocations(thread).push(OpenInvocation.callerSideStarted(kind, thread, portType, method,
				callerType, argumentsDescription, eventTimeNanos));
	}

	/**
	 * Records that the provider's side of an interaction started, and emits its call message when it concerns an
	 * observed component. An interaction whose caller side was not seen is recorded as such.
	 *
	 * @param kind the kind of interaction
	 * @param thread the thread running the interaction
	 * @param eventTimeNanos the {@link System#nanoTime()} of the notification
	 * @param portType the port interface
	 * @param method the invoked method of the port
	 * @param calleeType the type of the component providing the port
	 */
	public synchronized void onProviderSideStarted(final InteractionKind kind, final Thread thread, final long eventTimeNanos,
			final Class<?> portType, final Method method, final Class<?> calleeType) {
		this.captureRecordingStart(eventTimeNanos);

		OpenInvocation invocation = this.findInvocationWaitingForProvider(thread, kind, portType, method);
		if (invocation == null) {
			invocation = OpenInvocation.orphanProviderSideStarted(kind, thread, portType, method, calleeType,
					eventTimeNanos);
			this.getOrCreateThreadInvocations(thread).push(invocation);
		} else {
			invocation.markProviderStarted(calleeType, eventTimeNanos);
		}

		if (invocation.shouldDisplayFor(this.observedComponentTypes)
				&& !invocation.isCallDisplayed()) {
			this.emitCall(invocation);
		}
	}

	/**
	 * Records that the provider's side of an interaction finished.
	 *
	 * @param kind the kind of interaction
	 * @param thread the thread running the interaction
	 * @param portType the port interface
	 * @param method the invoked method of the port
	 * @param calleeType the type of the component providing the port
	 * @param durationNanos the time spent in the provider, in nanoseconds
	 * @param failure the failure the provider ended with, {@code null} when none
	 * @param outcome how the invocation ended, {@code null} when not known
	 */
	public synchronized void onProviderSideFinished(final InteractionKind kind, final Thread thread,
			final Class<?> portType, final Method method, final Class<?> calleeType, final long durationNanos,
			final Throwable failure, final CallOutcome outcome) {
		OpenInvocation invocation = this.findInvocationWaitingForProviderFinish(thread, kind, portType, method,
				calleeType);
		if (invocation != null) {
			invocation.markProviderFinished(durationNanos, failure);
			invocation.markReturned(outcome);
		}
	}

	/**
	 * Records that the caller's side of an interaction finished, emits the call and return messages not yet emitted
	 * and closes the interaction.
	 *
	 * @param kind the kind of interaction
	 * @param thread the thread running the interaction
	 * @param eventTimeNanos the {@link System#nanoTime()} of the notification
	 * @param portType the port interface
	 * @param method the invoked method of the port
	 * @param callerType the type of the calling component
	 * @param durationNanos the time spent in the caller, in nanoseconds
	 * @param failure the failure the call ended with, {@code null} when none
	 * @param outcome how the invocation ended, {@code null} when not known
	 */
	public synchronized void onCallerSideFinished(final InteractionKind kind, final Thread thread, final long eventTimeNanos,
			final Class<?> portType, final Method method, final Class<?> callerType, final long durationNanos,
			final Throwable failure, final CallOutcome outcome) {
		Deque<OpenInvocation> threadInvocations = this.openInvocationsByThread.get(thread);
		if (threadInvocations == null) {
			return;
		}

		OpenInvocation invocation = this.findInvocationWaitingForCallerFinish(threadInvocations, kind, portType, method,
				callerType);
		if (invocation == null) {
			return;
		}

		invocation.markCallerFinished(durationNanos, failure, eventTimeNanos);
		invocation.markReturned(outcome);

		if (invocation.shouldDisplayFor(this.observedComponentTypes) && !invocation.isCallDisplayed()) {
			this.emitCall(invocation);
		}
		if (invocation.isCallDisplayed() && !invocation.isReturnDisplayed()) {
			this.emitReturn(invocation);
		}

		threadInvocations.remove(invocation);
		if (threadInvocations.isEmpty()) {
			this.openInvocationsByThread.remove(thread);
		}
	}

	/**
	 * Records an interaction whose two sides are reported together, such as an event notification, and emits its
	 * call message when it concerns an observed component.
	 *
	 * @param kind the kind of interaction
	 * @param thread the thread running the interaction
	 * @param eventTimeNanos the {@link System#nanoTime()} of the notification
	 * @param portType the port interface
	 * @param method the invoked method of the port
	 * @param callerType the type of the calling component
	 * @param calleeType the type of the receiving component
	 * @param argumentsDescription the printed arguments
	 * @param receiverDescription the object receiving the interaction when it is not the component itself,
	 *        {@code null} otherwise
	 * @param asynchronous whether the interaction runs on another thread than its caller's
	 * @param queueWaitNanos how long the notification waited before it started, {@code -1} when unknown
	 */
	public synchronized void onDirectInteractionStarted(final InteractionKind kind, final Thread thread, final long eventTimeNanos,
			final Class<?> portType, final Method method, final Class<?> callerType, final Class<?> calleeType,
			final String argumentsDescription, final String receiverDescription, final boolean asynchronous,
			final long queueWaitNanos) {
		this.captureRecordingStart(eventTimeNanos);

		OpenInvocation invocation = OpenInvocation.directStarted(kind, thread, portType, method, callerType,
				calleeType, argumentsDescription, eventTimeNanos);
		invocation.receiverDescription = receiverDescription;
		invocation.asynchronous = asynchronous;
		invocation.queueWaitNanos = queueWaitNanos;
		this.getOrCreateThreadInvocations(thread).push(invocation);

		if (invocation.shouldDisplayFor(this.observedComponentTypes)) {
			this.emitCall(invocation);
		}
	}

	/**
	 * Records the end of an interaction reported by {@link #onDirectInteractionStarted}, and emits its return
	 * message, or a completion message for an asynchronous event, which has no return.
	 *
	 * @param kind the kind of interaction
	 * @param thread the thread running the interaction
	 * @param eventTimeNanos the {@link System#nanoTime()} of the notification
	 * @param portType the port interface
	 * @param method the invoked method of the port
	 * @param callerType the type of the calling component
	 * @param calleeType the type of the receiving component
	 * @param durationNanos the duration of the interaction, in nanoseconds
	 * @param failure the failure the interaction ended with, {@code null} when none
	 */
	public synchronized void onDirectInteractionFinished(final InteractionKind kind, final Thread thread, final long eventTimeNanos,
			final Class<?> portType, final Method method, final Class<?> callerType, final Class<?> calleeType,
			final long durationNanos, final Throwable failure) {
		Deque<OpenInvocation> threadInvocations = this.openInvocationsByThread.get(thread);
		if (threadInvocations == null) {
			return;
		}

		OpenInvocation invocation = this.findDirectInvocation(threadInvocations, kind, portType, method, callerType,
				calleeType);
		if (invocation == null) {
			return;
		}

		invocation.markProviderFinished(durationNanos, failure);
		invocation.markCallerFinished(durationNanos, failure, eventTimeNanos);

		// A synchronous event blocks its producer like a void service call, so it returns;
		// only an asynchronous one is fire and forget.
		boolean returns = kind.hasReturnFlow() || !invocation.asynchronous;
		if (returns && invocation.isCallDisplayed() && !invocation.isReturnDisplayed()) {
			this.emitReturn(invocation);
		} else if (!returns && invocation.isCallDisplayed()) {
			this.emitCompletion(invocation);
		}

		threadInvocations.remove(invocation);
		if (threadInvocations.isEmpty()) {
			this.openInvocationsByThread.remove(thread);
		}
	}

	/**
	 * Records that an action was triggered, and emits it as a call message when it concerns an observed component.
	 *
	 * @param thread the thread running the action
	 * @param eventTimeNanos the {@link System#nanoTime()} of the trigger
	 * @param actionLabel the action's name
	 * @param actionCaller the type of the component triggering the action
	 * @param actionImplementer the type of the component implementing the action
	 * @param argumentsDescription the printed arguments
	 */
	public synchronized void onActionTriggered(final Thread thread, final long eventTimeNanos, final String actionLabel,
			final Class<?> actionCaller, final Class<?> actionImplementer, final String argumentsDescription) {
		this.captureRecordingStart(eventTimeNanos);
		if (!this.shouldDisplay(actionCaller, actionImplementer)) {
			return;
		}
		this.emitMessage(new SequenceTraceMessage(actionCaller, actionImplementer, actionLabel,
				this.buildActionDetails(thread, actionLabel, actionCaller, actionImplementer, argumentsDescription),
				this.toRelativeTimeMillis(eventTimeNanos), SequenceTraceMessage.Style.CALL,
				new SequenceTraceMessage.Facts(OpenInvocation.nextId(), "Action", actionLabel, null, thread.getName(),
						null, argumentsDescription, null, -1, null, false, -1)));
	}

	/** Forgets the interactions still open and the start of the recording. */
	public synchronized void clear() {
		this.openInvocationsByThread.clear();
		this.recordingStarted = false;
	}

	private void emitCall(final OpenInvocation invocation) {
		invocation.markCallDisplayed();
		this.emitMessage(new SequenceTraceMessage(invocation.getCallSourceType(), invocation.getCallTargetType(),
				invocation.buildCallLabel(), invocation.buildCallDetails(),
				this.toRelativeTimeMillis(invocation.getCallDisplayTimeNanos()), SequenceTraceMessage.Style.CALL,
				invocation.buildFacts(false)));
	}

	private void emitReturn(final OpenInvocation invocation) {
		invocation.markReturnDisplayed();
		this.emitMessage(new SequenceTraceMessage(invocation.getReturnSourceType(), invocation.getReturnTargetType(),
				invocation.buildReturnLabel(), invocation.buildReturnDetails(),
				this.toRelativeTimeMillis(invocation.getReturnDisplayTimeNanos()), SequenceTraceMessage.Style.RETURN,
				invocation.buildFacts(true)));
	}

	private void emitCompletion(final OpenInvocation invocation) {
		this.emitMessage(new SequenceTraceMessage(invocation.getReturnSourceType(), invocation.getReturnTargetType(),
				invocation.buildReturnLabel(), invocation.buildReturnDetails(),
				this.toRelativeTimeMillis(invocation.getReturnDisplayTimeNanos()), SequenceTraceMessage.Style.COMPLETION,
				invocation.buildFacts(true)));
	}

	private void emitMessage(final SequenceTraceMessage message) {
		if (message == null || !this.shouldDisplay(message.getSourceType(), message.getTargetType())) {
			return;
		}
		this.messageConsumer.accept(message);
	}

	private boolean shouldDisplay(final Class<?> sourceType, final Class<?> targetType) {
		if (sourceType == null || targetType == null) {
			return false;
		}
		if (this.exclusionPredicate.test(sourceType) || this.exclusionPredicate.test(targetType)) {
			return false;
		}
		return this.observedComponentTypes.contains(sourceType) || this.observedComponentTypes.contains(targetType);
	}

	private void captureRecordingStart(final long eventTimeNanos) {
		if (!this.recordingStarted) {
			this.recordingStartNanos = eventTimeNanos;
			this.recordingStarted = true;
		}
	}

	private long toRelativeTimeMillis(final long eventTimeNanos) {
		return this.recordingStarted ? (eventTimeNanos - this.recordingStartNanos) / 1_000_000 : 0;
	}

	private Deque<OpenInvocation> getOrCreateThreadInvocations(final Thread thread) {
		return this.openInvocationsByThread.computeIfAbsent(thread, ignored -> new ArrayDeque<>());
	}

	private OpenInvocation findInvocationWaitingForProvider(final Thread thread, final InteractionKind kind,
			final Class<?> portType, final Method method) {
		Deque<OpenInvocation> threadInvocations = this.openInvocationsByThread.get(thread);
		if (threadInvocations == null) {
			return null;
		}
		for (OpenInvocation invocation : threadInvocations) {
			if (invocation.isWaitingForProvider(kind, portType, method)) {
				return invocation;
			}
		}
		return null;
	}

	private OpenInvocation findInvocationWaitingForProviderFinish(final Thread thread, final InteractionKind kind,
			final Class<?> portType, final Method method, final Class<?> calleeType) {
		Deque<OpenInvocation> threadInvocations = this.openInvocationsByThread.get(thread);
		if (threadInvocations == null) {
			return null;
		}
		for (OpenInvocation invocation : threadInvocations) {
			if (invocation.isWaitingForProviderFinish(kind, portType, method, calleeType)) {
				return invocation;
			}
		}
		return null;
	}

	private OpenInvocation findInvocationWaitingForCallerFinish(final Deque<OpenInvocation> threadInvocations,
			final InteractionKind kind, final Class<?> portType, final Method method, final Class<?> callerType) {
		for (OpenInvocation invocation : threadInvocations) {
			if (invocation.isWaitingForCallerFinish(kind, portType, method, callerType)) {
				return invocation;
			}
		}
		return null;
	}

	private OpenInvocation findDirectInvocation(final Deque<OpenInvocation> threadInvocations,
			final InteractionKind kind, final Class<?> portType, final Method method, final Class<?> callerType,
			final Class<?> calleeType) {
		for (OpenInvocation invocation : threadInvocations) {
			if (invocation.isDirectInvocation(kind, portType, method, callerType, calleeType)) {
				return invocation;
			}
		}
		return null;
	}

	private String buildActionDetails(final Thread thread, final String actionLabel, final Class<?> actionCaller,
			final Class<?> actionImplementer, final String argumentsDescription) {
		StringBuilder builder = new StringBuilder();
		builder.append(SequenceTexts.get("details.action")).append('\n');
		builder.append(SequenceTexts.get("details.from", actionCaller.getName())).append('\n');
		builder.append(SequenceTexts.get("details.to", actionImplementer.getName())).append('\n');
		builder.append(SequenceTexts.get("details.action.name", actionLabel)).append('\n');
		builder.append(SequenceTexts.get("details.thread", thread.getName())).append('\n');
		builder.append(SequenceTexts.get("details.arguments")).append('\n').append(argumentsDescription);
		return builder.toString();
	}

	private static final class OpenInvocation {

		private static final AtomicLong ID_SEQUENCE = new AtomicLong();

		/** Shared by this invocation's call and return messages. */
		private final long id = nextId();

		private final InteractionKind kind;

		private final Thread thread;

		private final Class<?> portType;

		private final Method method;

		private final Class<?> callerType;

		private final String argumentsDescription;

		/**
		 * The object actually receiving the interaction inside the callee
		 * component, when it is not the component itself - an object the
		 * component subscribed to events, for instance - or {@code null}.
		 */
		private String receiverDescription;

		/** Whether it runs on another thread than its caller's - an event delivered so. */
		private boolean asynchronous;

		/** How long the notification waited before it started, {@code -1} when unknown. */
		private long queueWaitNanos = -1;

		/** How the invocation ended - {@code null} until known. */
		private CallOutcome outcome;

		private final long callerStartedAtNanos;

		private Class<?> calleeType;

		private long providerStartedAtNanos = -1;

		private long providerDurationNanos = -1;

		private Throwable providerFailure;

		private long callerDurationNanos = -1;

		private Throwable callerFailure;

		private long callerFinishedAtNanos = -1;

		private boolean callDisplayed = false;

		private boolean returnDisplayed = false;

		private OpenInvocation(final InteractionKind kind, final Thread thread, final Class<?> portType,
				final Method method, final Class<?> callerType, final Class<?> calleeType,
				final String argumentsDescription, final long callerStartedAtNanos,
				final long providerStartedAtNanos) {
			this.kind = kind;
			this.thread = thread;
			this.portType = portType;
			this.method = method;
			this.callerType = callerType;
			this.calleeType = calleeType;
			this.argumentsDescription = argumentsDescription;
			this.callerStartedAtNanos = callerStartedAtNanos;
			this.providerStartedAtNanos = providerStartedAtNanos;
		}

		static OpenInvocation callerSideStarted(final InteractionKind kind, final Thread thread,
				final Class<?> portType, final Method method, final Class<?> callerType,
				final String argumentsDescription, final long callerStartedAtNanos) {
			return new OpenInvocation(kind, thread, portType, method, callerType, null, argumentsDescription,
					callerStartedAtNanos, -1);
		}

		static OpenInvocation orphanProviderSideStarted(final InteractionKind kind, final Thread thread,
				final Class<?> portType, final Method method, final Class<?> calleeType,
				final long providerStartedAtNanos) {
			return new OpenInvocation(kind, thread, portType, method, null, calleeType, SequenceTexts.get("callerSideMissing"),
					providerStartedAtNanos, providerStartedAtNanos);
		}

		static OpenInvocation directStarted(final InteractionKind kind, final Thread thread,
				final Class<?> portType, final Method method, final Class<?> callerType, final Class<?> calleeType,
				final String argumentsDescription, final long startedAtNanos) {
			return new OpenInvocation(kind, thread, portType, method, callerType, calleeType, argumentsDescription,
					startedAtNanos, startedAtNanos);
		}

		boolean isWaitingForProvider(final InteractionKind expectedKind, final Class<?> expectedPortType,
				final Method expectedMethod) {
			return this.kind == expectedKind && this.portType == expectedPortType && this.method.equals(expectedMethod)
					&& this.calleeType == null;
		}

		boolean isWaitingForProviderFinish(final InteractionKind expectedKind, final Class<?> expectedPortType,
				final Method expectedMethod, final Class<?> expectedCalleeType) {
			return this.kind == expectedKind && this.portType == expectedPortType && this.method.equals(expectedMethod)
					&& this.calleeType == expectedCalleeType && this.providerDurationNanos < 0;
		}

		boolean isWaitingForCallerFinish(final InteractionKind expectedKind, final Class<?> expectedPortType,
				final Method expectedMethod, final Class<?> expectedCallerType) {
			return this.kind == expectedKind && this.portType == expectedPortType && this.method.equals(expectedMethod)
					&& this.callerType == expectedCallerType && this.callerFinishedAtNanos < 0;
		}

		boolean isDirectInvocation(final InteractionKind expectedKind, final Class<?> expectedPortType,
				final Method expectedMethod, final Class<?> expectedCallerType, final Class<?> expectedCalleeType) {
			return this.kind == expectedKind && this.portType == expectedPortType && this.method.equals(expectedMethod)
					&& this.callerType == expectedCallerType && this.calleeType == expectedCalleeType
					&& this.callerFinishedAtNanos < 0;
		}

		void markProviderStarted(final Class<?> calleeType, final long providerStartedAtNanos) {
			this.calleeType = calleeType;
			this.providerStartedAtNanos = providerStartedAtNanos;
		}

		void markProviderFinished(final long durationNanos, final Throwable failure) {
			this.providerDurationNanos = durationNanos;
			this.providerFailure = failure;
		}

		void markReturned(final CallOutcome callOutcome) {
			if (callOutcome != null) {
				this.outcome = callOutcome;
			}
		}

		void markCallerFinished(final long durationNanos, final Throwable failure, final long callerFinishedAtNanos) {
			this.callerDurationNanos = durationNanos;
			this.callerFailure = failure;
			this.callerFinishedAtNanos = callerFinishedAtNanos;
		}

		/**
		 * Displayed when at least one end is observed, unless both ends are
		 * the same component: a call that stays inside one lifeline - a
		 * composite standing for its own contents - is not an interaction
		 * between lifelines.
		 */
		boolean shouldDisplayFor(final Set<Class<?>> observedComponentTypes) {
			if (this.callerType != null && this.callerType.equals(this.calleeType)) {
				return false;
			}
			return this.callerType != null && observedComponentTypes.contains(this.callerType)
					|| this.calleeType != null && observedComponentTypes.contains(this.calleeType);
		}

		boolean isCallDisplayed() {
			return this.callDisplayed;
		}

		void markCallDisplayed() {
			this.callDisplayed = true;
		}

		boolean isReturnDisplayed() {
			return this.returnDisplayed;
		}

		void markReturnDisplayed() {
			this.returnDisplayed = true;
		}

		Class<?> getCallSourceType() {
			return this.callerType != null ? this.callerType : this.portType;
		}

		Class<?> getCallTargetType() {
			return this.calleeType != null ? this.calleeType : this.portType;
		}

		Class<?> getReturnSourceType() {
			return this.calleeType != null ? this.calleeType : this.portType;
		}

		Class<?> getReturnTargetType() {
			return this.callerType != null ? this.callerType : this.portType;
		}

		long getCallDisplayTimeNanos() {
			return this.providerStartedAtNanos >= 0 ? this.providerStartedAtNanos : this.callerStartedAtNanos;
		}

		long getReturnDisplayTimeNanos() {
			return this.callerFinishedAtNanos >= 0 ? this.callerFinishedAtNanos : this.getCallDisplayTimeNanos();
		}

		String buildCallLabel() {
			return this.portType.getSimpleName() + "\n" + this.method.getName() + "()";
		}

		String buildReturnLabel() {
			return this.hasFailure() ? this.getFailure().getClass().getSimpleName() : SequenceTexts.get("return");
		}

		String buildCallDetails() {
			StringBuilder builder = new StringBuilder();
			builder.append(SequenceTexts.get("details.call." + this.kind.name())).append('\n');
			builder.append(SequenceTexts.get("details.from", this.getCallSourceType().getName())).append('\n');
			builder.append(SequenceTexts.get("details.to", this.getCallTargetType().getName())).append('\n');
			if (this.receiverDescription != null) {
				builder.append(SequenceTexts.get("details.receivedBy", this.receiverDescription)).append('\n');
			}
			builder.append(SequenceTexts.get("details.port", this.portType.getName())).append('\n');
			builder.append(SequenceTexts.get("details.method", this.method.getName())).append('\n');
			builder.append(SequenceTexts.get("details.thread", this.thread.getName())).append('\n');
			builder.append(SequenceTexts.get("details.arguments")).append('\n').append(this.argumentsDescription);
			return builder.toString();
		}

		String buildReturnDetails() {
			StringBuilder builder = new StringBuilder();
			builder.append(SequenceTexts.get("details.return." + this.kind.name())).append('\n');
			builder.append(SequenceTexts.get("details.from", this.getReturnSourceType().getName())).append('\n');
			builder.append(SequenceTexts.get("details.to", this.getReturnTargetType().getName())).append('\n');
			builder.append(SequenceTexts.get("details.port", this.portType.getName())).append('\n');
			builder.append(SequenceTexts.get("details.method", this.method.getName())).append('\n');
			builder.append(SequenceTexts.get("details.thread", this.thread.getName())).append('\n');
			builder.append(SequenceTexts.get("details.status", SequenceTexts
					.get(this.hasFailure() ? "details.status.failure" : "details.status.success"))).append('\n');
			if (this.providerDurationNanos >= 0) {
				builder.append(SequenceTexts.get("details.providerDuration",
						this.formatDurationMillis(this.providerDurationNanos))).append('\n');
			}
			if (this.callerDurationNanos >= 0) {
				builder.append(SequenceTexts.get("details.callerDuration",
						this.formatDurationMillis(this.callerDurationNanos))).append('\n');
			}
			if (this.hasFailure()) {
				builder.append(SequenceTexts.get("details.failure", this.getFailure().toString())).append('\n');
			} else if (this.outcome != null) {
				builder.append(SequenceTexts.get("details.returned", this.outcome.toText())).append('\n');
			}
			builder.append(SequenceTexts.get("details.arguments")).append('\n').append(this.argumentsDescription);
			return builder.toString();
		}

		static long nextId() {
			return ID_SEQUENCE.incrementAndGet();
		}

		SequenceTraceMessage.Facts buildFacts(final boolean returning) {
			long durationNanos = -1;
			String failureDescription = null;
			if (returning) {
				durationNanos = this.callerDurationNanos >= 0 ? this.callerDurationNanos : this.providerDurationNanos;
				failureDescription = this.hasFailure() ? this.getFailure().toString() : null;
			}
			return new SequenceTraceMessage.Facts(this.id, this.kind.label, this.portType.getSimpleName(),
					this.method.getName(), this.thread.getName(), this.receiverDescription, this.argumentsDescription,
					returning ? this.outcome : null, durationNanos, failureDescription, this.asynchronous,
					this.queueWaitNanos);
		}

		private boolean hasFailure() {
			return this.getFailure() != null;
		}

		private Throwable getFailure() {
			return this.callerFailure != null ? this.callerFailure : this.providerFailure;
		}

		private String formatDurationMillis(final long durationNanos) {
			return String.format(Locale.ROOT, "%.3f ms", durationNanos / 1_000_000.0);
		}
	}
}
