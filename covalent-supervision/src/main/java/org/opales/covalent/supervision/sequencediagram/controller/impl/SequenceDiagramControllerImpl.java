/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.controller.impl;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.opales.covalent.supervision.SupervisionThreads;
import org.opales.covalent.supervision.observed.ObservedParameters;
import org.opales.covalent.supervision.observed.CallOutcome;
import org.opales.covalent.supervision.observed.ObservedEventsManagerEvents;
import org.opales.covalent.supervision.observed.ObservedMetamodelReadServices;
import org.opales.covalent.supervision.observed.ObservedParametersManagerEvents;
import org.opales.covalent.supervision.observed.ObservedServicesManagerEvents;
import org.opales.covalent.hmi.action.ActionEvents;
import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.hmi.action.ActionObservationServices;
import org.opales.covalent.supervision.observed.DataPrinter;
import org.opales.covalent.supervision.componentview.sequence.SequenceTraceMessage;
import org.opales.covalent.supervision.componentview.sequence.SequenceTraceReconciler;
import org.opales.covalent.supervision.componentview.sequence.SequenceTraceReconciler.InteractionKind;
import org.opales.covalent.supervision.exclusionpolicy.ExclusionCascade;
import org.opales.covalent.supervision.exclusionpolicy.controller.ExclusionParameters;
import org.opales.covalent.supervision.stereotypepolicy.MetamodelStereotypes;
import org.opales.covalent.supervision.stereotypepolicy.controller.StereotypeParameters;
import org.opales.covalent.supervision.sequencediagram.controller.SequenceDiagramController;
import org.opales.covalent.supervision.sequencediagram.controller.SequenceDiagramControllerServices;
import org.opales.covalent.supervision.sequencediagram.controller.SequenceDiagramServices;
import org.opales.covalent.supervision.sequencediagram.controller.RecordingStateEvents;
import org.opales.covalent.supervision.sequencediagram.model.SequenceDiagramModelServices;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

import org.opales.covalent.container.component.annotations.AccessedParameters;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.ProducedEvents;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.component.internals.Internal;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.container.metamodel.metamodel.ReadOnlyComponentMetamodel;

/**
 * SequenceDiagram's Controller: the sole provider of the external
 * {@link SequenceDiagramServices} and sole producer of
 * {@link RecordingStateEvents}. Reacts to the observed container's live call activity and
 * {@link ActionObservationServices}, filtered through
 * {@link ExclusionParameters}.
 * <p>
 * Only one session records at a time, matching the parameterless external/
 * internal {@code stopRecording()} methods: a second
 * {@code startRecording(...)} call while one is already active is ignored.
 */
