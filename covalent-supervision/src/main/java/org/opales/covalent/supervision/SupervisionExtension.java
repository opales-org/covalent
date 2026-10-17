/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision;

import java.awt.GraphicsEnvironment;
import java.lang.management.ManagementFactory;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

import org.opales.covalent.Covalent;
import org.opales.covalent.ManagedContainerEvents;
import org.opales.covalent.container.CovalentContainer;
import org.opales.covalent.container.ShutdownReport;
import org.opales.covalent.container.StartupReport;
import org.opales.covalent.container.extension.CovalentExtension;

/**
 * The extension with the identifier {@code component-viewer}
 * ({@code -Dcovalent.extensions=component-viewer}) that makes the
 * supervision tool available to a container. It does not deploy
 * anything into the container that enables it - the supervision tool runs
 * as managed Covalent composites of its own ({@code SupervisionHmi}/
 * {@code ContainerManager}), which live in their own dedicated container
 * (see {@link SupervisionApplication}), reaching into an observed
 * container from outside.
 * <p>
 * Once the container has started, the extension makes the supervisor available without starting it: a keyboard
 * shortcut (Ctrl+Shift+F12 by default, see {@link #SHORTCUT_PROPERTY}) in the
 * JavaFX and in the Swing/AWT windows of the application, and the {@code org.opales.covalent:type=Supervision} MBean,
 * both summoning the tool - which then points its default connection at the container that enabled the extension.
 * With {@code -Dcovalent.supervision.startup=open} the tool also opens, once, as soon as the first container has started.
 */
public final class SupervisionExtension implements CovalentExtension {

	/** Creates the extension, instantiated by the extension mechanism. */
	public SupervisionExtension() {
		// Nothing to initialize.
	}

	/** Identifier of this extension, the value given to {@code -Dcovalent.extensions}. */
	public static final String EXTENSION_ID = "component-viewer";

	/**
	 * Read by {@code ContainerManagerInteractionImpl} at its own
	 * {@code @Activation} to pick which managed container to auto-connect
	 * to; defaults to {@link Covalent#main} when unset.
	 */
	public static final String DEFAULT_CONTAINER_PROPERTY = "covalent.supervision.defaultContainer";

	/**
	 * System property choosing what happens when the first container has started: {@code dormant} (the default, also
	 * the meaning of any other value) leaves the supervisor to the shortcut and to the MBean, {@code open} also opens
	 * it at once, once.
	 */
	public static final String STARTUP_PROPERTY = "covalent.supervision.startup";

	/** Value of {@link #STARTUP_PROPERTY} that opens the supervisor as soon as the first container has started. */
	private static final String OPEN_AT_START = "open";

	/**
	 * System property redefining the key combination that summons the supervisor, in the syntax of
	 * {@code javafx.scene.input.KeyCombination}, for example {@code Ctrl+Shift+F11}; {@code none} removes the
	 * shortcut.
	 */
	public static final String SHORTCUT_PROPERTY = "covalent.supervision.shortcut";

	/** The shortcut and the MBean are installed once per JVM. */
	private static final AtomicBoolean installed = new AtomicBoolean(false);

	/** The disposal tracking is set up once per JVM, for the first container that has a name. */
	private static final AtomicBoolean disposalTracked = new AtomicBoolean(false);

	@Override
	public String getId() {
		return EXTENSION_ID;
	}

	@Override
	public void onContainerStarted(final CovalentContainer container) {
		String containerName = findRegisteredName(container);
		if (containerName != null && System.getProperty(DEFAULT_CONTAINER_PROPERTY) == null) {
			System.setProperty(DEFAULT_CONTAINER_PROPERTY, containerName);
		}
		boolean firstStart = installed.compareAndSet(false, true);
		if (firstStart) {
			install();
		}
		trackDisposal(containerName);
		if (firstStart && OPEN_AT_START.equalsIgnoreCase(System.getProperty(STARTUP_PROPERTY, "").trim())) {
			SupervisionApplication.summon();
		}
	}

	/** Makes the supervisor available on demand: the MBean and the shortcut. */
	private static void install() {
		SupervisionControl control = SupervisionApplication.control();
		SupervisionControl.register(ManagementFactory.getPlatformMBeanServer(), control);
		SupervisionShortcut shortcut = SupervisionShortcut.of(System.getProperty(SHORTCUT_PROPERTY));
		JavaFxShortcutTrigger.install(shortcut, SupervisionApplication::summon);
		if (!GraphicsEnvironment.isHeadless()) {
			AwtShortcutTrigger.install(shortcut, SupervisionApplication::summon);
		}
	}

	/**
	 * Makes the end of JavaFX follow the disposal of the first container that has a name, the one that enabled
	 * the extension when it is registered. A container without a name, started before it, does not use this up.
	 *
	 * @param containerName the registered name of the container that has just started, {@code null} if it has none
	 * @return {@code true} when this call set the tracking up
	 */
	static boolean trackDisposal(final String containerName) {
		if (containerName == null || !disposalTracked.compareAndSet(false, true)) {
			return false;
		}
		SupervisionApplication.trackApplicationDisposal();
		Covalent.subscribe(new DisposalObserver(containerName, SupervisionApplication.control()::onApplicationDisposed));
		return true;
	}

	/**
	 * Looks up {@code container}'s own name in
	 * {@link Covalent#getManagedContainers()}. The result is {@code null} when it is
	 * not registered under a name (an unmanaged
	 * {@link Covalent#container()} instance), in which case the
	 * auto-connect default is left alone.
	 *
	 * @param container the container to look up
	 * @return its registered name, or {@code null} if it has none
	 */
	private static String findRegisteredName(final CovalentContainer container) {
		for (Entry<String, CovalentContainer> entry : Covalent.getManagedContainers().entrySet()) {
			if (entry.getValue() == container) {
				return entry.getKey();
			}
		}
		return null;
	}

	/** Reports the disposal of one managed container, and nothing else of the lifecycle of any container. */
	static final class DisposalObserver implements ManagedContainerEvents {

		private final String containerName;
		private final Runnable onDisposed;

		DisposalObserver(final String containerName, final Runnable onDisposed) {
			this.containerName = containerName;
			this.onDisposed = onDisposed;
		}

		@Override
		public void onContainerDisposed(final String name) {
			if (this.containerName.equals(name)) {
				this.onDisposed.run();
			}
		}

		@Override
		public void onContainerCreated(final String name) {
		}

		@Override
		public void onContainerStarting(final String name) {
		}

		@Override
		public void onContainerStarted(final String name, final StartupReport report) {
		}

		@Override
		public void onContainerStartFailed(final String name, final Throwable failure) {
		}

		@Override
		public void onContainerStopping(final String name) {
		}

		@Override
		public void onContainerStopped(final String name, final ShutdownReport report) {
		}

		@Override
		public void onContainerDisposing(final String name) {
		}
	}
}
