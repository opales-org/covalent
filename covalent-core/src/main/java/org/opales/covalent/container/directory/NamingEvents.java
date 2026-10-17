/*
 * Copyright (c) 2026 opales.org and Covalent contributors.
 * SPDX-License-Identifier: MPL-2.0
 */
package org.opales.covalent.container.directory;

import org.opales.covalent.container.component.annotations.Events;

/**
 * Reports directory binding changes.
 */
@Events
public interface NamingEvents {
	/**
	 * Notifies that a services interface has been bound.
	 *
	 * @param <SERVICES>        services interface type
	 * @param servicesInterface bound services interface
	 */
	<SERVICES> void onServicesBound(Class<SERVICES> servicesInterface);

	/**
	 * Notifies that a services interface has been unbound.
	 *
	 * @param <SERVICES>        services interface type
	 * @param servicesInterface unbound services interface
	 */
	<SERVICES> void onServicesUnbound(Class<SERVICES> servicesInterface);

	/**
	 * Notifies that an events interface has a bound subscribe proxy.
	 *
	 * @param <EVENTS>        events interface type
	 * @param eventsInterface events interface whose subscribe proxy was bound
	 */
	<EVENTS> void onSubscriberBound(Class<EVENTS> eventsInterface);

	/**
	 * Notifies that an events interface has had its subscribe proxy unbound.
	 *
	 * @param <EVENTS>        events interface type
	 * @param eventsInterface events interface whose subscribe proxy was unbound
	 */
	<EVENTS> void onSubscriberUnbound(final Class<EVENTS> eventsInterface);

	/**
	 * Notifies that a parameters interface has been bound.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param parametersInterface bound parameters interface
	 */
	<PARAMETERS> void onParametersBound(Class<PARAMETERS> parametersInterface);

	/**
	 * Notifies that a parameters interface has been unbound.
	 *
	 * @param <PARAMETERS>        parameters interface type
	 * @param parametersInterface unbound parameters interface
	 */
	<PARAMETERS> void onParametersUnbound(Class<PARAMETERS> parametersInterface);

}
