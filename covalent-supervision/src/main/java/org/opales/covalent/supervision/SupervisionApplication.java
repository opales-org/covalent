/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision;

import java.awt.GraphicsEnvironment;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opales.covalent.Covalent;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.raw.RawComponentsServices;
import org.opales.covalent.hmi.action.ActionConsumerServices;
import org.opales.covalent.supervision.cockpit.impl.CockpitImpl;
import org.opales.covalent.supervision.componentdiagram.impl.ComponentDiagramImpl;
import org.opales.covalent.supervision.containermanager.impl.ContainerManagerImpl;
import org.opales.covalent.supervision.containermanager.raw.ObservedContainerBridge;
import org.opales.covalent.supervision.dependencymanagement.impl.DependencyManagementImpl;
import org.opales.covalent.supervision.diagrampersistence.impl.DiagramPersistenceImpl;
import org.opales.covalent.supervision.exclusionpolicy.impl.ExclusionPolicyImpl;
import org.opales.covalent.supervision.stereotypepolicy.impl.StereotypePolicyImpl;
import org.opales.covalent.supervision.sequencediagram.impl.SequenceDiagramImpl;
import org.opales.covalent.supervision.supervisionhmi.impl.SupervisionHmiImpl;
import org.opales.covalent.supervision.supervisionhmi.interaction.SupervisionHmiActionKey;
import org.opales.covalent.supervision.threads.impl.ThreadsImpl;
import org.opales.covalent.hmi.impl.HmiCoreImpl;

import javafx.application.Platform;

/**
 * Entry point for the supervision tool's own dedicated container - shared
 * by two callers: {@link #main(String[])}, a plain standalone launch, and
 * {@link SupervisionExtension}, the extension with the identifier {@code component-viewer}
 * ({@code -Dcovalent.extensions=component-viewer}). Either way, this tool
 * runs in its own unmanaged container ({@link Covalent#container()}), never
 * inside the container it ends up observing - {@code SupervisionHmi}/
 * {@code ContainerManager} deploy and start there exactly once per JVM
 * ({@link #ensureStarted()} is idempotent), then {@code
 * ContainerManagerInteractionImpl} auto-connects at its own
 * {@code @Activation} to whichever managed container name the
 * {@code covalent.supervision.defaultContainer} system property names,
 * defaulting to {@link Covalent#main}. {@link SupervisionExtension} sets
 * that property to the enabling container's own registered name (when it
 * has one) before calling {@link #ensureStarted()}, so supervision opens
 * on the container that enabled it.
 * <p>
 * {@link #summon()} is what the keyboard shortcut and the {@code openSupervisor} JMX operation do: the first call
 * starts the tool, the later ones show its window again.
 */
public final class SupervisionApplication {

	private static final AtomicBoolean started = new AtomicBoolean(false);

	/** Set when the extension stops JavaFX itself at the disposal of the application's container. */
	private static volatile boolean disposalTracked;

	/** Set once the supervisor started the JavaFX toolkit with the implicit exit disabled: only it can stop it. */
	private static volatile boolean toolkitKeptAlive;

	/** The way into the started supervisor's container, to show its window again. */
	private static volatile WindowPort windowPort;

	private static final SupervisionControl control = new SupervisionControl(new ApplicationHost());

	private SupervisionApplication() {
	}

	/**
	 * Starts the supervision tool if needed and shows its window.
	 *
	 * @see SupervisionControl#openSupervisor()
	 */
	static void summon() {
		control.openSupervisor();
	}

	/**
	 * The control behind {@link #summon()}, which the extension registers as an MBean.
	 *
	 * @return the control
	 */
	static SupervisionControl control() {
		return control;
	}

	/**
	 * Declares that the extension stops JavaFX when the application's container is disposed: the supervisor can then
	 * keep a JavaFX toolkit it started alive when its window closes, to show the window again.
	 */
	static void trackApplicationDisposal() {
		disposalTracked = true;
	}

	/**
	 * Starts the supervision tool in its own container, on the JavaFX application thread.
	 *
	 * @param args the command-line arguments, ignored
	 */
	public static void main(final String[] args) {
		runOnFxToolkit(SupervisionApplication::ensureStarted, true);
	}

	/**
	 * Deploys and starts {@code SupervisionHmi}/{@code ContainerManager} in
	 * their own dedicated container - a no-op on every call after the
	 * first successful one in this JVM. A start that fails leaves the supervisor not started, so that the next call
	 * tries again instead of finding a supervisor that does not exist.
	 */
	static void ensureStarted() {
		if (!started.compareAndSet(false, true)) {
			return;
		}
		try {
			startSupervisionContainer();
		} catch (RuntimeException | Error failure) {
			started.set(false);
			throw failure;
		}
	}

