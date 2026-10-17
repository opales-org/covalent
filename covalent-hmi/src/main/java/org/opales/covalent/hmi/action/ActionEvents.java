/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.hmi.action;

/**
 * Receives action execution notifications.
 */
public interface ActionEvents {

	/**
	 * Notifies that an action is about to be executed: the notification is sent just before the action's
	 * implementation runs.
	 *
	 * @param actionKey         action identifier
	 * @param actionCaller      component type that requested the action
	 * @param actionImplementer component type that provided the action
	 * @param inputData         input data passed to the action
	 */
	void onActionTriggered(
		ActionKey actionKey,
		Class<?> actionCaller,
		Class<?> actionImplementer,
		Object... inputData);

}
