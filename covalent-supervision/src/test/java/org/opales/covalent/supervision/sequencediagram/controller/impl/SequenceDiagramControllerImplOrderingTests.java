/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.sequencediagram.controller.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.opales.covalent.container.component.internals.Internal;
import org.opales.covalent.container.metamodel.metamodel.ReadOnlyComponentMetamodel;
import org.opales.covalent.hmi.action.ActionEvents;
import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.hmi.action.ActionObservationServices;
import org.opales.covalent.supervision.componentview.sequence.SequenceTraceMessage;
import org.opales.covalent.supervision.exclusionpolicy.controller.ExclusionParameters;
import org.opales.covalent.supervision.observed.ObservedParameters;
import org.opales.covalent.supervision.observed.ObservedMetamodelReadServices;
import org.opales.covalent.supervision.sequencediagram.controller.RecordingStateEvents;
import org.opales.covalent.supervision.sequencediagram.model.SequenceDiagramModelServices;
import org.opales.covalent.supervision.stereotypepolicy.MetamodelStereotypes;
import org.opales.covalent.supervision.stereotypepolicy.controller.StereotypeParameters;
import org.opales.covalent.supervision.supervisionhmi.view.ViewKey;

/**
 * Verifies, with hand-written fakes and no container, how {@link SequenceDiagramControllerImpl}
 * orders the notifications of one recording: per-thread call sequence with gaps given up on, time
 * order inside the holding window, the representative of a component inside a recorded composite,
 * the stereotype of a lifeline from the observed metamodel, the receiver of an event, and the
 * behavior when the worker thread is stopped or the component is passivated.
 */
public class SequenceDiagramControllerImplOrderingTests {

	private interface Parent {
	}

	private interface Middle {
	}

	private interface Child {
	}

	private interface Consumer extends Runnable {
	}

	@Internal
	private interface InternalThing {
	}

	private interface PlainInterface {
	}

	private static final class Subscriber {
	}

	private static final class FakeModel implements SequenceDiagramModelServices {
		final List<ViewKey> sessions = new CopyOnWriteArrayList<>();
		final List<SequenceTraceMessage> messages = new CopyOnWriteArrayList<>();
		volatile RuntimeException failNext;

		@Override
		public void createSession(final ViewKey sessionKey, final Set<Class<?>> observedComponentTypes) {
			this.sessions.add(sessionKey);
		}

		@Override
		public void appendMessage(final ViewKey sessionKey, final SequenceTraceMessage message) {
			RuntimeException failure = this.failNext;
			if (failure != null) {
				this.failNext = null;
				throw failure;
			}
			this.messages.add(message);
		}

		@Override
		public void setSelectedActors(final ViewKey sessionKey, final Set<Class<?>> actorTypes) {
			// Not under test here.
		}

		@Override
		public void closeSession(final ViewKey sessionKey) {
			// Not under test here.
		}

		@Override
		public void recordingLimitReached(final ViewKey sessionKey, final int limit) {
			// Not under test here.
		}
	}

	private static final class FakeMetamodels implements ObservedMetamodelReadServices {
		final Map<Class<?>, ReadOnlyComponentMetamodel> metamodels = new HashMap<>();
		final Map<Class<?>, Class<?>> types = new HashMap<>();
		final Map<Class<?>, Class<?>> implementations = new HashMap<>();
		RuntimeException failure;
		int implementationReads;

		@Override
		public ReadOnlyComponentMetamodel readMetamodel(final Class<?> componentType) {
			if (this.failure != null) {
				throw this.failure;
			}
			return this.metamodels.get(componentType);
		}

		@Override
		@SuppressWarnings("unchecked")
		public <CT, CI> Class<CT> getType(final Class<CI> implementation) {
			return (Class<CT>) this.types.get(implementation);
		}

		@Override
		@SuppressWarnings("unchecked")
		public <CT, CI> Class<CI> getImplementation(final Class<CT> componentType) {
			this.implementationReads++;
			return (Class<CI>) this.implementations.get(componentType);
		}
	}

