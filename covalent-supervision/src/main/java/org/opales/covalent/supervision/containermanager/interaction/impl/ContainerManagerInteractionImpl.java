/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.containermanager.interaction.impl;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.opales.covalent.Covalent;
import org.opales.covalent.ManagedContainerEvents;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.ShutdownReport;
import org.opales.covalent.container.StartupReport;
import org.opales.covalent.container.async.AsyncServices;
import org.opales.covalent.container.component.annotations.Implementation;
import org.opales.covalent.container.component.annotations.UsedServices;
import org.opales.covalent.container.lifecycle.Activation;
import org.opales.covalent.container.lifecycle.Passivation;
import org.opales.covalent.hmi.action.ActionProviderServices;
import org.opales.covalent.supervision.SupervisionExtension;
import org.opales.covalent.supervision.containermanager.interaction.ContainerManagerActionKey;
import org.opales.covalent.supervision.containermanager.interaction.ContainerManagerInteraction;
import org.opales.covalent.supervision.containermanager.raw.ObservedContainerControl;
import org.opales.covalent.supervision.containermanager.view.ContainerManagerViewServices;
import org.opales.covalent.supervision.supervisionhmi.SupervisorSettings;

/**
 * ContainerManager's Interaction Controller: registers
 * {@link ContainerManagerActionKey#Connect} with the HMI's
 * {@code ActionManager} at activation - {@code ContainerManagerView}'s
 * "Connect" button triggers it through
 * {@code ActionConsumerServices.executeAction(...)}, not by calling this
 * class directly.
 * <p>
 * The only component allowed to point {@code ObservedContainerBridge} at a
 * container (see its own class javadoc): {@code ComponentDiagram}, {@code SequenceDiagram} and
 * {@code DependencyManagement} are deployed once, forever, right here in the supervision
 * container - a "Connect" click never deploys or undeploys anything, it only reconfigures which
 * container the bridge forwards to.
 * <p>
 * Follows the observed container across its stops and restarts through
 * {@link ManagedContainerEvents}: it disconnects as soon as the container is
 * stopping or being disposed, and connects again, under the same name, once
 * it is started or created anew. Those notifications arrive on the thread
 * acting on the observed container, which holds its lifecycle lock: the
 * disconnection runs there, before the container tears its components down;
 * the connection is handed over to a supervision thread.
 */
@Implementation
public class ContainerManagerInteractionImpl implements ContainerManagerInteraction {

	/** Creates the Interaction Controller; the container injects the services it uses. */
	public ContainerManagerInteractionImpl() {
		// Nothing to initialize.
	}

	private static final Logger log = Logger.getLogger(ContainerManagerInteractionImpl.class.getName());

	@UsedServices
	private ActionProviderServices actionManagerServices;

	@UsedServices
	private ContainerManagerViewServices useContainerManagerViewServices;

	@UsedServices
	private ObservedContainerControl observedContainerControl;

	@UsedServices
	private AsyncServices useAsyncServices;

	/** Queue serializing the connections made on a lifecycle notification. */
	private static final String CONNECTION_QUEUE = "supervision-connection";

	/**
	 * Name of the managed container auto-connected to at this composite's
	 * own {@code @Activation}, exactly like a manual "Connect" click with
	 * that name; the user can pick a different managed container from the
	 * selector afterward. Read fresh on every activation from the
	 * {@value SupervisionExtension#DEFAULT_CONTAINER_PROPERTY} system
	 * property, defaulting to {@link Covalent#main} - {@link
	 * SupervisionExtension} sets it to the enabling container's own name
	 * before starting this tool, so enabling the extension opens the
	 * tool on that container. A failure here (unlikely, since
	 * {@code Covalent.main} always exists, but not impossible if it has
	 * nothing deployed yet, or if the property names an unknown container)
	 * surfaces the same way a manual connect failure would.
	 */
	private static String defaultContainerName() {
		return System.getProperty(SupervisionExtension.DEFAULT_CONTAINER_PROPERTY, "main");
	}

	private volatile CovalentContainer observedContainer;

	/**
	 * Name of the managed container last connected to, kept while it is
	 * stopped or disposed so that it is connected to again once started or
	 * created anew.
	 */
	private volatile String observedName;

	/** Serializes the connections, without ever being held by a notifying thread. */
	private final Object connectionLock = new Object();

	private final ManagedContainerEvents managedContainerObserver = new ManagedContainerObserver();

	/** Whether data flows are switched on when connecting to a container. */
	private volatile boolean flowsEnabledByDefault = SupervisorSettings.getBoolean(SupervisorSettings.FLOWS_ON_CONNECT, false);

	/** Whether data flows are currently switched on for the observed container. */
	private volatile boolean flowsEnabled = true;

	/**
	 * Registers the Connect, SetFlowsEnabled and SetFlowsEnabledByDefault actions, subscribes to the
	 * managed containers' lifecycle events, then connects to the default container.
	 */
	@Activation
	public void registerActionsAndConnectToDefault() {
		this.actionManagerServices.registerActionExecution(
				ContainerManagerActionKey.Connect, name -> this.connect(name, true), String.class);
		this.actionManagerServices.registerActionExecution(
				ContainerManagerActionKey.SetFlowsEnabled, this::setFlowsEnabled, Boolean.class);
		this.actionManagerServices.registerActionExecution(ContainerManagerActionKey.SetFlowsEnabledByDefault,
				enabled -> this.flowsEnabledByDefault = enabled.booleanValue(), Boolean.class);
		Covalent.subscribe(this.managedContainerObserver);
		this.connect(defaultContainerName(), false);
	}

