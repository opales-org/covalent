/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.hmi.action;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Registers and describes actions, on behalf of the function that owns
 * them. Called by the action's own provider, from its own
 * {@code @Activation}/{@code @Passivation} - the provider registers what
 * it can do and reports its own state (availability, value)
 * unconditionally, never knowing who (if anyone) is listening.
 */
@Services
public interface ActionProviderServices {

	/**
	 * Registers a one-argument action implementation.
	 *
	 * @param <T>               input data type
	 * @param anActionKey       action identifier
	 * @param anInteractionImpl action implementation
	 * @param anInputDataClass  input data class
	 */
	public <T> void registerActionExecution(
		final ActionKey anActionKey,
		final Consumer<T> anInteractionImpl,
		final Class<T> anInputDataClass);

	/**
	 * Registers a two-argument action implementation.
	 *
	 * @param <T1>              first input data type
	 * @param <T2>              second input data type
	 * @param anActionKey       action identifier
	 * @param anInteractionImpl action implementation
	 * @param anInputDataClass1 first input data class
	 * @param anInputDataClass2 second input data class
	 */
	public <T1, T2> void registerActionExecution(
		final ActionKey anActionKey,
		final BiConsumer<T1, T2> anInteractionImpl,
		final Class<T1> anInputDataClass1,
		final Class<T2> anInputDataClass2);

	/**
	 * Registers a no-argument action implementation.
	 *
	 * @param anActionKey       action identifier
	 * @param anInteractionImpl action implementation
	 */
	public void registerActionExecution(final ActionKey anActionKey, final Runnable anInteractionImpl);

	/**
	 * Removes the action implementation associated with an action identifier.
	 *
	 * @param anActionKey action identifier
	 */
	public void unregisterActionExecution(final ActionKey anActionKey);

	/**
	 * Sets whether an action can be executed. Reported unconditionally by
	 * the action's own provider, whether or not anything currently
	 * listens.
	 *
	 * @param anActionKey action identifier
	 * @param isAvailable {@code true} makes the action executable
	 */
	public void setActionAvailable(final ActionKey anActionKey, final boolean isAvailable);

	/**
	 * Reports an action's current value, for a value the action's own
	 * provider owns and can change on its own (for example, a scale
	 * changed through a gesture the provider itself handles, not through
	 * {@code executeAction(...)}). Reported unconditionally, whether or
	 * not anything currently listens via
	 * {@link ActionConsumerServices#bindActionValue(ActionKey, Consumer)}.
	 *
	 * @param anActionKey action identifier
	 * @param aValue      the action's current value
	 */
	public void setActionValue(final ActionKey anActionKey, final Object aValue);

}