	private static void startSupervisionContainer() {
		CovalentContainer supervisionContainer = Covalent.container();

		// Deployment order (vertical start activates each root, and its full
		// composed tree, before moving to the next one):
		// 1. HmiCoreImpl (ActionManager) - a sibling deploy here, not composed
		//    by any one function, since a container's ActionProviderServices/
		//    ActionConsumerServices only resolve once one is deployed into it
		//    (every demo in this repository deploys its own the same way).
		// 2. ObservedContainerBridge - an ordinary @Implementation component
		//    (see its own class javadoc for the two-object split behind it),
		//    the sole provider/producer of every Observed*Services/
		//    Observed*Events port in this container, so it must activate
		//    before any of the functions below that consume them.
		// 3. ExclusionPolicyImpl - ComponentDiagram/SequenceDiagram/
		//    DependencyManagement each @AccessedParameters ExclusionParameters,
		//    whose sole provider is ExclusionPolicyControllerImpl; only
		//    activating the provider binds it, so it must activate before any
		//    of its three accessors.
		//    StereotypePolicyImpl follows it for the same reason: ComponentDiagram and
		//    SequenceDiagram each @AccessedParameters StereotypeParameters.
		// 4. ContainerManagerImpl - SupervisionHmiView reads
		//    ContainerManagerViewParameters (the selector widget) at its own
		//    @Activation to embed it in the shared toolbar, and its own
		//    @Activation is also what first calls ObservedContainerControl.connect(...),
		//    so the bridge holds real data by the time the functions
		//    below activate.
		// 5. SupervisionHmiImpl - provides PanelHostingServices, which every
		//    function's own View uses at its own @Activation to show its panel.
		// 6. DiagramPersistenceImpl before ComponentDiagramImpl - ComponentDiagram's
		//    own Controller calls DiagramPersistenceServices directly for its
		//    Save/Load buttons, the same direct-call mechanism it uses to reach
		//    SequenceDiagramServices.
		// 7. SequenceDiagramImpl before ComponentDiagramImpl - ComponentDiagram
		//    uses SequenceDiagramServices (the Record toggle).
		// 8. ThreadsImpl before CockpitImpl - the cockpit's overview shows a
		//    Threads tile, from the Threads model.
		// 9. CockpitImpl before ComponentDiagramImpl - the cockpit listens to the
		//    component diagram's live structure, published when that diagram first
		//    reads the observed container.
		// 10. DependencyManagementImpl - only needs ExclusionPolicy/the bridge's
		//    Observed* ports, satisfied by steps 2 and 3.
		try {
			supervisionContainer.deployComponents(HmiCoreImpl.class, ObservedContainerBridge.class, ExclusionPolicyImpl.class,
					StereotypePolicyImpl.class, ContainerManagerImpl.class, SupervisionHmiImpl.class, DiagramPersistenceImpl.class, SequenceDiagramImpl.class,
					ThreadsImpl.class, CockpitImpl.class, ComponentDiagramImpl.class, DependencyManagementImpl.class);
			supervisionContainer.start();
			WindowPort port = new WindowPort();
			port.connect(supervisionContainer.rcs());
			windowPort = port;
		} catch (RuntimeException | Error failure) {
			// The container that did not come up is not kept: the next attempt builds a new one.
			try {
				supervisionContainer.dispose();
			} catch (RuntimeException disposalFailure) {
				failure.addSuppressed(disposalFailure);
			}
			throw failure;
		}
	}

	/**
	 * Runs {@code action} on the JavaFX application thread, starting the toolkit first if nothing has started it yet
	 * in this JVM.
	 *
	 * @param action       the action to run once the toolkit is ready
	 * @param implicitExit whether the toolkit it starts stops with its last window; when {@code false}, closing the
	 *                     supervisor's window leaves the toolkit running so that the window can be shown again
	 */
	static void runOnFxToolkit(final Runnable action, final boolean implicitExit) {
		if (Platform.isFxApplicationThread()) {
			action.run();
			return;
		}
		try {
			Platform.startup(() -> {
				Platform.setImplicitExit(implicitExit);
				toolkitKeptAlive = !implicitExit;
				action.run();
			});
		} catch (IllegalStateException e) {
			// JavaFX toolkit already started elsewhere in this JVM - run directly on it.
			Platform.runLater(action);
		}
	}

	/** What the control needs from this JVM: the JavaFX toolkit, the display and the supervisor's container. */
	private static final class ApplicationHost implements SupervisionControl.Host {

		@Override
		public boolean isDisplayAvailable() {
			return !GraphicsEnvironment.isHeadless();
		}

		@Override
		public void runOnFxThread(final Runnable action) {
			runOnFxToolkit(action, !disposalTracked);
		}

		@Override
		public void startSupervisor() {
			ensureStarted();
		}

		@Override
		public void showWindow() {
			WindowPort port = windowPort;
			if (port == null) {
				throw new IllegalStateException("The supervisor is not started.");
			}
			port.showWindow();
		}

		@Override
		public boolean ownsToolkit() {
			return toolkitKeptAlive;
		}

		@Override
		public void exitToolkit() {
			Platform.exit();
		}
	}

	/**
	 * The only way from outside into the supervisor's container: a raw component that uses the
	 * {@code ActionConsumerServices} of the supervisor's own {@code ActionManager} to trigger
	 * {@link SupervisionHmiActionKey#ShowWindow}. Carries no Covalent annotation, like the delegate of
	 * {@link ObservedContainerBridge}.
	 */
	private static final class WindowPort {

		private volatile ActionConsumerServices actionServices;

		/** Registers the port as a raw component of the supervisor's container and opens its port on the actions. */
		void connect(final RawComponentsServices rcs) {
			rcs.registerComponent(this);
			this.actionServices = rcs.useServices(ActionConsumerServices.class);
		}

		/** Triggers the action that shows the supervisor's window again. */
		void showWindow() {
			this.actionServices.executeAction(SupervisionHmiActionKey.ShowWindow);
		}
	}
}
