/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.supervision.observed;

import java.util.Map;
import java.util.Set;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Mirror of {@link org.opales.covalent.container.rr.services.ServicesManagerServices},
 * rooted in the <em>observed</em> container - see {@link ObservedRegistryReadServices}
 * for why a distinct type is needed instead of reusing the original. Full
 * surface duplicated, including the mutation methods no supervision
 * function calls: a faithful mirror is cheaper to maintain than a trimmed
 * copy that would have to be widened when a supervision feature needs one
 * of them.
 */
@Services
public interface ObservedServicesManagerServices {

	/**
	 * Reports whether a client port exists, including an unbound surrogate.
	 *
	 * @param componentType candidate client component type
	 * @param servicesInterface services interface to check
	 * @return {@code true} if a client-side proxy exists for that pair
	 */
	boolean isServicesUsed(Class<?> componentType, Class<?> servicesInterface);

	/**
	 * Creates provider-side services for a services interface.
	 *
	 * @param <SERVICES> services interface type
	 * @param servicesInterface services interface to provide
	 */
	<SERVICES> void createProvidedServices(Class<SERVICES> servicesInterface);

	/**
	 * Creates provider-side services for a component type and services interface.
	 *
	 * @param <SERVICES> services interface type
	 * @param componentType provider component type
	 * @param servicesInterface services interface to provide
	 */
	<SERVICES> void createProvidedServices(Class<?> componentType, Class<SERVICES> servicesInterface);

	/**
	 * Returns the provider-side proxy of a services interface.
	 *
	 * @param <SERVICES> services interface type
	 * @param servicesInterface services interface to inspect
	 * @return provider-side services proxy
	 */
	<SERVICES> SERVICES getServicesProvider(Class<SERVICES> servicesInterface);

	/**
	 * Creates a user-side proxy for a services interface.
	 *
	 * @param <SERVICES> services interface type
	 * @param servicesUserComponentType user component type
	 * @param servicesInterface services interface to use
	 * @return user-side services proxy
	 */
	<SERVICES> SERVICES createUsedServices(Class<?> servicesUserComponentType,
			Class<SERVICES> servicesInterface);

	/**
	 * Creates a user-side proxy for a services interface, handing back the existing one when the component
	 *     already uses it.
	 *
	 * @param <SERVICES> services interface type
	 * @param servicesUserComponentType user component type
	 * @param servicesInterface services interface to use
	 * @return user-side services proxy
	 */
	<SERVICES> SERVICES reconnectUsedServices(Class<?> servicesUserComponentType,
			Class<SERVICES> servicesInterface);

	/**
	 * Deletes the user-side proxy for a services interface.
	 *
	 * @param <SERVICES> services interface type
	 * @param userComponentType user component type
	 * @param servicesInterface used services interface
	 */
	<SERVICES> void deleteUsedServices(Class<?> userComponentType, Class<SERVICES> servicesInterface);

	/**
	 * Reports whether a services interface has provider-side services.
	 *
	 * @param servicesInterface services interface to inspect
	 * @return {@code true} when the services interface is provided
	 */
	boolean isServicesProvided(Class<?> servicesInterface);

	/**
	 * Deletes provider-side services for a services interface.
	 *
	 * @param <SERVICES> services interface type
	 * @param servicesInterface services interface to delete
	 */
	<SERVICES> void deleteProvidedServices(Class<SERVICES> servicesInterface);

	/**
	 * Returns the services interfaces provided by each component type.
	 *
	 * @return provided services grouped by provider component type
	 */
	Map<Class<?>, Set<Class<?>>> getProviderToServices();

	/**
	 * Returns the component types using each services interface.
	 *
	 * @return services users grouped by services interface
	 */
	Map<Class<?>, Set<Class<?>>> getServiceToUsers();

	/**
	 * Activates provider-side services for a services interface.
	 *
	 * @param <SERVICES> services interface type
	 * @param servicesInterface services interface to activate
	 */
	<SERVICES> void activateServices(Class<SERVICES> servicesInterface);

	/**
	 * Passivates provider-side services for a services interface.
	 *
	 * @param <SERVICES> services interface type
	 * @param servicesInterface services interface to passivate
	 */
	<SERVICES> void passivateServices(Class<SERVICES> servicesInterface);

	/**
	 * Reports whether provider-side services are activated.
	 *
	 * @param <SERVICES> services interface type
	 * @param servicesInterface services interface to inspect
	 * @return {@code true} when the services interface is activated
	 */
	<SERVICES> boolean isActivated(Class<SERVICES> servicesInterface);

}
