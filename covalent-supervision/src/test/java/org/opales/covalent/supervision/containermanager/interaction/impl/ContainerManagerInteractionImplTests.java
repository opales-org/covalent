/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.containermanager.interaction.impl;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.opales.covalent.Covalent;
import org.opales.covalent.ManagedContainerEvents;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.async.AsyncServices;
import org.opales.covalent.hmi.action.ActionKey;
import org.opales.covalent.hmi.action.ActionProviderServices;
import org.opales.covalent.supervision.SupervisionExtension;
import org.opales.covalent.supervision.containermanager.interaction.ContainerManagerActionKey;
import org.opales.covalent.supervision.containermanager.raw.ObservedContainerControl;
import org.opales.covalent.supervision.containermanager.view.ContainerManagerViewServices;
import org.opales.covalent.supervision.supervisionhmi.SupervisorSettings;
import org.opales.covalent.supervision.testsupport.CapturingLogHandler;

/**
 * {@link ContainerManagerInteractionImpl} against hand-written fakes of the services it uses and real managed
 * containers; the lifecycle notifications are delivered to its observer directly. The default flows setting it
 * reads from the supervisor's preferences is put back after each test.
 */
public class ContainerManagerInteractionImplTests {

	private static final String FIRST = "cm-interaction-tests-first";

	private static final String SECOND = "cm-interaction-tests-second";

	private static final String QUEUE = "supervision-connection";

	private static final class FakeActionProviderServices implements ActionProviderServices {
		final Map<ActionKey, Consumer<Object>> consumers = new LinkedHashMap<>();
		final Set<ActionKey> registered = new HashSet<>();
		final List<ActionKey> unregistered = new ArrayList<>();
		final List<Object> flowsValues = new ArrayList<>();
		boolean failOnUnregister;

		@Override
		@SuppressWarnings("unchecked")
		public <T> void registerActionExecution(final ActionKey anActionKey, final Consumer<T> anInteractionImpl,
				final Class<T> anInputDataClass) {
			this.registered.add(anActionKey);
			this.consumers.put(anActionKey, (Consumer<Object>) anInteractionImpl);
		}

		@Override
		public <T1, T2> void registerActionExecution(final ActionKey anActionKey,
				final BiConsumer<T1, T2> anInteractionImpl, final Class<T1> anInputDataClass1,
				final Class<T2> anInputDataClass2) {
			this.registered.add(anActionKey);
		}

		@Override
		public void registerActionExecution(final ActionKey anActionKey, final Runnable anInteractionImpl) {
			this.registered.add(anActionKey);
		}

		@Override
		public void unregisterActionExecution(final ActionKey anActionKey) {
			if (this.failOnUnregister) {
				throw new IllegalStateException("already passivated");
			}
			this.unregistered.add(anActionKey);
		}

		@Override
		public void setActionAvailable(final ActionKey anActionKey, final boolean isAvailable) {
		}

		@Override
		public void setActionValue(final ActionKey anActionKey, final Object aValue) {
			if (anActionKey == ContainerManagerActionKey.SetFlowsEnabled) {
				this.flowsValues.add(aValue);
			}
		}

		void fire(final ActionKey key, final Object input) {
			this.consumers.get(key).accept(input);
		}
	}

	private static final class FakeControl implements ObservedContainerControl {
		final List<CovalentContainer> connected = new ArrayList<>();
		int disconnects;
		RuntimeException failure;

		@Override
		public void connect(final CovalentContainer target) {
			if (this.failure != null) {
				throw this.failure;
			}
			this.connected.add(target);
		}

		@Override
		public void disconnect() {
			this.disconnects++;
		}

		@Override
		public String getObservedContainerName() {
			return null;
		}
	}

	private static final class FakeView implements ContainerManagerViewServices {
		final List<String> reports = new ArrayList<>();

		@Override
		public void connectionSucceeded(final String containerName) {
			this.reports.add("succeeded " + containerName);
		}

		@Override
		public void connectionFailed(final String reason) {
			this.reports.add("failed " + reason);
		}

		@Override
		public void showConnectionError(final String containerName, final String reason) {
			this.reports.add("error " + containerName + " | " + reason);
		}

		@Override
		public void containerStopped(final String containerName) {
			this.reports.add("stopped " + containerName);
		}

		@Override
		public void containerDisposed(final String containerName) {
			this.reports.add("disposed " + containerName);
		}
	}