	private final SequenceDiagramControllerImpl controller = new SequenceDiagramControllerImpl();
	private final FakeModel model = new FakeModel();
	private final FakeMetamodels metamodels = new FakeMetamodels();
	private final AtomicInteger started = new AtomicInteger();
	private final AtomicInteger stopped = new AtomicInteger();
	private final List<Boolean> streamEnabled = new CopyOnWriteArrayList<>();
	private final List<String> listenerCalls = new CopyOnWriteArrayList<>();
	private Method run;
	private Method compareTo;

	@Before
	public void setUp() throws Exception {
		this.run = Runnable.class.getMethod("run");
		this.compareTo = Comparable.class.getMethod("compareTo", Object.class);
		this.set("useModelServices", this.model);
		this.set("useObservedMetamodelReadServices", this.metamodels);
		this.set("accessExclusionParameters", (ExclusionParameters) type -> false);
		this.set("accessStereotypeParameters", (StereotypeParameters) type -> null);
		this.set("accessObservedParameters", proxy(ObservedParameters.class,
				(p, m, a) -> this.streamEnabled.add((Boolean) a[0])));
		this.set("actionObservationServices", proxy(ActionObservationServices.class, (p, m, a) -> {
			this.listenerCalls.add(m.getName());
			return null;
		}));
		this.set("produceRecordingStateEvents", proxy(RecordingStateEvents.class, (p, m, a) -> {
			(m.getName().equals("onRecordingStarted") ? this.started : this.stopped).incrementAndGet();
			return null;
		}));
		this.controller.activate();
	}

	@After
	public void tearDown() {
		this.controller.passivate();
	}