@Implementation
public class SequenceDiagramControllerImpl implements SequenceDiagramController, SequenceDiagramControllerServices,
		SequenceDiagramServices, ObservedServicesManagerEvents, ObservedParametersManagerEvents, ObservedEventsManagerEvents,
		ActionEvents {

	private static final Logger log = Logger.getLogger(SequenceDiagramControllerImpl.class.getName());

	/** Creates the Controller; the container injects the services, events and parameters it uses. */
	public SequenceDiagramControllerImpl() {
		// Nothing to initialize.
	}

	@UsedServices
	private SequenceDiagramModelServices useModelServices;

	@UsedServices
	private ActionObservationServices actionObservationServices;

	@AccessedParameters
	private ObservedParameters accessObservedParameters;

	@UsedServices
	private ObservedMetamodelReadServices useObservedMetamodelReadServices;

	/** The components the current recording was started on - see {@link #representativeOf}. */
	private volatile Set<Class<?>> recordedTypes = Set.of();

	/** {@link #representativeOf}, cached for the current recording. */
	private final Map<Class<?>, Class<?>> representatives = new ConcurrentHashMap<>();

	@AccessedParameters
	private ExclusionParameters accessExclusionParameters;

	@AccessedParameters
	private StereotypeParameters accessStereotypeParameters;

	@ProducedEvents
	private RecordingStateEvents produceRecordingStateEvents;

	/** Serializes starting and stopping: one recording at a time, whoever asks. */
	private final Object recordingLock = new Object();

	/** The session recording, {@code null} when none: changed under {@link #recordingLock}. */
	private volatile ViewKey currentRecordingSessionKey;

	/** When the current recording started, in epoch milliseconds, {@code 0} when none runs. */
	private volatile long recordingStartMillis;

	private volatile SequenceTraceReconciler currentReconciler;

	/**
	 * The most messages one recording keeps. The View draws every message, so this is what it can
	 * draw while staying usable, not what the memory can hold: past it the recording is stopped.
	 */
	static final int MAX_RECORDED_MESSAGES = 100_000;

	/** How many messages (calls and returns, not completions) the current recording has produced. */
	private final AtomicInteger recordedMessageCount = new AtomicInteger();

	/**
	 * How long a call notification that arrived ahead of its predecessors waits for them. The
	 * observed calls are numbered per thread at the source, but their notifications are delivered
	 * here by several threads, so they can arrive out of order. Past this delay the missing ones
	 * are considered lost and the waiting ones are processed.
	 */
	private static final long CALL_ORDER_GAP_MILLIS = 200;

	/**
	 * How long a notification, once in its thread's order, is held before reaching the
	 * reconciler, so that the notifications of every thread reach it in the order of their times:
	 * a notification from another thread made earlier may still be on its way. One arriving
	 * later than that is handed over as soon as possible, after the ones already handed over.
	 */
	private static final long TIME_ORDER_WINDOW_NANOS = TimeUnit.MILLISECONDS.toNanos(50);

	/** Single thread processing call notifications in their source order. */
	private ScheduledExecutorService callOrderWorker;

	/** Reorder state per observed thread, only touched by {@link #callOrderWorker}. */
	private final Map<Thread, CallOrderBuffer> callOrderBuffers = new HashMap<>();

	/** Notifications held in time order, every thread together, only touched by {@link #callOrderWorker}. */
	private final PriorityQueue<HeldNotification> timeOrder = new PriorityQueue<>();

	/** Rank of arrival in {@link #timeOrder}, breaking ties between equal times. */
	private long heldCount;

	/** The pending release of {@link #timeOrder}, {@code null} when none. */
	private ScheduledFuture<?> timeOrderRelease;

	/** What each lifeline stands for - see {@link #stereotypeOf(Class)}. */
	private final Map<Class<?>, String> stereotypes = new ConcurrentHashMap<>();

	/** Implementation class per component type, for the "Received by" detail of event messages. */
	private final Map<Class<?>, Class<?>> implementations = new ConcurrentHashMap<>();

	/** This Controller's own working copy per session - it never reads the Model. */
	private final Map<ViewKey, Set<Class<?>>> selectedActorsBySession = new HashMap<>();

	/**
	 * Starts the worker that processes call notifications in their source order, and starts listening to the actions
	 * performed.
	 */
	@Activation
	public void activate() {
		this.callOrderWorker = Executors.newSingleThreadScheduledExecutor(SupervisionThreads.factory("sequence-order"));
		this.actionObservationServices.addActionEventsListener(this);
	}

	/** Stops listening to the actions performed and shuts the call notification worker down. */
	@Passivation
	public void passivate() {
		this.actionObservationServices.removeActionEventsListener(this);
		ScheduledExecutorService worker = this.callOrderWorker;
		this.callOrderWorker = null;
		if (worker != null) {
			worker.shutdownNow();
		}
	}

	// --- SequenceDiagramServices (external, called by ComponentDiagram and Threads) ---

	@Override
	public void startRecording(final Set<Class<?>> selectedTypes) {
		synchronized (this.recordingLock) {
			if (this.currentRecordingSessionKey != null) {
				// A recording is already active: the shared Record button stops it rather than
				// starting another, so this only happens when two start at once.
				return;
			}
			ViewKey sessionKey = new SequenceDiagramSessionKey();
			this.representatives.clear();
			this.implementations.clear();
			this.stereotypes.clear();
			this.resetCallOrder();
			this.recordedTypes = Set.copyOf(selectedTypes);
			this.recordedMessageCount.set(0);
			this.currentReconciler = new SequenceTraceReconciler(selectedTypes, this::isExcluded,
					message -> this.record(sessionKey, message));
			this.selectedActorsBySession.put(sessionKey, Set.of());
			this.useModelServices.createSession(sessionKey, selectedTypes);
			this.accessObservedParameters.setCallStreamEnabled(true);
			this.recordingStartMillis = System.currentTimeMillis();
			this.currentRecordingSessionKey = sessionKey;
		}
		// A notice only, sent outside the lock: its consumers read the state back.
		this.produceRecordingStateEvents.onRecordingStarted();
	}

	/**
	 * Hands a reconciled message to the Model, unless the recording already holds the most
	 * messages it keeps: the first message past that stops the recording and tells the Model, the
	 * next ones are dropped. Completions are not counted, the View draws no figure for them.
	 */
	private void record(final ViewKey sessionKey, final SequenceTraceMessage message) {
		if (message.getStyle() != SequenceTraceMessage.Style.COMPLETION) {
			int count = this.recordedMessageCount.incrementAndGet();
			if (count > MAX_RECORDED_MESSAGES) {
				if (count == MAX_RECORDED_MESSAGES + 1 && this.currentRecordingSessionKey == sessionKey) {
					this.useModelServices.recordingLimitReached(sessionKey, MAX_RECORDED_MESSAGES);
					this.stopRecording();
				}
				return;
			}
		}
		this.useModelServices.appendMessage(sessionKey, message.withStereotypes(
				this.stereotypeOf(message.getSourceType()), this.stereotypeOf(message.getTargetType())));
	}

	@Override
	public void stopRecording() {
		synchronized (this.recordingLock) {
			if (this.currentRecordingSessionKey == null) {
				return;
			}
			this.accessObservedParameters.setCallStreamEnabled(false);
			SequenceTraceReconciler reconciler = this.currentReconciler;
			this.currentRecordingSessionKey = null;
			this.recordingStartMillis = 0;
			this.currentReconciler = null;
			this.endCallOrder(reconciler);
		}
		this.produceRecordingStateEvents.onRecordingStopped();
	}

	@Override
	public boolean isRecording() {
		return this.currentRecordingSessionKey != null;
	}

	@Override
	public long recordingStartMillis() {
		return this.recordingStartMillis;
	}

	// --- SequenceDiagramControllerServices (internal, called by this function's own Interaction) ---

	@Override
	public void selectActors(final ViewKey sessionKey, final Set<Class<?>> actorTypes) {
		this.selectedActorsBySession.put(sessionKey, Set.copyOf(actorTypes));
		this.useModelServices.setSelectedActors(sessionKey, Set.copyOf(actorTypes));
	}

	@Override
	public void toggleActorSelection(final ViewKey sessionKey, final Class<?> actorType) {
		Set<Class<?>> updated = new LinkedHashSet<>(this.selectedActorsBySession.getOrDefault(sessionKey, Set.of()));
		if (!updated.remove(actorType)) {
			updated.add(actorType);
		}
		this.selectActors(sessionKey, updated);
	}

	@Override
	public void closeSession(final ViewKey sessionKey) {
		if (sessionKey.equals(this.currentRecordingSessionKey)) {
			this.stopRecording();
		}
		this.selectedActorsBySession.remove(sessionKey);
		this.useModelServices.closeSession(sessionKey);
	}

	/**
	 * The lifeline a call end belongs to: a component the recording was
	 * started on stands for itself and for everything it contains, at any
	 * depth, so recording a composite shows the calls its contents make
	 * with the outside on the composite's own lifeline - and hides those
	 * that stay inside it (see {@code SequenceTraceReconciler}). A component
	 * under none of them keeps its own lifeline.
	 *
	 * @param componentType one end of an observed call, {@code null} when unknown
	 * @return the component whose lifeline that end is drawn on
	 */
	private Class<?> representativeOf(final Class<?> componentType) {
		if (componentType == null || this.recordedTypes.isEmpty()) {
			return componentType;
		}
		return this.representatives.computeIfAbsent(componentType, this::findRecordedAncestor);
	}

	private Class<?> findRecordedAncestor(final Class<?> componentType) {
		if (this.recordedTypes.contains(componentType)) {
			return componentType;
		}
		ReadOnlyComponentMetamodel metamodel = this.useObservedMetamodelReadServices.readMetamodel(componentType);
		ReadOnlyComponentMetamodel parent = metamodel == null ? null : metamodel.getParentMetamodel();
		while (parent != null) {
			if (this.recordedTypes.contains(parent.getComponentType())) {
				return parent.getComponentType();
			}
			parent = parent.getParentMetamodel();
		}
		return componentType;
	}

	/**
	 * Describes the object that actually received an event notification when it is not the
	 * consumer component itself, e.g. an inner observer registered by its implementation.
	 *
	 * @param eventsConsumer the component the notification is attributed to
	 * @param subscriberType the class of the object that subscribed, {@code null} when unknown
	 * @return the receiver's class name without its package, {@code null} when it is the component
	 */
	private String describeReceiver(final Class<?> eventsConsumer, final Class<?> subscriberType) {
		if (subscriberType == null || eventsConsumer == null || subscriberType == eventsConsumer) {
			return null;
		}
		Class<?> implementation = this.implementations.computeIfAbsent(eventsConsumer,
				type -> {
					Class<?> found = this.useObservedMetamodelReadServices.getImplementation(type);
					return found == null ? type : found;
				});
		if (subscriberType == implementation) {
			return null;
		}
		String name = subscriberType.getName();
		String packageName = subscriberType.getPackageName();
		if (!packageName.isEmpty()) {
			name = name.substring(packageName.length() + 1);
		}
		return name.replace('$', '.');
	}

	// --- Call ordering ---

	/**
	 * Hands a call notification to the reconciler in the order the observed thread made the calls.
	 *
	 * @param callThread the observed thread that made the call
	 * @param callSequence the rank of the notification among that thread's ones, starting at 1
	 * @param callTimeNanos the time of the call, as given by {@link System#nanoTime()}
	 * @param handler what to do with the reconciler once the notification's turn comes
	 */
	private void inCallOrder(final Thread callThread, final long callSequence, final long callTimeNanos,
			final Consumer<SequenceTraceReconciler> handler) {
		ScheduledExecutorService worker = this.callOrderWorker;
		SequenceTraceReconciler reconciler = this.currentReconciler;
		if (worker == null || reconciler == null) {
			return;
		}
		HeldNotification notification = new HeldNotification(callTimeNanos, reconciler, handler);
		try {
			worker.execute(() -> this.acceptInOrder(callThread, callSequence, notification));
		} catch (RejectedExecutionException stopped) {
			// The worker is stopping with this component.
		}
	}

	private void resetCallOrder() {
		ScheduledExecutorService worker = this.callOrderWorker;
		if (worker == null) {
			return;
		}
		try {
			worker.execute(this::clearCallOrder);
		} catch (RejectedExecutionException stopped) {
			// The worker is stopping with this component.
		}
	}

	/**
	 * Ends a recording's ordering: what is held in time order is handed over at once, then the
	 * reconciler is cleared. Notifications still waiting for a predecessor are dropped.
	 */
	private void endCallOrder(final SequenceTraceReconciler reconciler) {
		ScheduledExecutorService worker = this.callOrderWorker;
		if (worker == null) {
			reconciler.clear();
			return;
		}
		try {
			worker.execute(() -> {
				this.releaseTimeOrder(Long.MAX_VALUE);
				this.clearCallOrder();
				reconciler.clear();
			});
		} catch (RejectedExecutionException stopped) {
			reconciler.clear();
		}
	}

	private void clearCallOrder() {
		this.callOrderBuffers.values().forEach(CallOrderBuffer::cancelGapRelease);
		this.callOrderBuffers.clear();
		this.timeOrder.clear();
		if (this.timeOrderRelease != null) {
			this.timeOrderRelease.cancel(false);
			this.timeOrderRelease = null;
		}
	}

	private void acceptInOrder(final Thread callThread, final long callSequence,
			final HeldNotification notification) {
		CallOrderBuffer buffer = this.callOrderBuffers.computeIfAbsent(callThread, thread -> new CallOrderBuffer());
		if (callSequence < buffer.expected) {
			// Arrived after its gap was given up on: processed as is.
			this.hold(notification);
			return;
		}
		buffer.pending.put(callSequence, notification);
		this.drain(buffer);
		if (!buffer.pending.isEmpty() && buffer.gapRelease == null) {
			long awaited = buffer.expected;
			buffer.gapRelease = this.callOrderWorker.schedule(() -> this.releaseGap(callThread, buffer, awaited),
					CALL_ORDER_GAP_MILLIS, TimeUnit.MILLISECONDS);
		}
	}

	private void releaseGap(final Thread callThread, final CallOrderBuffer buffer, final long awaited) {
		buffer.gapRelease = null;
		if (this.callOrderBuffers.get(callThread) != buffer || buffer.pending.isEmpty()) {
			return;
		}
		if (buffer.expected == awaited) {
			buffer.expected = buffer.pending.firstKey();
		}
		this.drain(buffer);
		if (!buffer.pending.isEmpty()) {
			long next = buffer.expected;
			buffer.gapRelease = this.callOrderWorker.schedule(() -> this.releaseGap(callThread, buffer, next),
					CALL_ORDER_GAP_MILLIS, TimeUnit.MILLISECONDS);
		}
	}

	private void drain(final CallOrderBuffer buffer) {
		HeldNotification next;
		while ((next = buffer.pending.remove(buffer.expected)) != null) {
			buffer.expected++;
			this.hold(next);
		}
		if (buffer.pending.isEmpty()) {
			buffer.cancelGapRelease();
		}
	}

	/** Holds a notification in time order until {@link #TIME_ORDER_WINDOW_NANOS} has passed since it. */
	private void hold(final HeldNotification notification) {
		notification.rank = this.heldCount++;
		this.timeOrder.add(notification);
		this.releaseTimeOrder(System.nanoTime() - TIME_ORDER_WINDOW_NANOS);
	}

	/**
	 * Hands over, in time order, the held notifications made at or before {@code untilNanos},
	 * then schedules the next release if some are still held.
	 */
	private void releaseTimeOrder(final long untilNanos) {
		HeldNotification next;
		while ((next = this.timeOrder.peek()) != null && next.timeNanos - untilNanos <= 0) {
			this.timeOrder.poll();
			try {
				next.handler.accept(next.reconciler);
			} catch (RuntimeException failure) {
				// One notification the recording refuses must not hold back the ones behind it.
				log.log(Level.WARNING, "A recorded notification could not be handed over", failure);
			}
		}
		if (this.timeOrder.isEmpty() || this.callOrderWorker == null) {
			return;
		}
		if (this.timeOrderRelease != null) {
			this.timeOrderRelease.cancel(false);
		}
		long delay = Math.max(1, this.timeOrder.peek().timeNanos + TIME_ORDER_WINDOW_NANOS - System.nanoTime());
		try {
			this.timeOrderRelease = this.callOrderWorker.schedule(() -> {
				this.timeOrderRelease = null;
				this.releaseTimeOrder(System.nanoTime() - TIME_ORDER_WINDOW_NANOS);
			}, delay, TimeUnit.NANOSECONDS);
		} catch (RejectedExecutionException stopped) {
			// The worker is stopping with this component.
		}
	}

	/** A notification on its way to the reconciler of the recording it was made in. */
	private static final class HeldNotification implements Comparable<HeldNotification> {
		private final long timeNanos;
		private final SequenceTraceReconciler reconciler;
		private final Consumer<SequenceTraceReconciler> handler;
		private long rank;

		private HeldNotification(final long timeNanos, final SequenceTraceReconciler reconciler,
				final Consumer<SequenceTraceReconciler> handler) {
			this.timeNanos = timeNanos;
			this.reconciler = reconciler;
			this.handler = handler;
		}

		@Override
		public int compareTo(final HeldNotification other) {
			int byTime = Long.compare(this.timeNanos - other.timeNanos, 0);
			return byTime != 0 ? byTime : Long.compare(this.rank, other.rank);
		}
	}

	/** Notifications of one observed thread waiting for their predecessors. */
	private static final class CallOrderBuffer {
		private long expected = 1;
		private final TreeMap<Long, HeldNotification> pending = new TreeMap<>();
		private ScheduledFuture<?> gapRelease;

		private void cancelGapRelease() {
			if (this.gapRelease != null) {
				this.gapRelease.cancel(false);
				this.gapRelease = null;
			}
		}
	}

	/**
	 * What a lifeline stands for. Its name is used when it ends like a rule of the stereotype
	 * table - the longest name ending wins - with no obligation: otherwise it is
	 * what the metamodel tells, a composite, a component, a raw component or one of Covalent's
	 * internal components.
	 *
	 * @param type the lifeline's type
	 * @return its stereotype
	 */
	private String stereotypeOf(final Class<?> type) {
		if (type == null) {
			return null;
		}
		return this.stereotypes.computeIfAbsent(type, this::findStereotype);
	}

	private String findStereotype(final Class<?> type) {
		String role = this.accessStereotypeParameters.stereotypeFor(type);
		if (role != null) {
			return role;
		}
		try {
			Class<?> componentType = this.useObservedMetamodelReadServices.getType(type);
			if (componentType == null) {
				componentType = type;
			}
			if (componentType.isAnnotationPresent(Internal.class)) {
				return MetamodelStereotypes.INTERNAL_COMPONENT;
			}
			ReadOnlyComponentMetamodel metamodel = this.useObservedMetamodelReadServices.readMetamodel(componentType);
			if (metamodel == null) {
				return componentType.isInterface() ? MetamodelStereotypes.INTERFACE : MetamodelStereotypes.RAW_COMPONENT;
			}
			if (metamodel.isRaw()) {
				return MetamodelStereotypes.RAW_COMPONENT;
			}
			return metamodel.isComposite() ? MetamodelStereotypes.COMPOSITE : MetamodelStereotypes.COMPONENT;
		} catch (RuntimeException unreachable) {
			// The observed container went away meanwhile.
			return MetamodelStereotypes.COMPONENT;
		}
	}

	private boolean isExcluded(final Class<?> componentType) {
		return componentType == null
				|| ExclusionCascade.isExcluded(componentType, this.accessExclusionParameters, this::containerOf);
	}

	private Class<?> containerOf(final Class<?> componentType) {
		try {
			ReadOnlyComponentMetamodel metamodel = this.useObservedMetamodelReadServices.readMetamodel(componentType);
			ReadOnlyComponentMetamodel parent = metamodel == null ? null : metamodel.getParentMetamodel();
			return parent == null ? null : parent.getComponentType();
		} catch (RuntimeException unreachable) {
			// The observed container went away meanwhile.
			return null;
		}
	}

	private String describeInputData(final Object... inputData) {
		return DataPrinter.describeArguments(inputData);
	}

	// --- ActionEvents: forwarded to the reconciler only while actively recording. ---

	@Override
	public void onActionTriggered(final ActionKey actionKey, final Class<?> actionCaller,
			final Class<?> actionImplementer, final Object... inputData) {
		SequenceTraceReconciler reconciler = this.currentReconciler;
		ScheduledExecutorService worker = this.callOrderWorker;
		if (reconciler == null || worker == null) {
			return;
		}
		long timeNanos = System.nanoTime();
		Thread thread = Thread.currentThread();
		String label = actionKey.toString();
		String arguments = this.describeInputData(inputData);
		HeldNotification notification = new HeldNotification(timeNanos, reconciler,
				target -> target.onActionTriggered(thread, timeNanos, label, actionCaller, actionImplementer, arguments));
		try {
			worker.execute(() -> this.hold(notification));
		} catch (RejectedExecutionException stopped) {
			// The worker is stopping with this component.
		}
	}

	// --- ServicesManagerEvents: topology methods are not this function's concern - it never
	// queries structure, only live call activity while recording. ---

	@Override
	public <CT, SERVICES> void onServicesProvided(final Class<CT> componentType, final Class<SERVICES> servicesInterface) {
		// Ignored: only live call activity matters to this function.
	}

	@Override
	public <CT, SERVICES> void onServicesUnprovided(final Class<CT> componentType, final Class<SERVICES> servicesInterface) {
		// Ignored: only live call activity matters to this function.
	}

	@Override
	public <CT, SERVICES> void onServicesUsed(final Class<CT> servicesUser, final Class<SERVICES> servicesInterface) {
		// Ignored: only live call activity matters to this function.
	}

	@Override
	public <CT, SERVICES> void onServicesUnused(final Class<CT> servicesUser, final Class<SERVICES> servicesInterface) {
		// Ignored: only live call activity matters to this function.
	}

	@Override
	public <SERVICES> void onUsedServiceCallStarted(final Thread callThread, final long callTimeNanos,
			final long callSequence, final Class<SERVICES> servicesInterface, final Method method,
			final Class<?> servicesUser, final String argumentsDescription) {
		this.inCallOrder(callThread, callSequence, callTimeNanos, reconciler -> {
			reconciler.onCallerSideStarted(InteractionKind.SERVICE, callThread, callTimeNanos,
					servicesInterface, method, this.representativeOf(servicesUser), argumentsDescription);
		});
	}

	@Override
	public <SERVICES> void onUsedServiceCallFinished(final Thread callThread, final long callTimeNanos,
			final long callSequence, final Class<SERVICES> servicesInterface, final Method method,
			final Class<?> servicesUser, final long durationNanos, final Throwable failure,
			final CallOutcome outcome) {
		this.inCallOrder(callThread, callSequence, callTimeNanos, reconciler -> {
			reconciler.onCallerSideFinished(InteractionKind.SERVICE, callThread, callTimeNanos,
					servicesInterface, method, this.representativeOf(servicesUser), durationNanos, failure,
					outcome);
		});
	}

	@Override
	public <SERVICES> void onProvidedServiceCallStarted(final Thread callThread, final long callTimeNanos,
			final long callSequence, final Class<SERVICES> servicesInterface, final Method method,
			final Class<?> servicesProvider, final String argumentsDescription) {
		this.inCallOrder(callThread, callSequence, callTimeNanos, reconciler -> {
			reconciler.onProviderSideStarted(InteractionKind.SERVICE, callThread, callTimeNanos,
					servicesInterface, method, this.representativeOf(servicesProvider));
		});
	}

	@Override
	public <SERVICES> void onProvidedServiceCallFinished(final Thread callThread, final long callTimeNanos,
			final long callSequence, final Class<SERVICES> servicesInterface, final Method method,
			final Class<?> servicesProvider, final long durationNanos, final Throwable failure,
			final CallOutcome outcome) {
		this.inCallOrder(callThread, callSequence, callTimeNanos, reconciler -> {
			reconciler.onProviderSideFinished(InteractionKind.SERVICE, callThread, servicesInterface,
						method, this.representativeOf(servicesProvider), durationNanos, failure,
					outcome);
		});
	}

	// --- ParametersManagerEvents: same split as ServicesManagerEvents above. ---

	@Override
	public <PARAMETERS> void onParametersOffered(final Class<?> componentType, final Class<PARAMETERS> parametersInterface) {
		// Ignored: only live call activity matters to this function.
	}

	@Override
	public <PARAMETERS> void onParametersUnoffered(final Class<?> componentType, final Class<PARAMETERS> parametersInterface) {
		// Ignored: only live call activity matters to this function.
	}

	@Override
	public <PARAMETERS> void onParametersAccessed(final Class<?> parametersAccessor, final Class<PARAMETERS> parametersInterface) {
		// Ignored: only live call activity matters to this function.
	}

	@Override
	public <PARAMETERS> void onParametersUnaccessed(final Class<?> parametersAccessor, final Class<PARAMETERS> parametersInterface) {
		// Ignored: only live call activity matters to this function.
	}

	@Override
	public <PARAMETERS> void onAccessedParametersCallStarted(final Thread callThread, final long callTimeNanos,
			final long callSequence, final Class<PARAMETERS> parametersInterface, final Method method,
			final Class<?> parametersAccessor, final String argumentsDescription) {
		this.inCallOrder(callThread, callSequence, callTimeNanos, reconciler -> {
			reconciler.onCallerSideStarted(InteractionKind.PARAMETER, callThread, callTimeNanos,
					parametersInterface, method, this.representativeOf(parametersAccessor), argumentsDescription);
		});
	}

	@Override
	public <PARAMETERS> void onAccessedParametersCallFinished(final Thread callThread, final long callTimeNanos,
			final long callSequence, final Class<PARAMETERS> parametersInterface, final Method method,
			final Class<?> parametersAccessor, final long durationNanos, final Throwable failure,
			final CallOutcome outcome) {
		this.inCallOrder(callThread, callSequence, callTimeNanos, reconciler -> {
			reconciler.onCallerSideFinished(InteractionKind.PARAMETER, callThread, callTimeNanos,
					parametersInterface, method, this.representativeOf(parametersAccessor), durationNanos, failure,
					outcome);
		});
	}

	@Override
	public <PARAMETERS> void onOfferedParametersCallStarted(final Thread callThread, final long callTimeNanos,
			final long callSequence, final Class<PARAMETERS> parametersInterface, final Method method,
			final Class<?> parametersOfferor, final String argumentsDescription) {
		this.inCallOrder(callThread, callSequence, callTimeNanos, reconciler -> {
			reconciler.onProviderSideStarted(InteractionKind.PARAMETER, callThread, callTimeNanos,
					parametersInterface, method, this.representativeOf(parametersOfferor));
		});
	}

	@Override
	public <PARAMETERS> void onOfferedParametersCallFinished(final Thread callThread, final long callTimeNanos,
			final long callSequence, final Class<PARAMETERS> parametersInterface, final Method method,
			final Class<?> parametersOfferor, final long durationNanos, final Throwable failure,
			final CallOutcome outcome) {
		this.inCallOrder(callThread, callSequence, callTimeNanos, reconciler -> {
			reconciler.onProviderSideFinished(InteractionKind.PARAMETER, callThread,
					parametersInterface, method, this.representativeOf(parametersOfferor), durationNanos, failure,
					outcome);
		});
	}

	// --- EventsManagerEvents: same split as ServicesManagerEvents above; events have no
	// separate caller/provider sides, only a single direct producer/consumer interaction. ---

	@Override
	public <EVENTS> void onEventsProduced(final Class<?> componentType, final Class<EVENTS> eventsInterface) {
		// Ignored: only live call activity matters to this function.
	}

	@Override
	public <EVENTS> void onEventsUnproduced(final Class<?> componentType, final Class<EVENTS> eventsInterface) {
		// Ignored: only live call activity matters to this function.
	}

	@Override
	public <EVENTS> void onEventsSubscribed(final Class<? extends EVENTS> eventsConsumer, final Class<EVENTS> eventsInterface) {
		// Ignored: only live call activity matters to this function.
	}

	@Override
	public <EVENTS> void onEventsUnsubscribed(final Class<? extends EVENTS> eventsConsumer, final Class<EVENTS> eventsInterface) {
		// Ignored: only live call activity matters to this function.
	}

	@Override
	public <EVENTS> void onEventNotificationStarted(final Thread callThread, final long callTimeNanos,
			final long callSequence, final Class<EVENTS> eventsInterface, final Method method,
			final Class<?> eventsProducer, final Class<?> eventsConsumer, final Class<? extends EVENTS> subscriberType,
			final boolean asynchronous, final long queueWaitNanos, final String argumentsDescription) {
		this.inCallOrder(callThread, callSequence, callTimeNanos, reconciler -> {
			reconciler.onDirectInteractionStarted(InteractionKind.EVENT, callThread, callTimeNanos,
					eventsInterface, method, this.representativeOf(eventsProducer), this.representativeOf(eventsConsumer),
					argumentsDescription, this.describeReceiver(eventsConsumer, subscriberType), asynchronous,
					queueWaitNanos);
		});
	}

	@Override
	public <EVENTS> void onEventNotificationFinished(final Thread callThread, final long callTimeNanos,
			final long callSequence, final Class<EVENTS> eventsInterface, final Method method,
			final Class<?> eventsProducer, final Class<?> eventsConsumer, final Class<? extends EVENTS> subscriberType,
			final long durationNanos, final Throwable failure) {
		this.inCallOrder(callThread, callSequence, callTimeNanos, reconciler -> {
			reconciler.onDirectInteractionFinished(InteractionKind.EVENT, callThread, callTimeNanos,
					eventsInterface, method, this.representativeOf(eventsProducer), this.representativeOf(eventsConsumer),
					durationNanos, failure);
		});
	}

	/** One instance per recording session - identity-based, nothing more. */
	private static final class SequenceDiagramSessionKey implements ViewKey {
		private final UUID id = UUID.randomUUID();

		@Override
		public boolean equals(final Object other) {
			return other instanceof SequenceDiagramSessionKey && this.id.equals(((SequenceDiagramSessionKey) other).id);
		}

		@Override
		public int hashCode() {
			return this.id.hashCode();
		}
	}
}