	private final List<String> queues = new ArrayList<>();
	private final List<Runnable> tasks = new ArrayList<>();

	private boolean savedFlowsOnConnect;

	private FakeActionProviderServices actions;
	private FakeControl control;
	private FakeView view;
	private ContainerManagerInteractionImpl interaction;
	private ManagedContainerEvents events;
	private CovalentContainer first;
	private CovalentContainer second;

	private CapturingLogHandler containerManagerInteractionLogs;

	@Before
	public void setUp() throws Exception {
		this.containerManagerInteractionLogs = CapturingLogHandler.install(ContainerManagerInteractionImpl.class);
		this.savedFlowsOnConnect = SupervisorSettings.getBoolean(SupervisorSettings.FLOWS_ON_CONNECT, false);
		SupervisorSettings.putBoolean(SupervisorSettings.FLOWS_ON_CONNECT, false);
		System.setProperty(SupervisionExtension.DEFAULT_CONTAINER_PROPERTY, FIRST);
		this.first = Covalent.managedContainer(FIRST);
		this.second = Covalent.managedContainer(SECOND);

		this.actions = new FakeActionProviderServices();
		this.control = new FakeControl();
		this.view = new FakeView();
		AsyncServices async = (AsyncServices) Proxy.newProxyInstance(AsyncServices.class.getClassLoader(),
				new Class<?>[] { AsyncServices.class }, (proxy, method, args) -> {
					if (method.getName().equals("fireAndForget") && args.length == 2) {
						this.queues.add((String) args[0]);
						this.tasks.add((Runnable) args[1]);
						return null;
					}
					throw new UnsupportedOperationException(method.getName());
				});
		this.interaction = new ContainerManagerInteractionImpl();
		inject("actionManagerServices", this.actions);
		inject("useContainerManagerViewServices", this.view);
		inject("observedContainerControl", this.control);
		inject("useAsyncServices", async);
		this.events = (ManagedContainerEvents) field("managedContainerObserver");
	}

	@After
	public void tearDown() {
		this.interaction.unregisterActionsAndDisconnect();
		System.clearProperty(SupervisionExtension.DEFAULT_CONTAINER_PROPERTY);
		SupervisorSettings.putBoolean(SupervisorSettings.FLOWS_ON_CONNECT, this.savedFlowsOnConnect);
		Covalent.disposeManagedContainer(FIRST);
		Covalent.disposeManagedContainer(SECOND);
		this.containerManagerInteractionLogs.restore();
	}

	private void inject(final String fieldName, final Object value) throws Exception {
		Field field = ContainerManagerInteractionImpl.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(this.interaction, value);
	}

	private Object field(final String fieldName) throws Exception {
		Field field = ContainerManagerInteractionImpl.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		return field.get(this.interaction);
	}

	@Test
	public void activationRegistersTheThreeActionsAndConnectsToTheDefaultContainer() {
		this.interaction.registerActionsAndConnectToDefault();

		Assert.assertEquals(Set.of(ContainerManagerActionKey.values()), this.actions.registered);
		Assert.assertEquals(List.of(this.first), this.control.connected);
		Assert.assertEquals(List.of("succeeded " + FIRST), this.view.reports);
	}

	@Test
	public void anUnknownDefaultContainerIsReportedAndNothingIsConnected() {
		System.setProperty(SupervisionExtension.DEFAULT_CONTAINER_PROPERTY, "cm-interaction-tests-unknown");

		this.interaction.registerActionsAndConnectToDefault();

		Assert.assertEquals(List.of("failed Unknown managed container: cm-interaction-tests-unknown"),
				this.view.reports);
		Assert.assertTrue(this.control.connected.isEmpty());
	}

	@Test
	public void connectSwitchesTheObservedContainer() {
		this.interaction.registerActionsAndConnectToDefault();

		this.actions.fire(ContainerManagerActionKey.Connect, SECOND);

		Assert.assertEquals(List.of(this.first, this.second), this.control.connected);
		Assert.assertEquals(List.of("succeeded " + FIRST, "succeeded " + SECOND), this.view.reports);
	}

	@Test
	public void aFailedConnectionIsReportedWithItsReason() {
		this.interaction.registerActionsAndConnectToDefault();
		this.control.failure = new IllegalArgumentException("not managed");

		this.actions.fire(ContainerManagerActionKey.Connect, SECOND);

		int last = this.view.reports.size() - 1;
		Assert.assertEquals("failed not managed", this.view.reports.get(last - 1));
		Assert.assertEquals("error " + SECOND + " | not managed", this.view.reports.get(last));
		Assert.assertEquals(List.of("Failed to connect to managed container cm-interaction-tests-second"), this.containerManagerInteractionLogs.messages(Level.WARNING));
	}

