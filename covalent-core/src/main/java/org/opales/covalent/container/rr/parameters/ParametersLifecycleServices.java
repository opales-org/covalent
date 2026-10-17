/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.rr.parameters;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Internal {@code @Services} port implemented by
 * {@link org.opales.covalent.container.rr.parameters.impl.ParametersLifecycleManagerImpl}
 * and used by the component lifecycle machinery to drive the offered
 * parameters ports of a component type through activation, publication,
 * unpublication, and passivation, mirroring the equivalent lifecycle
 * transitions applied to a component's other port families. Each method
 * iterates over every parameters interface the given component type offers,
 * as declared in the metamodel, and applies the corresponding transition to
 * each one that is not already in that state.
 */
@Services
public interface ParametersLifecycleServices {

	/**
	 * Activates every offered parameters interface declared by the given
	 * component type that is not already activated.
	 *
	 * @param <PARAMETERS>  parameters interface type
	 * @param componentType offeror component type
	 */
	<PARAMETERS> void activateOfferedParameters(Class<?> componentType);

	/**
	 * Publishes (binds in the directory) every offered parameters interface
	 * declared by the given component type that is not already published.
	 *
	 * @param <PARAMETERS>  parameters interface type
	 * @param componentType offeror component type
	 */
	<PARAMETERS> void publishOfferedParameters(Class<?> componentType);

	/**
	 * Unpublishes (unbinds from the directory) every offered parameters
	 * interface declared by the given component type that is currently
	 * published.
	 *
	 * @param <PARAMETERS>  parameters interface type
	 * @param componentType offeror component type
	 */
	<PARAMETERS> void unpublishOfferedParameters(Class<?> componentType);

	/**
	 * Passivates every offered parameters interface declared by the given
	 * component type that is currently offered and activated.
	 *
	 * @param <PARAMETERS>  parameters interface type
	 * @param componentType offeror component type
	 */
	<PARAMETERS> void passivateOfferedParameters(Class<?> componentType);

}
