/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.supervisionhmi.view;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.hmi.action.ActionKey;

/**
 * Shared-toolbar bind/unbind commands and the window command, provided by {@code SupervisionHmiView}
 * and used by {@code SupervisionHmiInteraction}. Only
 * {@code SupervisionHmiView} holds the toolbar widgets' JavaFX {@code Node}
 * references and calls {@code ActionConsumerServices.bindActionAvailability}/
 * {@code unbindActionAvailability} on them - the Interaction only ever
 * deals with plain {@link ToolbarFunction}/{@link ActionKey} values, never
 * JavaFX itself.
 * <p>
 * All methods may be called from any thread: each one hands its work over to the JavaFX application thread.
 */
@Services
public interface SupervisionHmiViewServices {

	/**
	 * Binds a toolbar widget's availability to an ActionKey.
	 *
	 * @param function the toolbar widget kind to bind
	 * @param key      the ActionKey to bind it to
	 */
	void bindToolbarFunction(ToolbarFunction function, ActionKey key);

	/**
	 * Unbinds a toolbar widget's availability from an ActionKey.
	 *
	 * @param function the toolbar widget kind to unbind
	 * @param key      the ActionKey to unbind it from
	 */
	void unbindToolbarFunction(ToolbarFunction function, ActionKey key);

	/**
	 * Shows the supervisor's window again: opens it if it was closed, restores it if it was minimized and brings it
	 * in front of the other windows.
	 */
	void showWindow();
}