	@Test
	public void aRequestedConnectionToAnUnknownContainerIsShownToTheUser() {
		this.interaction.registerActionsAndConnectToDefault();

		this.actions.fire(ContainerManagerActionKey.Connect, "cm-interaction-tests-unknown");

		int last = this.view.reports.size() - 1;
		Assert.assertEquals("failed Unknown managed container: cm-interaction-tests-unknown",
				this.view.reports.get(last - 1));
		String shown = this.view.reports.get(last);
		Assert.assertTrue(shown, shown.startsWith("error cm-interaction-tests-unknown | "));
		Assert.assertFalse(shown, shown.contains("Unknown managed container"));
	}

	@Test
	public void aConnectionFailureAtStartIsNotShownToTheUser() {
		this.control.failure = new IllegalArgumentException("not managed");

		this.interaction.registerActionsAndConnectToDefault();

		Assert.assertEquals(List.of("failed not managed"), this.view.reports);
	}

	@Test
	public void flowsAreOffOnConnectionByDefault() {
		this.interaction.registerActionsAndConnectToDefault();

		Assert.assertFalse(this.first.getParameters().isInstrumented());
		Assert.assertEquals(List.of(false), this.actions.flowsValues);
	}

	@Test
	public void flowsAreOnWhenConnectingOnceTheirDefaultIsSwitchedOn() {
		this.interaction.registerActionsAndConnectToDefault();
		this.actions.fire(ContainerManagerActionKey.SetFlowsEnabledByDefault, true);

		this.actions.fire(ContainerManagerActionKey.Connect, SECOND);

		Assert.assertTrue(this.second.getParameters().isInstrumented());
		Assert.assertEquals(true, this.actions.flowsValues.get(this.actions.flowsValues.size() - 1));
	}

	@Test
	public void setFlowsEnabledInstrumentsTheObservedContainerAndPublishesTheValue() {
		this.interaction.registerActionsAndConnectToDefault();

		this.actions.fire(ContainerManagerActionKey.SetFlowsEnabled, true);
		Assert.assertTrue(this.first.getParameters().isInstrumented());
		this.actions.fire(ContainerManagerActionKey.SetFlowsEnabled, false);

		Assert.assertFalse(this.first.getParameters().isInstrumented());
		Assert.assertEquals(List.of(false, true, false), this.actions.flowsValues);
	}

	@Test
	public void switchingContainerLeavesThePreviousOneUninstrumented() {
		this.interaction.registerActionsAndConnectToDefault();
		this.actions.fire(ContainerManagerActionKey.SetFlowsEnabled, true);

		this.actions.fire(ContainerManagerActionKey.Connect, SECOND);

		Assert.assertFalse(this.first.getParameters().isInstrumented());
	}

	@Test
	public void theObservedContainerStoppingIsLeftAndReported() {
		this.interaction.registerActionsAndConnectToDefault();
		this.actions.fire(ContainerManagerActionKey.SetFlowsEnabled, true);

		this.events.onContainerStopping(FIRST);

		Assert.assertEquals(1, this.control.disconnects);
		Assert.assertFalse(this.first.getParameters().isInstrumented());
		Assert.assertEquals("stopped " + FIRST, this.view.reports.get(this.view.reports.size() - 1));
	}

	@Test
	public void theObservedContainerBeingDisposedIsLeftAndReported() {
		this.interaction.registerActionsAndConnectToDefault();

		this.events.onContainerDisposing(FIRST);

		Assert.assertEquals(1, this.control.disconnects);
		Assert.assertEquals("disposed " + FIRST, this.view.reports.get(this.view.reports.size() - 1));
	}

	@Test
	public void anotherContainerStoppingIsNotOfConcern() {
		this.interaction.registerActionsAndConnectToDefault();

		this.events.onContainerStopping(SECOND);
		this.events.onContainerDisposing(SECOND);

		Assert.assertEquals(0, this.control.disconnects);
		Assert.assertEquals(List.of("succeeded " + FIRST), this.view.reports);
	}