	private static <T> T proxy(final Class<T> type, final java.lang.reflect.InvocationHandler handler) {
		return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (p, m, a) -> {
			Object result = handler.invoke(p, m, a);
			return m.getReturnType() == void.class ? null : result;
		}));
	}

	private void set(final String name, final Object value) throws Exception {
		Field field = SequenceDiagramControllerImpl.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(this.controller, value);
	}

	private Object get(final String name) throws Exception {
		Field field = SequenceDiagramControllerImpl.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.get(this.controller);
	}

	private static void await(final BooleanSupplier condition) throws InterruptedException {
		long deadline = System.currentTimeMillis() + 5_000;
		while (!condition.getAsBoolean()) {
			Assert.assertTrue("Timed out waiting", System.currentTimeMillis() < deadline);
			Thread.sleep(5);
		}
	}

	/** Waits until the worker has run everything submitted before. */
	private void drainWorker() throws Exception {
		ScheduledExecutorService worker = (ScheduledExecutorService) this.get("callOrderWorker");
		worker.submit(() -> { }).get(5, TimeUnit.SECONDS);
	}

	private static ReadOnlyComponentMetamodel metamodel(final Class<?> type, final ReadOnlyComponentMetamodel parent,
			final List<Class<?>> composed) {
		return metamodel(type, parent, composed, false);
	}

	private static ReadOnlyComponentMetamodel metamodel(final Class<?> type, final ReadOnlyComponentMetamodel parent,
			final List<Class<?>> composed, final boolean raw) {
		return (ReadOnlyComponentMetamodel) Proxy.newProxyInstance(ReadOnlyComponentMetamodel.class.getClassLoader(),
				new Class<?>[] { ReadOnlyComponentMetamodel.class }, (p, m, a) -> switch (m.getName()) {
					case "getComponentType" -> type;
					case "getParentMetamodel" -> parent;
					case "getComposedTypes" -> composed;
					case "isComposite" -> !composed.isEmpty();
					case "isRaw" -> raw;
					default -> null;
				});
	}

	private void event(final Thread thread, final long sequence, final long timeNanos, final Method method,
			final Class<?> producer, final Class<?> consumer, final Class<? extends Runnable> subscriber) {
		this.controller.onEventNotificationStarted(thread, timeNanos, sequence, Runnable.class, method, producer,
				consumer, subscriber, false, -1L, "[no arguments]");
	}

	private List<String> methods() {
		return this.model.messages.stream().map(message -> message.getFacts().methodName()).toList();
	}

	private String stereotypeOf(final Class<?> type) throws Exception {
		Method method = SequenceDiagramControllerImpl.class.getDeclaredMethod("stereotypeOf", Class.class);
		method.setAccessible(true);
		return (String) method.invoke(this.controller, type);
	}

	private String describeReceiver(final Class<?> consumer, final Class<?> subscriber) throws Exception {
		Method method = SequenceDiagramControllerImpl.class.getDeclaredMethod("describeReceiver", Class.class,
				Class.class);
		method.setAccessible(true);
		return (String) method.invoke(this.controller, consumer, subscriber);
	}

	// --- Recording lifecycle ---

	@Test
	public void recordingEnablesTheCallStreamAndStoppingDisablesIt() {
		this.controller.startRecording(Set.of(Parent.class));
		Assert.assertEquals(List.of(true), this.streamEnabled);
		Assert.assertEquals(1, this.started.get());
		this.controller.stopRecording();
		Assert.assertEquals(List.of(true, false), this.streamEnabled);
		Assert.assertEquals(1, this.stopped.get());
	}

	@Test
	public void eachRecordingSessionHasItsOwnKey() {
		this.controller.startRecording(Set.of(Parent.class));
		this.controller.stopRecording();
		this.controller.startRecording(Set.of(Parent.class));
		ViewKey first = this.model.sessions.get(0);
		ViewKey second = this.model.sessions.get(1);
		Assert.assertNotEquals(first, second);
		Assert.assertEquals(first, first);
		Assert.assertNotEquals(first, new ViewKey() {
		});
		Assert.assertNotEquals(first.hashCode(), second.hashCode());
	}

	@Test
	public void activationRegistersTheActionListenerAndPassivationRemovesIt() {
		Assert.assertEquals(List.of("addActionEventsListener"), this.listenerCalls);
		this.controller.passivate();
		Assert.assertEquals(List.of("addActionEventsListener", "removeActionEventsListener"), this.listenerCalls);
		this.controller.passivate();
	}

	// --- Ordering ---

	@Test
	public void callsOfOneThreadAreProcessedInTheOrderTheyWereMadeIn() throws Exception {
		this.controller.startRecording(Set.of(Parent.class));
		Thread thread = new Thread();
		long past = System.nanoTime() - TimeUnit.SECONDS.toNanos(1);
		this.event(thread, 2, past + 2, this.compareTo, Parent.class, Consumer.class, Consumer.class);
		this.event(thread, 1, past + 1, this.run, Parent.class, Consumer.class, Consumer.class);
		await(() -> this.model.messages.size() == 2);
		Assert.assertEquals(List.of("run", "compareTo"), this.methods());
	}

	@Test
	public void aGapInTheSequenceIsGivenUpOnAndLaterGapsToo() throws Exception {
		this.controller.startRecording(Set.of(Parent.class));
		Thread thread = new Thread();
		long past = System.nanoTime() - TimeUnit.SECONDS.toNanos(1);
		// Calls 1 and 3 never arrive in order: 2 waits for 1, then 4 waits for 3.
		this.event(thread, 2, past + 2, this.run, Parent.class, Consumer.class, Consumer.class);
		this.event(thread, 4, past + 4, this.compareTo, Parent.class, Consumer.class, Consumer.class);
		await(() -> this.model.messages.size() == 2);
		Assert.assertEquals(List.of("run", "compareTo"), this.methods());
	}

	@Test
	public void notificationsOfDifferentThreadsAreReleasedInTimeOrder() throws Exception {
		this.controller.startRecording(Set.of(Parent.class));
		long later = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(300);
		// The later call is delivered first: both wait inside the holding window.
		this.event(new Thread(), 1, later + 1_000_000, this.compareTo, Parent.class, Consumer.class, Consumer.class);
		this.event(new Thread(), 1, later, this.run, Parent.class, Consumer.class, Consumer.class);
		this.drainWorker();
		Assert.assertTrue("held within the window", this.model.messages.isEmpty());
		await(() -> this.model.messages.size() == 2);
		Assert.assertEquals(List.of("run", "compareTo"), this.methods());
	}

	@Test
	public void stoppingFlushesHeldNotificationsAndClearsTheOrderingState() throws Exception {
		this.controller.startRecording(Set.of(Parent.class));
		Thread thread = new Thread();
		long later = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		this.event(thread, 1, later, this.run, Parent.class, Consumer.class, Consumer.class);
		this.event(thread, 3, later + 1, this.compareTo, Parent.class, Consumer.class, Consumer.class);
		this.drainWorker();
		Assert.assertTrue(this.model.messages.isEmpty());

		this.controller.stopRecording();
		this.drainWorker();

		Assert.assertEquals(List.of("run"), this.methods());
		Assert.assertTrue(((Map<?, ?>) this.get("callOrderBuffers")).isEmpty());
		Assert.assertTrue(((java.util.Collection<?>) this.get("timeOrder")).isEmpty());
		Assert.assertNull(this.get("timeOrderRelease"));
	}

	@Test
	public void startingANewRecordingForgetsTheOrderOfThePreviousOne() throws Exception {
		this.controller.startRecording(Set.of(Parent.class));
		Thread thread = new Thread();
		long past = System.nanoTime() - TimeUnit.SECONDS.toNanos(1);
		this.event(thread, 1, past, this.run, Parent.class, Consumer.class, Consumer.class);
		await(() -> this.model.messages.size() == 1);
		this.controller.stopRecording();
		this.controller.startRecording(Set.of(Parent.class));
		// The same thread starts again at call 1 in the new recording.
		this.event(thread, 1, past + 1, this.compareTo, Parent.class, Consumer.class, Consumer.class);
		await(() -> this.model.messages.size() == 2);
		Assert.assertEquals(List.of("run", "compareTo"), this.methods());
	}

	// --- Worker stopped or absent ---

	@Test
	public void aRecordingStillStartsAndStopsOnceTheComponentIsPassivated() {
		this.controller.passivate();
		this.controller.startRecording(Set.of(Parent.class));
		Assert.assertTrue(this.controller.isRecording());
		this.event(new Thread(), 1, System.nanoTime(), this.run, Parent.class, Consumer.class, Consumer.class);
		this.controller.onActionTriggered(new ActionKey() {
		}, Parent.class, Consumer.class);
		this.controller.stopRecording();
		Assert.assertFalse(this.controller.isRecording());
		Assert.assertEquals(1, this.stopped.get());
		Assert.assertTrue(this.model.messages.isEmpty());
	}

	@Test
	public void notificationsAreDroppedWhenTheWorkerIsStoppedButNotYetReleased() throws Exception {
		ScheduledExecutorService dead = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
		dead.shutdownNow();
		this.set("callOrderWorker", dead);
		this.controller.startRecording(Set.of(Parent.class));
		this.event(new Thread(), 1, System.nanoTime(), this.run, Parent.class, Consumer.class, Consumer.class);
		this.controller.onActionTriggered(new ActionKey() {
		}, Parent.class, Consumer.class);
		this.controller.stopRecording();
		Assert.assertFalse(this.controller.isRecording());
		Assert.assertEquals(1, this.stopped.get());
		Assert.assertTrue(this.model.messages.isEmpty());
	}

	@Test
	public void aFailingNotificationDoesNotBlockTheOnesBehindIt() throws Exception {
		this.controller.startRecording(Set.of(Parent.class));
		this.model.failNext = new IllegalStateException("the model refuses the first message");
		Thread thread = new Thread();
		long past = System.nanoTime() - TimeUnit.SECONDS.toNanos(1);
		this.event(thread, 1, past + 1, this.run, Parent.class, Consumer.class, Consumer.class);
		this.event(thread, 2, past + 2, this.compareTo, Parent.class, Consumer.class, Consumer.class);
		this.drainWorker();
		await(() -> this.methods().equals(List.of("compareTo")));
		Assert.assertTrue(((java.util.Collection<?>) this.get("timeOrder")).isEmpty());
	}

	@Test
	public void aFailingNotificationInsideTheHoldingWindowDoesNotBlockTheNextRelease() throws Exception {
		this.controller.startRecording(Set.of(Parent.class));
		this.model.failNext = new IllegalStateException("the model refuses the first message");
		long later = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(300);
		this.event(new Thread(), 1, later, this.run, Parent.class, Consumer.class, Consumer.class);
		this.event(new Thread(), 1, later + 1_000_000, this.compareTo, Parent.class, Consumer.class,
				Consumer.class);
		this.drainWorker();
		Assert.assertTrue("held within the window", this.model.messages.isEmpty());
		await(() -> this.methods().equals(List.of("compareTo")));
		Assert.assertTrue(((java.util.Collection<?>) this.get("timeOrder")).isEmpty());
	}

	@Test
	public void anActionWhileRecordingBecomesAMessage() throws Exception {
		this.controller.startRecording(Set.of(Parent.class));
		this.controller.onActionTriggered(new ActionKey() {
		}, Parent.class, Consumer.class, "argument");
		await(() -> !this.model.messages.isEmpty());
		Assert.assertEquals(Parent.class, this.model.messages.get(0).getSourceType());
	}

	// --- Notifications that are none of this function's concern ---

	@Test
	public void topologyNotificationsChangeNothing() {
		this.controller.onServicesProvided(Parent.class, Runnable.class);
		this.controller.onServicesUnprovided(Parent.class, Runnable.class);
		this.controller.onServicesUsed(Parent.class, Runnable.class);
		this.controller.onServicesUnused(Parent.class, Runnable.class);
		this.controller.onParametersOffered(Parent.class, Runnable.class);
		this.controller.onParametersUnoffered(Parent.class, Runnable.class);
		this.controller.onParametersAccessed(Parent.class, Runnable.class);
		this.controller.onParametersUnaccessed(Parent.class, Runnable.class);
		this.controller.onEventsProduced(Parent.class, Runnable.class);
		this.controller.onEventsUnproduced(Parent.class, Runnable.class);
		this.controller.onEventsSubscribed(Runnable.class, Runnable.class);
		this.controller.onEventsUnsubscribed(Runnable.class, Runnable.class);
		Assert.assertFalse(this.controller.isRecording());
		Assert.assertTrue(this.model.messages.isEmpty());
		Assert.assertTrue(this.model.sessions.isEmpty());
	}

	@Test
	public void eventFinishedWhileRecordingReachesTheReconciler() throws Exception {
		this.controller.startRecording(Set.of(Parent.class));
		long past = System.nanoTime() - TimeUnit.SECONDS.toNanos(1);
		Thread thread = new Thread();
		this.event(thread, 1, past, this.run, Parent.class, Consumer.class, Consumer.class);
		this.controller.onEventNotificationFinished(thread, past + 1, 2, Runnable.class, this.run, Parent.class,
				Consumer.class, Consumer.class, 1_000, null);
		await(() -> !this.model.messages.isEmpty());
		this.drainWorker();
		Assert.assertEquals("run", this.model.messages.get(0).getFacts().methodName());
	}

	// --- Representatives ---

	@Test
	public void aComponentInsideARecordedCompositeStandsForItsRecordedAncestor() throws Exception {
		ReadOnlyComponentMetamodel parent = metamodel(Parent.class, null, List.of(Middle.class));
		ReadOnlyComponentMetamodel middle = metamodel(Middle.class, parent, List.of(Child.class));
		this.metamodels.metamodels.put(Child.class, metamodel(Child.class, middle, List.of()));
		this.controller.startRecording(Set.of(Parent.class, Consumer.class));
		long past = System.nanoTime() - TimeUnit.SECONDS.toNanos(1);
		this.event(new Thread(), 1, past, this.run, Child.class, Consumer.class, Consumer.class);
		await(() -> !this.model.messages.isEmpty());
		Assert.assertEquals("the producer is shown as the recorded composite holding it", Parent.class,
				this.model.messages.get(0).getSourceType());
	}

	@Test
	public void aComponentWithoutRecordedAncestorStandsForItself() throws Exception {
		this.metamodels.metamodels.put(Child.class, metamodel(Child.class, metamodel(Middle.class, null, List.of()), List.of()));
		this.controller.startRecording(Set.of(Child.class, Consumer.class));
		long past = System.nanoTime() - TimeUnit.SECONDS.toNanos(1);
		this.event(new Thread(), 1, past, this.run, Child.class, Consumer.class, Consumer.class);
		await(() -> !this.model.messages.isEmpty());
		Assert.assertEquals(Child.class, this.model.messages.get(0).getSourceType());
	}

	// --- Stereotypes ---

	@Test
	public void aStereotypeFromThePolicyPrevailsOverTheMetamodel() throws Exception {
		this.set("accessStereotypeParameters", (StereotypeParameters) type -> type == Parent.class ? "Role" : null);
		Assert.assertEquals("Role", this.stereotypeOf(Parent.class));
	}

	@Test
	public void anImplementationIsReadAsTheComponentTypeItImplements() throws Exception {
		this.metamodels.types.put(Subscriber.class, InternalThing.class);
		Assert.assertEquals(MetamodelStereotypes.INTERNAL_COMPONENT, this.stereotypeOf(Subscriber.class));
	}

	@Test
	public void aComponentWithComposedTypesIsAComposite() throws Exception {
		this.metamodels.metamodels.put(Parent.class, metamodel(Parent.class, null, List.of(Child.class)));
		Assert.assertEquals(MetamodelStereotypes.COMPOSITE, this.stereotypeOf(Parent.class));
	}

	@Test
	public void aComponentWithoutComposedTypesIsAPlainComponent() throws Exception {
		this.metamodels.metamodels.put(Child.class, metamodel(Child.class, null, List.of()));
		Assert.assertEquals(MetamodelStereotypes.COMPONENT, this.stereotypeOf(Child.class));
	}

	@Test
	public void aRawComponentTheMetamodelDescribesIsRaw() throws Exception {
		this.metamodels.metamodels.put(Child.class, metamodel(Child.class, null, List.of(), true));
		Assert.assertEquals(MetamodelStereotypes.RAW_COMPONENT, this.stereotypeOf(Child.class));
	}

	@Test
	public void aTypeWithoutMetamodelIsAnInterfaceOrARawComponent() throws Exception {
		Assert.assertEquals(MetamodelStereotypes.INTERFACE, this.stereotypeOf(PlainInterface.class));
		Assert.assertEquals(MetamodelStereotypes.RAW_COMPONENT, this.stereotypeOf(Subscriber.class));
	}

	@Test
	public void aStereotypeIsReadOnlyOnce() throws Exception {
		Assert.assertEquals(MetamodelStereotypes.RAW_COMPONENT, this.stereotypeOf(Subscriber.class));
		this.metamodels.metamodels.put(Subscriber.class, metamodel(Subscriber.class, null, List.of()));
		Assert.assertEquals(MetamodelStereotypes.RAW_COMPONENT, this.stereotypeOf(Subscriber.class));
	}

	@Test
	public void aContainerThatWentAwayLeavesThePlainComponentStereotype() throws Exception {
		this.metamodels.failure = new IllegalStateException("gone");
		Assert.assertEquals(MetamodelStereotypes.COMPONENT, this.stereotypeOf(Subscriber.class));
	}

	// --- Receivers ---

	@Test
	public void noReceiverIsDescribedWithoutASubscriberOrAConsumerOrWhenTheyAreTheSame() throws Exception {
		Assert.assertNull(this.describeReceiver(Consumer.class, null));
		Assert.assertNull(this.describeReceiver(null, Subscriber.class));
		Assert.assertNull(this.describeReceiver(Consumer.class, Consumer.class));
	}

	@Test
	public void aSubscriberDifferentFromTheConsumersImplementationIsDescribedByItsNestedName() throws Exception {
		Assert.assertEquals("SequenceDiagramControllerImplOrderingTests.Subscriber",
				this.describeReceiver(Consumer.class, Subscriber.class));
	}

	@Test
	public void aSubscriberThatIsTheConsumersImplementationIsNotAnotherReceiver() throws Exception {
		this.metamodels.implementations.put(Consumer.class, Subscriber.class);
		Assert.assertNull(this.describeReceiver(Consumer.class, Subscriber.class));
		Assert.assertNull(this.describeReceiver(Consumer.class, Subscriber.class));
		Assert.assertEquals("the implementation is read once", 1, this.metamodels.implementationReads);
	}
}
