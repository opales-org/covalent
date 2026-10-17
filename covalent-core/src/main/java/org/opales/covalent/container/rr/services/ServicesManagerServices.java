/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.rr.services;

import java.util.Map;
import java.util.Set;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Creates, tracks, activates, passivates, and deletes Covalent service
 * ports. This is the main services port exposed by the services manager and
 * consumed internally by other managers (e.g. the component and lifecycle
 * managers) to drive provider-side and client-side services proxies.
 */
@Services
public interface ServicesManagerServices {

	/**
	 * Reports whether a client port exists, including an unbound surrogate.
	 *
	 * @param componentType     the candidate client component type
	 * @param servicesInterface the services interface to check
	 * @return {@code true} if a client-side proxy exists for that pair
	 */
	boolean isServicesUsed(Class<?> componentType, Class<?> servicesInterface);

	/**
	 * Creates provider-side services for a services interface.
	 *
	 * @param <SERVICES>       services interface type
	 * @param servicesInterface services interface to provide
	 */
	public <SERVICES> void createProvidedServices(final Class<SERVICES> servicesInterface);

	/**
	 * Creates provider-side services for a component type and services interface.
	 *
	 * @param <SERVICES>        services interface type
	 * @param componentType      provider component type
	 * @param servicesInterface  services interface to provide
	 */
	<SERVICES> void createProvidedServices(final Class<?> componentType, final Class<SERVICES> servicesInterface);

	/**
	 * Returns the provider-side proxy of services provider.
	 * 
	 * @param <SERVICES>        services interface type
	 * @param servicesInterface services interface to inspect
	 * @return provider-side services proxy
	 */
	<SERVICES> SERVICES getServicesProvider(final Class<SERVICES> servicesInterface);

	/**
	 * Creates a user-side proxy for a services interface.
	 * 
	 * @param <SERVICES>                services interface type
	 * @param servicesUserComponentType user component type
	 * @param servicesInterface         services interface to use
	 * @return user-side services proxy
	 */
	<SERVICES> SERVICES createUsedServices(final Class<?> servicesUserComponentType,
			final Class<SERVICES> servicesInterface);

	/**
	 * Same as {@link #createUsedServices(Class, Class)}, except that a
	 * component already using this services interface is tolerated instead
	 * of reported as a duplicate usage: the existing user-side proxy is
	 * simply handed back.
	 * <p>
	 * This is meant for the lifecycle machinery, to reconnect a used
	 * services port that may still be registered from before a
	 * passivate/activate cycle (used services stay functioning across
	 * passivation - they are only ever torn down on removal), not for
	 * application code, which should keep using
	 * {@link #createUsedServices(Class, Class)} so an actual duplicate
	 * manual use is still reported.
	 *
	 * @param <SERVICES>                services interface type
	 * @param servicesUserComponentType user component type
	 * @param servicesInterface         services interface to use
	 * @return user-side services proxy
	 */
	<SERVICES> SERVICES reconnectUsedServices(final Class<?> servicesUserComponentType,
			final Class<SERVICES> servicesInterface);

	/**
	 * Deletes the user-side proxy for a services interface.
	 *
	 * @param <SERVICES>        services interface type
	 * @param userComponentType user component type
	 * @param servicesInterface used services interface
	 */
	<SERVICES> void deleteUsedServices(final Class<?> userComponentType, final Class<SERVICES> servicesInterface);

	/**
	 * Reports whether a services interface has provider-side services.
	 *
	 * @param servicesInterface services interface to inspect
	 * @return {@code true} when the services interface is provided
	 */
	boolean isServicesProvided(final Class<?> servicesInterface);

	/**
	 * Deletes provider-side services for a services interface.
	 *
	 * @param <SERVICES>        services interface type
	 * @param servicesInterface services interface to delete
	 */
	<SERVICES> void deleteProvidedServices(final Class<SERVICES> servicesInterface);

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
	 * @param <SERVICES>        services interface type
	 * @param servicesInterface services interface to activate
	 */
	<SERVICES> void activateServices(final Class<SERVICES> servicesInterface);

	/**
	 * Passivates provider-side services for a services interface.
	 *
	 * @param <SERVICES>        services interface type
	 * @param servicesInterface services interface to passivate
	 */
	<SERVICES> void passivateServices(final Class<SERVICES> servicesInterface);

	/**
	 * Reports whether provider-side services are activated.
	 *
	 * @param <SERVICES>        services interface type
	 * @param servicesInterface services interface to inspect
	 * @return {@code true} when the services interface is activated
	 */
	<SERVICES> boolean isActivated(final Class<SERVICES> servicesInterface);

}
