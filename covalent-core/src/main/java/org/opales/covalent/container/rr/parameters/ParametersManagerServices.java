/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.rr.parameters;

import java.util.Map;
import java.util.Set;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Internal {@code @Services} port implemented by
 * {@link org.opales.covalent.container.rr.parameters.impl.ParametersManagerImpl}:
 * creates, tracks, activates, passivates, and deletes Covalent parameters
 * ports, both offeror-side (an offered parameters interface a component
 * implements) and accessor-side (a client-side proxy a component uses to
 * set another component's parameters). This is the parameters
 * flavor-specific counterpart of
 * {@link org.opales.covalent.container.rr.services.ServicesManagerServices}.
 */
@Services
public interface ParametersManagerServices {

	/**
	 * Reports whether a client port exists for the given component type and
	 * parameters interface, including one still backed by an unbound
	 * surrogate proxy (i.e. no offeror is currently published).
	 *
	 * @param componentType       the candidate accessor component type
	 * @param parametersInterface the parameters interface to check
	 * @return {@code true} if a client-side proxy exists for that pair
	 */
	boolean isParametersAccessed(Class<?> componentType, Class<?> parametersInterface);

	/**
	 * Creates offeror-side parameters for a parameters interface.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param parametersInterface parameters interface to offer
	 */
	public <PARAMETERS> void createOfferedParameters(final Class<PARAMETERS> parametersInterface);

	/**
	 * Creates offeror-side parameters for a component type and parameters
	 * interface.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param componentType       offeror component type
	 * @param servicesInterface   parameters interface to offer
	 */
	<PARAMETERS> void createOfferedParameters(final Class<?> componentType, final Class<PARAMETERS> servicesInterface);

	/**
	 * Returns the offeror-side proxy of parameters offerors.
	 * 
	 * @param <PARAMETERS>        parameters interface type
	 * @param parametersInterface parameters interface to inspect
	 * @return offeror-side parameters proxy
	 */
	<PARAMETERS> PARAMETERS getParametersOfferor(final Class<PARAMETERS> parametersInterface);

	/**
	 * Creates an accessor-side proxy for a parameters interface.
	 * 
	 * @param <PARAMETERS>                    parameters interface type
	 * @param parametersAccessorComponentType accessor component type
	 * @param parametersInterface             parameters interface to access
	 * @return accessor-side parameters proxy
	 */
	<PARAMETERS> PARAMETERS createAccessedParameters(final Class<?> parametersAccessorComponentType,
			final Class<PARAMETERS> parametersInterface);

	/**
	 * Deletes the accessor-side proxy for a parameters interface.
	 *
	 * @param <PARAMETERS>          parameters interface type
	 * @param accessorComponentType accessor component type
	 * @param parametersInterface   accessed parameters interface
	 */
	<PARAMETERS> void deleteAccessedParameters(final Class<?> accessorComponentType,
			final Class<PARAMETERS> parametersInterface);

	/**
	 * Reports whether a parameters interface has offeror-side parameters.
	 *
	 * @param parametersInterface parameters interface to inspect
	 * @return {@code true} when the parameters interface is offered
	 */
	boolean isParametersOffered(final Class<?> parametersInterface);

	/**
	 * Deletes offeror-side parameters for a parameters interface.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param parametersInterface parameters interface to delete
	 */
	<PARAMETERS> void deleteOfferedParameters(final Class<PARAMETERS> parametersInterface);

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
	 * @param <PARAMETERS>        parameters interface type
	 * @param parametersInterface parameters interface to activate
	 */
	<PARAMETERS> void activateParameters(final Class<PARAMETERS> parametersInterface);

	/**
	 * Passivates offeror-side parameters for a parameters interface.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param parametersInterface parameters interface to passivate
	 */
	<PARAMETERS> void passivateParameters(final Class<PARAMETERS> parametersInterface);

}
