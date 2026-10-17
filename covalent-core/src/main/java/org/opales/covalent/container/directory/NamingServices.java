/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.directory;

import org.opales.covalent.container.component.annotations.Services;

/**
 * Binds and looks up published Covalent service, event subscribe, and parameter
 * ports by interface.
 */
@Services
public interface NamingServices {
	/**
	 * Binds a services proxy to a services interface.
	 *
	 * @param <SERVICES>        services interface type
	 * @param servicesInterface services interface to bind
	 * @param services          services proxy to publish
	 */
	<SERVICES> void bindServices(Class<SERVICES> servicesInterface, SERVICES services);

	/**
	 * Removes the services binding for a services interface.
	 *
	 * @param <SERVICES>        services interface type
	 * @param servicesInterface services interface to unbind
	 */
	<SERVICES> void unbindServices(Class<SERVICES> servicesInterface);

	/**
	 * Reports whether a services interface is bound.
	 *
	 * @param <SERVICES>        services interface type
	 * @param servicesInterface services interface to inspect
	 * @return {@code true} when services are bound
	 */
	<SERVICES> boolean isServicesBound(Class<SERVICES> servicesInterface);

	/**
	 * Looks up services bound to a services interface.
	 *
	 * @param <SERVICES>        services interface type
	 * @param servicesInterface services interface to look up
	 * @return bound services proxy
	 */
	<SERVICES> SERVICES lookupServices(Class<SERVICES> servicesInterface);

	// we must bind on the events interface, because generic subscribe interface is
	// shared among several events interfaces
	/**
	 * Binds a subscribe proxy to an events interface.
	 *
	 * @param <EVENTS>        events interface type
	 * @param <SUBSCRIBE>     subscribe interface type
	 * @param eventsInterface events interface to bind
	 * @param subscribe       subscribe proxy to publish
	 */
	<EVENTS, SUBSCRIBE> void bindSubscriber(Class<EVENTS> eventsInterface, SUBSCRIBE subscribe);

	/**
	 * Removes the subscribe binding for an events interface.
	 *
	 * @param <EVENTS>        events interface type
	 * @param eventsInterface events interface to unbind
	 */
	<EVENTS> void unbindSubscriber(final Class<EVENTS> eventsInterface);

	/**
	 * Reports whether an events interface has a bound subscribe proxy.
	 *
	 * @param <EVENTS>        events interface type
	 * @param eventsInterface events interface to inspect
	 * @return {@code true} when a subscribe proxy is bound
	 */
	<EVENTS> boolean isSubscriberBound(Class<EVENTS> eventsInterface);

	/**
	 * Looks up the subscribe proxy bound to an events interface.
	 *
	 * @param <EVENTS>        events interface type
	 * @param <SUBSCRIBE>     subscribe interface type
	 * @param eventsInterface events interface to look up
	 * @return bound subscribe proxy
	 */
	<EVENTS, SUBSCRIBE> SUBSCRIBE lookupSubscriber(Class<EVENTS> eventsInterface);

	/**
	 * Binds a parameters proxy to a parameters interface.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param parametersInterface parameters interface to bind
	 * @param parameters          parameters proxy to publish
	 */
	<PARAMETERS> void bindParameters(Class<PARAMETERS> parametersInterface, PARAMETERS parameters);

	/**
	 * Removes the parameters binding for a parameters interface.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param parametersInterface parameters interface to unbind
	 */
	<PARAMETERS> void unbindParameters(Class<PARAMETERS> parametersInterface);

	/**
	 * Reports whether a parameters interface is bound.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param parametersInterface parameters interface to inspect
	 * @return {@code true} when parameters are bound
	 */
	<PARAMETERS> boolean isParametersBound(Class<PARAMETERS> parametersInterface);

	/**
	 * Looks up parameters bound to a parameters interface.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param parametersInterface parameters interface to look up
	 * @return bound parameters proxy
	 */
	<PARAMETERS> PARAMETERS lookupParameters(Class<PARAMETERS> parametersInterface);

}