	@Test
	public void aStoppedThenStartedContainerIsConnectedAgainOnTheConnectionQueue() {
		this.interaction.registerActionsAndConnectToDefault();
		this.events.onContainerStopping(FIRST);

		this.events.onContainerStarted(FIRST, null);

		Assert.assertEquals(List.of(QUEUE), this.queues);
		Assert.assertEquals(1, this.control.connected.size());
		this.tasks.get(0).run();
		Assert.assertEquals(List.of(this.first, this.first), this.control.connected);
		Assert.assertEquals("succeeded " + FIRST, this.view.reports.get(this.view.reports.size() - 1));
	}

	@Test
	public void aRecreatedContainerIsConnectedAgainToo() {
		this.interaction.registerActionsAndConnectToDefault();
		this.events.onContainerDisposing(FIRST);

		this.events.onContainerCreated(FIRST);

		Assert.assertEquals(1, this.tasks.size());
	}

	@Test
	public void nothingIsReconnectedWhileConnectedOrForAnotherName() {
		this.interaction.registerActionsAndConnectToDefault();

		this.events.onContainerStarted(FIRST, null);
		this.events.onContainerStopping(FIRST);
		this.events.onContainerStarted(SECOND, null);

		Assert.assertTrue(this.tasks.isEmpty());
	}

	@Test
	public void aPendingReconnectionDoesNothingOnceTheContainerIsConnectedAgainOrTheToolPassivated() {
		this.interaction.registerActionsAndConnectToDefault();
		this.events.onContainerStopping(FIRST);
		this.events.onContainerStarted(FIRST, null);
		this.events.onContainerCreated(FIRST);

		this.tasks.get(0).run();
		this.tasks.get(1).run();

		Assert.assertEquals(2, this.control.connected.size());

		this.events.onContainerStopping(FIRST);
		this.events.onContainerStarted(FIRST, null);
		this.interaction.unregisterActionsAndDisconnect();
		this.tasks.get(2).run();

		Assert.assertEquals(2, this.control.connected.size());
	}

	@Test
	public void passivationUnregistersTheActionsAndLeavesTheContainerUninstrumentedAndDisconnected() {
		this.interaction.registerActionsAndConnectToDefault();
		this.actions.fire(ContainerManagerActionKey.SetFlowsEnabled, true);

		this.interaction.unregisterActionsAndDisconnect();

		Assert.assertEquals(Set.of(ContainerManagerActionKey.values()), new HashSet<>(this.actions.unregistered));
		Assert.assertFalse(this.first.getParameters().isInstrumented());
		Assert.assertEquals(1, this.control.disconnects);
	}

	@Test
	public void passivationToleratesAnActionManagerAlreadyPassivated() {
		this.interaction.registerActionsAndConnectToDefault();
		this.actions.failOnUnregister = true;

		this.interaction.unregisterActionsAndDisconnect();

		Assert.assertEquals(1, this.control.disconnects);
	}

	@Test
	public void afterPassivationTheLifecycleOfContainersIsNoLongerFollowed() {
		this.interaction.registerActionsAndConnectToDefault();
		this.interaction.unregisterActionsAndDisconnect();
		this.view.reports.clear();
		this.first.start();

		this.first.stop();

		Assert.assertTrue(this.view.reports.isEmpty());
	}

	@Test
	public void aFailedReconnectionToTheSameContainerLeavesItUninstrumented() {
		this.interaction.registerActionsAndConnectToDefault();
		this.actions.fire(ContainerManagerActionKey.SetFlowsEnabled, true);
		this.control.failure = new IllegalStateException("boom");

		this.actions.fire(ContainerManagerActionKey.Connect, FIRST);

		Assert.assertFalse(this.first.getParameters().isInstrumented());
		Assert.assertEquals(List.of("Failed to connect to managed container cm-interaction-tests-first"), this.containerManagerInteractionLogs.messages(Level.WARNING));
	}

	@Test
	public void aFailureWithoutMessageIsReportedWithItsClassName() {
		this.interaction.registerActionsAndConnectToDefault();
		this.control.failure = new IllegalStateException();

		this.actions.fire(ContainerManagerActionKey.Connect, SECOND);

		int last = this.view.reports.size() - 1;
		Assert.assertEquals("failed java.lang.IllegalStateException", this.view.reports.get(last - 1));
		Assert.assertEquals("error " + SECOND + " | IllegalStateException", this.view.reports.get(last));
		Assert.assertEquals(List.of("Failed to connect to managed container cm-interaction-tests-second"), this.containerManagerInteractionLogs.messages(Level.WARNING));
	}
}
