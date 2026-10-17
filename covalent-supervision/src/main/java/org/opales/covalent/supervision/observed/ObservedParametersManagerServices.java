/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.observed;

import java.util.Map;
import java.util.Set;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Mirror of {@link org.opales.covalent.container.rr.parameters.ParametersManagerServices},
 * rooted in the <em>observed</em> container - see {@link ObservedRegistryReadServices}
 * for why a distinct type is needed instead of reusing the original. Full
 * surface duplicated, same rationale as {@link ObservedServicesManagerServices}.
 */
@Services
public interface ObservedParametersManagerServices {

	/**
	 * Reports whether a client port exists for a component type and parameters interface.
	 *
	 * @param componentType candidate accessor component type
	 * @param parametersInterface parameters interface to check
	 * @return {@code true} if an accessor-side proxy exists for that pair
	 */
	boolean isParametersAccessed(Class<?> componentType, Class<?> parametersInterface);

	/**
	 * Creates offeror-side parameters for a parameters interface.
	 *
	 * @param <PARAMETERS> parameters interface type
	 * @param parametersInterface parameters interface to offer
	 */
	<PARAMETERS> void createOfferedParameters(Class<PARAMETERS> parametersInterface);

	/**
	 * Creates offeror-side parameters for a component type and parameters interface.
	 *
	 * @param <PARAMETERS> parameters interface type
	 * @param componentType offeror component type
	 * @param parametersInterface parameters interface to offer
	 */
	<PARAMETERS> void createOfferedParameters(Class<?> componentType, Class<PARAMETERS> parametersInterface);

	/**
	 * Returns the offeror-side proxy of a parameters interface.
	 *
	 * @param <PARAMETERS> parameters interface type
	 * @param parametersInterface parameters interface to inspect
	 * @return offeror-side parameters proxy
	 */
	<PARAMETERS> PARAMETERS getParametersOfferor(Class<PARAMETERS> parametersInterface);

	/**
	 * Creates an accessor-side proxy for a parameters interface.
	 *
	 * @param <PARAMETERS> parameters interface type
	 * @param parametersAccessorComponentType accessor component type
	 * @param parametersInterface parameters interface to access
	 * @return accessor-side parameters proxy
	 */
	<PARAMETERS> PARAMETERS createAccessedParameters(Class<?> parametersAccessorComponentType,
			Class<PARAMETERS> parametersInterface);

	/**
	 * Deletes the accessor-side proxy for a parameters interface.
	 *
	 * @param <PARAMETERS> parameters interface type
	 * @param accessorComponentType accessor component type
	 * @param parametersInterface accessed parameters interface
	 */
	<PARAMETERS> void deleteAccessedParameters(Class<?> accessorComponentType,
			Class<PARAMETERS> parametersInterface);

	/**
	 * Reports whether a parameters interface has offeror-side parameters.
	 *
	 * @param parametersInterface parameters interface to inspect
	 * @return {@code true} when the parameters interface is offered
	 */
	boolean isParametersOffered(Class<?> parametersInterface);

	/**
	 * Deletes offeror-side parameters for a parameters interface.
	 *
	 * @param <PARAMETERS> parameters interface type
	 * @param parametersInterface parameters interface to delete
	 */
	<PARAMETERS> void deleteOfferedParameters(Class<PARAMETERS> parametersInterface);

	/**
	 * Returns the parameters interfaces offered by each component type.
	 *
	 * @return offered parameters grouped by offeror component type
	 */
	Map<Class<?>, Set<Class<?>>> getOfferorToParameters();

	/**
	 * Returns the component types accessing each parameters interface.
	 *
	 * @return parameter accessors grouped by parameters interface
	 */
	Map<Class<?>, Set<Class<?>>> getParametersToAccessor();

	/**
	 * Activates offeror-side parameters for a parameters interface.
	 *
	 * @param <PARAMETERS> parameters interface type
	 * @param parametersInterface parameters interface to activate
	 */
	<PARAMETERS> void activateParameters(Class<PARAMETERS> parametersInterface);

	/**
	 * Passivates offeror-side parameters for a parameters interface.
	 *
	 * @param <PARAMETERS> parameters interface type
	 * @param parametersInterface parameters interface to passivate
	 */
	<PARAMETERS> void passivateParameters(Class<PARAMETERS> parametersInterface);

}
