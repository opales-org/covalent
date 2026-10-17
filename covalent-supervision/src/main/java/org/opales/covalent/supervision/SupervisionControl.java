/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.InstanceAlreadyExistsException;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

/**
 * Summons the supervisor on a running application, for the keyboard shortcut and for the JMX operation alike, and
 * reports its state. A summon starts the supervisor the first time and shows its window again afterwards; where
 * nothing can be displayed it is refused and the state says why, without any effect on the application.
 */
public final class SupervisionControl implements SupervisionControlMBean {

	/** The name of the MBean in the platform MBean server. */
	static final ObjectName OBJECT_NAME = objectName();

	private static final Logger log = Logger.getLogger(SupervisionControl.class.getName());

	/** What the control needs from the JVM it runs in. */
	interface Host {

		/**
		 * Tells whether a window can be displayed.
		 *
		 * @return {@code false} on a machine without a display
		 */
		boolean isDisplayAvailable();

		/**
		 * Runs an action on the JavaFX application thread, starting the JavaFX toolkit if no one has.
		 *
		 * @param action the action to run
		 */
		void runOnFxThread(Runnable action);

		/** Deploys and starts the supervisor, whose window opens. */
		void startSupervisor();

		/** Shows the window of the started supervisor again. */
		void showWindow();

		/**
		 * Tells whether the supervisor started the JavaFX toolkit and must stop it.
		 *
		 * @return {@code true} when no one else uses the toolkit
		 */
		boolean ownsToolkit();

		/** Stops the JavaFX toolkit. */
		void exitToolkit();
	}

	private final Host host;

	/** Set on the JavaFX thread once the supervisor has been started. */
	private volatile boolean started;

	/** The reason the supervisor cannot be shown, {@code null} while nothing prevents it. */
	private volatile String unavailableReason;

	SupervisionControl(final Host host) {
		this.host = host;
	}

	private static ObjectName objectName() {
		try {
			return new ObjectName("org.opales.covalent:type=Supervision");
		} catch (MalformedObjectNameException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Registers a control as the supervision MBean.
	 *
	 * @param server  the MBean server
	 * @param control the control to register
	 * @return {@code true} when it is registered, {@code false} when the name is already taken or the registration
	 *         failed; the failure is logged and never reaches the application
	 */
	static boolean register(final MBeanServer server, final SupervisionControl control) {
		try {
			server.registerMBean(control, OBJECT_NAME);
			return true;
		} catch (InstanceAlreadyExistsException e) {
			return false;
		} catch (JMException | RuntimeException e) {
			log.log(Level.WARNING, "The supervision MBean could not be registered.", e);
			return false;
		}
	}

	@Override
	public String getState() {
		String reason = this.unavailableReason;
		if (reason != null) {
			return "UNAVAILABLE: " + reason;
		}
		return this.started ? "STARTED" : "DORMANT";
	}

	@Override
	public void openSupervisor() {
		if (!this.host.isDisplayAvailable()) {
			this.refuse("no display available", null);
			return;
		}
		try {
			this.host.runOnFxThread(this::summonOnFxThread);
		} catch (RuntimeException e) {
			this.refuse(describe(e), e);
		}
	}

	/**
	 * Stops the JavaFX toolkit the supervisor started, which the application no longer needs once it is disposed.
	 * Does nothing when the toolkit belongs to the application.
	 */
	void onApplicationDisposed() {
		try {
			if (this.host.ownsToolkit()) {
				this.host.exitToolkit();
			}
		} catch (IllegalStateException e) {
			// The toolkit is already stopped.
		}
	}

	private void summonOnFxThread() {
		try {
			if (this.started) {
				this.host.showWindow();
			} else {
				this.host.startSupervisor();
				this.started = true;
			}
			this.unavailableReason = null;
		} catch (RuntimeException e) {
			this.refuse(describe(e), e);
		}
	}

	private void refuse(final String reason, final Throwable cause) {
		this.unavailableReason = reason;
		log.log(Level.WARNING, "The supervisor cannot be shown: {0}", reason);
		if (cause != null) {
			log.log(Level.FINE, "Cause of the refused summon.", cause);
		}
	}

	private static String describe(final RuntimeException failure) {
		String message = failure.getMessage();
		return message != null ? message : failure.getClass().getSimpleName();
	}
}
