/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.hmi.action;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Observes action executions generically, independently of any specific
 * action's provider or consumer - a third party that wants to know
 * whenever any action runs.
 */
@Services
public interface ActionObservationServices {

	/**
	 * Adds an action event listener.
	 *
	 * @param actionEventsSubscribe listener to add
	 */
	void addActionEventsListener(ActionEvents actionEventsSubscribe);

	/**
	 * Removes an action event listener.
	 *
	 * @param actionEventsSubscribe listener to remove
	 */
	void removeActionEventsListener(ActionEvents actionEventsSubscribe);

}
