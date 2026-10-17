/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.hmi.action;

import java.util.function.Consumer;

import org.opales.covalent.container.component.annotations.Services;

import javafx.scene.Node;

/**
 * Triggers actions and reflects their state in a widget. Called by
 * whoever triggers an action or displays its state - a View, a shared
 * toolbar - never by the action's own provider.
 */
@Services
public interface ActionConsumerServices {

	/**
	 * Executes a no-argument action.
	 *
	 * @param anActionKey action identifier
	 * @return {@code true} when an available action implementation was executed
	 */
	public boolean executeAction(final ActionKey anActionKey);

	/**
	 * Executes a one-argument action.
	 *
	 * @param <T>         input data type
	 * @param anActionKey action identifier
	 * @param anInputData input data passed to the action
	 * @return {@code true} when an available action implementation was executed
	 */
	public <T> boolean executeAction(final ActionKey anActionKey, final T anInputData);

	/**
	 * Executes a two-argument action.
	 *
	 * @param <T1>         first input data type
	 * @param <T2>         second input data type
	 * @param anActionKey  action identifier
	 * @param anInputData1 first input data passed to the action
	 * @param anInputData2 second input data passed to the action
	 * @return {@code true} when an available action implementation was executed
	 */
	public <T1, T2> boolean executeAction(final ActionKey anActionKey, final T1 anInputData1, final T2 anInputData2);

	/**
	 * Binds the disabled state of a JavaFX node to action availability.
	 * A node follows a single action: binding it again to the same action
	 * changes nothing, and binding it to another action while it is still
	 * bound is rejected. While bound, the node's disabled state belongs to
	 * the action: it is disabled whenever the action is unavailable and
	 * enabled whenever the action becomes available, so any other condition
	 * for disabling the node is expressed through the action's availability.
	 *
	 * @param anActionKey action identifier
	 * @param aNode       JavaFX node bound to action availability
	 * @throws IllegalStateException if the node is already bound to another
	 *                               action
	 */
	public void bindActionAvailability(final ActionKey anActionKey, final Node aNode);

	/**
	 * Removes a binding previously established by
	 * {@link #bindActionAvailability(ActionKey, Node)}, so the node stops
	 * following that action's availability - needed before rebinding the
	 * same node to a different action's {@link ActionKey} (a shared
	 * toolbar switching tabs, for example).
	 *
	 * @param anActionKey action identifier
	 * @param aNode       JavaFX node to unbind from action availability
	 */
	public void unbindActionAvailability(final ActionKey anActionKey, final Node aNode);

	/**
	 * Subscribes to an action's value, as reported by its own provider
	 * through {@link ActionProviderServices#setActionValue(ActionKey, Object)}.
	 * Unlike availability, a value has no single fixed widget property to
	 * write to, so the caller supplies the consumer that knows how to
	 * apply it to whatever it owns (for example, a slider's value).
	 * Invoked immediately with the action's current value, if one is
	 * already known.
	 *
	 * @param anActionKey    action identifier
	 * @param aValueConsumer callback invoked with the action's value,
	 *                       immediately and every time it changes
	 */
	public void bindActionValue(final ActionKey anActionKey, final Consumer<Object> aValueConsumer);

	/**
	 * Removes a binding previously established by
	 * {@link #bindActionValue(ActionKey, Consumer)} - the same consumer
	 * reference passed there identifies which binding to remove.
	 *
	 * @param anActionKey    action identifier
	 * @param aValueConsumer the same callback reference passed to
	 *                       {@link #bindActionValue(ActionKey, Consumer)}
	 */
	public void unbindActionValue(final ActionKey anActionKey, final Consumer<Object> aValueConsumer);

}
