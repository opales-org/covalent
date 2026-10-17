/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.hmi.action;

import org.opales.covalent.container.component.annotations.Component;

/**
 * Component type for JavaFX action registration, availability, and execution.
 * <p>
 * Thread contract: the action manager keeps its state in collections that are not synchronized, so it is used by one
 * thread at a time; the callers see to that. The methods that bind a widget to an action
 * ({@code bindActionAvailability} and {@code unbindActionAvailability}) act on JavaFX nodes, and the availability
 * of a bound widget changes on the thread that changes the action's availability, so a displayed widget is bound,
 * and its action made available or not, from the JavaFX application thread.
 */
@Component
public interface ActionManager {

}