	/**
	 * Data flows exist only while the observed container is instrumented:
	 * its {@code instrumented} parameter is dynamic, so this takes effect on
	 * the very next call. Off, no call or event is reported at all.
	 *
	 * @param enabled whether data flows are switched on
	 */
	private void setFlowsEnabled(final Boolean enabled) {
		this.flowsEnabled = enabled.booleanValue();
		if (this.observedContainer != null) {
			this.observedContainer.getParameters().setInstrumented(this.flowsEnabled);
		}
		this.actionManagerServices.setActionValue(ContainerManagerActionKey.SetFlowsEnabled, this.flowsEnabled);
	}

	/**
	 * Unsubscribes from the lifecycle events and unregisters the actions, then leaves the observed
	 * container uninstrumented and disconnects from it.
	 */
	@Passivation
	public void unregisterActionsAndDisconnect() {
		Covalent.unsubscribe(this.managedContainerObserver);
		try {
			this.actionManagerServices.unregisterActionExecution(ContainerManagerActionKey.Connect);
			this.actionManagerServices.unregisterActionExecution(ContainerManagerActionKey.SetFlowsEnabled);
			this.actionManagerServices.unregisterActionExecution(ContainerManagerActionKey.SetFlowsEnabledByDefault);
		} catch (IllegalStateException e) {
			// ActionManager already passivated as part of the same container-stop cascade.
		}
		this.stopFlows();
		this.observedContainerControl.disconnect();
		this.observedContainer = null;
		this.observedName = null;
	}

	/** Leaves the observed container uninstrumented, as it was before being observed. */
	private void stopFlows() {
		if (this.observedContainer != null) {
			this.observedContainer.getParameters().setInstrumented(false);
		}
	}

	/**
	 * Switches the observed container: {@link ObservedContainerControl}
	 * itself disconnects from whichever container was previously observed,
	 * if any, before connecting to the newly selected one.
	 *
	 * @param containerName the managed container name selected in the view
	 * @param requested     whether the user asked for this connection: only then is a failure shown to them, the
	 *                      automatic ones (the default container at start, a container that started again) are not
	 */
	private void connect(final String containerName, final boolean requested) {
		synchronized (this.connectionLock) {
			CovalentContainer targetContainer = Covalent.getManagedContainer(containerName);
			if (targetContainer == null) {
				this.useContainerManagerViewServices.connectionFailed("Unknown managed container: " + containerName);
				if (requested) {
					this.useContainerManagerViewServices.showConnectionError(containerName,
							ContainerManagerInteractionTexts.get("error.unknown"));
				}
				return;
			}
			this.observedName = containerName;
			try {
				if (targetContainer != this.observedContainer) {
					this.stopFlows();
				}
				this.observedContainerControl.connect(targetContainer);
				this.observedContainer = targetContainer;
				this.setFlowsEnabled(this.flowsEnabledByDefault);
				this.useContainerManagerViewServices.connectionSucceeded(containerName);
			} catch (RuntimeException e) {
				log.log(Level.WARNING, "Failed to connect to managed container " + containerName, e);
				this.stopFlows();
				this.observedContainer = null;
				this.useContainerManagerViewServices.connectionFailed(e.getMessage() != null ? e.getMessage() : e.getClass().getName());
				if (requested) {
					this.useContainerManagerViewServices.showConnectionError(containerName,
							e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage()
									: e.getClass().getSimpleName());
				}
			}
		}
	}

	/**
	 * Connects again, on a supervision thread, to the container last observed
	 * under this name, if it is not observed right now.
	 */
	private void reconnectLater(final String containerName) {
		if (containerName.equals(this.observedName) && this.observedContainer == null) {
			this.useAsyncServices.fireAndForget(CONNECTION_QUEUE, () -> {
				if (containerName.equals(this.observedName) && this.observedContainer == null) {
					this.connect(containerName, false);
				}
			});
		}
	}

	/**
	 * Disconnects from the observed container, on the thread acting on it, if
	 * it is the one named: the view shows it as stopped or disposed.
	 */
	private void disconnectFrom(final String containerName, final boolean disposed) {
		CovalentContainer container = this.observedContainer;
		if (container == null || !containerName.equals(this.observedName)) {
			return;
		}
		this.stopFlows();
		this.observedContainerControl.disconnect();
		this.observedContainer = null;
		if (disposed) {
			this.useContainerManagerViewServices.containerDisposed(containerName);
		} else {
			this.useContainerManagerViewServices.containerStopped(containerName);
		}
	}

	/** Follows the lifecycle of the observed managed container. */
	private final class ManagedContainerObserver implements ManagedContainerEvents {

		@Override
		public void onContainerCreated(final String name) {
			ContainerManagerInteractionImpl.this.reconnectLater(name);
		}

		@Override
		public void onContainerStarting(final String name) {
		}

		@Override
		public void onContainerStarted(final String name, final StartupReport report) {
			ContainerManagerInteractionImpl.this.reconnectLater(name);
		}

		@Override
		public void onContainerStartFailed(final String name, final Throwable failure) {
		}

		@Override
		public void onContainerStopping(final String name) {
			ContainerManagerInteractionImpl.this.disconnectFrom(name, false);
		}

		@Override
		public void onContainerStopped(final String name, final ShutdownReport report) {
		}

		@Override
		public void onContainerDisposing(final String name) {
			ContainerManagerInteractionImpl.this.disconnectFrom(name, true);
		}

		@Override
		public void onContainerDisposed(final String name) {
		}
	}
}
