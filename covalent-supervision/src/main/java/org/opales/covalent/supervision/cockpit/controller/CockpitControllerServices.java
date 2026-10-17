/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.cockpit.controller;

import org.opales.covalent.container.component.annotations.Services;
import org.opales.covalent.supervision.cockpit.model.LifecycleCommand;

/**
 * The console's commands. Each one runs in the background, one at a time, in the order asked;
 * what it does and how it ends goes to the console's log.
 */
@Services
public interface CockpitControllerServices {

	/**
	 * Applies a lifecycle transition to one component or composite of the observed container.
	 *
	 * @param command the transition
	 * @param componentType the component or composite it applies to
	 */
	void execute(LifecycleCommand command, Class<?> componentType);

	/**
	 * Applies a transition to every top-level component of the observed container, providers
	 * first when activating, their users first otherwise.
	 *
	 * @param command {@link LifecycleCommand#ACTIVATE}, {@link LifecycleCommand#PASSIVATE} or
	 *                {@link LifecycleCommand#REMOVE}, which passivates first what is active
	 */
	void executeOnContainer(LifecycleCommand command);

	/**
	 * Shows a composite's children as the console's slices, or, when it holds no composite,
	 * the level where it is itself a slice.
	 *
	 * @param compositeType the composite to enter
	 */
	void enterComposite(Class<?> compositeType);

	/**
	 * Goes back up to a level of the console's path.
	 *
	 * @param depth how many of the composites entered to keep, {@code 0} for the top
	 */
	void showConsoleLevel(int depth);
}
