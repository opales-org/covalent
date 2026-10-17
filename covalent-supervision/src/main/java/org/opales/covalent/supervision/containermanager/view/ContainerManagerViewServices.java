/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.containermanager.view;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Connection-result feedback, provided by {@code ContainerManagerView} and
 * used by {@code ContainerManagerInteraction}. {@code executeAction}
 * has no return value, so a failed connection attempt is pushed back here
 * instead of being returned.
 * <p>
 * All methods may be called from any thread: each one hands its work over to the JavaFX application thread.
 */
@Services
public interface ContainerManagerViewServices {

	/**
	 * Reports that the observed container is now {@code containerName}, so
	 * the view can show it as selected and connected.
	 *
	 * @param containerName the managed container name now observed
	 */
	void connectionSucceeded(String containerName);

	/**
	 * Reports that the last connection attempt failed, so the view can
	 * reflect it.
	 *
	 * @param reason a human-readable failure reason
	 */
	void connectionFailed(String reason);

	/**
	 * Tells the user that the connection they asked for failed, with the reason. It is not called for the connections
	 * made on their own, which fail without a word.
	 *
	 * @param containerName the managed container name the user asked to connect to
	 * @param reason        why the connection failed, in the supervisor's language where the supervisor wrote it
	 */
	void showConnectionError(String containerName, String reason);

	/**
	 * Reports that the observed container is stopped, so the view can show it
	 * as such until it is started again and connected to anew.
	 *
	 * @param containerName the managed container name observed until now
	 */
	void containerStopped(String containerName);

	/**
	 * Reports that the observed container is disposed, so the view can show it
	 * as such until a container with this name is created and connected to.
	 *
	 * @param containerName the managed container name observed until now
	 */
	void containerDisposed(String containerName);
}
